"use client";

import { useDashboardController } from "@/controllers/useDashboardController";
import { DashboardView } from "@/views/DashboardView";

export default function DashboardPage() {
  const { stats, topVideos } = useDashboardController();
  return (
    <DashboardView
      stats={stats.state}
      onRetryStats={stats.reload}
      topVideos={topVideos.state}
      onRetryTopVideos={topVideos.reload}
    />
  );
}
