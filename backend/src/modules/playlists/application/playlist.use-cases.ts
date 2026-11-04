import {
  PlaylistFullError,
  PlaylistNotFoundError,
  TooManyPlaylistsError,
  VideoNotFoundError,
} from '../domain/playlist.errors.js';
import {
  MAX_PLAYLISTS_PER_USER,
  MAX_VIDEOS_PER_PLAYLIST,
  type PlaylistDetail,
  type PlaylistSummary,
} from '../domain/playlist.js';
import type { PlaylistRepository, PlaylistVideos } from '../domain/ports.js';

/**
 * Playlists de l'utilisateur connecté (ADR-019, partie 3) : créer, renommer, supprimer, ajouter
 * et retirer des vidéos, consulter. La playlist d'un autre est traitée comme inexistante
 * (PlaylistNotFoundError), une vidéo inaccessible aussi (VideoNotFoundError).
 */
export class ManagePlaylistsUseCase {
  constructor(
    private readonly playlists: PlaylistRepository,
    private readonly videos: PlaylistVideos,
  ) {}

  list(userId: string, videoId?: string): Promise<PlaylistSummary[]> {
    return this.playlists.listOwned(userId, videoId);
  }

  /** Lève TooManyPlaylistsError au-delà de 50 playlists. */
  async create(userId: string, title: string): Promise<PlaylistSummary> {
    if ((await this.playlists.countOwned(userId)) >= MAX_PLAYLISTS_PER_USER) {
      throw new TooManyPlaylistsError();
    }
    return this.playlists.create(userId, title.trim());
  }

  async detail(id: string, userId: string): Promise<PlaylistDetail> {
    const playlist = await this.playlists.findDetail(id, userId);
    if (!playlist) {
      throw new PlaylistNotFoundError();
    }
    return playlist;
  }

  async rename(
    id: string,
    userId: string,
    title: string,
  ): Promise<PlaylistDetail> {
    if (!(await this.playlists.rename(id, userId, title.trim()))) {
      throw new PlaylistNotFoundError();
    }
    return this.detail(id, userId);
  }

  async delete(id: string, userId: string): Promise<void> {
    if (!(await this.playlists.delete(id, userId))) {
      throw new PlaylistNotFoundError();
    }
  }

  /** Lève PlaylistFullError au-delà de 200 vidéos ; ajouter une vidéo déjà présente ne change rien. */
  async addVideo(id: string, userId: string, videoId: string): Promise<void> {
    const count = await this.playlists.countItems(id, userId);
    if (count === null) {
      throw new PlaylistNotFoundError();
    }
    if (!(await this.videos.isAccessible(videoId, userId))) {
      throw new VideoNotFoundError();
    }
    if (count >= MAX_VIDEOS_PER_PLAYLIST) {
      // Déjà présente : rien à ajouter, la limite ne s'applique pas.
      const detail = await this.playlists.findDetail(id, userId);
      if (detail?.videos.some((video) => video.id === videoId)) return;
      throw new PlaylistFullError();
    }
    await this.playlists.addVideo(id, userId, videoId);
  }

  async removeVideo(
    id: string,
    userId: string,
    videoId: string,
  ): Promise<void> {
    if (!(await this.playlists.removeVideo(id, userId, videoId))) {
      throw new PlaylistNotFoundError();
    }
  }
}
