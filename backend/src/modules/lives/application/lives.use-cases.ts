import type { LiveRooms } from '../../realtime/domain/live-rooms.js';
import {
  CHAT_HISTORY_SIZE,
  ChannelRequiredError,
  ChatRateLimiter,
  ChatTooFastError,
  LiveAlreadyRunningError,
  LiveEndedError,
  MIN_FOLLOWERS_FOR_LIVE,
  NotEnoughFollowersError,
  VideoNotEligibleError,
  canStreamVideo,
  toLiveView,
  type LiveChatMessage,
  type LiveRecord,
  type LiveView,
} from '../domain/live.js';
import type { LivesRepository } from '../domain/ports.js';

// L'appelant n'est pas le propriétaire de la chaîne du live.
export class NotLiveOwnerError extends Error {
  constructor() {
    super('Seul le diffuseur peut terminer ce live.');
  }
}

/**
 * Cas d'usage des lives (ADR-029, étape 1) : démarrer, regarder, terminer, discuter. Le serveur
 * est seul juge : seuil d'abonnés, vidéo admissible, propriétaire, débit du chat.
 */
export class LivesUseCases {
  constructor(
    private readonly repository: LivesRepository,
    private readonly rooms: LiveRooms,
    private readonly rateLimiter: ChatRateLimiter = new ChatRateLimiter(),
    private readonly now: () => Date = () => new Date(),
  ) {}

  /**
   * Démarre un live sur la chaîne de [userId], avec [videoId] comme flux. Lève une erreur du
   * domaine si l'utilisateur n'a pas de chaîne, compte moins de 50 abonnés, si la vidéo n'est pas
   * admissible (D1) ou si un live est déjà en cours.
   */
  async start(
    userId: string,
    title: string,
    videoId: string,
  ): Promise<LiveView> {
    const channelId = await this.repository.findOwnChannelId(userId);
    if (!channelId) {
      throw new ChannelRequiredError();
    }
    if (
      (await this.repository.countFollowers(channelId)) < MIN_FOLLOWERS_FOR_LIVE
    ) {
      throw new NotEnoughFollowersError();
    }
    const candidate = await this.repository.findStreamCandidate(videoId);
    if (!canStreamVideo(candidate, channelId)) {
      throw new VideoNotEligibleError();
    }
    const live = await this.repository.createIfNoneRunning(
      channelId,
      title.trim(),
      videoId,
    );
    if (!live) {
      throw new LiveAlreadyRunningError();
    }
    return this.view(live);
  }

  // État du live, en cours ou terminé ; null s'il n'existe pas.
  async get(liveId: string): Promise<LiveView | null> {
    const live = await this.repository.find(liveId);
    return live ? this.view(await this.refresh(live)) : null;
  }

  // Lives en cours, publics, avec leur nombre de spectateurs.
  async listRunning(channelId?: string): Promise<LiveView[]> {
    const lives = await this.repository.listRunning(channelId);
    const refreshed = await Promise.all(
      lives.map((live) => this.refresh(live)),
    );
    return refreshed
      .filter((live) => live.status === 'LIVE')
      .map((live) => this.view(live));
  }

  /**
   * Termine le live (D2) : il disparaît de l'accueil, ses spectateurs en sont prévenus puis
   * sortent de la salle. Sans effet s'il est déjà terminé. null s'il n'existe pas.
   */
  async end(userId: string, liveId: string): Promise<LiveView | null> {
    const live = await this.repository.find(liveId);
    if (!live) {
      return null;
    }
    if (live.channel.ownerId !== userId) {
      throw new NotLiveOwnerError();
    }
    return this.view(await this.finish(live));
  }

  // Derniers messages du chat, du plus récent au plus ancien ; null si le live n'existe pas.
  async listMessages(liveId: string): Promise<LiveChatMessage[] | null> {
    if (!(await this.repository.find(liveId))) {
      return null;
    }
    return this.repository.listMessages(liveId, CHAT_HISTORY_SIZE);
  }

  /**
   * Écrit dans le chat, puis diffuse le message à la salle (la WebSocket ne fait que recevoir,
   * ADR-010). null si le live n'existe pas. Lève LiveEndedError s'il est terminé et
   * ChatTooFastError au-delà d'un message par seconde.
   */
  async send(
    userId: string,
    liveId: string,
    content: string,
  ): Promise<LiveChatMessage | null> {
    const found = await this.repository.find(liveId);
    if (!found) {
      return null;
    }
    const live = await this.refresh(found);
    if (live.status !== 'LIVE') {
      throw new LiveEndedError();
    }
    if (!this.rateLimiter.tryAcquire(userId)) {
      throw new ChatTooFastError();
    }
    const message = await this.repository.addMessage(
      liveId,
      userId,
      content.trim(),
    );
    this.rooms.toLive(liveId, 'live:message', message);
    return message;
  }

  /**
   * Fin paresseuse (D1) : si la vidéo du flux a été supprimée, est devenue privée ou Premium, ou
   * a changé de chaîne, le live se termine à la prochaine lecture de son état, sans crochet dans
   * le module des vidéos.
   */
  private async refresh(live: LiveRecord): Promise<LiveRecord> {
    if (live.status !== 'LIVE') {
      return live;
    }
    if (live.video && canStreamVideo(live.video, live.channel.id)) {
      return live;
    }
    return this.finish(live);
  }

  private async finish(live: LiveRecord): Promise<LiveRecord> {
    if (live.status === 'ENDED') {
      return live;
    }
    const at = this.now();
    if (await this.repository.end(live.id, at)) {
      this.rooms.toLive(live.id, 'live:ended', { liveId: live.id });
      this.rooms.close(live.id);
    }
    return { ...live, status: 'ENDED', endedAt: live.endedAt ?? at };
  }

  private view(live: LiveRecord): LiveView {
    return toLiveView(
      live,
      live.status === 'LIVE' ? this.rooms.viewerCount(live.id) : 0,
    );
  }
}
