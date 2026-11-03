import { hlsRungs } from './hls.js';

const sizes = (width: number, height: number) =>
  hlsRungs(width, height).map((rung) => rung.size);

describe('hlsRungs', () => {
  it('plafonne à 720p une source 1080p', () => {
    expect(sizes(1920, 1080)).toEqual([720, 480, 360]);
  });

  it("n'agrandit jamais la source", () => {
    expect(sizes(854, 480)).toEqual([480, 360]);
    expect(sizes(640, 360)).toEqual([360]);
  });

  it('compare le petit côté : la largeur pour un Short vertical', () => {
    expect(sizes(720, 1280)).toEqual([720, 480, 360]);
    expect(sizes(360, 640)).toEqual([360]);
  });

  it('garde une seule qualité, à sa taille paire, sous 360', () => {
    expect(hlsRungs(426, 241)).toEqual([{ size: 240, videoKbps: 600 }]);
  });
});
