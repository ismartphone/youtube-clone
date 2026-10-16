import type { VideoDetail, VideoFormat } from "./catalog";

// Formulaire de création et de modification d'une vidéo.
// Mêmes règles que la validation de l'API (admin.bodies.ts), pour signaler l'erreur avant l'envoi.

export interface VideoFormValues {
  title: string;
  description: string;
  channelId: string;
  categoryId: string; // "" : aucune catégorie
  format: VideoFormat;
  isPremium: boolean;
  videoUrl: string;
  thumbnailUrl: string;
  durationSeconds: string; // saisi en texte, converti à l'envoi
}

export type VideoFormErrors = Partial<Record<keyof VideoFormValues, string>>;

// Corps envoyé à POST et PATCH /admin/videos.
export interface VideoPayload {
  title: string;
  description: string | null;
  channelId: string;
  categoryId: string | null;
  format: VideoFormat;
  isPremium: boolean;
  videoUrl: string;
  thumbnailUrl: string | null; // null : le serveur génère une image extraite de la vidéo
  durationSeconds: number;
}

const MAX_DURATION_SECONDS = 12 * 60 * 60;

export const EMPTY_VIDEO_FORM: VideoFormValues = {
  title: "",
  description: "",
  channelId: "",
  categoryId: "",
  format: "LONG",
  isPremium: false,
  videoUrl: "",
  thumbnailUrl: "",
  durationSeconds: "",
};

export function videoToFormValues(video: VideoDetail): VideoFormValues {
  return {
    title: video.title,
    description: video.description ?? "",
    channelId: video.channel.id,
    categoryId: video.category?.id ?? "",
    format: video.format,
    isPremium: video.isPremium,
    videoUrl: video.videoUrl ?? "",
    thumbnailUrl: video.thumbnailUrl,
    durationSeconds: String(video.durationSeconds),
  };
}

// Chemin des miniatures générées par le serveur (voir mediaUrl dans models/media.ts).
function isGeneratedThumbnail(value: string): boolean {
  return /^\/media\/thumbnails\/[0-9a-f-]{36}\.jpg$/.test(value);
}

// Vidéo hébergée chez nous (ADR-002) : chemin relatif vers le stockage du serveur.
function isLocalVideo(value: string): boolean {
  return /^\/media\/videos\/[\w.-]+\.mp4$/.test(value);
}

function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

export function validateVideoForm(values: VideoFormValues): VideoFormErrors {
  const errors: VideoFormErrors = {};
  const title = values.title.trim();
  if (!title) errors.title = "Saisissez un titre.";
  else if (title.length > 100) errors.title = "Le titre ne doit pas dépasser 100 caractères.";

  if (values.description.trim().length > 5000) {
    errors.description = "La description ne doit pas dépasser 5 000 caractères.";
  }
  if (!values.channelId) errors.channelId = "Choisissez une chaîne.";

  const video = values.videoUrl.trim();
  if (!isHttpUrl(video) && !isLocalVideo(video)) {
    errors.videoUrl =
      "Saisissez une URL complète (http:// ou https://) ou le chemin d'une vidéo stockée sur le serveur (/media/videos/…).";
  }
  // Facultative : vide, le serveur la génère. Une miniature déjà générée garde son chemin relatif.
  const thumbnail = values.thumbnailUrl.trim();
  if (thumbnail && !isHttpUrl(thumbnail) && !isGeneratedThumbnail(thumbnail)) {
    errors.thumbnailUrl = "Saisissez une URL complète, commençant par http:// ou https://.";
  }

  const duration = Number(values.durationSeconds);
  if (!Number.isInteger(duration) || duration < 1 || duration > MAX_DURATION_SECONDS) {
    errors.durationSeconds = "Saisissez une durée en secondes, entre 1 et 43 200.";
  }
  return errors;
}

export function toVideoPayload(values: VideoFormValues): VideoPayload {
  const description = values.description.trim();
  return {
    title: values.title.trim(),
    description: description === "" ? null : description,
    channelId: values.channelId,
    categoryId: values.categoryId === "" ? null : values.categoryId,
    format: values.format,
    isPremium: values.isPremium,
    videoUrl: values.videoUrl.trim(),
    thumbnailUrl: values.thumbnailUrl.trim() || null,
    durationSeconds: Number(values.durationSeconds),
  };
}
