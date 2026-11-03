# YouTube Clone

Clone de YouTube réalisé dans le cadre d'un projet d'école (rendu le 6 novembre 2026).
On y regarde des vidéos et des Shorts, on s'abonne à des chaînes, on commente en direct et on peut
passer à une offre Premium simulée. Le catalogue est administré depuis un back-office web.

## Les trois livrables

| Dossier | Livrable | Rôle |
|---|---|---|
| [`android/`](android/) | Application Android native | Fil d'accueil, lecture, Shorts, chaînes, abonnements, commentaires en direct, Premium |
| [`backend/`](backend/) | API REST + serveur WebSocket | Comptes, catalogue, temps réel, vérification de l'accès Premium, fichiers vidéo |
| [`backoffice/`](backoffice/) | Back-office web | Administration du catalogue : vidéos, catégories, tableau de bord (rôle ADMIN) |

Les décisions d'architecture sont consignées dans [`docs/adr/`](docs/adr/README.md).
Le déroulé du projet est dans [`docs/journal.md`](docs/journal.md), et l'encadrement de l'IA dans
[`docs/processus-ia.md`](docs/processus-ia.md).

## Stack

**Android** — Kotlin, Jetpack Compose, Media3 ExoPlayer, Retrofit, OkHttp (WebSocket), Coil, Hilt,
Navigation Compose, DataStore. Architecture MVVM + Clean Architecture (`presentation/`, `domain/`,
`data/`). minSdk 26. Application 100 % native, sans iOS ([ADR-001](docs/adr/ADR-001-android-natif.md)).

**Backend** — NestJS (TypeScript), PostgreSQL, Prisma 7, Redis (compteurs, refresh tokens),
passerelle WebSocket standard (`ws`). Architecture hexagonale en monolithe modulaire.
FFmpeg pour les miniatures et la préparation des vidéos de démonstration
([ADR-011](docs/adr/ADR-011-ffmpeg-chemins-medias.md)).

**Back-office** — Next.js (TypeScript), Tailwind CSS, architecture MVC. Il ne parle jamais à la
base : tout passe par l'API.

**Mesure** — PostHog (cloud Europe) pour les mesures d'audience et le test A/B du paywall,
appelé directement par l'application ([ADR-006](docs/adr/ADR-006-analytics-posthog.md)).

## Installation pas à pas

Les commandes ci-dessous sont écrites pour Windows (PowerShell), système sur lequel le projet est
développé.

### 1. Prérequis

| Outil | Version | Installation |
|---|---|---|
| Node.js | 24 | [nodejs.org](https://nodejs.org) |
| Docker Desktop | récente | [docker.com](https://www.docker.com/products/docker-desktop/) |
| FFmpeg (avec ffprobe) | récente | `winget install Gyan.FFmpeg` |
| Android Studio | récente | [developer.android.com](https://developer.android.com/studio), avec un émulateur |

> **Piège n° 1 — fermer VS Code en entier après une installation qui touche au PATH.**
> Node.js et FFmpeg s'ajoutent au PATH. Un terminal déjà ouvert, y compris le terminal intégré de
> VS Code, garde l'ancien PATH : `node` ou `ffmpeg` y restent introuvables. Fermer **toutes** les
> fenêtres de VS Code (pas seulement le terminal), puis le rouvrir.
> Vérifier ensuite : `node --version`, `ffmpeg -version`, `docker --version`.

Si FFmpeg ne peut pas être dans le PATH, indiquer son chemin dans `backend/.env` avec
`FFMPEG_PATH` (et `FFPROBE_PATH` si ffprobe n'est pas à côté).

### 2. Cloner le dépôt

```powershell
git clone <url-du-depot> Youtube_project
cd Youtube_project
```

### 3. Démarrer PostgreSQL et Redis

> **Piège n° 2 — Docker avant le backend.** Le backend a besoin de PostgreSQL et de Redis dès son
> démarrage, comme les migrations et le seed. Lancer d'abord Docker Desktop et attendre qu'il soit
> prêt, puis :

```powershell
docker compose up -d
```

Les deux services tournent alors sur `localhost:5432` (PostgreSQL, utilisateur, mot de passe et base
`youtube`) et `localhost:6379` (Redis). Vérifier avec `docker compose ps`.

### 4. Configurer et préparer le backend

```powershell
cd backend
npm install
Copy-Item .env.example .env
```

Dans `backend/.env`, remplacer `JWT_ACCESS_SECRET` et `JWT_REFRESH_SECRET` par deux valeurs longues
et différentes. Pour en générer une :

```powershell
node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
```

Puis créer les tables, générer le client Prisma et remplir la base :

```powershell
npx prisma migrate deploy   # applique les migrations de prisma/migrations
npx prisma generate         # génère le client Prisma (src/generated, non versionné)
npx prisma db seed          # données de démonstration
```

Le seed **efface** les données existantes avant de les recréer : 3 catégories, 4 comptes,
2 chaînes, des abonnements et des vidéos.

### 5. Récupérer les vidéos et les bannières

La base ne stocke que des chemins relatifs (`/media/...`). Les fichiers eux-mêmes sont dans
`backend/storage/`, qui n'est pas versionné : après un clone, il faut les reconstituer.

```powershell
npm run seed:videos    # télécharge les vidéos depuis Pexels, les vérifie et les réencode en 720p
npm run seed:banners   # tire de ces vidéos les bannières des chaînes
```

Les deux commandes demandent FFmpeg, et `seed:videos` une connexion Internet. L'ordre compte :
`seed:banners` utilise les vidéos téléchargées par `seed:videos`. Une vidéo ou une bannière déjà
présente est gardée ; `--force` refait tout (`npm run seed:videos -- --force`).

### 6. Lancer les trois projets

Chaque projet dans son propre terminal.

**Backend** (http://localhost:3000) :

```powershell
cd backend
npm run start:dev
```

Si FFmpeg est introuvable, le serveur démarre quand même et l'écrit dans ses journaux : seule la
génération de miniatures est refusée.

**Back-office** (http://localhost:3001) :

```powershell
cd backoffice
npm install
npm run dev
```

Le back-office appelle l'API par son relais `/api`. L'adresse de l'API (`API_URL`, par défaut
`http://localhost:3000`) est lue **à la build** : la changer impose de relancer `npm run build`.

**Application Android** :

1. Ouvrir le dossier `android/` dans Android Studio et laisser Gradle se synchroniser.
2. Démarrer un émulateur.
3. Lancer la configuration `app` (ou `.\gradlew.bat installDebug` depuis `android/`).

L'émulateur joint l'API par `http://10.0.2.2:3000/` (valeur `apiBaseUrl` de
`android/gradle.properties`). Sur un téléphone réel, remplacer cette valeur par l'IP locale du PC,
ou passer `-PapiBaseUrl=http://<ip>:3000/` à Gradle.

**Mesures PostHog (facultatif)** — l'application lit ses clés dans `android/secrets.properties`,
non versionné. Sans ce fichier, elle fonctionne normalement et n'envoie aucune mesure.

```properties
posthogApiKey=<clé du projet PostHog>
posthogHost=https://eu.i.posthog.com
```

## Comptes de démonstration

Tous les comptes du seed ont le mot de passe **`Youtube2026!`**.

| Email | Rôle | Pour montrer |
|---|---|---|
| `admin@example.com` | ADMIN | Connexion au back-office |
| `viewer@example.com` | USER — Camille Spectatrice | Spectatrice : suit Studio Libre mais pas Carnet de Route (bouton S'abonner) |
| `studio@example.com` | CREATOR — chaîne Studio Libre | Créateur avec chaîne |
| `explorer@example.com` | CREATOR — chaîne Carnet de Route | Créateur avec chaîne |

Aucun compte n'est Premium au départ : le passage en Premium se fait depuis l'application, par le
paiement simulé ([ADR-012](docs/adr/ADR-012-paiement-simule-backend.md)), sans aucun débit.

## Pièges courants

| Symptôme | Cause et solution |
|---|---|
| `node`, `npm` ou `ffmpeg` introuvable juste après l'installation | PATH pas encore relu : fermer VS Code **en entier** et le rouvrir |
| Le backend, les migrations ou le seed échouent sur la connexion à la base ou à Redis | Docker Desktop n'est pas lancé, ou `docker compose up -d` n'a pas été fait |
| Le backend ne compile pas : client Prisma introuvable | `npx prisma generate` n'a pas été lancé après le clone |
| Vidéos ou miniatures qui ne s'affichent pas | `backend/storage/` vide : lancer `npm run seed:videos` puis `npm run seed:banners` |
| L'application Android n'atteint pas l'API | Backend arrêté, ou téléphone réel avec `10.0.2.2` : mettre l'IP locale du PC |
| Le back-office appelle une mauvaise adresse d'API | `API_URL` est lue à la build : relancer `npm run build` |
