import { isDownloadable } from './download.use-cases.js';

describe('isDownloadable', () => {
  it('accepte une vidéo hébergée chez nous', () => {
    expect(isDownloadable('/media/videos/cc-04.mp4')).toBe(true);
  });

  it('refuse une vidéo externe ou un autre fichier du stockage', () => {
    expect(isDownloadable('https://cdn.example.com/video.mp4')).toBe(false);
    expect(isDownloadable('/media/hls/abc/master.m3u8')).toBe(false);
  });
});
