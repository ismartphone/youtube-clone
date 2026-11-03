import {
  formatProblem,
  InvalidVideoFileError,
  NoChannelError,
  type ChannelOwnership,
  type UploadDetails,
  type VideoFileStore,
  type VideoProber,
} from '../domain/video-upload.js';
import type { CreateVideoUseCase } from './create-video.use-case.js';
import type { VideoView } from './video-view.js';

// Publication d'une vidéo envoyée depuis l'application mobile, par le propriétaire d'une chaîne
// (ADR-015). Le fichier est validé par ffprobe avant d'entrer dans le stockage, puis la vidéo
// est créée comme depuis le back-office : la miniature vient du même service FFmpeg.
// Une vidéo publiée depuis l'application n'est jamais exclusive (isPremium reste au back-office).
export class UploadVideoUseCase {
  constructor(
    private readonly ownership: ChannelOwnership,
    private readonly prober: VideoProber,
    private readonly files: VideoFileStore,
    private readonly createVideo: CreateVideoUseCase,
  ) {}

  // [tempPath] : fichier reçu, dans un dossier temporaire. Il est déplacé s'il est accepté ;
  // sinon, c'est à l'appelant de le supprimer.
  async execute(
    userId: string,
    tempPath: string,
    details: UploadDetails,
  ): Promise<VideoView> {
    const channelId = await this.ownership.channelIdOf(userId);
    if (!channelId) {
      throw new NoChannelError();
    }
    const probed = await this.prober.probe(tempPath);
    const problem = formatProblem(details.format, probed);
    if (problem) {
      throw new InvalidVideoFileError(problem);
    }
    const videoUrl = await this.files.save(tempPath);
    try {
      return await this.createVideo.execute({
        ...details,
        channelId,
        isPremium: false,
        videoUrl,
        thumbnailUrl: null,
        durationSeconds: Math.max(1, Math.round(probed.durationSeconds)),
      });
    } catch (error) {
      // Catégorie inconnue, miniature impossible... : le fichier rangé ne sert plus.
      await this.files.remove(videoUrl);
      throw error;
    }
  }
}
