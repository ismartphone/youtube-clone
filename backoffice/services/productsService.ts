import type { Product } from "@/models/products";
import { apiRequest } from "./apiClient";

// Produits (ADR-029), routes réservées au rôle ADMIN.

export async function listProducts(): Promise<Product[]> {
  return (await apiRequest<{ items: Product[] }>("/admin/products")).items;
}

// Le prix part en centimes ; le serveur vérifie l'image (ffprobe), la recadre et la range.
export function createProduct(
  fields: { channelId: string; name: string; priceCents: number; description: string },
  image: File,
): Promise<Product> {
  const form = new FormData();
  form.append("channelId", fields.channelId);
  form.append("name", fields.name);
  form.append("price", String(fields.priceCents));
  form.append("description", fields.description);
  form.append("image", image);
  return apiRequest("/admin/products", { method: "POST", body: form });
}

export function updateProduct(
  id: string,
  fields: { name: string; priceCents: number; description: string },
): Promise<Product> {
  return apiRequest(`/admin/products/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: { name: fields.name, price: fields.priceCents, description: fields.description },
  });
}

// JPEG ou PNG, vérifié et recadré en carré par le serveur.
export function setProductImage(id: string, image: File): Promise<Product> {
  const form = new FormData();
  form.append("image", image);
  return apiRequest(`/admin/products/${encodeURIComponent(id)}/image`, { method: "PUT", body: form });
}

export function deleteProduct(id: string): Promise<void> {
  return apiRequest(`/admin/products/${encodeURIComponent(id)}`, { method: "DELETE" });
}
