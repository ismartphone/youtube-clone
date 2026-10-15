import type {
  CategoryRepository,
  CategoryWithCount,
} from '../domain/catalog.js';
import type { CategorySummary } from '../domain/video.js';

export class ListCategoriesUseCase {
  constructor(private readonly categories: CategoryRepository) {}

  execute(): Promise<CategoryWithCount[]> {
    return this.categories.findAll();
  }
}

export class CreateCategoryUseCase {
  constructor(private readonly categories: CategoryRepository) {}

  execute(name: string): Promise<CategorySummary> {
    return this.categories.create(name.trim());
  }
}

export class DeleteCategoryUseCase {
  constructor(private readonly categories: CategoryRepository) {}

  // Renvoie false si la catégorie n'existe pas. Ses vidéos restent, sans catégorie.
  execute(id: string): Promise<boolean> {
    return this.categories.delete(id);
  }
}
