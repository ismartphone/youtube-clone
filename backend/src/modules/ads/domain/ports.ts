import type { Ad, AdChanges, NewAd } from './ad.js';

// Ports du module ads (ADR-021) : les cas d'usage n'en connaissent que ces interfaces.

export const AD_REPOSITORY = Symbol('AD_REPOSITORY');
export const AD_FILE_STORE = Symbol('AD_FILE_STORE');

export interface AdRepository {
  // De la plus récente à la plus ancienne.
  findAll(): Promise<Ad[]>;
  create(ad: NewAd): Promise<Ad>;
  // Renvoie false si la publicité a été supprimée entre-temps.
  setHlsUrl(id: string, hlsUrl: string): Promise<boolean>;
  // Renvoie la publicité modifiée, ou null si elle n'existe pas.
  update(id: string, changes: AdChanges): Promise<Ad | null>;
  // Renvoie la publicité avant modification, ou null si elle n'existe pas.
  setLogoUrl(id: string, logoUrl: string | null): Promise<Ad | null>;
  // Renvoie la publicité supprimée, ou null si elle n'existait pas.
  delete(id: string): Promise<Ad | null>;
  // Une publicité au hasard, ou null s'il n'y en a aucune.
  pickRandom(): Promise<Ad | null>;
  // Fichiers déjà enregistrés : l'import du dossier ne les reprend pas.
  findVideoUrls(): Promise<string[]>;
}

export interface AdFileStore {
  // Range le fichier [path] dans /media/ads/ sous un nom généré et renvoie son URL.
  save(path: string): Promise<string>;
  // Supprime un fichier rangé par save ; sans effet pour toute autre URL.
  remove(videoUrl: string): Promise<void>;
}

// Logo de l'annonceur : JPEG ou PNG vérifié par ffprobe, recadré carré (ADR-021).
export interface AdLogoStore {
  // Lève InvalidChannelImageError ou ChannelImageUnavailableError.
  save(path: string): Promise<string>;
  // Supprime un logo rangé par save ; sans effet pour toute autre URL.
  remove(url: string): Promise<void>;
}
