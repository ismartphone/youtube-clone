import Image from "next/image";
import type { RemoteData } from "@/controllers/useRemoteData";
import type { ChannelSummary } from "@/models/catalog";
import { mediaUrl } from "@/models/media";
import { Button, ErrorBanner, Field, Loading, PageHeader, TextInput } from "./ui";

export function ChannelsView({
  state,
  onRetry,
  bannerDraft,
  onBannerChange,
  errors,
  savingId,
  onSave,
}: {
  state: RemoteData<ChannelSummary[]>;
  onRetry: () => void;
  bannerDraft: (channel: ChannelSummary) => string;
  onBannerChange: (channelId: string, value: string) => void;
  errors: Record<string, string>;
  savingId: string | null;
  onSave: (channel: ChannelSummary) => void;
}) {
  return (
    <>
      <PageHeader title="Chaînes" />
      {state.status === "loading" && <Loading />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && state.data.length === 0 && (
        <p className="py-12 text-center text-sm text-muted">Aucune chaîne pour le moment.</p>
      )}
      {state.status === "success" && state.data.length > 0 && (
        <ul className="flex max-w-3xl flex-col gap-6">
          {state.data.map((channel) => {
            const fieldId = `banner-${channel.id}`;
            const saving = savingId === channel.id;
            return (
              <li key={channel.id} className="rounded-2xl border border-border p-4">
                <div className="mb-4">
                  <p className="font-medium">{channel.name}</p>
                  <p className="text-sm text-muted">@{channel.handle}</p>
                </div>
                {channel.bannerUrl ? (
                  // URL saisie à la main ou fichier du stockage local : pas d'optimisation Next.js.
                  <Image
                    src={mediaUrl(channel.bannerUrl)}
                    alt={`Bannière de ${channel.name}`}
                    width={720}
                    height={200}
                    unoptimized
                    className="mb-4 aspect-[18/5] w-full rounded-xl bg-surface-hover object-cover"
                  />
                ) : (
                  // Même aplat que dans l'application quand la chaîne n'a pas de bannière.
                  <div className="mb-4 flex aspect-[18/5] w-full items-center justify-center rounded-xl bg-surface-hover text-sm text-muted">
                    Pas de bannière
                  </div>
                )}
                <form
                  noValidate
                  onSubmit={(event) => {
                    event.preventDefault();
                    onSave(channel);
                  }}
                  className="flex flex-wrap items-start gap-3"
                >
                  <div className="min-w-60 flex-1">
                    <Field
                      id={fieldId}
                      label="Bannière"
                      error={errors[channel.id]}
                      hint="URL http(s) ou chemin /media/banners/… ; laisser vide pour l'effacer."
                    >
                      <TextInput
                        id={fieldId}
                        value={bannerDraft(channel)}
                        onChange={(value) => onBannerChange(channel.id, value)}
                        error={errors[channel.id]}
                      />
                    </Field>
                  </div>
                  <div className="pt-7">
                    <Button type="submit" disabled={saving}>
                      {saving ? "Enregistrement…" : "Enregistrer"}
                    </Button>
                  </div>
                </form>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}
