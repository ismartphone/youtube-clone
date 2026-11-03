import { Controller, Get, Query } from '@nestjs/common';
import { Transform } from 'class-transformer';
import { IsString, Length } from 'class-validator';
import { SearchUseCase } from '../../application/search.use-case.js';
import { ANONYMOUS_VIEWER } from '../../domain/video.js';
import { PaginationQuery } from './list-videos.query.js';
import { presentVideoSummary } from './video.presenter.js';

// Paramètres de GET /search : le terme, puis la pagination commune aux listes.
export class SearchQuery extends PaginationQuery {
  @Transform(({ value }: { value: unknown }) =>
    typeof value === 'string' ? value.trim() : value,
  )
  @IsString()
  @Length(1, 100)
  q: string;
}

// Recherche publique, ouverte aux visiteurs. Comme les autres listes, les résultats
// ne contiennent jamais l'URL des fichiers.
@Controller('search')
export class SearchController {
  constructor(private readonly search: SearchUseCase) {}

  @Get()
  async find(@Query() query: SearchQuery) {
    const { videos, channels } = await this.search.execute(
      query.q,
      { page: query.page, pageSize: query.pageSize },
      ANONYMOUS_VIEWER,
    );
    return {
      videos: { ...videos, items: videos.items.map(presentVideoSummary) },
      channels: {
        ...channels,
        items: channels.items.map((channel) => ({
          id: channel.id,
          name: channel.name,
          handle: channel.handle,
          avatarUrl: channel.avatarUrl,
          followerCount: channel.followerCount,
        })),
      },
    };
  }
}
