"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  EMPTY_VIDEO_FORM,
  toVideoPayload,
  validateVideoForm,
  videoToFormValues,
  type VideoFormErrors,
  type VideoFormValues,
} from "@/models/videoForm";
import {
  createVideo,
  getVideo,
  listCategories,
  listChannels,
  updateVideo,
} from "@/services/catalogService";
import { errorMessage, useRemoteData } from "./useRemoteData";

/** Création (sans videoId) ou modification d'une vidéo. */
export function useVideoFormController(videoId?: string) {
  const router = useRouter();
  const { state, reload } = useRemoteData(`video-form:${videoId ?? "new"}`, async () => {
    const [channels, categories, video] = await Promise.all([
      listChannels(),
      listCategories(),
      videoId ? getVideo(videoId) : Promise.resolve(null),
    ]);
    return { channels, categories, video };
  });

  // Valeurs saisies ; tant que rien n'est modifié, on affiche celles de la vidéo chargée.
  const [edited, setEdited] = useState<VideoFormValues | null>(null);
  const loadedVideo = state.status === "success" ? state.data.video : null;
  const values = edited ?? (loadedVideo ? videoToFormValues(loadedVideo) : EMPTY_VIDEO_FORM);

  const [errors, setErrors] = useState<VideoFormErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function change<K extends keyof VideoFormValues>(field: K, value: VideoFormValues[K]) {
    setEdited({ ...values, [field]: value });
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  async function submit() {
    const fieldErrors = validateVideoForm(values);
    setErrors(fieldErrors);
    if (Object.values(fieldErrors).some(Boolean)) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const payload = toVideoPayload(values);
      if (videoId) await updateVideo(videoId, payload);
      else await createVideo(payload);
      router.push("/videos");
    } catch (error) {
      setSubmitError(errorMessage(error));
      setSubmitting(false);
    }
  }

  return { state, reload, values, change, errors, submitError, submitting, submit, isEdit: !!videoId };
}
