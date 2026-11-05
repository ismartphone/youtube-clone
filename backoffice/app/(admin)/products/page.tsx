"use client";

import { useProductsController } from "@/controllers/useProductsController";
import { ProductsView } from "@/views/ProductsView";

export default function ProductsPage() {
  const products = useProductsController();
  return (
    <ProductsView
      state={products.state}
      channels={products.channels}
      onRetry={products.reload}
      draft={products.draft}
      onDraftChange={products.changeDraft}
      fileInputKey={products.fileInputKey}
      onImageChange={products.changeImage}
      formError={products.formError}
      creating={products.creating}
      onCreate={products.submit}
      deletingId={products.deletingId}
      actionError={products.actionError}
      onDelete={products.remove}
      rowDraft={products.rowDraft}
      onRowChange={products.changeRow}
      isRowChanged={products.isRowChanged}
      rowErrors={products.rowErrors}
      savingId={products.savingId}
      onSaveRow={products.saveRow}
      onRowImageChange={products.changeRowImage}
    />
  );
}
