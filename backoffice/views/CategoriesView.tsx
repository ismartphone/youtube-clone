import type { RemoteData } from "@/controllers/useRemoteData";
import type { CategoryWithCount } from "@/models/catalog";
import { formatNumber } from "@/models/format";
import {
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

const COLUMNS = ["Catégorie", "Vidéos", "Actions"];

export function CategoriesView({
  state,
  onRetry,
  name,
  onNameChange,
  nameError,
  creating,
  onCreate,
  deletingId,
  actionError,
  onDelete,
}: {
  state: RemoteData<CategoryWithCount[]>;
  onRetry: () => void;
  name: string;
  onNameChange: (value: string) => void;
  nameError: string | null;
  creating: boolean;
  onCreate: () => void;
  deletingId: string | null;
  actionError: string | null;
  onDelete: (category: CategoryWithCount) => void;
}) {
  return (
    <>
      <PageHeader title="Catégories" description="Catégories proposées à la publication d'une vidéo." />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="order-2 lg:order-1">
          {actionError && (
            <div className="mb-4">
              <ErrorBanner message={actionError} />
            </div>
          )}
          {state.status === "loading" && <TableSkeleton columns={COLUMNS} rows={4} />}
          {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
          {state.status === "success" && state.data.length === 0 && (
            <EmptyState
              title="Aucune catégorie pour le moment"
              description="Créez une première catégorie avec le formulaire."
            />
          )}
          {state.status === "success" && state.data.length > 0 && (
            <DataTable minWidth={420}>
              <thead>
                <tr>
                  <Th>Catégorie</Th>
                  <Th align="right">Vidéos</Th>
                  <Th align="right">Actions</Th>
                </tr>
              </thead>
              <tbody>
                {state.data.map((category) => (
                  <Tr key={category.id}>
                    <Td className="font-medium">{category.name}</Td>
                    <Td align="right">{formatNumber(category.videoCount)}</Td>
                    <Td align="right">
                      <div className="flex justify-end">
                        <Button
                          variant="danger"
                          size="sm"
                          disabled={deletingId === category.id}
                          onClick={() => onDelete(category)}
                        >
                          {deletingId === category.id ? "Suppression…" : "Supprimer"}
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
          <Panel title="Nouvelle catégorie">
            <form
              noValidate
              onSubmit={(event) => {
                event.preventDefault();
                onCreate();
              }}
              className="flex flex-col gap-4"
            >
              <Field id="category-name" label="Nom" error={nameError}>
                <TextInput id="category-name" value={name} onChange={onNameChange} error={nameError} />
              </Field>
              <div>
                <Button type="submit" disabled={creating}>
                  {creating ? "Création…" : "Créer la catégorie"}
                </Button>
              </div>
            </form>
          </Panel>
        </div>
      </div>
    </>
  );
}
