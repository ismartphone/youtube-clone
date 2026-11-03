// Modèles du domaine des posts (ADR-015), indépendants de Prisma et de NestJS.

// Texte de 500 caractères au plus.
export const POST_TEXT_MAX_LENGTH = 500;

// Image facultative de 5 Mo au plus, en JPEG ou PNG (validée par ffprobe).
export const MAX_POST_IMAGE_BYTES = 5 * 1024 * 1024;

export type ImageFormat = 'jpeg' | 'png';

export interface PostChannel {
  id: string;
  name: string;
  handle: string;
  avatarUrl: string | null;
}

export interface Post {
  id: string;
  text: string;
  // Chemin relatif (/media/posts/...) ou null sans image.
  imageUrl: string | null;
  createdAt: Date;
  channel: PostChannel;
}

export interface NewPost {
  channelId: string;
  text: string;
  imageUrl: string | null;
}

export interface PostPage {
  items: Post[];
  page: number;
  pageSize: number;
  total: number;
}
