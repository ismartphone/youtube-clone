// Les miniatures générées par le serveur sont enregistrées avec un chemin relatif
// (/media/thumbnails/<id>.jpg) : chaque client le complète avec l'adresse par laquelle il
// joint l'API. Ici, le navigateur passe par le relais /api (voir next.config.ts).
// Une URL complète (fichier hébergé ailleurs, futur CDN) est gardée telle quelle.
export function mediaUrl(url: string): string {
  return url.startsWith("/") ? `/api${url}` : url;
}
