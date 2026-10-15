"use client";

import { useVideoListController } from "@/controllers/useVideoListController";
import { VideoListView } from "@/views/VideoListView";

export default function VideosPage() {
  const list = useVideoListController();
  return (
    <VideoListView
      state={list.state}
      onRetry={list.reload}
      page={list.page}
      onPageChange={list.setPage}
      deletingId={list.deletingId}
      actionError={list.actionError}
      onDelete={list.remove}
    />
  );
}
