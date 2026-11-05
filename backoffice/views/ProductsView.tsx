import Image from "next/image";
import type { RemoteData } from "@/controllers/useRemoteData";
import type { ChannelSummary } from "@/models/catalog";
import { mediaUrl } from "@/models/media";
import { formatMoney } from "@/models/payments";
import type { Product, ProductDraft } from "@/models/products";
import {
  Button,
  DataTable,
  EmptyState,
  ErrorBanner,
  Field,
  PageHeader,
  Panel,
  Select,
  TableSkeleton,
  Td,
  TextArea,
  TextInput,
  Th,
  Tr,
} from "./ui";

const COLUMNS = ["Produit", "Chaîne", "Prix", "Actions"];

const FILE_INPUT_STYLES =
  "w-full text-sm text-muted file:mr-3 file:rounded-full file:border-0 file:bg-surface-hover file:px-4 file:py-2 file:text-sm file:font-medium file:text-foreground";

const IMAGE_TYPES = "image/jpeg,image/png";

/** Champs texte d'un produit, communs à l'ajout et à la modification d'une ligne. */
function DraftFields({
  idPrefix,
  draft,
  onChange,
  compact,
  error,
}: {
  idPrefix: string;
  draft: ProductDraft;
  onChange: (changes: Partial<ProductDraft>) => void;
  // Ligne de la liste : libellés réservés aux lecteurs d'écran, exemples dans les champs.
  compact: boolean;
  error?: string | null;
}) {
  return (
    <>
      <Field id={`${idPrefix}-name`} label="Nom" hideLabel={compact}>
        <TextInput
          id={`${idPrefix}-name`}
          value={draft.name}
          placeholder={compact ? "Nom" : undefined}
          onChange={(name) => onChange({ name })}
        />
      </Field>
      <Field
        id={`${idPrefix}-price`}
        label="Prix en euros"
        hideLabel={compact}
        hint={compact ? undefined : "Par exemple 49,90. Minimum 0,50 € (minimum de Stripe)."}
      >
        <TextInput
          id={`${idPrefix}-price`}
          value={draft.price}
          inputMode="numeric"
          placeholder={compact ? "Prix en euros" : undefined}
          onChange={(price) => onChange({ price })}
        />
      </Field>
      <Field
        id={`${idPrefix}-description`}
        label="Description"
        hideLabel={compact}
        // L'erreur de la ligne s'affiche sous le dernier champ.
        error={error}
      >
        <TextArea id={`${idPrefix}-description`} value={draft.description} onChange={(description) => onChange({ description })} />
      </Field>
    </>
  );
}

export function ProductsView({
  state,
  channels,
  onRetry,
  draft,
  onDraftChange,
  fileInputKey,
  onImageChange,
  formError,
  creating,
  onCreate,
  deletingId,
  actionError,
  onDelete,
  rowDraft,
  onRowChange,
  isRowChanged,
  rowErrors,
  savingId,
  onSaveRow,
  onRowImageChange,
}: {
  state: RemoteData<Product[]>;
  channels: RemoteData<ChannelSummary[]>;
  onRetry: () => void;
  draft: ProductDraft;
  onDraftChange: (changes: Partial<ProductDraft>) => void;
  fileInputKey: number;
  onImageChange: (file: File | null) => void;
  formError: string | null;
  creating: boolean;
  onCreate: () => void;
  deletingId: string | null;
  actionError: string | null;
  onDelete: (product: Product) => void;
  rowDraft: (product: Product) => ProductDraft;
  onRowChange: (product: Product, changes: Partial<ProductDraft>) => void;
  isRowChanged: (product: Product) => boolean;
  rowErrors: Record<string, string>;
  savingId: string | null;
  onSaveRow: (product: Product) => void;
  onRowImageChange: (product: Product, file: File | null) => void;
}) {
  const channelOptions = [
    { value: "", label: "Choisir une chaîne…" },
    ...(channels.status === "success" ? channels.data.map((c) => ({ value: c.id, label: c.name })) : []),
  ];
  return (
    <>
      <PageHeader
        title="Produits"
        description="Les produits d'une chaîne, que son diffuseur épingle pendant un live. Un seul produit est épinglé à la fois."
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
              title="Aucun produit pour le moment"
              description="Ajoutez un premier produit avec le formulaire : le diffuseur de sa chaîne pourra l'épingler."
            />
          )}
          {state.status === "success" && state.data.length > 0 && (
            <DataTable minWidth={720}>
              <thead>
                <tr>
                  <Th>Produit</Th>
                  <Th>Chaîne</Th>
                  <Th align="right">Prix</Th>
                  <Th align="right">Actions</Th>
                </tr>
              </thead>
              <tbody>
                {state.data.map((product) => {
                  const saving = savingId === product.id;
                  return (
                    <Tr key={product.id}>
                      <Td>
                        <div className="flex items-start gap-3">
                          <div className="flex w-24 shrink-0 flex-col gap-2">
                            <Image
                              src={mediaUrl(product.imageUrl)}
                              alt={`Image de ${product.name}`}
                              width={96}
                              height={96}
                              unoptimized
                              className="aspect-square w-24 rounded-md bg-surface-hover object-cover"
                            />
                            <label className="cursor-pointer text-xs font-medium underline-offset-2 hover:underline">
                              Changer l&apos;image
                              <input
                                type="file"
                                accept={IMAGE_TYPES}
                                className="sr-only"
                                disabled={saving}
                                onChange={(event) => {
                                  onRowImageChange(product, event.target.files?.[0] ?? null);
                                  event.target.value = "";
                                }}
                              />
                            </label>
                          </div>
                          <form
                            noValidate
                            onSubmit={(event) => {
                              event.preventDefault();
                              onSaveRow(product);
                            }}
                            className="flex min-w-0 flex-1 flex-col gap-2"
                          >
                            <DraftFields
                              idPrefix={`product-${product.id}`}
                              draft={rowDraft(product)}
                              onChange={(changes) => onRowChange(product, changes)}
                              compact
                              error={rowErrors[product.id]}
                            />
                            <div>
                              <Button
                                type="submit"
                                variant="secondary"
                                size="sm"
                                disabled={saving || !isRowChanged(product)}
                              >
                                {saving ? "Enregistrement…" : "Enregistrer"}
                              </Button>
                            </div>
                          </form>
                        </div>
                      </Td>
                      <Td>{product.channel.name}</Td>
                      <Td align="right">{formatMoney(product.price, product.currency)}</Td>
                      <Td align="right">
                        <div className="flex justify-end">
                          <Button
                            variant="danger"
                            size="sm"
                            disabled={deletingId === product.id}
                            onClick={() => onDelete(product)}
                          >
                            {deletingId === product.id ? "Suppression…" : "Supprimer"}
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
          <Panel title="Ajouter un produit">
            <form
              noValidate
              onSubmit={(event) => {
                event.preventDefault();
                onCreate();
              }}
              className="flex flex-col gap-4"
            >
              <Field id="product-new-channel" label="Chaîne" hint="Le diffuseur de cette chaîne pourra l'épingler.">
                <Select
                  id="product-new-channel"
                  value={draft.channelId}
                  onChange={(channelId) => onDraftChange({ channelId })}
                  options={channelOptions}
                />
              </Field>
              <DraftFields idPrefix="product-new" draft={draft} onChange={onDraftChange} compact={false} />
              <Field id="product-new-image" label="Image" hint="JPEG ou PNG, 5 Mo au plus, recadrée en carré.">
                <input
                  key={`image-${fileInputKey}`}
                  id="product-new-image"
                  type="file"
                  accept={IMAGE_TYPES}
                  onChange={(event) => onImageChange(event.target.files?.[0] ?? null)}
                  className={FILE_INPUT_STYLES}
                />
              </Field>
              {formError && (
                <p role="alert" className="text-sm text-danger">
                  {formError}
                </p>
              )}
              <div>
                <Button type="submit" disabled={creating}>
                  {creating ? "Ajout en cours…" : "Ajouter"}
                </Button>
              </div>
            </form>
          </Panel>
        </div>
      </div>
    </>
  );
}
