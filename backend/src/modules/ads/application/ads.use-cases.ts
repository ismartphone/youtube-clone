import { Logger } from '@nestjs/common';
import type { HlsGenerator } from '../../videos/domain/hls.js';
import {
  thumbnailPosition,
  thumbnailSize,
  type ThumbnailGenerator,
} from '../../videos/domain/thumbnail-generator.js';
import type { VideoProber } from '../../videos/domain/video-upload.js';
import { AdNotFoundError, type Ad, type AdChanges } from '../domain/ad.js';
import type { AdPicker } from '../domain/ad-picker.js';
import type { AdFileStore, AdRepository } from '../domain/ports.js';

// Publicités du back-office (ADR-021) : même traitement que les vidéos. Le fichier est vérifié
// par ffprobe, rangé sous un nom généré, une miniature en est extraite, puis l'échelle HLS est
// générée en tâche de fond ; la publicité est lue en MP4 en attendant.
export class ManageAdsUseCase {
  private readonly logger = new Logger('Ads');

  constructor(
    private readonly ads: AdRepository,
    private readonly files: AdFileStore,
    private readonly prober: VideoProber,
    private readonly thumbnails: ThumbnailGenerator,
    private readonly hls: HlsGenerator,
  ) {}

  list(): Promise<Ad[]> {
    return this.ads.findAll();
  }

  // [tempPath] est déplacé s'il est accepté ; sinon, c'est à l'appelant de le supprimer.
  // Lève InvalidVideoFileError, UploadUnavailableError ou ThumbnailUnavailableError.
  // [waitForHls] : la commande ads:import attend l'encodage, le back-office non.
  async import(
    tempPath: string,
    title: string,
    destinationUrl: string,
    waitForHls = false,
  ): Promise<Ad> {
    const probed = await this.prober.probe(tempPath);
    const durationSeconds = Math.max(1, Math.round(probed.durationSeconds));
    const videoUrl = await this.files.save(tempPath);
    let thumbnailUrl: string | null = null;
    try {
      thumbnailUrl = await this.thumbnails.generate(
        videoUrl,
        thumbnailPosition(durationSeconds),
        thumbnailSize('LONG'),
      );
      const ad = await this.ads.create({
        title,
        videoUrl,
        thumbnailUrl,
        durationSeconds,
        destinationUrl,
      });
      const hls = this.generateHls(ad);
      if (waitForHls) await hls;
      return ad;
    } catch (error) {
      if (thumbnailUrl) await this.thumbnails.remove(thumbnailUrl);
      await this.files.remove(videoUrl);
      throw error;
    }
  }

  // Titre (bandeau sponsorisé) et site de l'annonceur, modifiables depuis le back-office.
  async update(id: string, changes: AdChanges): Promise<Ad> {
    const ad = await this.ads.update(id, changes);
    if (!ad) {
      throw new AdNotFoundError();
    }
    return ad;
  }

  // Supprime aussi le fichier, l'échelle HLS et la miniature.
  async remove(id: string): Promise<void> {
    const ad = await this.ads.delete(id);
    if (!ad) {
      throw new AdNotFoundError();
    }
    await this.files.remove(ad.videoUrl);
    if (ad.hlsUrl) await this.hls.remove(ad.hlsUrl);
    if (ad.thumbnailUrl) await this.thumbnails.remove(ad.thumbnailUrl);
  }

  // Ne lève jamais : un échec est journalisé et laisse la lecture en MP4.
  private async generateHls(ad: Ad): Promise<void> {
    try {
      const hlsUrl = await this.hls.generate(ad.videoUrl);
      // Publicité supprimée pendant l'encodage : l'échelle ne sert plus.
      if (!(await this.ads.setHlsUrl(ad.id, hlsUrl))) {
        await this.hls.remove(hlsUrl);
      }
    } catch (error) {
      this.logger.warn(
        `HLS de la publicité ${ad.id} en échec, lue en MP4 : ${(error as Error).message}`,
      );
    }
  }
}

// Tirage au sort parmi toutes les publicités, pour le module videos.
export class RandomAdPicker implements AdPicker {
  constructor(private readonly ads: AdRepository) {}

  pick(): Promise<Ad | null> {
    return this.ads.pickRandom();
  }
}
