// Abonnements Premium et recettes, tels que les renvoient les routes /admin/premium de l'API.
// Données personnelles (nom, email) et financières : la section Paiements est réservée aux
// administrateurs, comme tout le back-office (ADR-014).

export interface Subscription {
  id: string;
  user: { displayName: string; email: string };
  startedAt: string;
  expiresAt: string;
  isActive: boolean;
  // Montant réellement payé, en centimes, et sa devise ISO en minuscules (« eur »).
  amount: number;
  currency: string;
  // Identifiant du PaymentIntent Stripe (pi_…), ou jeton de l'achat simulé de repli.
  paymentReference: string;
}

export interface MonthlyRevenue {
  month: string; // « 2026-11 »
  currency: string;
  amount: number;
}

export interface RevenueStats {
  totals: { currency: string; amount: number }[];
  activeCount: number;
  expiredCount: number;
  monthly: MonthlyRevenue[];
}

const MONTHS_SHOWN = 12;

/** Montant en centimes mis en forme dans sa devise : 4,99 €. */
export function formatMoney(amount: number, currency: string): string {
  return new Intl.NumberFormat("fr-FR", { style: "currency", currency: currency.toUpperCase() }).format(amount / 100);
}

/** Total encaissé, toutes devises écrites à la suite (en pratique une seule : l'euro). */
export function formatTotals(totals: RevenueStats["totals"]): string {
  return totals.length === 0 ? formatMoney(0, "eur") : totals.map((t) => formatMoney(t.amount, t.currency)).join(" + ");
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("fr-FR", { day: "numeric", month: "short", year: "numeric" });
}

/**
 * Fiche du paiement dans le tableau de bord Stripe (mode test). Un jeton de l'achat simulé n'a
 * pas de fiche : null.
 */
export function stripePaymentUrl(reference: string): string | null {
  return reference.startsWith("pi_") ? `https://dashboard.stripe.com/test/payments/${reference}` : null;
}

/**
 * Recettes des douze derniers mois dans la devise principale, du plus ancien au plus récent,
 * mois sans recette compris (à zéro), pour que le graphique garde un mois par ligne.
 */
export function revenueByMonth(stats: RevenueStats, now: Date = new Date()): MonthlyRevenue[] {
  const currency = stats.totals[0]?.currency ?? "eur";
  const amounts = new Map(stats.monthly.filter((m) => m.currency === currency).map((m) => [m.month, m.amount]));
  return Array.from({ length: MONTHS_SHOWN }, (_, index) => {
    const date = new Date(now.getFullYear(), now.getMonth() - (MONTHS_SHOWN - 1 - index), 1);
    const month = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
    return { month, currency, amount: amounts.get(month) ?? 0 };
  });
}

/** « 2026-11 » devient « nov. 2026 ». */
export function formatMonth(month: string): string {
  const [year, monthIndex] = month.split("-").map(Number);
  return new Date(year, monthIndex - 1, 1).toLocaleDateString("fr-FR", { month: "short", year: "numeric" });
}
