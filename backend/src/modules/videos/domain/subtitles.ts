// Sous-titres générés automatiquement (ADR-017) : transcription hors ligne par le filtre whisper
// de FFmpeg, une fois par vidéo hébergée chez nous, en WebVTT sous /media/subtitles/.

export const SUBTITLE_GENERATOR = Symbol('SUBTITLE_GENERATOR');
export const SUBTITLE_QUEUE = Symbol('SUBTITLE_QUEUE');

// Au-delà de deux fois la durée de la vidéo, et de 5 minutes au plus, la transcription est abandonnée.
const MAX_TRANSCRIPTION_SECONDS = 5 * 60;

export function transcriptionTimeoutMs(durationSeconds: number): number {
  return Math.min(2 * durationSeconds, MAX_TRANSCRIPTION_SECONDS) * 1000;
}

// Ligne de temps SRT : « 00:00:03,200 --> 00:00:06,800 ».
const SRT_TIMING =
  /^(\d{2,}):(\d{2}):(\d{2}),(\d{3})\s+-->\s+(\d{2,}):(\d{2}):(\d{2}),(\d{3})/;

// Règle du sous-titrage : deux lignes au plus, d'environ 40 caractères chacune. Le filtre whisper
// limite déjà chaque réplique (SUBTITLE_MAX_CHARS) ; la conversion la répartit sur deux lignes.
export const SUBTITLE_MAX_CHARS = 80;
const MAX_LINE_CHARS = 42;
const MAX_LINES = 2;
// Une réplique reste affichée au moins une seconde, le temps d'être lue.
const MIN_CUE_MS = 1000;

interface Cue {
  start: number;
  end: number;
  text: string;
}

/**
 * Convertit un SRT, seul format horodaté que sait produire le filtre whisper, en WebVTT, seul
 * format de sous-titres lu par le lecteur. Les numéros de bloc du SRT disparaissent ; un bloc
 * sans ligne de temps valide est ignoré. Chaque réplique tient sur deux lignes d'au plus
 * 42 caractères ; une réplique plus longue est scindée, son temps réparti selon la longueur de
 * chaque morceau. Une réplique de moins d'une seconde rejoint la précédente quand l'ensemble
 * tient sur deux lignes ; sinon, elle est allongée jusqu'à une seconde, quitte à retarder un peu
 * la suivante. Renvoie null si aucune réplique n'a de texte : la vidéo n'a alors pas de sous-titres.
 */
export function srtToWebVtt(srt: string): string | null {
  const merged: Cue[] = [];
  for (const cue of parseSrt(srt)) {
    const previous = merged.at(-1);
    const text = previous && `${previous.text} ${cue.text}`;
    if (
      previous &&
      text &&
      cue.end - cue.start < MIN_CUE_MS &&
      wrapLines(text).length === 1
    ) {
      previous.end = cue.end;
      previous.text = text;
    } else {
      merged.push({ ...cue });
    }
  }
  // Précédente déjà pleine : la réplique brève rejoint la suivante, si l'ensemble tient.
  for (let i = merged.length - 2; i >= 0; i--) {
    const cue = merged[i];
    const next = merged[i + 1];
    const text = `${cue.text} ${next.text}`;
    if (cue.end - cue.start < MIN_CUE_MS && wrapLines(text).length === 1) {
      next.start = cue.start;
      next.text = text;
      merged.splice(i, 1);
    }
  }
  const cues: Cue[] = merged.flatMap(splitCue);
  if (cues.length === 0) {
    return null;
  }
  // whisper fait parfois chevaucher deux répliques : la première s'arrête au début de la suivante.
  // Puis une seconde au moins par réplique : la suivante commence au plus tôt à la fin de
  // celle-ci. Le léger retard ainsi pris se résorbe au premier blanc dans la parole.
  let previousEnd = 0;
  const blocks = cues.map((cue, i) => {
    const start = Math.max(cue.start, previousEnd);
    const nextStart = cues[i + 1]?.start ?? Infinity;
    const end = Math.max(Math.min(cue.end, nextStart), start + MIN_CUE_MS);
    previousEnd = end;
    return `${vttTime(start)} --> ${vttTime(end)}\n${cue.text}`;
  });
  return `WEBVTT\n\n${blocks.join('\n\n')}\n`;
}

// Répliques du SRT, texte sur une seule ligne. « --> » est réservé à la ligne de temps en WebVTT.
function parseSrt(srt: string): Cue[] {
  const cues: Cue[] = [];
  for (const block of srt.replace(/\r/g, '').split(/\n{2,}/)) {
    const lines = block.split('\n').filter((line) => line.trim() !== '');
    const timingIndex = lines.findIndex((line) => SRT_TIMING.test(line));
    if (timingIndex === -1) {
      continue;
    }
    const text = lines
      .slice(timingIndex + 1)
      .join(' ')
      .replace(/-->/g, '->')
      .replace(/\s+/g, ' ')
      .trim();
    if (text === '') {
      continue;
    }
    const t = SRT_TIMING.exec(lines[timingIndex])!.slice(1).map(Number);
    const start = toMs(t[0], t[1], t[2], t[3]);
    cues.push({
      start,
      end: Math.max(start, toMs(t[4], t[5], t[6], t[7])),
      text,
    });
  }
  return cues;
}

// Réplique répartie sur deux lignes, ou scindée en plusieurs si elle n'y tient pas : le temps
// est partagé selon la longueur de chaque morceau. Les lignes sont séparées par « \n ».
function splitCue({ start, end, text }: Cue): Cue[] {
  const chunks = wrapLines(text);
  const totalChars = chunks.reduce(
    (sum, chunk) => sum + chunk.join('').length,
    0,
  );
  const cues: Cue[] = [];
  let chunkStart = start;
  chunks.forEach((chunk, i) => {
    const chunkEnd =
      i === chunks.length - 1
        ? end
        : chunkStart +
          Math.round(((end - start) * chunk.join('').length) / totalChars);
    cues.push({ start: chunkStart, end: chunkEnd, text: chunk.join('\n') });
    chunkStart = chunkEnd;
  });
  return cues;
}

// Découpe [text] en répliques de deux lignes au plus. Deux lignes tiennent : coupure au mot le plus
// proche du milieu, pour des lignes équilibrées. Sinon, lignes remplies au plus, deux par réplique.
// Un mot plus long qu'une ligne reste entier.
function wrapLines(text: string): string[][] {
  if (text.length <= MAX_LINE_CHARS) {
    return [[text]];
  }
  const words = text.split(' ');
  let best: string[] | null = null;
  for (let i = 1; i < words.length; i++) {
    const first = words.slice(0, i).join(' ');
    const second = words.slice(i).join(' ');
    if (first.length <= MAX_LINE_CHARS && second.length <= MAX_LINE_CHARS) {
      const balance = Math.abs(first.length - second.length);
      if (!best || balance < Math.abs(best[0].length - best[1].length)) {
        best = [first, second];
      }
    }
  }
  if (best) {
    return [best];
  }
  const lines: string[] = [];
  for (const word of words) {
    const last = lines.at(-1);
    if (last !== undefined && last.length + 1 + word.length <= MAX_LINE_CHARS) {
      lines[lines.length - 1] = `${last} ${word}`;
    } else {
      lines.push(word);
    }
  }
  const chunks: string[][] = [];
  for (let i = 0; i < lines.length; i += MAX_LINES) {
    chunks.push(lines.slice(i, i + MAX_LINES));
  }
  return chunks;
}

function toMs(
  hours: number,
  minutes: number,
  seconds: number,
  ms: number,
): number {
  return ((hours * 60 + minutes) * 60 + seconds) * 1000 + ms;
}

// Temps WebVTT : « 00:00:03.200 », avec un point avant les millisecondes.
function vttTime(ms: number): string {
  const pad = (value: number, size = 2) => String(value).padStart(size, '0');
  const hours = Math.floor(ms / 3_600_000);
  const minutes = Math.floor(ms / 60_000) % 60;
  const seconds = Math.floor(ms / 1000) % 60;
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}.${pad(ms % 1000, 3)}`;
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

// Vidéos jamais sous-titrées : le modèle base de whisper ne gère correctement ni le tagalog
// (cc-03, détecté comme de l'allemand, texte sans rapport) ni les paroles chantées (cc-06, clip de
// rap, paroles en partie inventées). De mauvais sous-titres se remarquent plus qu'une absence de
// sous-titres. Limite de la reconnaissance vocale, pas du code (ADR-017).
export const SUBTITLES_DISABLED_FOR: readonly string[] = [
  '/media/videos/cc-03.mp4',
  '/media/videos/cc-06.mp4',
];

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
