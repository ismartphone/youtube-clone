import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { describe, expect, it } from 'vitest';
import { UpdateChannelBody } from '../../../modules/channels/interfaces/http/channel.bodies.js';
import { CreateVideoBody } from '../../../modules/videos/interfaces/http/admin.bodies.js';
import { isLocalUrl, LOCAL_URL_MESSAGE } from './local-url.js';

describe('isLocalUrl', () => {
  it.each([
    'http://localhost:3000/media/videos/a.mp4',
    'https://LOCALHOST/x',
    'http://127.0.0.1:3000/a',
    'http://127.1.2.3/a',
    'http://0.0.0.0:3000/a',
    'http://[::1]:3000/a',
    'http://10.0.2.2:3000/a',
    'http://api.localhost/a',
    '  http://localhost:3000/a  ',
  ])('refuse %s', (url) => {
    expect(isLocalUrl(url)).toBe(true);
  });

  it.each([
    '/media/videos/a.mp4',
    'https://cdn.exemple.fr/a.mp4',
    'http://192.168.1.20:3000/a.mp4',
    'https://localhostile.com/a',
    'https://exemple.fr/localhost',
    '',
    'pas une url',
    null,
    undefined,
    42,
  ])('accepte %s', (url) => {
    expect(isLocalUrl(url)).toBe(false);
  });
});

describe('corps de requête', () => {
  const messagesOf = async (
    cls: new () => object,
    plain: object,
    field: string,
  ) => {
    const errors = await validate(plainToInstance(cls, plain));
    return errors.find((e) => e.property === field)?.constraints ?? {};
  };

  const video = {
    title: 'Titre',
    channelId: '3f2504e0-4f89-41d3-9a0c-0305e82c3301',
    format: 'LONG',
    isPremium: false,
    durationSeconds: 60,
  };

  it('refuse une vidéo et une miniature vers localhost', async () => {
    const videoErrors = await messagesOf(
      CreateVideoBody,
      { ...video, videoUrl: 'http://localhost:3000/media/videos/a.mp4' },
      'videoUrl',
    );
    expect(videoErrors.isNotLocalUrl).toBe(LOCAL_URL_MESSAGE);
    const thumbErrors = await messagesOf(
      CreateVideoBody,
      {
        ...video,
        videoUrl: 'https://cdn.exemple.fr/a.mp4',
        thumbnailUrl: 'http://127.0.0.1:3000/t.jpg',
      },
      'thumbnailUrl',
    );
    expect(thumbErrors.isNotLocalUrl).toBe(LOCAL_URL_MESSAGE);
  });

  it('accepte une URL publique et un chemin du stockage', async () => {
    expect(
      await messagesOf(
        CreateVideoBody,
        { ...video, videoUrl: 'https://cdn.exemple.fr/a.mp4' },
        'videoUrl',
      ),
    ).toEqual({});
    expect(
      await messagesOf(
        CreateVideoBody,
        { ...video, videoUrl: '/media/videos/a.mp4' },
        'videoUrl',
      ),
    ).toEqual({});
  });

  it('refuse une bannière vers localhost, accepte un chemin local et null', async () => {
    expect(
      (
        await messagesOf(
          UpdateChannelBody,
          { bannerUrl: 'http://localhost:3000/b.png' },
          'bannerUrl',
        )
      ).isNotLocalUrl,
    ).toBe(LOCAL_URL_MESSAGE);
    expect(
      await messagesOf(
        UpdateChannelBody,
        { bannerUrl: '/media/banners/b.png' },
        'bannerUrl',
      ),
    ).toEqual({});
    expect(
      await messagesOf(UpdateChannelBody, { bannerUrl: null }, 'bannerUrl'),
    ).toEqual({});
  });
});
