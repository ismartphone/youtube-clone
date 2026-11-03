// Sous-titres générés automatiquement (ADR-017) : transcription hors ligne par le filtre whisper
// de FFmpeg, une fois par vidéo hébergée chez nous, en WebVTT sous /media/subtitles/.

export const SUBTITLE_GENERATOR = Symbol('SUBTITLE_GENERATOR');
export const SUBTITLE_QUEUE = Symbol('SUBTITLE_QUEUE');

// Au-delà de deux fois la durée de la vidéo, et de 5 minutes au plus, la transcription est abandonnée.
const MAX_TRANSCRIPTION_SECONDS = 5 * 60;

export function transcriptionTimeoutMs(durationSeconds: number): number {
  return Math.min(2 * durationSeconds, MAX_TRANSCRIPTION_SECONDS) * 1000;
}

// Ligne de temps SRT : « 00:00:03,200 --> 00:00:06,800 ». WebVTT veut un point avant les millisecondes.
const SRT_TIMING =
  /^(\d{2,}:\d{2}:\d{2}),(\d{3})\s+-->\s+(\d{2,}:\d{2}:\d{2}),(\d{3})/;

/**
 * Convertit un SRT, seul format horodaté que sait produire le filtre whisper, en WebVTT, seul
 * format de sous-titres lu par le lecteur. Les numéros de bloc du SRT disparaissent ; un bloc
 * sans ligne de temps valide est ignoré. Renvoie null si aucune réplique n'a de texte : la vidéo
 * n'a alors pas de sous-titres.
 */
export function srtToWebVtt(srt: string): string | null {
  const cues: string[] = [];
  for (const block of srt.replace(/\r/g, '').split(/\n{2,}/)) {
    const lines = block.split('\n').filter((line) => line.trim() !== '');
    const timingIndex = lines.findIndex((line) => SRT_TIMING.test(line));
    if (timingIndex === -1) {
      continue;
    }
    const text = lines.slice(timingIndex + 1).map((line) => line.trim());
    if (text.length === 0) {
      continue;
    }
    const timing = lines[timingIndex].replace(SRT_TIMING, '$1.$2 --> $3.$4');
    // « --> » est réservé à la ligne de temps en WebVTT.
    cues.push(
      [timing, ...text.map((line) => line.replace(/-->/g, '->'))].join('\n'),
    );
  }
  return cues.length === 0 ? null : `WEBVTT\n\n${cues.join('\n\n')}\n`;
}

// Garde-fou contre les répliques inventées : sur un fond sonore sans parole (rue, musique), la
// reconnaissance vocale produit quelques phrases sans rapport. Une vidéo de plus de 20 secondes
// dont la transcription compte moins de 10 mots est considérée comme sans parole.
const MIN_SPOKEN_WORDS = 10;
const WORD_CHECK_MIN_SECONDS = 20;
// Annotations de bruit ajoutées par whisper : « [Music] », « (crickets chirping) », « ♪ ».
const SOUND_ANNOTATION = /\[[^\]]*\]|\([^)]*\)|♪/g;
const WORD = /[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*/gu;

/** Nombre de mots prononcés dans un SRT : ni numéros de bloc, ni lignes de temps, ni annotations. */
export function spokenWordCount(srt: string): number {
  return (
    srt
      .replace(/\r/g, '')
      .split('\n')
      .filter((line) => !SRT_TIMING.test(line) && !/^\s*\d+\s*$/.test(line))
      .join(' ')
      .replace(SOUND_ANNOTATION, ' ')
      .match(WORD)?.length ?? 0
  );
}

/** Vrai si la transcription [srt] d'une vidéo de [durationSeconds] contient vraiment de la parole. */
export function hasSpeech(srt: string, durationSeconds: number): boolean {
  const words = spokenWordCount(srt);
  if (durationSeconds > WORD_CHECK_MIN_SECONDS) {
    return words >= MIN_SPOKEN_WORDS;
  }
  return words > 0;
}

export interface SubtitleGenerator {
  // Vrai si la vidéo est hébergée chez nous (/media/videos/...) : seule celle-ci a des sous-titres.
  isLocal(videoUrl: string): boolean;
  /**
   * Transcrit la vidéo [videoUrl], longue de [durationSeconds], et renvoie l'URL relative du
   * fichier WebVTT. Lève une erreur si FFmpeg n'a pas le filtre whisper, si le modèle manque, si
   * la vidéo n'a pas de piste audio, si la transcription ne contient pas de parole (hasSpeech) ou
   * dépasse son temps (transcriptionTimeoutMs). Rien n'est alors laissé sur le disque.
   */
  generate(videoUrl: string, durationSeconds: number): Promise<string>;
  // Supprime un fichier de sous-titres généré ; sans effet pour toute autre URL.
  remove(subtitleUrl: string): Promise<void>;
}

// File de transcription en tâche de fond : la publication n'attend jamais les sous-titres (ADR-017).
export interface SubtitleQueue {
  enqueue(videoId: string): void;
}
