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

export interface SubtitleGenerator {
  // Vrai si la vidéo est hébergée chez nous (/media/videos/...) : seule celle-ci a des sous-titres.
  isLocal(videoUrl: string): boolean;
  /**
   * Transcrit la vidéo [videoUrl] et renvoie l'URL relative du fichier WebVTT. Lève une erreur si
   * FFmpeg n'a pas le filtre whisper, si le modèle manque, si la vidéo n'a pas de piste audio,
   * si rien n'est transcrit ou si [timeoutMs] est dépassé (rien n'est laissé sur le disque).
   */
  generate(videoUrl: string, timeoutMs: number): Promise<string>;
  // Supprime un fichier de sous-titres généré ; sans effet pour toute autre URL.
  remove(subtitleUrl: string): Promise<void>;
}

// File de transcription en tâche de fond : la publication n'attend jamais les sous-titres (ADR-017).
export interface SubtitleQueue {
  enqueue(videoId: string): void;
}
