"use client";

import { useState } from "react";
import type { ChannelSummary } from "@/models/catalog";
import {
  EMPTY_PRODUCT_DRAFT,
  MAX_PRODUCT_IMAGE_BYTES,
  centsToEuros,
  eurosToCents,
  productDraftError,
  type Product,
  type ProductDraft,
} from "@/models/products";
import { listChannels } from "@/services/catalogService";
import { createProduct, deleteProduct, listProducts, setProductImage, updateProduct } from "@/services/productsService";
import { errorMessage, useRemoteData } from "./useRemoteData";

function imageError(image: File | null): string | null {
  return image && image.size > MAX_PRODUCT_IMAGE_BYTES ? "L'image dépasse 5 Mo." : null;
}

/** Page Produits : liste, ajout avec image, modification d'une ligne, suppression (ADR-029, D11). */
export function useProductsController() {
  const { state, reload } = useRemoteData("products", listProducts);
  const { state: channels } = useRemoteData<ChannelSummary[]>("product-channels", listChannels);
  const [draft, setDraft] = useState<ProductDraft>(EMPTY_PRODUCT_DRAFT);
  const [image, setImage] = useState<File | null>(null);
  // Remis à zéro après un ajout, pour vider le sélecteur de fichier.
  const [fileInputKey, setFileInputKey] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // Modifications en cours, par produit. Sans saisie, les champs affichent les valeurs enregistrées.
  const [rowDrafts, setRowDrafts] = useState<Record<string, ProductDraft>>({});
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({});
  const [savingId, setSavingId] = useState<string | null>(null);

  function rowDraft(product: Product): ProductDraft {
    return (
      rowDrafts[product.id] ?? {
        channelId: product.channel.id,
        name: product.name,
        price: centsToEuros(product.price),
        description: product.description,
      }
    );
  }

  function changeRow(product: Product, changes: Partial<ProductDraft>) {
    setRowDrafts((current) => ({ ...current, [product.id]: { ...rowDraft(product), ...changes } }));
    setRowErrors((current) => without(current, product.id));
  }

  function isRowChanged(product: Product): boolean {
    const current = rowDraft(product);
    return (
      current.name.trim() !== product.name ||
      eurosToCents(current.price) !== product.price ||
      current.description.trim() !== product.description
    );
  }

  async function saveRow(product: Product) {
    const current = rowDraft(product);
    const error = productDraftError(current, { needsChannel: false });
    if (error) return setRowErrors((errors) => ({ ...errors, [product.id]: error }));
    setSavingId(product.id);
    try {
      await updateProduct(product.id, {
        name: current.name.trim(),
        priceCents: eurosToCents(current.price) as number,
        description: current.description.trim(),
      });
      setRowDrafts((drafts) => without(drafts, product.id));
      reload();
    } catch (failure) {
      setRowErrors((errors) => ({ ...errors, [product.id]: errorMessage(failure) }));
    } finally {
      setSavingId(null);
    }
  }

  // Image d'un produit déjà ajouté : envoyée dès qu'elle est choisie.
  async function changeRowImage(product: Product, value: File | null) {
    if (!value) return;
    const error = imageError(value);
    if (error) return setRowErrors((errors) => ({ ...errors, [product.id]: error }));
    setSavingId(product.id);
    try {
      await setProductImage(product.id, value);
      reload();
    } catch (failure) {
      setRowErrors((errors) => ({ ...errors, [product.id]: errorMessage(failure) }));
    } finally {
      setSavingId(null);
    }
  }

  function changeDraft(changes: Partial<ProductDraft>) {
    setDraft((current) => ({ ...current, ...changes }));
    setFormError(null);
  }

  function changeImage(value: File | null) {
    setImage(value);
    setFormError(null);
  }

  async function submit() {
    const error = productDraftError(draft, { needsChannel: true }) ?? imageError(image);
    if (error) return setFormError(error);
    if (!image) return setFormError("Choisissez une image.");
    setCreating(true);
    try {
      await createProduct(
        {
          channelId: draft.channelId,
          name: draft.name.trim(),
          priceCents: eurosToCents(draft.price) as number,
          description: draft.description.trim(),
        },
        image,
      );
      setDraft(EMPTY_PRODUCT_DRAFT);
      setImage(null);
      setFileInputKey((key) => key + 1);
      reload();
    } catch (failure) {
      // Image refusée par ffprobe (422), trop grosse (413)… : affiché sous le formulaire.
      setFormError(errorMessage(failure));
    } finally {
      setCreating(false);
    }
  }

  async function remove(product: Product) {
    const message = `Supprimer le produit « ${product.name} » ? S'il est épinglé dans un live, la carte disparaît chez les spectateurs. Les achats déjà faits sont conservés.`;
    if (!window.confirm(message)) return;
    setDeletingId(product.id);
    setActionError(null);
    try {
      await deleteProduct(product.id);
      reload();
    } catch (failure) {
      setActionError(errorMessage(failure));
    } finally {
      setDeletingId(null);
    }
  }

  return {
    state,
    channels,
    reload,
    draft,
    changeDraft,
    changeImage,
    fileInputKey,
    formError,
    creating,
    submit,
    deletingId,
    actionError,
    remove,
    rowDraft,
    changeRow,
    isRowChanged,
    rowErrors,
    savingId,
    saveRow,
    changeRowImage,
  };
}

function without<T>(record: Record<string, T>, key: string): Record<string, T> {
  const copy = { ...record };
  delete copy[key];
  return copy;
}
