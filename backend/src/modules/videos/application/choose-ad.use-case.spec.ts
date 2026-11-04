import type { Ad } from '../../ads/domain/ad.js';
import { ANONYMOUS_VIEWER } from '../domain/video.js';
import { ChooseAdUseCase } from './choose-ad.use-case.js';

const AD: Ad = {
  id: 'ad-1',
  title: 'Publicité',
  videoUrl: '/media/ads/a.mp4',
  hlsUrl: null,
  thumbnailUrl: null,
  durationSeconds: 20,
  destinationUrl: 'https://www.exemple.fr',
  createdAt: new Date(0),
};
const chooseAd = new ChooseAdUseCase({ pick: () => Promise.resolve(AD) });
const FREE = { isPremium: false, userId: 'user-1' };
const PREMIUM = { isPremium: true, userId: 'user-2' };

describe('ChooseAdUseCase', () => {
  it('montre une publicité avant une vidéo classique à un compte gratuit ou un visiteur', async () => {
    expect(await chooseAd.beforeVideo(FREE, 'LONG')).toBe(AD);
    expect(await chooseAd.beforeVideo(ANONYMOUS_VIEWER, 'LONG')).toBe(AD);
  });

  it("n'en montre jamais à un compte Premium ni avant un Short", async () => {
    expect(await chooseAd.beforeVideo(PREMIUM, 'LONG')).toBeNull();
    expect(await chooseAd.beforeVideo(FREE, 'SHORT')).toBeNull();
  });

  it('réserve le bandeau sponsorisé aux comptes non Premium', async () => {
    expect(await chooseAd.sponsored(FREE)).toBe(AD);
    expect(await chooseAd.sponsored(PREMIUM)).toBeNull();
  });
});
