# Projet — Clone de YouTube

Monorepo de trois projets : application Android native, backend, back-office web.
Rendu le 6 novembre 2026. Projet d'école, développé sous Windows.

## Règles générales

- Répondre en français.
- Les décisions d'architecture sont dans `docs/adr/`. **Lire les ADR concernés avant de proposer du code**, respecter les ADR acceptés, signaler toute contradiction.
- Ne jamais ajouter de fonctionnalité, de dépendance ou d'écran qui n'a pas été demandé.
- En cas de doute ou d'information manquante, poser la question au lieu de deviner.
- Le sous-agent `adr-architecte` (`.claude/agents/`) est le seul à rédiger les ADR. Seul un humain passe un ADR en « Accepté ».
- Passer un ADR en Accepté signifie modifier son fichier ET l'index `docs/adr/README.md`. Les deux doivent toujours concorder.
- Modifier un ADR accepté :
  - changement de décision sur le fond → un nouvel ADR qui remplace l'ancien (comme pour les ADR-005, 012 et 002) ;
  - précision, ou ajustement de périmètre explicitement demandé par le lead développeur → amendement sur place, avec une ligne datée en fin d'ADR, « Amendé le 4 novembre : … », et la raison. L'ADR reste « Accepté ».
- Pas d'hybride : l'application mobile est 100 % native (contrainte du sujet).

## Structure

```
android/      application Android (Kotlin + Jetpack Compose)
backend/      API REST + serveur WebSocket (NestJS)
backoffice/   administration web (Next.js)
docs/adr/     décisions d'architecture
```

## Stack

**Android** — Kotlin, Jetpack Compose, Media3 ExoPlayer (lecture vidéo), Room (cache local),
Retrofit (API), OkHttp WebSocket (temps réel), Coil (images), Hilt (injection), Navigation Compose, DataStore.
Architecture **MVVM + Clean Architecture** : `presentation/` · `domain/` · `data/`.
minSdk 26.

**Backend** — NestJS (TypeScript), PostgreSQL, Prisma, Redis (cache, sessions, compteurs),
passerelle WebSocket NestJS. Stockage vidéo : dossier local en développement, Cloudflare R2 + CDN au déploiement.
Architecture **hexagonale, monolithe modulaire** : `domain/` · `application/` · `infrastructure/` · `interfaces/`.
Modules : auth, channels (dont les abonnements), videos, comments, premium, realtime.

**Back-office** — Next.js (TypeScript), Tailwind CSS.
Architecture **MVC** : `app/` · `controllers/` · `models/` · `views/` · `services/`.
Le back-office ne contacte jamais PostgreSQL directement : tout passe par l'API.

**Mesure** — PostHog (cloud Europe) pour les mesures d'audience et le test A/B.
Les événements partent de l'application directement vers PostHog, pas par le backend.

## Modèle de données

`USER` · `CHANNEL` · `VIDEO` · `CATEGORY` · `COMMENT` · `LIKE` · `FOLLOW` · `PREMIUM_SUBSCRIPTION` · `WATCH_HISTORY` · `POST`

- `FOLLOW` = abonnement **gratuit** à une chaîne. `PREMIUM_SUBSCRIPTION` = abonnement **payant**. Ne jamais confondre les deux.
- `VIDEO.format` vaut `SHORT` ou `LONG` : Shorts et vidéos classiques partagent la même table.
- `VIDEO.isPremium` marque les vidéos exclusives. L'accès est **toujours vérifié côté serveur**, jamais seulement dans l'application.
- La base ne stocke que l'URL ou le chemin relatif des fichiers, jamais les fichiers eux-mêmes.

## Périmètre

**Socle prioritaire** — comptes et chaînes (inscription, connexion, profil, création de chaîne, page de chaîne, abonnement) ; vidéos et Shorts (fil d'accueil, écran de lecture, onglet Shorts en défilement vertical, compteur de vues).

**Ensuite** — « j'aime », commentaires en temps réel, offre payante, mesures et test A/B, recherche, historique.

**Hors périmètre** — version iOS, live, monétisation des créateurs, mot de passe oublié, tendances, mini-lecteur, signalement, notifications, playlists, « à regarder plus tard ».

## Conventions

- Code et noms de variables en anglais, commentaires et documentation en français.
- Kotlin : `PascalCase` pour les composables et les classes, `camelCase` pour le reste.
- TypeScript : `camelCase` pour les fonctions, `PascalCase` pour les types et les composants React.
- Un commit par unité de travail, message en français à l'impératif.
- Vérifier que le projet compile avant d'annoncer qu'une tâche est terminée
  (`gradlew.bat assembleDebug` pour Android, `npm run build` pour le backend et le back-office).

