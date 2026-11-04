import Image from "next/image";
import type { AdDraft } from "@/controllers/useAdsController";
import type { RemoteData } from "@/controllers/useRemoteData";
import { DEFAULT_AD_CTA, type Ad } from "@/models/ads";
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

const IMAGE_TYPES = "image/jpeg,image/png";

/** Champs texte de la carte, communs à l'import et à la modification d'une ligne. */
function DraftFields({
  idPrefix,
  draft,
  onChange,
  compact,
  error,
}: {
  idPrefix: string;
  draft: AdDraft;
  onChange: (changes: Partial<AdDraft>) => void;
  // Ligne de la liste : libellés réservés aux lecteurs d'écran, exemples dans les champs.
  compact: boolean;
  error?: string | null;
}) {
  const fields: { key: keyof AdDraft; label: string; hint?: string; type?: "url" }[] = [
    { key: "title", label: "Titre", hint: "Affiché sur le bandeau sponsorisé de l'application." },
    { key: "advertiserName", label: "Annonceur", hint: "Nom affiché sur la carte et pendant l'annonce." },
    { key: "description", label: "Description courte", hint: "Deux lignes au plus sur la carte." },
    {
      key: "destinationUrl",
      label: "URL de destination",
      type: "url",
      hint: "Ouverte par le bouton principal et par la bande pendant l'annonce.",
    },
    { key: "ctaLabel", label: "Libellé du bouton principal", hint: `Vide : « ${DEFAULT_AD_CTA} ».` },
  ];
  return (
    <>
      {fields.map(({ key, label, hint, type }, index) => (
        <Field
          key={key}
          id={`${idPrefix}-${key}`}
          label={label}
          hideLabel={compact}
          hint={compact ? undefined : hint}
          // L'erreur de la ligne s'affiche sous le dernier champ.
          error={index === fields.length - 1 ? error : undefined}
        >
          <TextInput
            id={`${idPrefix}-${key}`}
            type={type}
            value={draft[key]}
            placeholder={compact ? (key === "ctaLabel" ? DEFAULT_AD_CTA : label) : undefined}
            onChange={(value) => onChange({ [key]: value })}
          />
        </Field>
      ))}
    </>
  );
}

function Logo({ ad }: { ad: Ad }) {
  if (ad.logoUrl) {
    return (
      // Fichier du stockage local : pas d'optimisation Next.js.
      <Image
        src={mediaUrl(ad.logoUrl)}
        alt={`Logo de ${ad.advertiserName ?? ad.title}`}
        width={40}
        height={40}
        unoptimized
        className="size-10 shrink-0 rounded-md bg-surface-hover object-cover"
      />
    );
  }
  // Même repli que l'application : l'initiale de l'annonceur.
  return (
    <span
      aria-hidden
      className="flex size-10 shrink-0 items-center justify-center rounded-md bg-surface-hover text-sm font-semibold"
    >
      {(ad.advertiserName ?? ad.title).trim().charAt(0).toUpperCase()}
    </span>
  );
}

export function AdsView({
  state,
  onRetry,
  draft,
  onDraftChange,
  fileInputKey,
  onFileChange,
  onLogoChange,
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
  onRowLogoChange,
  onRemoveRowLogo,
}: {
  state: RemoteData<Ad[]>;
  onRetry: () => void;
  draft: AdDraft;
  onDraftChange: (changes: Partial<AdDraft>) => void;
  fileInputKey: number;
  onFileChange: (file: File | null) => void;
  onLogoChange: (file: File | null) => void;
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
  onRowLogoChange: (ad: Ad, file: File | null) => void;
  onRemoveRowLogo: (ad: Ad) => void;
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
                  const saving = savingId === ad.id;
                  return (
                    <Tr key={ad.id}>
                      <Td>
                        <div className="flex items-start gap-3">
                          <div className="flex w-24 shrink-0 flex-col gap-2">
                            {ad.thumbnailUrl ? (
                              <Image
                                src={mediaUrl(ad.thumbnailUrl)}
                                alt=""
                                width={96}
                                height={54}
                                unoptimized
                                className="aspect-video w-24 rounded-md bg-surface-hover object-cover"
                              />
                            ) : (
                              <span className="aspect-video w-24 rounded-md bg-surface-hover" />
                            )}
                            <Logo ad={ad} />
                            <label className="cursor-pointer text-xs font-medium underline-offset-2 hover:underline">
                              {ad.logoUrl ? "Changer le logo" : "Ajouter un logo"}
                              <input
                                type="file"
                                accept={IMAGE_TYPES}
                                className="sr-only"
                                disabled={saving}
                                onChange={(event) => {
                                  onRowLogoChange(ad, event.target.files?.[0] ?? null);
                                  event.target.value = "";
                                }}
                              />
                            </label>
                            {ad.logoUrl && (
                              <button
                                type="button"
                                disabled={saving}
                                onClick={() => onRemoveRowLogo(ad)}
                                className="text-left text-xs text-muted underline-offset-2 hover:underline"
                              >
                                Retirer le logo
                              </button>
                            )}
                          </div>
                          <form
                            noValidate
                            onSubmit={(event) => {
                              event.preventDefault();
                              onSaveRow(ad);
                            }}
                            className="flex min-w-0 flex-1 flex-col gap-2"
                          >
                            <DraftFields
                              idPrefix={`ad-${ad.id}`}
                              draft={rowDraft(ad)}
                              onChange={(changes) => onRowChange(ad, changes)}
                              compact
                              error={rowErrors[ad.id]}
                            />
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
              <DraftFields idPrefix="ad-new" draft={draft} onChange={onDraftChange} compact={false} />
              <Field id="ad-logo" label="Logo (facultatif)" hint="JPEG ou PNG, 5 Mo au plus, recadré en carré.">
                <input
                  key={`logo-${fileInputKey}`}
                  id="ad-logo"
                  type="file"
                  accept={IMAGE_TYPES}
                  onChange={(event) => onLogoChange(event.target.files?.[0] ?? null)}
                  className={FILE_INPUT_STYLES}
                />
              </Field>
              <Field id="ad-file" label="Fichier vidéo" hint="MP4, 200 Mo au plus. Vérifié par le serveur.">
                <input
                  key={`file-${fileInputKey}`}
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
