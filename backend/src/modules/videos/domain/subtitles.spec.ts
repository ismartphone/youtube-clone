import {
  hasSpeech,
  spokenWordCount,
  srtToWebVtt,
  transcriptionTimeoutMs,
} from './subtitles.js';

describe('transcriptionTimeoutMs', () => {
  it('laisse deux fois la durée de la vidéo', () => {
    expect(transcriptionTimeoutMs(37)).toBe(74_000);
  });

  it('plafonne à 5 minutes', () => {
    expect(transcriptionTimeoutMs(1658)).toBe(300_000);
  });
});

describe('srtToWebVtt', () => {
  it('convertit les temps et retire les numéros de bloc', () => {
    const srt =
      '0\r\n00:00:00,000 --> 00:00:03,200\r\nBonjour à tous.\r\n\r\n1\r\n00:00:03,200 --> 00:00:06,800\r\nOn commence.\r\n';
    expect(srtToWebVtt(srt)).toBe(
      'WEBVTT\n\n00:00:00.000 --> 00:00:03.200\nBonjour à tous.\n\n00:00:03.200 --> 00:00:06.800\nOn commence.\n',
    );
  });

  it('répartit une réplique longue sur deux lignes équilibrées de 42 caractères au plus', () => {
    const srt =
      "1\n00:00:30,029 --> 00:00:35,569\nmother after all. I don't know what the future holds. I do know that I love you.\n";
    expect(srtToWebVtt(srt)).toBe(
      "WEBVTT\n\n00:00:30.029 --> 00:00:35.569\nmother after all. I don't know what the\nfuture holds. I do know that I love you.\n",
    );
  });

  it('scinde une réplique trop longue pour deux lignes, son temps réparti selon le texte', () => {
    const words = Array.from({ length: 30 }, () => 'mot');
    const vtt = srtToWebVtt(
      `1\n00:00:00,000 --> 00:00:12,000\n${words.join(' ')}\n`,
    )!;
    const cues = vtt.split('\n\n').slice(1);
    expect(cues.length).toBe(2);
    for (const cue of cues) {
      const [, ...lines] = cue.trim().split('\n');
      expect(lines.length).toBeLessThanOrEqual(2);
      lines.forEach((line) => expect(line.length).toBeLessThanOrEqual(42));
    }
    expect(cues[0].startsWith('00:00:00.000 --> ')).toBe(true);
    expect(cues[1]).toContain('--> 00:00:12.000');
  });

  it('remplace « --> », réservé aux lignes de temps en WebVTT', () => {
    expect(
      srtToWebVtt('1\n00:00:00,000 --> 00:00:01,000\na --> b\n'),
    ).toContain('\na -> b\n');
  });

  it('renvoie null sans réplique exploitable', () => {
    expect(srtToWebVtt('')).toBeNull();
    expect(srtToWebVtt('1\n00:00:00,000 --> 00:00:01,000\n\n')).toBeNull();
    expect(srtToWebVtt('texte sans ligne de temps')).toBeNull();
  });
});

// Transcription réelle de whisper sur 30 s de bruit de rue (« Une demi-minute en ville »).
const STREET_NOISE = [
  '0',
  '00:00:00,000 --> 00:00:20,000',
  '[Music]',
  '',
  '1',
  '00:00:19,988 --> 00:00:21,988',
  '(crickets chirping)',
  '',
  '2',
  '00:00:21,988 --> 00:00:23,908',
  '- We should put that.',
  '',
  '3',
  '00:00:23,908 --> 00:00:25,428',
  "- That's our station.",
  '',
].join('\n');

describe('spokenWordCount', () => {
  it('ne compte ni les numéros, ni les temps, ni les annotations de bruit', () => {
    expect(spokenWordCount(STREET_NOISE)).toBe(7);
    expect(
      spokenWordCount('1\n00:00:00,000 --> 00:00:02,000\n♪ [Musique] ♪\n'),
    ).toBe(0);
  });
});

describe('hasSpeech', () => {
  it('écarte une vidéo de plus de 20 s transcrite en moins de 10 mots', () => {
    expect(hasSpeech(STREET_NOISE, 30)).toBe(false);
  });

  it('garde une vidéo de plus de 20 s avec au moins 10 mots', () => {
    const srt =
      "1\n00:00:00,000 --> 00:00:05,000\nHere's how you turn a 3D model into a real sheep.\n";
    expect(hasSpeech(srt, 37)).toBe(true);
  });

  it('se contente d’un mot pour une vidéo de 20 s ou moins', () => {
    expect(hasSpeech('1\n00:00:00,000 --> 00:00:01,000\nBonjour\n', 20)).toBe(
      true,
    );
    expect(hasSpeech('1\n00:00:00,000 --> 00:00:01,000\n[Music]\n', 20)).toBe(
      false,
    );
  });
});
