import { Logger } from '@nestjs/common';
import { execFile } from 'node:child_process';
import { mkdir, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { promisify } from 'node:util';
import { MEDIA_URL_PREFIX } from '../../../shared/infrastructure/media.config.js';
import {
  avatarColor,
  avatarInitials,
  type AvatarGenerator,
} from '../domain/avatar.js';

const run = promisify(execFile);

const AVATARS_FOLDER = 'avatars';
const AVATAR_SIZE = 256;
const GENERATION_TIMEOUT_MS = 15_000;

// Police des initiales : AVATAR_FONT si elle est définie, sinon une police courante du système.
// FFmpeg ne trouve pas toujours de police par défaut (fontconfig absent sous Windows).
const FONT_CANDIDATES = [
  process.env.AVATAR_FONT,
  'C:/Windows/Fonts/arialbd.ttf',
  '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
  '/System/Library/Fonts/Supplemental/Arial Bold.ttf',
].filter((font): font is string => Boolean(font));

async function exists(file: string): Promise<boolean> {
  return stat(file).then(
    () => true,
    () => false,
  );
}

export interface FfmpegAvatarConfig {
  command: string;
  mediaDir: string;
}

/** Avatar PNG (initiales blanches sur fond de couleur) dessiné par FFmpeg, appelé en ligne de commande. */
export class FfmpegAvatarGenerator implements AvatarGenerator {
  private readonly logger = new Logger(FfmpegAvatarGenerator.name);

  constructor(private readonly config: FfmpegAvatarConfig) {}

  async generate(name: string, fileKey: string): Promise<string | null> {
    try {
      const folder = join(this.config.mediaDir, AVATARS_FOLDER);
      await mkdir(folder, { recursive: true });
      const font = await this.font();
      // Dans un filtre FFmpeg, « : » sépare les options, et « ' » délimite le texte : on les échappe.
      const fontPath = font.replace(/\\/g, '/').replace(/:/g, '\\:');
      const text = avatarInitials(name).replace(/[\\':]/g, (c) => `\\${c}`);
      await run(
        this.config.command,
        [
          '-v',
          'error',
          '-f',
          'lavfi',
          '-i',
          `color=c=0x${avatarColor(name)}:s=${AVATAR_SIZE}x${AVATAR_SIZE}:d=1`,
          '-frames:v',
          '1',
          '-vf',
          `drawtext=text='${text}':fontfile='${fontPath}':fontcolor=white:fontsize=110:x=(w-text_w)/2:y=(h-text_h)/2`,
          '-y',
          join(folder, `${fileKey}.png`),
        ],
        { timeout: GENERATION_TIMEOUT_MS },
      );
      return `${MEDIA_URL_PREFIX}/${AVATARS_FOLDER}/${fileKey}.png`;
    } catch (error) {
      this.logger.warn(
        `Avatar non généré pour « ${name} » : ${(error as Error).message}`,
      );
      return null;
    }
  }

  private async font(): Promise<string> {
    for (const font of FONT_CANDIDATES) {
      if (await exists(font)) return font;
    }
    throw new Error(
      'aucune police trouvée : indiquez un fichier .ttf dans AVATAR_FONT',
    );
  }
}
