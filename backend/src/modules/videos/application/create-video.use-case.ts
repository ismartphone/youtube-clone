import type { VideoCatalog, VideoDraft } from '../domain/catalog.js';
import type { VideoCounters } from '../domain/counters.js';
import {
  thumbnailPosition,
  thumbnailSize,
  type ThumbnailGenerator,
} from '../domain/thumbnail-generator.js';
import { ADMIN_VIEWER } from '../domain/video.js';
import { toVideoViews, type VideoView } from './video-view.js';

// Publication d'une vidéo depuis le back-office, à partir d'URL déjà hébergées.
// Sans miniature, une image est extraite de la vidéo à 10 % de sa durée.
export class CreateVideoUseCase {
  constructor(
    private readonly catalog: VideoCatalog,
    private readonly counters: VideoCounters,
    private readonly thumbnails: ThumbnailGenerator,
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
