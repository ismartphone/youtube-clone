import { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import {
  UnknownCategoryError,
  UnknownChannelError,
} from '../domain/catalog.errors.js';
import type {
  VideoCatalog,
  VideoInput,
  VideoPatch,
} from '../domain/catalog.js';
import type { Video } from '../domain/video.js';
import { VIDEO_SELECT } from './prisma-video.repository.js';

// Code Prisma d'un enregistrement introuvable lors d'une mise à jour.
const RECORD_NOT_FOUND = 'P2025';

export class PrismaVideoCatalog implements VideoCatalog {
  constructor(private readonly prisma: PrismaService) {}

  async create(input: VideoInput): Promise<Video> {
    await this.checkReferences(input);
    return this.prisma.video.create({ data: input, select: VIDEO_SELECT });
  }

  async update(id: string, patch: VideoPatch): Promise<Video | null> {
    await this.checkReferences(patch);
    try {
      return await this.prisma.video.update({
        where: { id },
        data: patch,
        select: VIDEO_SELECT,
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === RECORD_NOT_FOUND
      ) {
        return null;
      }
      throw error;
    }
  }

  async delete(id: string): Promise<boolean> {
    const { count } = await this.prisma.video.deleteMany({ where: { id } });
    return count > 0;
  }

  // Vérifie les références avant d'écrire, pour renvoyer une erreur claire
  // plutôt qu'une violation de clé étrangère.
  private async checkReferences({ channelId, categoryId }: VideoPatch) {
    const [channel, category] = await Promise.all([
      channelId
        ? this.prisma.channel.findUnique({
            where: { id: channelId },
            select: { id: true },
          })
        : undefined,
      categoryId
        ? this.prisma.category.findUnique({
            where: { id: categoryId },
            select: { id: true },
          })
        : undefined,
    ]);
    if (channel === null) {
      throw new UnknownChannelError();
    }
    if (category === null) {
      throw new UnknownCategoryError();
    }
  }
}
