import { MAX_PLAYLISTS_PER_USER, MAX_VIDEOS_PER_PLAYLIST } from './playlist.js';

// Erreurs métier des playlists, traduites en codes HTTP par la couche interfaces.

// Aussi pour la playlist d'un autre utilisateur : elle n'existe que pour son propriétaire.
export class PlaylistNotFoundError extends Error {
  constructor() {
    super('Playlist introuvable');
  }
}

// Aussi pour une vidéo privée d'un autre (ADR-016).
export class VideoNotFoundError extends Error {
  constructor() {
    super('Vidéo introuvable');
  }
}

export class TooManyPlaylistsError extends Error {
  constructor() {
    super(
      `Vous avez atteint la limite de ${MAX_PLAYLISTS_PER_USER} playlists.`,
    );
  }
}

export class PlaylistFullError extends Error {
  constructor() {
    super(
      `Cette playlist contient déjà ${MAX_VIDEOS_PER_PLAYLIST} vidéos, le maximum.`,
    );
  }
}
