"use client";

import { useDashboardController } from "@/controllers/useDashboardController";
import { DashboardView } from "@/views/DashboardView";

export default function DashboardPage() {
  const { state, reload } = useDashboardController();
  return <DashboardView state={state} onRetry={reload} />;
}
