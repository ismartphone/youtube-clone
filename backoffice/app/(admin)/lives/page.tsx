"use client";

import { useLivesController } from "@/controllers/useLivesController";
import { LivesView } from "@/views/LivesView";

export default function LivesPage() {
  const lives = useLivesController();
  return (
    <LivesView
      state={lives.state}
      onRetry={lives.reload}
      endingId={lives.endingId}
      actionError={lives.actionError}
      onEnd={lives.end}
    />
  );
}
