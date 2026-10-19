"use client";

import { useChannelsController } from "@/controllers/useChannelsController";
import { ChannelsView } from "@/views/ChannelsView";

export default function ChannelsPage() {
  const channels = useChannelsController();
  return (
    <ChannelsView
      state={channels.state}
      onRetry={channels.reload}
      bannerDraft={channels.bannerDraft}
      onBannerChange={channels.changeBanner}
      errors={channels.errors}
      savingId={channels.savingId}
      onSave={channels.saveBanner}
    />
  );
}
