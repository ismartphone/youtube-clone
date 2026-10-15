import { Controller, Get } from '@nestjs/common';
import { ListCategoriesUseCase } from '../../application/manage-categories.use-case.js';

// Liste publique des catégories (choix des centres d'intérêt dans l'application).
// Le nombre de vidéos par catégorie reste réservé au back-office (GET /admin/categories).
@Controller('categories')
export class CategoriesController {
  constructor(private readonly listCategories: ListCategoriesUseCase) {}

  @Get()
  async list() {
    const categories = await this.listCategories.execute();
    return categories.map((category) => ({
      id: category.id,
      name: category.name,
    }));
  }
}
