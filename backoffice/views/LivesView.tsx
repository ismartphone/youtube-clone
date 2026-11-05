import type { RemoteData } from "@/controllers/useRemoteData";
import { formatDateTime, purchaseTotals, type AdminLive } from "@/models/lives";
import { formatMoney, stripePaymentUrl } from "@/models/payments";
import { Badge, Button, DataTable, EmptyState, ErrorBanner, PageHeader, TableSkeleton, Td, Th, Tr } from "./ui";

// Page Lives (ADR-029). Elle affiche des données personnelles (nom, email de l'acheteur) et
// financières (montants, références de paiement) : comme tout le back-office, elle n'est
// accessible qu'aux administrateurs, et l'API vérifie elle-même le rôle ADMIN.

const COLUMNS = ["Live", "Chaîne", "Début", "Fin", "État", "Achats", "Actions"];

function Purchases({ live }: { live: AdminLive }) {
  if (live.purchases.length === 0) return <span className="text-muted">Aucun achat</span>;
  const total = purchaseTotals(live.purchases)
    .map((t) => formatMoney(t.amount, t.currency))
    .join(" + ");
  return (
    <details>
      <summary className="cursor-pointer text-sm font-medium">
        {live.purchases.length} achat{live.purchases.length > 1 ? "s" : ""}, {total}
      </summary>
      <ul className="mt-2 flex flex-col gap-2 text-sm">
        {live.purchases.map((purchase) => {
          const stripeUrl = stripePaymentUrl(purchase.paymentReference);
          return (
            <li key={purchase.id} className="rounded-md border border-border p-2">
              <div className="font-medium">
                {purchase.productName}, {formatMoney(purchase.amount, purchase.currency)}
              </div>
              <div className="text-muted">
                {purchase.buyer.displayName} ({purchase.buyer.email}), {formatDateTime(purchase.createdAt)}
              </div>
              <div className="break-all text-xs text-muted">
                {stripeUrl ? (
                  <a href={stripeUrl} target="_blank" rel="noreferrer" className="underline">
                    {purchase.paymentReference}
                  </a>
                ) : (
                  purchase.paymentReference
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </details>
  );
}

export function LivesView({
  state,
  onRetry,
  endingId,
  actionError,
  onEnd,
}: {
  state: RemoteData<AdminLive[]>;
  onRetry: () => void;
  endingId: string | null;
  actionError: string | null;
  onEnd: (live: AdminLive) => void;
}) {
  return (
    <>
      <PageHeader title="Lives" description="Les lives en cours et terminés, avec les achats faits pendant chacun." />
      {actionError && (
        <div className="mb-4">
          <ErrorBanner message={actionError} />
        </div>
      )}
      {state.status === "loading" && <TableSkeleton columns={COLUMNS} rows={4} />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && state.data.length === 0 && (
        <EmptyState
          title="Aucun live pour le moment"
          description="Un live démarre depuis l'onglet « En direct » de la feuille de création de l'application."
        />
      )}
      {state.status === "success" && state.data.length > 0 && (
        <DataTable minWidth={900}>
          <thead>
            <tr>
              {COLUMNS.map((column) => (
                <Th key={column} align={column === "Actions" ? "right" : "left"}>
                  {column}
                </Th>
              ))}
            </tr>
          </thead>
          <tbody>
            {state.data.map((live) => (
              <Tr key={live.id}>
                <Td>{live.title}</Td>
                <Td>{live.channel.name}</Td>
                <Td>{formatDateTime(live.startedAt)}</Td>
                <Td>{live.endedAt ? formatDateTime(live.endedAt) : "—"}</Td>
                <Td>{live.status === "LIVE" ? <Badge tone="accent">En direct</Badge> : <Badge>Terminé</Badge>}</Td>
                <Td>
                  <Purchases live={live} />
                </Td>
                <Td align="right">
                  {live.status === "LIVE" && (
                    <div className="flex justify-end">
                      <Button variant="danger" size="sm" disabled={endingId === live.id} onClick={() => onEnd(live)}>
                        {endingId === live.id ? "Fin en cours…" : "Terminer"}
                      </Button>
                    </div>
                  )}
                </Td>
              </Tr>
            ))}
          </tbody>
        </DataTable>
      )}
    </>
  );
}
