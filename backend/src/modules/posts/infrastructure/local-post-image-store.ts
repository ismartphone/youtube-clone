import { randomUUID } from 'node:crypto';
import { copyFile, mkdir, rename, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import type { ImageFormat } from '../domain/post.js';
import type { ImageStore } from '../domain/ports.js';

const POSTS_FOLDER = 'posts';
const URL_PREFIX = `${MEDIA_URL_PREFIX}/${POSTS_FOLDER}/`;
const EXTENSIONS: Record<ImageFormat, string> = { jpeg: 'jpg', png: 'png' };
// Nom généré par le serveur : UUID + extension du format reconnu par ffprobe.
const STORED_URL = /^\/media\/posts\/[0-9a-f-]{36}\.(jpg|png)$/;

// Images des posts, rangées dans le stockage local (ADR-002, ADR-015) et servies en statique
// sous /media/posts.
export class LocalPostImageStore implements ImageStore {
  constructor(private readonly mediaDir: string) {}

  async save(path: string, format: ImageFormat): Promise<string> {
    const folder = join(this.mediaDir, POSTS_FOLDER);
    await mkdir(folder, { recursive: true });
    const fileName = `${randomUUID()}.${EXTENSIONS[format]}`;
    const target = join(folder, fileName);
    try {
      await rename(path, target);
    } catch (error) {
      // Dossier temporaire sur un autre disque : rename est impossible, on copie.
      if ((error as NodeJS.ErrnoException).code !== 'EXDEV') {
        throw error;
      }
      await copyFile(path, target);
      await rm(path, { force: true });
    }
    return URL_PREFIX + fileName;
  }

  async remove(imageUrl: string): Promise<void> {
    if (!STORED_URL.test(imageUrl)) {
      return;
    }
    await rm(
      join(this.mediaDir, POSTS_FOLDER, imageUrl.slice(URL_PREFIX.length)),
      {
        force: true,
      },
    );
  }
}
