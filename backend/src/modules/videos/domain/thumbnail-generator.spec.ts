import {
  isPlaceholderThumbnail,
  thumbnailPosition,
  thumbnailSize,
} from './thumbnail-generator.js';

describe('thumbnailPosition', () => {
  it('prend l’image à 10 % de la durée', () => {
    expect(thumbnailPosition(120)).toBe(12);
    expect(thumbnailPosition(0)).toBe(0);
  });
});

describe('thumbnailSize', () => {
  it('met un Short en portrait et une vidéo classique en paysage', () => {
    expect(thumbnailSize('SHORT')).toEqual({ width: 720, height: 1280 });
    expect(thumbnailSize('LONG')).toEqual({ width: 1280, height: 720 });
  });
});

describe('isPlaceholderThumbnail', () => {
  it('reconnaît les images génériques de placehold.co', () => {
    expect(isPlaceholderThumbnail('https://placehold.co/1280x720')).toBe(true);
  });

  it('ne prend pour générique ni une vraie URL ni un chemin relatif', () => {
    expect(isPlaceholderThumbnail('https://cdn.example.com/a.jpg')).toBe(false);
    expect(isPlaceholderThumbnail('/media/thumbnails/a.jpg')).toBe(false);
  });
});
