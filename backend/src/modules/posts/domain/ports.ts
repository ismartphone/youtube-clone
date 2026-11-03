import type { ImageFormat, NewPost, Post, PostPage } from './post.js';

// Ports du module posts : les cas d'usage n'en connaissent que ces interfaces.

export const POST_REPOSITORY = Symbol('POST_REPOSITORY');
export const POST_CHANNELS = Symbol('POST_CHANNELS');
export const POST_IMAGE_PROBER = Symbol('POST_IMAGE_PROBER');
export const POST_IMAGE_STORE = Symbol('POST_IMAGE_STORE');

export interface PostRepository {
  create(post: NewPost): Promise<Post>;
  // Posts de la chaîne, du plus récent au plus ancien.
  findByChannel(
    channelId: string,
    page: number,
    pageSize: number,
  ): Promise<PostPage>;
}

// Lecture des chaînes depuis le module posts (monolithe à base unique, ADR-003).
export interface PostChannels {
  // Chaîne dont l'utilisateur est propriétaire, ou null s'il n'en a pas.
  channelIdOf(userId: string): Promise<string | null>;
  exists(channelId: string): Promise<boolean>;
}

export interface ImageProber {
  /**
   * Analyse le fichier local [path]. Lève ImageUnavailableError si ffprobe est absent,
   * InvalidImageError si ce n'est pas une image JPEG ou PNG lisible.
   */
  probe(path: string): Promise<ImageFormat>;
}

export interface ImageStore {
  // Range le fichier temporaire [path] sous /media/posts, sous un nom généré, et renvoie son URL.
  save(path: string, format: ImageFormat): Promise<string>;
  // Supprime une image rangée par save ; sans effet pour toute autre URL.
  remove(imageUrl: string): Promise<void>;
}
