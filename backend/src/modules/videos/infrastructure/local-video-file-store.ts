import { randomUUID } from 'node:crypto';
import { copyFile, mkdir, rename, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import type { VideoFileStore } from '../domain/video-upload.js';

const VIDEOS_FOLDER = 'videos';
const URL_PREFIX = `${MEDIA_URL_PREFIX}/${VIDEOS_FOLDER}/`;
// Nom généré par le serveur : UUID + .mp4. Sert aussi à reconnaître les fichiers envoyés.
const UPLOADED_URL = /^\/media\/videos\/[0-9a-f-]{36}\.mp4$/;

// Vidéos envoyées depuis l'application, rangées dans le stockage local (ADR-002, ADR-015)
// à côté des vidéos du seed, et servies en statique sous /media/videos.
export class LocalVideoFileStore implements VideoFileStore {
  constructor(private readonly mediaDir: string) {}

  async save(path: string): Promise<string> {
    const folder = join(this.mediaDir, VIDEOS_FOLDER);
    await mkdir(folder, { recursive: true });
    const fileName = `${randomUUID()}.mp4`;
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

  async remove(videoUrl: string): Promise<void> {
    if (!UPLOADED_URL.test(videoUrl)) {
      return;
    }
    await rm(
      join(this.mediaDir, VIDEOS_FOLDER, videoUrl.slice(URL_PREFIX.length)),
      {
        force: true,
      },
    );
  }
}
