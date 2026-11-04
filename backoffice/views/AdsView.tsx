import Image from "next/image";
import type { RemoteData } from "@/controllers/useRemoteData";
import type { Ad } from "@/models/ads";
import { formatDuration } from "@/models/format";
import { mediaUrl } from "@/models/media";
import {
  Badge,
  Button,
  DataTable,
  EmptyState,
  ErrorBanner,
  Field,
  PageHeader,
  Panel,
  TableSkeleton,
  Td,
  TextInput,
  Th,
  Tr,
} from "./ui";

const COLUMNS = ["Publicité", "Durée", "Lecture", "Actions"];

const FILE_INPUT_STYLES =
  "w-full text-sm text-muted file:mr-3 file:rounded-full file:border-0 file:bg-surface-hover file:px-4 file:py-2 file:text-sm file:font-medium file:text-foreground";

export function AdsView({
  state,
  onRetry,
  title,
  onTitleChange,
  fileInputKey,
  onFileChange,
  formError,
  importing,
  onImport,
  deletingId,
  actionError,
  onDelete,
}: {
  state: RemoteData<Ad[]>;
  onRetry: () => void;
  title: string;
  onTitleChange: (value: string) => void;
  fileInputKey: number;
  onFileChange: (file: File | null) => void;
  formError: string | null;
  importing: boolean;
  onImport: () => void;
  deletingId: string | null;
  actionError: string | null;
  onDelete: (ad: Ad) => void;
}) {
  return (
    <>
      <PageHeader
        title="Publicités"
        description="Lues avant les vidéos et dans le bandeau du fil pour les comptes non Premium. Une publicité est tirée au sort à chaque lecture."
      />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="order-2 lg:order-1">
          {actionError && (
            <div className="mb-4">
              <ErrorBanner message={actionError} />
            </div>
          )}
          {state.status === "loading" && <TableSkeleton columns={COLUMNS} rows={3} />}
          {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
          {state.status === "success" && state.data.length === 0 && (
            <EmptyState
              title="Aucune publicité pour le moment"
              description="Sans publicité, aucune annonce ne s'affiche. Importez un premier fichier avec le formulaire."
            />
          )}
          {state.status === "success" && state.data.length > 0 && (
            <DataTable minWidth={560}>
              <thead>
                <tr>
                  <Th>Publicité</Th>
                  <Th align="right">Durée</Th>
                  <Th>Lecture</Th>
                  <Th align="right">Actions</Th>
                </tr>
              </thead>
              <tbody>
                {state.data.map((ad) => (
                  <Tr key={ad.id}>
                    <Td>
                      <div className="flex items-center gap-3">
                        {ad.thumbnailUrl ? (
                          // Fichier du stockage local : pas d'optimisation Next.js.
                          <Image
                            src={mediaUrl(ad.thumbnailUrl)}
                            alt=""
                            width={96}
                            height={54}
                            unoptimized
                            className="aspect-video w-24 shrink-0 rounded-md bg-surface-hover object-cover"
                          />
                        ) : (
                          <span className="aspect-video w-24 shrink-0 rounded-md bg-surface-hover" />
                        )}
                        <span className="font-medium">{ad.title}</span>
                      </div>
                    </Td>
                    <Td align="right">{formatDuration(ad.durationSeconds)}</Td>
                    <Td>
                      {/* HLS généré en tâche de fond après l'import ; MP4 en attendant. */}
                      {ad.hlsUrl ? <Badge tone="accent">HLS</Badge> : <Badge>MP4, HLS en cours</Badge>}
                    </Td>
                    <Td align="right">
                      <div className="flex justify-end">
                        <Button
                          variant="danger"
                          size="sm"
                          disabled={deletingId === ad.id}
                          onClick={() => onDelete(ad)}
                        >
                          {deletingId === ad.id ? "Suppression…" : "Supprimer"}
                        </Button>
                      </div>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </DataTable>
          )}
        </div>
        <div className="order-1 lg:order-2">
          <Panel title="Importer une publicité">
            <form
              noValidate
              onSubmit={(event) => {
                event.preventDefault();
                onImport();
              }}
              className="flex flex-col gap-4"
            >
              <Field id="ad-title" label="Titre">
                <TextInput id="ad-title" value={title} onChange={onTitleChange} />
              </Field>
              <Field id="ad-file" label="Fichier vidéo" hint="MP4, 200 Mo au plus. Vérifié par le serveur.">
                <input
                  key={fileInputKey}
                  id="ad-file"
                  type="file"
                  accept="video/mp4,video/quicktime"
                  onChange={(event) => onFileChange(event.target.files?.[0] ?? null)}
                  className={FILE_INPUT_STYLES}
                />
              </Field>
              {formError && (
                <p role="alert" className="text-sm text-danger">
                  {formError}
                </p>
              )}
              <div>
                <Button type="submit" disabled={importing}>
                  {importing ? "Import en cours…" : "Importer"}
                </Button>
              </div>
            </form>
          </Panel>
        </div>
      </div>
    </>
  );
}
