"use client";

import { useState } from "react";
import type { CategoryWithCount } from "@/models/catalog";
import { createCategory, deleteCategory, listCategories } from "@/services/catalogService";
import { errorMessage, useRemoteData } from "./useRemoteData";

const MAX_NAME_LENGTH = 50;

export function useCategoriesController() {
  const { state, reload } = useRemoteData("categories", listCategories);
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  function changeName(value: string) {
    setName(value);
    setNameError(null);
  }

  async function create() {
    const trimmed = name.trim();
    if (!trimmed) return setNameError("Saisissez un nom.");
    if (trimmed.length > MAX_NAME_LENGTH) return setNameError("Le nom ne doit pas dépasser 50 caractères.");

    setCreating(true);
    setActionError(null);
    try {
      await createCategory(trimmed);
      setName("");
      reload();
    } catch (error) {
      // Nom déjà pris (409) : affiché sous le champ.
      setNameError(errorMessage(error));
    } finally {
      setCreating(false);
    }
  }

  async function remove(category: CategoryWithCount) {
    const detail =
      category.videoCount > 0
        ? ` Ses ${category.videoCount} vidéo(s) seront conservées, sans catégorie.`
        : "";
    if (!window.confirm(`Supprimer la catégorie « ${category.name} » ?${detail}`)) return;

    setDeletingId(category.id);
    setActionError(null);
    try {
      await deleteCategory(category.id);
      reload();
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setDeletingId(null);
    }
  }

  return { state, reload, name, changeName, nameError, creating, create, deletingId, actionError, remove };
}
