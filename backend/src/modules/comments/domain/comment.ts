// Modèles du domaine des commentaires, indépendants de Prisma et de NestJS.

// Auteur tel qu'affiché sous la vidéo : jamais son email ni son rôle.
export interface CommentAuthor {
  id: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface Comment {
  id: string;
  videoId: string;
  content: string;
  createdAt: Date;
  author: CommentAuthor;
}

export interface CommentPage {
  items: Comment[];
  page: number;
  pageSize: number;
  total: number;
}

export const COMMENT_MAX_LENGTH = 1000;
