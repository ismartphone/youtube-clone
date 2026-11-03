import type { RemoteData } from "@/controllers/useRemoteData";
import type { CatalogStats, Video } from "@/models/catalog";
import { formatNumber } from "@/models/format";
import { EmptyState, ErrorBanner, PageHeader, Panel, Skeleton } from "./ui";

// Pas de graphique des vues par jour, par choix : la base ne garde qu'un total par vidéo, et
// la donnée horodatée existe déjà dans PostHog (événement video_played).

const STATS: { key: keyof CatalogStats; label: string }[] = [
  { key: "users", label: "Utilisateurs" },
  { key: "channels", label: "Chaînes" },
  { key: "videos", label: "Vidéos" },
  { key: "totalViews", label: "Vues totales" },
];

export function DashboardView({
  stats,
  onRetryStats,
  topVideos,
  onRetryTopVideos,
}: {
  stats: RemoteData<CatalogStats>;
  onRetryStats: () => void;
  topVideos: RemoteData<Video[]>;
  onRetryTopVideos: () => void;
}) {
  return (
    <>
      <PageHeader title="Tableau de bord" description="Vue d'ensemble du catalogue." />
      {stats.status === "error" && <ErrorBanner message={stats.message} onRetry={onRetryStats} />}
      {stats.status !== "error" && (
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-busy={stats.status === "loading"}>
          {STATS.map(({ key, label }) => (
            <div key={key} className="rounded-xl border border-border bg-surface px-5 py-6">
              <dt className="text-xs font-medium tracking-wide text-muted uppercase">{label}</dt>
              <dd className="mt-3 text-4xl font-semibold tracking-tight tabular-nums">
                {stats.status === "success" ? formatNumber(stats.data[key]) : <Skeleton className="h-10 w-24" />}
              </dd>
            </div>
          ))}
        </dl>
      )}
      <div className="mt-6">
        <Panel title="Les 10 vidéos les plus vues">
          {topVideos.status === "loading" && <TopVideosSkeleton />}
          {topVideos.status === "error" && <ErrorBanner message={topVideos.message} onRetry={onRetryTopVideos} />}
          {topVideos.status === "success" &&
            (topVideos.data.length === 0 || topVideos.data[0].viewCount === 0 ? (
              <EmptyState
                title="Aucune vue pour le moment"
                description="Le classement apparaîtra dès qu'une vidéo aura été regardée."
              />
            ) : (
              <TopVideosChart videos={topVideos.data} />
            ))}
        </Panel>
      </div>
    </>
  );
}

const ROW_GRID = "grid grid-cols-[1.5rem_minmax(0,16rem)_1fr_3.5rem] items-center gap-3";

/** Barres horizontales en SVG : une par vidéo, longueur proportionnelle à la plus vue. */
function TopVideosChart({ videos }: { videos: Video[] }) {
  const max = videos[0].viewCount;
  return (
    <ol className="flex flex-col">
      {videos.map((video, index) => {
        const share = (video.viewCount / max) * 100;
        const views = `${formatNumber(video.viewCount)} vue(s)`;
        return (
          <li
            key={video.id}
            title={`${video.title} : ${views}`}
            className={`${ROW_GRID} rounded-md px-2 py-1.5 text-sm hover:bg-surface-hover`}
          >
            <span className="text-right text-xs text-muted tabular-nums">{index + 1}</span>
            <span className="truncate">
              {video.title}
              <span className="sr-only"> : {views}</span>
            </span>
            <svg aria-hidden className="h-3 w-full overflow-visible">
              {/* Extrémité arrondie côté valeur, bord droit côté ligne de base. */}
              <rect x="0" y="0" width={`${share}%`} height="12" rx="4" fill="var(--accent)" />
              <rect x="0" y="0" width={`${share / 2}%`} height="12" fill="var(--accent)" />
            </svg>
            <span aria-hidden className="text-right text-muted tabular-nums">
              {formatNumber(video.viewCount)}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function TopVideosSkeleton() {
  return (
    <div role="status" aria-label="Chargement…" className="flex flex-col">
      {Array.from({ length: 6 }, (_, row) => (
        <div key={row} className={`${ROW_GRID} px-2 py-2`}>
          <Skeleton className="h-3 w-3 justify-self-end" />
          <Skeleton className="h-3.5 w-40" />
          <Skeleton className="h-3" />
          <Skeleton className="h-3.5 w-8 justify-self-end" />
        </div>
      ))}
    </div>
  );
}
