import type { RemoteData } from "@/controllers/useRemoteData";
import { pageCount, type Page } from "@/models/catalog";
import { formatNumber } from "@/models/format";
import {
  formatDate,
  formatMoney,
  formatMonth,
  formatTotals,
  revenueByMonth,
  stripePaymentUrl,
  type MonthlyRevenue,
  type RevenueStats,
  type Subscription,
} from "@/models/payments";
import {
  Badge,
  DataTable,
  EmptyState,
  ErrorBanner,
  PageHeader,
  Pagination,
  Panel,
  Skeleton,
  TableSkeleton,
  Td,
  Th,
  Tr,
} from "./ui";

// Section Paiements (ADR-014). Elle affiche des données personnelles (nom, email) et
// financières (montants, références de paiement) : comme tout le back-office, elle n'est
// accessible qu'aux administrateurs, et l'API vérifie elle-même le rôle ADMIN.

const COLUMNS = ["Utilisateur", "Souscription", "Fin", "État", "Montant", "Référence du paiement"];

export function PaymentsView({
  stats,
  onRetryStats,
  subscriptions,
  onRetrySubscriptions,
  page,
  onPageChange,
}: {
  stats: RemoteData<RevenueStats>;
  onRetryStats: () => void;
  subscriptions: RemoteData<Page<Subscription>>;
  onRetrySubscriptions: () => void;
  page: number;
  onPageChange: (page: number) => void;
}) {
  const cards: { label: string; value: (s: RevenueStats) => string }[] = [
    { label: "Total encaissé", value: (s) => formatTotals(s.totals) },
    { label: "Abonnements actifs", value: (s) => formatNumber(s.activeCount) },
    { label: "Abonnements expirés", value: (s) => formatNumber(s.expiredCount) },
  ];
  return (
    <>
      <PageHeader title="Paiements" description="Abonnements Premium et recettes." />
      {stats.status === "error" && <ErrorBanner message={stats.message} onRetry={onRetryStats} />}
      {stats.status !== "error" && (
        <>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-3" aria-busy={stats.status === "loading"}>
            {cards.map(({ label, value }) => (
              <div key={label} className="rounded-xl border border-border bg-surface px-5 py-6">
                <dt className="text-xs font-medium tracking-wide text-muted uppercase">{label}</dt>
                <dd className="mt-3 text-4xl font-semibold tracking-tight tabular-nums">
                  {stats.status === "success" ? value(stats.data) : <Skeleton className="h-10 w-24" />}
                </dd>
              </div>
            ))}
          </dl>
          <div className="mt-6">
            <Panel title="Recettes par mois">
              {stats.status === "loading" && <Skeleton className="h-48" />}
              {stats.status === "success" && <RevenueChart months={revenueByMonth(stats.data)} />}
            </Panel>
          </div>
        </>
      )}
      <div className="mt-6">
        {subscriptions.status === "loading" && <TableSkeleton columns={COLUMNS} />}
        {subscriptions.status === "error" && (
          <ErrorBanner message={subscriptions.message} onRetry={onRetrySubscriptions} />
        )}
        {subscriptions.status === "success" && subscriptions.data.items.length === 0 && (
          <EmptyState
            title="Aucun abonnement pour le moment"
            description="Les abonnements Premium achetés depuis l'application apparaîtront ici."
          />
        )}
        {subscriptions.status === "success" && subscriptions.data.items.length > 0 && (
          <>
            <DataTable minWidth={880}>
              <thead>
                <tr>
                  <Th>Utilisateur</Th>
                  <Th>Souscription</Th>
                  <Th>Fin</Th>
                  <Th>État</Th>
                  <Th align="right">Montant</Th>
                  <Th>Référence du paiement</Th>
                </tr>
              </thead>
              <tbody>
                {subscriptions.data.items.map((subscription) => (
                  <SubscriptionRow key={subscription.id} subscription={subscription} />
                ))}
              </tbody>
            </DataTable>
            <Pagination page={page} pageCount={pageCount(subscriptions.data)} onChange={onPageChange} />
          </>
        )}
      </div>
    </>
  );
}

function SubscriptionRow({ subscription }: { subscription: Subscription }) {
  const stripeUrl = stripePaymentUrl(subscription.paymentReference);
  return (
    <Tr>
      <Td>
        <div className="font-medium">{subscription.user.displayName}</div>
        <div className="text-muted">{subscription.user.email}</div>
      </Td>
      <Td>{formatDate(subscription.startedAt)}</Td>
      <Td>{formatDate(subscription.expiresAt)}</Td>
      <Td>
        {subscription.isActive ? <Badge tone="accent">Actif</Badge> : <Badge>Expiré</Badge>}
      </Td>
      <Td align="right">
        <span className="tabular-nums">{formatMoney(subscription.amount, subscription.currency)}</span>
      </Td>
      <Td>
        {stripeUrl ? (
          <a
            href={stripeUrl}
            target="_blank"
            rel="noreferrer"
            className="font-mono text-xs text-accent underline-offset-2 hover:underline"
          >
            {subscription.paymentReference}
          </a>
        ) : (
          // Achat simulé de repli : pas de fiche Stripe.
          <span className="font-mono text-xs text-muted" title="Achat simulé, sans fiche Stripe">
            simulé · {subscription.paymentReference.slice(0, 10)}…
          </span>
        )}
      </Td>
    </Tr>
  );
}

const ROW_GRID = "grid grid-cols-[5.5rem_1fr_5.5rem] items-center gap-3";

/** Barres horizontales en SVG, une par mois, longueur proportionnelle au meilleur mois. */
function RevenueChart({ months }: { months: MonthlyRevenue[] }) {
  const max = Math.max(...months.map((m) => m.amount));
  if (max === 0) {
    return (
      <EmptyState
        title="Aucune recette sur les douze derniers mois"
        description="Le graphique apparaîtra au premier abonnement payé."
      />
    );
  }
  return (
    <ol className="flex flex-col">
      {months.map((month) => {
        const share = (month.amount / max) * 100;
        const amount = formatMoney(month.amount, month.currency);
        return (
          <li
            key={month.month}
            title={`${formatMonth(month.month)} : ${amount}`}
            className={`${ROW_GRID} rounded-md px-2 py-1.5 text-sm hover:bg-surface-hover`}
          >
            <span className="text-muted capitalize">
              {formatMonth(month.month)}
              <span className="sr-only"> : {amount}</span>
            </span>
            <svg aria-hidden className="h-3 w-full overflow-visible">
              {share > 0 && (
                <>
                  {/* Extrémité arrondie côté valeur, bord droit côté ligne de base. */}
                  <rect x="0" y="0" width={`${share}%`} height="12" rx="4" fill="var(--accent)" />
                  <rect x="0" y="0" width={`${share / 2}%`} height="12" fill="var(--accent)" />
                </>
              )}
            </svg>
            <span aria-hidden className="text-right text-muted tabular-nums">
              {amount}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
