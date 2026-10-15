"use client";

import { getStats } from "@/services/catalogService";
import { useRemoteData } from "./useRemoteData";

export function useDashboardController() {
  return useRemoteData("stats", getStats);
}
