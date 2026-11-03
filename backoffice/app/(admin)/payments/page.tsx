"use client";

import { usePaymentsController } from "@/controllers/usePaymentsController";
import { PaymentsView } from "@/views/PaymentsView";

export default function PaymentsPage() {
  const { stats, subscriptions, page, setPage } = usePaymentsController();
  return (
    <PaymentsView
      stats={stats.state}
      onRetryStats={stats.reload}
      subscriptions={subscriptions.state}
      onRetrySubscriptions={subscriptions.reload}
      page={page}
      onPageChange={setPage}
    />
  );
}
