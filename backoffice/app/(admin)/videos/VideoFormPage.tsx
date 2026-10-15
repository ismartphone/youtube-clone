"use client";

import { useVideoFormController } from "@/controllers/useVideoFormController";
import { VideoFormView } from "@/views/VideoFormView";

/** Formulaire partagé par /videos/new (sans videoId) et /videos/[id]. */
export function VideoFormPage({ videoId }: { videoId?: string }) {
  const form = useVideoFormController(videoId);
  return (
    <VideoFormView
      isEdit={form.isEdit}
      state={form.state}
      onRetry={form.reload}
      values={form.values}
      onChange={form.change}
      errors={form.errors}
      submitError={form.submitError}
      submitting={form.submitting}
      onSubmit={form.submit}
    />
  );
}
