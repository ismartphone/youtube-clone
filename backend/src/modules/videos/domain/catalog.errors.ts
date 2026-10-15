// Erreurs métier de l'administration du catalogue, traduites en codes HTTP par la couche interfaces.

export class UnknownChannelError extends Error {
  constructor() {
    super("Cette chaîne n'existe pas");
  }
}

export class UnknownCategoryError extends Error {
  constructor() {
    super("Cette catégorie n'existe pas");
  }
}

export class CategoryAlreadyExistsError extends Error {
  constructor() {
    super('Cette catégorie existe déjà');
  }
}
