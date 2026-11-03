import type { ChannelSummary, Video } from './video.js';
import type { Page, Pagination } from './video.repository.js';

export const SEARCH_REPOSITORY = Symbol('SEARCH_REPOSITORY');

// Chaîne trouvée par la recherche, avec son nombre d'abonnés (FOLLOW).
export interface ChannelSearchResult extends ChannelSummary {
  followerCount: number;
}

// Port de recherche : correspondance partielle, insensible à la casse et aux accents.
// Vidéos de la plus récente à la plus ancienne, chaînes par ordre alphabétique.
export interface SearchRepository {
  searchVideos(term: string, pagination: Pagination): Promise<Page<Video>>;
  searchChannels(
    term: string,
    pagination: Pagination,
  ): Promise<Page<ChannelSearchResult>>;
}
