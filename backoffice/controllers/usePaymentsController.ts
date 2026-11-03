"use client";

import { useState } from "react";
import { getRevenueStats, listSubscriptions } from "@/services/paymentsService";
import { useRemoteData } from "./useRemoteData";

const PAGE_SIZE = 20;

// Chiffres et table se chargent séparément : l'échec de l'un n'empêche pas l'affichage de l'autre.
export function usePaymentsController() {
  const [page, setPage] = useState(1);
  const stats = useRemoteData("payments:stats", getRevenueStats);
  const subscriptions = useRemoteData(`payments:${page}`, () => listSubscriptions(page, PAGE_SIZE));
  return { stats, subscriptions, page, setPage };
}
