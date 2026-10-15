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
  thumbnailUrl: string;
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

  if (!isHttpUrl(values.videoUrl.trim())) {
    errors.videoUrl = "Saisissez une URL complète, commençant par http:// ou https://.";
  }
  if (!isHttpUrl(values.thumbnailUrl.trim())) {
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
    thumbnailUrl: values.thumbnailUrl.trim(),
    durationSeconds: Number(values.durationSeconds),
  };
}
