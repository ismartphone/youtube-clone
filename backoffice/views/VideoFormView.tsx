import Image from "next/image";
import type { RemoteData } from "@/controllers/useRemoteData";
import {
  VIDEO_FORMATS,
  VIDEO_VISIBILITIES,
  type CategoryWithCount,
  type ChannelSummary,
} from "@/models/catalog";
import { mediaUrl } from "@/models/media";
import type { VideoFormErrors, VideoFormValues } from "@/models/videoForm";
import {
  Button,
  ErrorBanner,
  Field,
  FormSection,
  LinkButton,
  PageHeader,
  Select,
  Skeleton,
  TextArea,
  TextInput,
} from "./ui";

interface FormData {
  channels: ChannelSummary[];
  categories: CategoryWithCount[];
}

export function VideoFormView({
  isEdit,
  state,
  onRetry,
  values,
  onChange,
  errors,
  submitError,
  submitting,
  onSubmit,
  regenerating,
  thumbnailMessage,
  onRegenerate,
}: {
  isEdit: boolean;
  state: RemoteData<FormData>;
  onRetry: () => void;
  values: VideoFormValues;
  onChange: <K extends keyof VideoFormValues>(field: K, value: VideoFormValues[K]) => void;
  errors: VideoFormErrors;
  submitError: string | null;
  submitting: boolean;
  onSubmit: () => void;
  regenerating: boolean;
  thumbnailMessage: { text: string; isError: boolean } | null;
  onRegenerate: () => void;
}) {
  return (
    <>
      <PageHeader
        title={isEdit ? "Modifier la vidéo" : "Ajouter une vidéo"}
        action={<LinkButton href="/videos">Retour à la liste</LinkButton>}
      />
      {state.status === "loading" && <FormSkeleton />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && (
        <form
          noValidate
          aria-busy={submitting}
          onSubmit={(event) => {
            event.preventDefault();
            onSubmit();
          }}
          className="flex max-w-3xl flex-col gap-5"
        >
          <FormSection title="Informations">
            <Field id="title" label="Titre" error={errors.title}>
              <TextInput id="title" value={values.title} onChange={(v) => onChange("title", v)} error={errors.title} />
            </Field>
            <Field id="description" label="Description (facultative)" error={errors.description}>
              <TextArea
                id="description"
                value={values.description}
                onChange={(v) => onChange("description", v)}
                error={errors.description}
              />
            </Field>
          </FormSection>

          <FormSection title="Classement">
            <div className="grid gap-5 sm:grid-cols-2">
              <Field id="channelId" label="Chaîne" error={errors.channelId}>
                <Select
                  id="channelId"
                  value={values.channelId}
                  onChange={(v) => onChange("channelId", v)}
                  error={errors.channelId}
                  options={[
                    { value: "", label: "Choisir une chaîne" },
                    ...state.data.channels.map((c) => ({ value: c.id, label: `${c.name} (@${c.handle})` })),
                  ]}
                />
              </Field>
              <Field id="categoryId" label="Catégorie">
                <Select
                  id="categoryId"
                  value={values.categoryId}
                  onChange={(v) => onChange("categoryId", v)}
                  options={[
                    { value: "", label: "Aucune catégorie" },
                    ...state.data.categories.map((c) => ({ value: c.id, label: c.name })),
                  ]}
                />
              </Field>
              <Field id="format" label="Format">
                <Select
                  id="format"
                  value={values.format}
                  onChange={(v) => onChange("format", v as VideoFormValues["format"])}
                  options={VIDEO_FORMATS}
                />
              </Field>
              <Field
                id="durationSeconds"
                label="Durée (secondes)"
                error={errors.durationSeconds}
                hint="Affichée sur la miniature dans l'application."
              >
                <TextInput
                  id="durationSeconds"
                  inputMode="numeric"
                  value={values.durationSeconds}
                  onChange={(v) => onChange("durationSeconds", v)}
                  error={errors.durationSeconds}
                />
              </Field>
            </div>
          </FormSection>

          <FormSection title="Fichiers">
            <Field
              id="videoUrl"
              label="URL de la vidéo (MP4)"
              error={errors.videoUrl}
              hint="URL complète, ou /media/videos/… pour une vidéo stockée sur le serveur."
            >
              <TextInput
                id="videoUrl"
                type="url"
                placeholder="https://…/video.mp4"
                value={values.videoUrl}
                onChange={(v) => onChange("videoUrl", v)}
                error={errors.videoUrl}
              />
            </Field>
            <Field
              id="thumbnailUrl"
              label="URL de la miniature (facultative)"
              error={errors.thumbnailUrl}
              hint="Laissez vide pour que le serveur extraie une image de la vidéo, à 10 % de sa durée."
            >
              <TextInput
                id="thumbnailUrl"
                type="url"
                placeholder="https://…/miniature.jpg"
                value={values.thumbnailUrl}
                onChange={(v) => onChange("thumbnailUrl", v)}
                error={errors.thumbnailUrl}
              />
            </Field>
            {isEdit && (
              <div className="flex flex-wrap items-center gap-4 rounded-lg border border-border bg-background p-3">
                {values.thumbnailUrl ? (
                  // URL saisie à la main ou générée par le serveur : pas d'optimisation Next.js.
                  <Image
                    src={mediaUrl(values.thumbnailUrl)}
                    alt="Miniature actuelle"
                    width={160}
                    height={90}
                    unoptimized
                    className="h-[90px] w-40 rounded-md bg-surface-hover object-cover"
                  />
                ) : (
                  <div className="flex h-[90px] w-40 items-center justify-center rounded-md bg-surface-hover text-xs text-muted">
                    Pas de miniature
                  </div>
                )}
                <div className="flex flex-col items-start gap-2">
                  <Button variant="secondary" disabled={regenerating} onClick={onRegenerate}>
                    {regenerating ? "Génération…" : "Régénérer la miniature"}
                  </Button>
                  {thumbnailMessage && (
                    <p
                      role={thumbnailMessage.isError ? "alert" : "status"}
                      className={`text-sm ${thumbnailMessage.isError ? "text-danger" : "text-muted"}`}
                    >
                      {thumbnailMessage.text}
                    </p>
                  )}
                </div>
              </div>
            )}
          </FormSection>

          <FormSection title="Accès">
            <label className="flex items-start gap-3 text-sm">
              <input
                type="checkbox"
                checked={values.isPremium}
                onChange={(event) => onChange("isPremium", event.target.checked)}
                className="mt-0.5 size-4 accent-accent"
              />
              <span>
                <span className="font-medium">Vidéo exclusive Premium</span>
                <span className="block text-muted">L&apos;API ne donne son URL qu&apos;aux membres Premium.</span>
              </span>
            </label>
          </FormSection>

          {/* Réglages de publication (ADR-016) : le back-office est le seul endroit où ils se
              modifient après la publication. */}
          <FormSection title="Réglages de publication">
            <Field
              id="visibility"
              label="Visibilité"
              hint="Non répertoriée : accessible par son lien seulement. Privée : visible de son propriétaire seul."
            >
              <Select
                id="visibility"
                value={values.visibility}
                onChange={(v) => onChange("visibility", v as VideoFormValues["visibility"])}
                options={VIDEO_VISIBILITIES}
              />
            </Field>
            <label className="flex items-start gap-3 text-sm">
              <input
                type="checkbox"
                checked={values.commentsEnabled}
                onChange={(event) => onChange("commentsEnabled", event.target.checked)}
                className="mt-0.5 size-4 accent-accent"
              />
              <span>
                <span className="font-medium">Commentaires activés</span>
                <span className="block text-muted">
                  Désactivés, ils sont masqués sans être supprimés et l&apos;API refuse les nouveaux.
                </span>
              </span>
            </label>
          </FormSection>

          {submitError && <ErrorBanner message={submitError} />}
          <div className="flex flex-wrap items-center gap-3 border-t border-border pt-5">
            <Button type="submit" disabled={submitting}>
              {submitting ? "Enregistrement…" : isEdit ? "Enregistrer" : "Ajouter la vidéo"}
            </Button>
            <LinkButton href="/videos">Annuler</LinkButton>
          </div>
        </form>
      )}
    </>
  );
}

function FormSkeleton() {
  return (
    <div role="status" aria-label="Chargement…" className="flex max-w-3xl flex-col gap-5">
      {[2, 4, 2].map((fields, section) => (
        <div key={section} className="rounded-xl border border-border bg-surface p-5">
          <Skeleton className="mb-5 h-4 w-28" />
          <div className="grid gap-5 sm:grid-cols-2">
            {Array.from({ length: fields }, (_, field) => (
              <div key={field} className="flex flex-col gap-2">
                <Skeleton className="h-3.5 w-24" />
                <Skeleton className="h-9 w-full" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
