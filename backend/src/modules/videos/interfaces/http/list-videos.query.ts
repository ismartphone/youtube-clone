import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, IsUUID, Max, Min } from 'class-validator';
import { VIDEO_FORMATS, type VideoFormat } from '../../domain/video.js';

export const MAX_PAGE_SIZE = 50;

// Pagination commune aux listes de vidéos.
export class PaginationQuery {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(MAX_PAGE_SIZE)
  pageSize: number = 20;
}

// Paramètres de GET /videos, validés avant d'atteindre le cas d'usage.
export class ListVideosQuery extends PaginationQuery {
  @IsOptional()
  @IsIn(VIDEO_FORMATS)
  format?: VideoFormat;

  @IsOptional()
  @IsUUID()
  categoryId?: string;
}
