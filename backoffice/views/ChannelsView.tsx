import Image from "next/image";
import type { RemoteData } from "@/controllers/useRemoteData";
import type { ChannelSummary } from "@/models/catalog";
import { mediaUrl } from "@/models/media";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorBanner,
  Field,
  PageHeader,
  TableSkeleton,
  Td,
  TextInput,
  Th,
  Tr,
} from "./ui";

const COLUMNS = ["Chaîne", "Bannière", "Modifier la bannière"];

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
      <PageHeader
        title="Chaînes"
        description="Bannière : URL http(s) ou chemin /media/banners/… ; laisser vide pour l'effacer."
      />
      {state.status === "loading" && <TableSkeleton columns={COLUMNS} rows={4} />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && state.data.length === 0 && (
        <EmptyState
          title="Aucune chaîne pour le moment"
          description="Les chaînes créées depuis l'application apparaîtront ici."
        />
      )}
      {state.status === "success" && state.data.length > 0 && (
        <DataTable minWidth={720}>
          <thead>
            <tr>
              <Th>Chaîne</Th>
              <Th>Bannière</Th>
              <Th>Modifier la bannière</Th>
            </tr>
          </thead>
          <tbody>
            {state.data.map((channel) => {
              const fieldId = `banner-${channel.id}`;
              const saving = savingId === channel.id;
              return (
                <Tr key={channel.id}>
                  <Td>
                    <div className="flex items-center gap-3">
                      {channel.avatarUrl ? (
                        // URL saisie à la main ou fichier du stockage local : pas d'optimisation Next.js.
                        <Image
                          src={mediaUrl(channel.avatarUrl)}
                          alt=""
                          width={36}
                          height={36}
                          unoptimized
                          className="size-9 shrink-0 rounded-full bg-surface-hover object-cover"
                        />
                      ) : (
                        <span
                          aria-hidden
                          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-surface-hover text-sm font-semibold"
                        >
                          {channel.name.trim().charAt(0).toUpperCase()}
                        </span>
                      )}
                      <div className="min-w-0">
                        <p className="font-medium">{channel.name}</p>
                        <p className="text-xs text-muted">@{channel.handle}</p>
                      </div>
                    </div>
                  </Td>
                  <Td>
                    {channel.bannerUrl ? (
                      <Image
                        src={mediaUrl(channel.bannerUrl)}
                        alt={`Bannière de ${channel.name}`}
                        width={180}
                        height={50}
                        unoptimized
                        className="aspect-[18/5] w-44 rounded-md bg-surface-hover object-cover"
                      />
                    ) : (
                      // Même aplat que dans l'application quand la chaîne n'a pas de bannière.
                      <div className="flex aspect-[18/5] w-44 items-center justify-center rounded-md bg-surface-hover text-xs text-muted">
                        Pas de bannière
                      </div>
                    )}
                  </Td>
                  <Td>
                    <form
                      noValidate
                      onSubmit={(event) => {
                        event.preventDefault();
                        onSave(channel);
                      }}
                      className="flex items-start gap-2"
                    >
                      <div className="min-w-40 flex-1">
                        <Field
                          id={fieldId}
                          label={`Bannière de ${channel.name}`}
                          hideLabel
                          error={errors[channel.id]}
                        >
                          <TextInput
                            id={fieldId}
                            value={bannerDraft(channel)}
                            onChange={(value) => onBannerChange(channel.id, value)}
                            error={errors[channel.id]}
                          />
                        </Field>
                      </div>
                      <Button type="submit" variant="secondary" disabled={saving}>
                        {saving ? "Enregistrement…" : "Enregistrer"}
                      </Button>
                    </form>
                  </Td>
                </Tr>
              );
            })}
          </tbody>
        </DataTable>
      )}
    </>
  );
}
