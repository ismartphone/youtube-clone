// Lives et leurs achats (ADR-029), tels que renvoyés par GET /admin/lives. Les achats sont des
// données personnelles (nom, email de l'acheteur) et financières, comme les paiements Premium :
// la page est réservée aux administrateurs, comme tout le back-office.

export interface LivePurchase {
  id: string;
  buyer: { id: string; displayName: string; email: string };
  productName: string;
  // Montant réellement payé, en centimes, et sa devise ISO en minuscules (« eur »).
  amount: number;
  currency: string;
  // Identifiant du PaymentIntent Stripe (pi_…), ou jeton de l'achat simulé de repli.
  paymentReference: string;
  createdAt: string;
}

export interface AdminLive {
  id: string;
  title: string;
  status: "LIVE" | "ENDED";
  startedAt: string;
  endedAt: string | null;
  channel: { id: string; name: string };
  purchases: LivePurchase[];
}

/** Total encaissé par un live, devises écrites à la suite (en pratique une seule : l'euro). */
export function purchaseTotals(purchases: LivePurchase[]): { currency: string; amount: number }[] {
  const totals = new Map<string, number>();
  for (const purchase of purchases) {
    totals.set(purchase.currency, (totals.get(purchase.currency) ?? 0) + purchase.amount);
  }
  return [...totals].map(([currency, amount]) => ({ currency, amount }));
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("fr-FR", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}
