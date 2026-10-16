import Image from "next/image";
import Link from "next/link";
import type { RemoteData } from "@/controllers/useRemoteData";
import { pageCount, type Page, type Video } from "@/models/catalog";
import { formatDuration, formatNumber } from "@/models/format";
import { mediaUrl } from "@/models/media";
import { Button, ErrorBanner, Loading, PageHeader, Pagination } from "./ui";

export function VideoListView({
  state,
  onRetry,
  page,
  onPageChange,
  deletingId,
  actionError,
  onDelete,
}: {
  state: RemoteData<Page<Video>>;
  onRetry: () => void;
  page: number;
  onPageChange: (page: number) => void;
  deletingId: string | null;
  actionError: string | null;
  onDelete: (video: Video) => void;
}) {
  return (
    <>
      <PageHeader
        title="Vidéos"
        action={
          <Link
            href="/videos/new"
            className="rounded-full bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent-hover"
          >
            Ajouter une vidéo
          </Link>
        }
      />
      {actionError && (
        <div className="mb-4">
          <ErrorBanner message={actionError} />
        </div>
      )}
      {state.status === "loading" && <Loading />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && state.data.items.length === 0 && (
        <p className="py-12 text-center text-sm text-muted">Aucune vidéo dans le catalogue.</p>
      )}
      {state.status === "success" && state.data.items.length > 0 && (
        <>
          <p className="mb-3 text-sm text-muted">{formatNumber(state.data.total)} vidéo(s)</p>
          <div className="overflow-x-auto rounded-2xl border border-border">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="bg-surface text-muted">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">Vidéo</th>
                  <th scope="col" className="px-4 py-3 font-medium">Chaîne</th>
                  <th scope="col" className="px-4 py-3 font-medium">Format</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Vues</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {state.data.items.map((video) => (
                  <tr key={video.id} className="border-t border-border">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        {/* URL saisies à la main, de n'importe quel domaine : pas d'optimisation Next.js. */}
                        <Image
                          src={mediaUrl(video.thumbnailUrl)}
                          alt=""
                          width={112}
                          height={63}
                          unoptimized
                          className="h-[63px] w-28 shrink-0 rounded-md bg-surface-hover object-cover"
                        />
                        <div className="min-w-0">
                          <p className="line-clamp-2 font-medium">{video.title}</p>
                          <p className="mt-0.5 text-xs text-muted">
                            {formatDuration(video.durationSeconds)}
                            {video.category && ` · ${video.category.name}`}
                            {video.isPremium && " · Premium"}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">{video.channel.name}</td>
                    <td className="px-4 py-3">{video.format === "SHORT" ? "Short" : "Vidéo"}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{formatNumber(video.viewCount)}</td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-2">
                        <Link
                          href={`/videos/${video.id}`}
                          className="rounded-full border border-border px-4 py-2 text-sm font-medium hover:bg-surface-hover"
                        >
                          Modifier
                        </Link>
                        <Button
                          variant="danger"
                          disabled={deletingId === video.id}
                          onClick={() => onDelete(video)}
                        >
                          {deletingId === video.id ? "Suppression…" : "Supprimer"}
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} pageCount={pageCount(state.data)} onChange={onPageChange} />
        </>
      )}
    </>
  );
}
