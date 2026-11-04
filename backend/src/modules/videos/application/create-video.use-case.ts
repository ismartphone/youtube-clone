import type { HlsQueue } from '../domain/hls.js';
import type { SubtitleQueue } from '../domain/subtitles.js';
import type { VideoCatalog, VideoDraft } from '../domain/catalog.js';
import type { VideoCounters } from '../domain/counters.js';
import {
  thumbnailPosition,
  thumbnailSize,
  type ThumbnailGenerator,
} from '../domain/thumbnail-generator.js';
import { ADMIN_VIEWER } from '../domain/video.js';
import type { PublicationNotifier } from '../../notifications/domain/publication-notifier.js';
import { toVideoViews, type VideoView } from './video-view.js';

// Publication d'une vidéo depuis le back-office, à partir d'URL déjà hébergées.
// Sans miniature, une image est extraite de la vidéo à 10 % de sa durée.
export class CreateVideoUseCase {
  constructor(
    private readonly catalog: VideoCatalog,
    private readonly counters: VideoCounters,
    private readonly thumbnails: ThumbnailGenerator,
    private readonly hlsQueue: HlsQueue,
    private readonly subtitleQueue: SubtitleQueue,
    private readonly notifier: PublicationNotifier,
  ) {}

  async execute(draft: VideoDraft): Promise<VideoView> {
    const thumbnailUrl =
      draft.thumbnailUrl ??
      (await this.thumbnails.generate(
        draft.videoUrl,
        thumbnailPosition(draft.durationSeconds),
        thumbnailSize(draft.format),
      ));
    try {
      const video = await this.catalog.create({ ...draft, thumbnailUrl });
      const [view] = await toVideoViews([video], ADMIN_VIEWER, this.counters);
      // Échelle HLS et sous-titres en tâche de fond : la vidéo est publiée tout de suite en MP4,
      // sans sous-titres (ADR-017).
      this.hlsQueue.enqueue(video.id);
      this.subtitleQueue.enqueue(video.id);
      // Abonnés prévenus en arrière-plan (ADR-020) ; rien pour une vidéo privée ou non répertoriée.
      if (video.visibility === 'PUBLIC') this.notifier.notify(video.id);
      return view;
    } catch (error) {
      // Vidéo refusée (chaîne inconnue...) : la miniature générée pour elle ne sert plus.
      if (draft.thumbnailUrl === null) {
        await this.thumbnails.remove(thumbnailUrl);
      }
      throw error;
    }
  }
}
