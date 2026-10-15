"use client";

import { useCategoriesController } from "@/controllers/useCategoriesController";
import { CategoriesView } from "@/views/CategoriesView";

export default function CategoriesPage() {
  const categories = useCategoriesController();
  return (
    <CategoriesView
      state={categories.state}
      onRetry={categories.reload}
      name={categories.name}
      onNameChange={categories.changeName}
      nameError={categories.nameError}
      creating={categories.creating}
      onCreate={categories.create}
      deletingId={categories.deletingId}
      actionError={categories.actionError}
      onDelete={categories.remove}
    />
  );
}
