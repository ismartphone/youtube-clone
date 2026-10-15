import type { RemoteData } from "@/controllers/useRemoteData";
import type { CategoryWithCount } from "@/models/catalog";
import { Button, ErrorBanner, Field, Loading, PageHeader, TextInput } from "./ui";

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
      <PageHeader title="Catégories" />
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          onCreate();
        }}
        className="mb-8 flex max-w-xl flex-wrap items-start gap-3"
      >
        <div className="min-w-60 flex-1">
          <Field id="category-name" label="Nouvelle catégorie" error={nameError}>
            <TextInput id="category-name" value={name} onChange={onNameChange} error={nameError} />
          </Field>
        </div>
        <div className="pt-7">
          <Button type="submit" disabled={creating}>
            {creating ? "Création…" : "Créer"}
          </Button>
        </div>
      </form>
      {actionError && (
        <div className="mb-4">
          <ErrorBanner message={actionError} />
        </div>
      )}
      {state.status === "loading" && <Loading />}
      {state.status === "error" && <ErrorBanner message={state.message} onRetry={onRetry} />}
      {state.status === "success" && state.data.length === 0 && (
        <p className="py-12 text-center text-sm text-muted">Aucune catégorie pour le moment.</p>
      )}
      {state.status === "success" && state.data.length > 0 && (
        <ul className="max-w-xl divide-y divide-border rounded-2xl border border-border">
          {state.data.map((category) => (
            <li key={category.id} className="flex items-center justify-between gap-4 px-4 py-3">
              <div>
                <p className="font-medium">{category.name}</p>
                <p className="text-sm text-muted">{category.videoCount} vidéo(s)</p>
              </div>
              <Button
                variant="danger"
                disabled={deletingId === category.id}
                onClick={() => onDelete(category)}
              >
                {deletingId === category.id ? "Suppression…" : "Supprimer"}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
