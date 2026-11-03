import Image from "next/image";
import type { RemoteData } from "@/controllers/useRemoteData";
import { pageCount, type Page, type Video } from "@/models/catalog";
import { formatDuration, formatNumber } from "@/models/format";
import { mediaUrl } from "@/models/media";
import {
  Badge,
  Button,
  DataTable,
  EmptyState,
  ErrorBanner,
  LinkButton,
  PageHeader,
  Pagination,
  TableSkeleton,
  Td,
  Th,
  Tr,
} from "./ui";

const COLUMNS = ["Vidéo", "Chaîne", "Format", "Vues", "Actions"];

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
        description={
          state.status === "success"
            ? `${formatNumber(state.data.total)} vidéo(s), Shorts compris, de la plus récente à la plus ancienne.`
            : "Vidéos et Shorts du catalogue."
        }
        action={
          <LinkButton href="/videos/new" variant="primary">
            Ajouter une vidéo
          </LinkButton>
        }
      />
      {actionError && (
        <div className="mb-4">
          <ErrorBanner message={actionError} />
        </div>
      )}
      {state.status === "loading" && <TableSkeleton columns={COLUMNS} />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && state.data.items.length === 0 && (
        <EmptyState
          title="Aucune vidéo dans le catalogue"
          description="Les vidéos ajoutées ici ou publiées depuis l'application apparaîtront dans cette liste."
          action={
            <LinkButton href="/videos/new" variant="primary">
              Ajouter une vidéo
            </LinkButton>
          }
        />
      )}
      {state.status === "success" && state.data.items.length > 0 && (
        <>
          <DataTable>
            <thead>
              <tr>
                <Th>Vidéo</Th>
                <Th>Chaîne</Th>
                <Th>Format</Th>
                <Th align="right">Vues</Th>
                <Th align="right">Actions</Th>
              </tr>
            </thead>
            <tbody>
              {state.data.items.map((video) => (
                <Tr key={video.id}>
                  <Td>
                    <div className="flex items-center gap-3">
                      <div className="relative shrink-0">
                        {/* URL saisies à la main, de n'importe quel domaine : pas d'optimisation Next.js. */}
                        <Image
                          src={mediaUrl(video.thumbnailUrl)}
                          alt=""
                          width={96}
                          height={54}
                          unoptimized
                          className="h-[54px] w-24 rounded-md bg-surface-hover object-cover"
                        />
                        <span className="absolute right-1 bottom-1 rounded bg-black/80 px-1 text-[10px] font-medium tabular-nums">
                          {formatDuration(video.durationSeconds)}
                        </span>
                      </div>
                      <div className="min-w-0">
                        <p className="line-clamp-2 font-medium">{video.title}</p>
                        <p className="mt-0.5 text-xs text-muted">{video.category?.name ?? "Sans catégorie"}</p>
                      </div>
                    </div>
                  </Td>
                  <Td className="text-muted">{video.channel.name}</Td>
                  <Td>
                    <div className="flex flex-wrap gap-1.5">
                      <Badge>{video.format === "SHORT" ? "Short" : "Vidéo"}</Badge>
                      {video.isPremium && <Badge tone="accent">Premium</Badge>}
                      {video.visibility !== "PUBLIC" && (
                        <Badge>{video.visibility === "PRIVATE" ? "Privée" : "Non répertoriée"}</Badge>
                      )}
                      {!video.commentsEnabled && <Badge>Commentaires désactivés</Badge>}
                    </div>
                  </Td>
                  <Td align="right">{formatNumber(video.viewCount)}</Td>
                  <Td align="right">
                    <div className="flex justify-end gap-2">
                      <LinkButton href={`/videos/${video.id}`} size="sm">
                        Modifier
                      </LinkButton>
                      <Button
                        variant="danger"
                        size="sm"
                        disabled={deletingId === video.id}
                        onClick={() => onDelete(video)}
                      >
                        {deletingId === video.id ? "Suppression…" : "Supprimer"}
                      </Button>
                    </div>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </DataTable>
          <Pagination page={page} pageCount={pageCount(state.data)} onChange={onPageChange} />
        </>
      )}
    </>
  );
}
