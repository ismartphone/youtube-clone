# Back-office

Administration du catalogue : tableau de bord, vidéos, catégories. Réservé au rôle ADMIN.

Next.js (App Router), TypeScript, Tailwind CSS. Architecture MVC :

```
app/          routes : chaque page relie un contrôleur à sa vue
controllers/  hooks React : état des écrans et appels aux services
models/       types et règles de validation, sans React ni réseau
views/        composants d'affichage, sans appel à l'API
services/     accès à l'API et stockage des tokens
```

Le back-office ne contacte jamais PostgreSQL : tout passe par l'API NestJS (routes `/admin/*`).
Le navigateur appelle `/api/...`, que Next.js relaie vers l'API (voir `next.config.ts`).

## Lancer

Le backend doit tourner (port 3000 par défaut).

```bash
npm install
npm run dev        # http://localhost:3001
```

Compte de démonstration du seed : `admin@example.com` / `Youtube2026!`.

## Adresse de l'API

`API_URL` (par défaut `http://localhost:3000`) est lue **à la build**. Pour une autre adresse :

```bash
API_URL=https://api.exemple.fr npm run build
npm start
```
