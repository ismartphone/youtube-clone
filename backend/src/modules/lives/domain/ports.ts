import type {
  LiveAuthor,
  LiveChatMessage,
  LiveRecord,
  StreamCandidate,
} from './live.js';

export const LIVES_REPOSITORY = Symbol('LIVES_REPOSITORY');

export interface LivesRepository {
  // Chaîne dont [userId] est propriétaire ; null s'il n'en a pas.
  findOwnChannelId(userId: string): Promise<string | null>;
  countFollowers(channelId: string): Promise<number>;
  // Vidéo du catalogue sous l'angle du flux ; null si elle n'existe pas.
  findStreamCandidate(videoId: string): Promise<StreamCandidate | null>;
  /**
   * Crée le live, dans une transaction qui vérifie qu'aucun live n'est déjà en cours sur la
   * chaîne : renvoie null dans ce cas.
   */
  createIfNoneRunning(
    channelId: string,
    title: string,
    videoId: string,
  ): Promise<LiveRecord | null>;
  find(liveId: string): Promise<LiveRecord | null>;
  // Lives en cours, du plus récent au plus ancien ; filtrés par chaîne si [channelId] est donné.
  listRunning(channelId?: string): Promise<LiveRecord[]>;
  // Passe le live à « terminé ». Faux s'il l'était déjà (aucune diffusion en double).
  end(liveId: string, at: Date): Promise<boolean>;
  // Les [limit] derniers messages, du plus récent au plus ancien.
  listMessages(liveId: string, limit: number): Promise<LiveChatMessage[]>;
  addMessage(
    liveId: string,
    userId: string,
    content: string,
  ): Promise<LiveChatMessage>;
  findAuthor(userId: string): Promise<LiveAuthor | null>;
}
