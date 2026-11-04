import type { NotificationLevel } from '../../channels/domain/channel.js';
import {
  levelsNotifiedFor,
  type Publication,
  type PublicationMessage,
} from '../domain/notification.js';
import type {
  DeviceTokenRepository,
  PublicationRepository,
  PushSender,
} from '../domain/ports.js';
import { NotifyPublicationUseCase } from './notify-publication.use-case.js';

const PUBLICATION: Publication = {
  videoId: 'video-1',
  title: 'Nouvelle vidéo',
  format: 'LONG',
  thumbnailUrl: '/media/thumbnails/a.jpg',
  channelId: 'channel-1',
  channelName: 'Studio Libre',
  ownerId: 'owner-1',
};

function setup(options: {
  enabled?: boolean;
  publication?: Publication | null;
  recipients?: string[];
  rejected?: string[];
}) {
  const calls = {
    levels: [] as NotificationLevel[][],
    excluded: [] as string[],
    sent: [] as { tokens: string[]; message: PublicationMessage }[],
    removed: [] as string[][],
  };
  const publications: PublicationRepository = {
    claim: () => Promise.resolve(options.publication ?? null),
    findRecipientTokens: (_channelId, levels, excludedUserId) => {
      calls.levels.push(levels);
      calls.excluded.push(excludedUserId);
      return Promise.resolve(options.recipients ?? []);
    },
  };
  const tokens: DeviceTokenRepository = {
    save: () => Promise.resolve(),
    remove: () => Promise.resolve(),
    removeAll: (list) => {
      calls.removed.push(list);
      return Promise.resolve();
    },
  };
  const sender: PushSender = {
    enabled: options.enabled ?? true,
    send: (list, message) => {
      calls.sent.push({ tokens: list, message });
      return Promise.resolve(options.rejected ?? []);
    },
  };
  return {
    calls,
    useCase: new NotifyPublicationUseCase(publications, tokens, sender),
  };
}

describe('levelsNotifiedFor', () => {
  it('prévient Toutes et Personnalisées pour une vidéo classique', () => {
    expect(levelsNotifiedFor('LONG')).toEqual(['ALL', 'PERSONALIZED']);
  });

  it('ne prévient que Toutes pour un Short : Personnalisées veut dire sans les Shorts', () => {
    expect(levelsNotifiedFor('SHORT')).toEqual(['ALL']);
  });
});

describe('NotifyPublicationUseCase', () => {
  it('envoie titre de chaîne, titre et identifiant aux abonnés, créateur exclu', async () => {
    const { calls, useCase } = setup({
      publication: PUBLICATION,
      recipients: ['t1', 't2'],
    });

    expect(await useCase.execute('video-1')).toBe(2);
    expect(calls.excluded).toEqual(['owner-1']);
    expect(calls.sent).toEqual([
      {
        tokens: ['t1', 't2'],
        message: {
          videoId: 'video-1',
          channelName: 'Studio Libre',
          title: 'Nouvelle vidéo',
          format: 'LONG',
          thumbnailUrl: '/media/thumbnails/a.jpg',
        },
      },
    ]);
  });

  it('demande les cloches qui couvrent le format de la vidéo', async () => {
    const { calls, useCase } = setup({
      publication: { ...PUBLICATION, format: 'SHORT' },
    });

    await useCase.execute('video-1');
    expect(calls.levels).toEqual([['ALL']]);
  });

  it("n'envoie rien pour une vidéo non publique ou déjà annoncée", async () => {
    const { calls, useCase } = setup({ publication: null });

    expect(await useCase.execute('video-1')).toBe(0);
    expect(calls.sent).toEqual([]);
  });

  it("n'envoie rien sans clé Firebase", async () => {
    const { calls, useCase } = setup({
      enabled: false,
      publication: PUBLICATION,
      recipients: ['t1'],
    });

    expect(await useCase.execute('video-1')).toBe(0);
    expect(calls.sent).toEqual([]);
  });

  it('supprime les jetons refusés par Firebase', async () => {
    const { calls, useCase } = setup({
      publication: PUBLICATION,
      recipients: ['t1', 't2', 't3'],
      rejected: ['t2'],
    });

    expect(await useCase.execute('video-1')).toBe(2);
    expect(calls.removed).toEqual([['t2']]);
  });

  it('découpe les envois par lots de 500 jetons', async () => {
    const recipients = Array.from({ length: 1001 }, (_, i) => `t${i}`);
    const { calls, useCase } = setup({ publication: PUBLICATION, recipients });

    expect(await useCase.execute('video-1')).toBe(1001);
    expect(calls.sent.map((call) => call.tokens.length)).toEqual([500, 500, 1]);
  });
});
