import Image from "next/image";
import type { AdDraft } from "@/controllers/useAdsController";
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

const TITLE_HINT = "Affiché sur le bandeau sponsorisé de l'application.";
const URL_HINT = "Ouvert par « Visiter le site » pendant l'annonce et par le bandeau sponsorisé.";

export function AdsView({
  state,
  onRetry,
  draft,
  onDraftChange,
  fileInputKey,
  onFileChange,
  formError,
  importing,
  onImport,
  deletingId,
  actionError,
  onDelete,
  rowDraft,
  onRowChange,
  isRowChanged,
  rowErrors,
  savingId,
  onSaveRow,
}: {
  state: RemoteData<Ad[]>;
  onRetry: () => void;
  draft: AdDraft;
  onDraftChange: (changes: Partial<AdDraft>) => void;
  fileInputKey: number;
  onFileChange: (file: File | null) => void;
  formError: string | null;
  importing: boolean;
  onImport: () => void;
  deletingId: string | null;
  actionError: string | null;
  onDelete: (ad: Ad) => void;
  rowDraft: (ad: Ad) => AdDraft;
  onRowChange: (ad: Ad, changes: Partial<AdDraft>) => void;
  isRowChanged: (ad: Ad) => boolean;
  rowErrors: Record<string, string>;
  savingId: string | null;
  onSaveRow: (ad: Ad) => void;
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
            <DataTable minWidth={760}>
              <thead>
                <tr>
                  <Th>Publicité</Th>
                  <Th align="right">Durée</Th>
                  <Th>Lecture</Th>
                  <Th align="right">Actions</Th>
                </tr>
              </thead>
              <tbody>
                {state.data.map((ad) => {
                  const current = rowDraft(ad);
                  const saving = savingId === ad.id;
                  return (
                    <Tr key={ad.id}>
                      <Td>
                        <div className="flex items-start gap-3">
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
                          <form
                            noValidate
                            onSubmit={(event) => {
                              event.preventDefault();
                              onSaveRow(ad);
                            }}
                            className="flex min-w-0 flex-1 flex-col gap-2"
                          >
                            <Field id={`ad-title-${ad.id}`} label="Titre de la publicité" hideLabel>
                              <TextInput
                                id={`ad-title-${ad.id}`}
                                value={current.title}
                                placeholder="Titre"
                                onChange={(title) => onRowChange(ad, { title })}
                              />
                            </Field>
                            <Field
                              id={`ad-url-${ad.id}`}
                              label="URL de destination"
                              hideLabel
                              error={rowErrors[ad.id]}
                            >
                              <TextInput
                                id={`ad-url-${ad.id}`}
                                type="url"
                                value={current.destinationUrl}
                                placeholder="URL de destination, https://…"
                                onChange={(destinationUrl) => onRowChange(ad, { destinationUrl })}
                                error={rowErrors[ad.id]}
                              />
                            </Field>
                            <div>
                              <Button type="submit" variant="secondary" size="sm" disabled={saving || !isRowChanged(ad)}>
                                {saving ? "Enregistrement…" : "Enregistrer"}
                              </Button>
                            </div>
                          </form>
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
                  );
                })}
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
              <Field id="ad-title" label="Titre" hint={TITLE_HINT}>
                <TextInput id="ad-title" value={draft.title} onChange={(title) => onDraftChange({ title })} />
              </Field>
              <Field id="ad-url" label="URL de destination" hint={URL_HINT}>
                <TextInput
                  id="ad-url"
                  type="url"
                  value={draft.destinationUrl}
                  placeholder="https://…"
                  onChange={(destinationUrl) => onDraftChange({ destinationUrl })}
                />
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
