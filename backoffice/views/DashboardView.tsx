import type { RemoteData } from "@/controllers/useRemoteData";
import type { CatalogStats } from "@/models/catalog";
import { formatNumber } from "@/models/format";
import { ErrorBanner, Loading, PageHeader } from "./ui";

const STATS: { key: keyof CatalogStats; label: string }[] = [
  { key: "users", label: "Utilisateurs" },
  { key: "channels", label: "Chaînes" },
  { key: "videos", label: "Vidéos" },
  { key: "totalViews", label: "Vues totales" },
];

export function DashboardView({ state, onRetry }: { state: RemoteData<CatalogStats>; onRetry: () => void }) {
  return (
    <>
      <PageHeader title="Tableau de bord" />
      {state.status === "loading" && <Loading />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && (
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {STATS.map(({ key, label }) => (
            <div key={key} className="rounded-2xl border border-border bg-surface p-6">
              <dt className="text-sm text-muted">{label}</dt>
              <dd className="mt-2 text-3xl font-semibold tabular-nums">{formatNumber(state.data[key])}</dd>
            </div>
          ))}
        </dl>
      )}
    </>
  );
}
