"use client";

import { useAdsController } from "@/controllers/useAdsController";
import { AdsView } from "@/views/AdsView";

export default function AdsPage() {
  const ads = useAdsController();
  return (
    <AdsView
      state={ads.state}
      onRetry={ads.reload}
      title={ads.title}
      onTitleChange={ads.changeTitle}
      fileInputKey={ads.fileInputKey}
      onFileChange={ads.changeFile}
      formError={ads.formError}
      importing={ads.importing}
      onImport={ads.submit}
      deletingId={ads.deletingId}
      actionError={ads.actionError}
      onDelete={ads.remove}
    />
  );
}
