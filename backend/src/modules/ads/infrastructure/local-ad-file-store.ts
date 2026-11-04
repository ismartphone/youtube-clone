import { randomUUID } from 'node:crypto';
import { copyFile, mkdir, rename, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import type { AdFileStore } from '../domain/ports.js';

export const ADS_FOLDER = 'ads';
export const ADS_URL_PREFIX = `${MEDIA_URL_PREFIX}/${ADS_FOLDER}/`;
// Nom généré par save : seul un tel fichier est supprimé.
const SAVED_URL = /^\/media\/ads\/[0-9a-f-]{36}\.mp4$/;

// Fichiers des publicités, dans le dossier ads/ du stockage, servis sous /media/ads/ (ADR-021).
export class LocalAdFileStore implements AdFileStore {
  constructor(private readonly mediaDir: string) {}

  async save(path: string): Promise<string> {
    const folder = join(this.mediaDir, ADS_FOLDER);
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
    return ADS_URL_PREFIX + fileName;
  }

  async remove(videoUrl: string): Promise<void> {
    if (!SAVED_URL.test(videoUrl)) {
      return;
    }
    await rm(
      join(this.mediaDir, ADS_FOLDER, videoUrl.slice(ADS_URL_PREFIX.length)),
      {
        force: true,
      },
    );
  }
}
