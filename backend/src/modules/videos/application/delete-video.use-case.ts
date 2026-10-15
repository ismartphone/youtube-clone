import type { VideoCatalog } from '../domain/catalog.js';

export class DeleteVideoUseCase {
  constructor(private readonly catalog: VideoCatalog) {}

  // Renvoie false si la vidéo n'existe pas. Ses commentaires, « j'aime » et historiques
  // sont supprimés avec elle (cascade du schéma). Le fichier vidéo, lui, n'est pas touché.
  execute(id: string): Promise<boolean> {
    return this.catalog.delete(id);
  }
}
