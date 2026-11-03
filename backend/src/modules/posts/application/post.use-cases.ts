import { ChannelNotFoundError, NoChannelError } from '../domain/post.errors.js';
import type { Post, PostPage } from '../domain/post.js';
import type {
  ImageProber,
  ImageStore,
  PostChannels,
  PostRepository,
} from '../domain/ports.js';

// Publication d'un post par le propriétaire d'une chaîne (ADR-015). L'image facultative est
// validée par ffprobe avant d'entrer dans le stockage, sous un nom généré par le serveur.
export class CreatePostUseCase {
  constructor(
    private readonly posts: PostRepository,
    private readonly channels: PostChannels,
    private readonly prober: ImageProber,
    private readonly images: ImageStore,
  ) {}

  // [tempImagePath] : image reçue dans un dossier temporaire, ou null. Déplacée si elle est
  // acceptée ; sinon, c'est à l'appelant de la supprimer.
  async execute(
    userId: string,
    text: string,
    tempImagePath: string | null,
  ): Promise<Post> {
    const channelId = await this.channels.channelIdOf(userId);
    if (!channelId) {
      throw new NoChannelError();
    }
    let imageUrl: string | null = null;
    if (tempImagePath) {
      const format = await this.prober.probe(tempImagePath);
      imageUrl = await this.images.save(tempImagePath, format);
    }
    try {
      return await this.posts.create({
        channelId,
        text: text.trim(),
        imageUrl,
      });
    } catch (error) {
      if (imageUrl) {
        await this.images.remove(imageUrl);
      }
      throw error;
    }
  }
}

// Onglet Posts de la page de chaîne : lecture publique.
export class ListChannelPostsUseCase {
  constructor(
    private readonly posts: PostRepository,
    private readonly channels: PostChannels,
  ) {}

  async execute(
    channelId: string,
    page: number,
    pageSize: number,
  ): Promise<PostPage> {
    if (!(await this.channels.exists(channelId))) {
      throw new ChannelNotFoundError();
    }
    return this.posts.findByChannel(channelId, page, pageSize);
  }
}
