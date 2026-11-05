// Les miniatures générées par le serveur sont enregistrées avec un chemin relatif
// (/media/thumbnails/<id>.jpg) : chaque client le complète avec l'adresse par laquelle il
// joint l'API. Ici, le navigateur passe par le relais /api (voir next.config.ts).
// Une URL complète (fichier hébergé ailleurs, futur CDN) est gardée telle quelle.
export function mediaUrl(url: string): string {
  return url.startsWith("/") ? `/api${url}` : url;
}

// Message affiché quand une URL saisie vise la machine qui héberge le serveur.
export const LOCALHOST_URL_MESSAGE =
  "Cette adresse pointe vers localhost : elle n'est pas joignable depuis un téléphone. " +
  "Utilisez le chemin relatif (/media/…) ou une adresse publique.";

// Vrai pour une URL complète vers la machine locale (localhost, 127.x, 0.0.0.0, ::1) ou vers
// 10.0.2.2, l'alias de l'ordinateur vu depuis l'émulateur Android. Un chemin relatif ou une
// valeur qui n'est pas une URL ne sont pas concernés : leurs propres contrôles s'en chargent.
export function isLocalhostUrl(value: string): boolean {
  let hostname: string;
  try {
    hostname = new URL(value.trim()).hostname.toLowerCase();
  } catch {
    return false;
  }
  return (
    hostname === "localhost" ||
    hostname.endsWith(".localhost") ||
    hostname === "0.0.0.0" ||
    hostname === "[::1]" ||
    hostname === "10.0.2.2" ||
    /^127(\.\d{1,3}){3}$/.test(hostname)
  );
}
