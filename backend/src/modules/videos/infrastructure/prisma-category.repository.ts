import { Prisma } from '../../../generated/prisma/client.js';
import type { PrismaService } from '../../../shared/infrastructure/prisma.service.js';
import { CategoryAlreadyExistsError } from '../domain/catalog.errors.js';
import type {
  CategoryRepository,
  CategoryWithCount,
} from '../domain/catalog.js';
import type { CategorySummary } from '../domain/video.js';

// Code Prisma d'une violation de contrainte d'unicité.
const UNIQUE_CONSTRAINT_VIOLATION = 'P2002';

export class PrismaCategoryRepository implements CategoryRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findAll(): Promise<CategoryWithCount[]> {
    const rows = await this.prisma.category.findMany({
      orderBy: { name: 'asc' },
      select: { id: true, name: true, _count: { select: { videos: true } } },
    });
    return rows.map(({ _count, ...category }) => ({
      ...category,
      videoCount: _count.videos,
    }));
  }

  async create(name: string): Promise<CategorySummary> {
    try {
      return await this.prisma.category.create({
        data: { name },
        select: { id: true, name: true },
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === UNIQUE_CONSTRAINT_VIOLATION
      ) {
        throw new CategoryAlreadyExistsError();
      }
      throw error;
    }
  }

  // La relation est en onDelete: SetNull : les vidéos perdent leur catégorie sans être supprimées.
  async delete(id: string): Promise<boolean> {
    const { count } = await this.prisma.category.deleteMany({ where: { id } });
    return count > 0;
  }
}
