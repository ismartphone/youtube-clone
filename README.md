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
| Android Studio | récente (compatible AGP 9.4) | [developer.android.com](https://developer.android.com/studio) |
| SDK Platform Android 37 | API 37 | Android Studio → *SDK Manager* → *SDK Platforms* |
| Émulateur Android | API 26 ou plus | Android Studio → *Device Manager* |

**Connexion Internet.** Elle est nécessaire pendant toute l'installation, pas seulement pour le seed :
`npm install`, premier build Gradle, téléchargement des vidéos et des bannières de démonstration.
Elle reste nécessaire ensuite pour lire les vidéos du seed hébergées à l'extérieur et pour envoyer
les mesures PostHog.

**Docker Desktop sous Windows** s'appuie sur WSL 2 et sur la virtualisation du processeur. Au
premier lancement, il peut demander d'installer WSL (`wsl --install`) puis de redémarrer le PC.
Si la virtualisation est désactivée, il faut l'activer dans le BIOS.

**Ports utilisés.** Ils doivent être libres :

| Port | Service |
|---|---|
| 5432 | PostgreSQL (conteneur Docker) |
| 6379 | Redis (conteneur Docker) |
| 3000 | API du backend |
| 3001 | Back-office |

Un PostgreSQL déjà installé sur la machine occupe souvent le port 5432. Le conteneur ne peut alors
pas démarrer, ou le backend se connecte à la mauvaise base. Arrêter ce service Windows avant de
continuer.

> **Piège n° 1 — PowerShell bloque `npm` et `npx`.** Sur un Windows neuf, PowerShell refuse
> d'exécuter des scripts. `npm install` échoue alors avec « l'exécution de scripts est désactivée
> sur ce système ». Autoriser les scripts locaux pour son seul compte, une fois pour toutes :
>
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
> ```

> **Piège n° 2 — fermer VS Code en entier après une installation qui touche au PATH.**
> Node.js et FFmpeg s'ajoutent au PATH. Un terminal déjà ouvert, y compris le terminal intégré de
> VS Code, garde l'ancien PATH : `node` ou `ffmpeg` y restent introuvables. Fermer **toutes** les
> fenêtres de VS Code (pas seulement le terminal), puis le rouvrir.
> Vérifier ensuite : `node --version`, `ffmpeg -version`, `docker --version`.

Si FFmpeg ne peut pas être dans le PATH, indiquer son chemin dans `backend/.env` avec
`FFMPEG_PATH` (et `FFPROBE_PATH` si ffprobe n'est pas à côté).

### 2. Cloner le dépôt

```powershell
git clone https://github.com/ismartphone/youtube-clone.git Youtube_project
cd Youtube_project
```

### 3. Démarrer PostgreSQL et Redis

> **Piège n° 3 — Docker avant le backend.** Le backend a besoin de PostgreSQL et de Redis dès son
> démarrage, comme les migrations et le seed. Lancer d'abord Docker Desktop et attendre qu'il soit
> prêt, puis :

```powershell
docker compose up -d --wait
```

`--wait` ne rend la main qu'une fois les conteneurs démarrés et PostgreSQL prêt à répondre. Les
deux services tournent alors sur `localhost:5432` (PostgreSQL : utilisateur, mot de passe et base
`youtube`) et `localhost:6379` (Redis).

### 4. Configurer et préparer le backend

```powershell
cd backend
npm install
Copy-Item .env.example .env
```

`npm install` signale 18 vulnérabilités (2 faibles, 7 modérées, 9 hautes). Elles sont connues et
n'ont pas été traitées. Toutes viennent de dépendances transitives, tirées par Prisma, NestJS et
Vitest. Elles n'empêchent pas le projet de tourner. Deux d'entre elles touchent des paquets utilisés
pendant l'exécution : `multer`, pour l'envoi de fichiers, et `body-parser`. Ne pas lancer
`npm audit fix --force`, qui peut changer les versions majeures et casser le projet.

Dans `backend/.env`, remplacer `JWT_ACCESS_SECRET` et `JWT_REFRESH_SECRET` par deux valeurs longues
et différentes. Pour en générer une :

```powershell
node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
```

Paiement Premium : pour un vrai paiement Stripe en mode test, renseigner aussi `STRIPE_SECRET_KEY`
(`sk_test_…`) et `STRIPE_PUBLISHABLE_KEY` (`pk_test_…`), tirées du tableau de bord Stripe en mode test.
Sans ces clés, l'application propose l'achat simulé, ce qui suffit pour une démonstration hors ligne
([ADR-014](docs/adr/ADR-014-paiement-stripe-mode-test.md)). Carte de test : 4242 4242 4242 4242,
date future et CVC quelconques.

Puis créer les tables, générer le client Prisma et remplir la base :

```powershell
npx prisma migrate deploy   # applique les migrations de prisma/migrations
npx prisma generate         # génère le client Prisma (src/generated, non versionné)
npx prisma db seed          # données de démonstration
```

Le seed **efface** les données existantes avant de les recréer : 3 catégories, 4 comptes,
2 chaînes, des abonnements et des vidéos, dont une exclusive Premium en tête du fil.

Les migrations installent l'extension PostgreSQL `unaccent` (recherche insensible aux accents).
L'image Docker du projet la fournit ; sur une autre base, le rôle utilisé doit avoir le droit
`CREATE EXTENSION`.

### 5. Récupérer les vidéos, les bannières et les miniatures

La base ne stocke que des chemins relatifs (`/media/...`). Les fichiers eux-mêmes sont dans
`backend/storage/`, qui n'est pas versionné : après un clone, il faut les reconstituer. Toujours
depuis `backend/`, et dans cet ordre :

```powershell
npm run seed:videos             # télécharge les vidéos depuis Pexels, les vérifie et les réencode
npm run seed:banners            # tire de ces vidéos les bannières des chaînes
npm run thumbnails:regenerate   # extrait une vraie miniature de chaque vidéo du seed
```

Les trois commandes demandent FFmpeg. L'ordre compte : les bannières et les miniatures sont tirées
des vidéos téléchargées par `seed:videos`.

Le réencodage ramène le petit côté de chaque vidéo à 720 pixels, en gardant les proportions de la
source. Une vidéo horizontale sort en 1280×720. Pour une vidéo verticale, la largeur fait 720 pixels
et la hauteur dépend de la source : un Short est par exemple sorti en 720×1366.

**Ne pas sauter `thumbnails:regenerate`.** Le seed ne pose que des miniatures génériques : une
image grise portant le titre en texte. Cette commande les remplace par une image extraite de la
vidéo, à 10 % de sa durée. Elle ne touche que les miniatures génériques : une miniature saisie
dans le back-office ou déjà générée est gardée. Une vidéo en échec est signalée et passée.

Une vidéo ou une bannière déjà présente est gardée ; `--force` refait tout
(`npm run seed:videos -- --force`).

### 6. Lancer les trois projets

Chaque projet dans son propre terminal.

**Backend** (http://localhost:3000) :

```powershell
cd backend
npm run start:dev
```

**Vérification :** ouvrir http://localhost:3000/videos dans un navigateur. Une page de JSON listant
les vidéos du seed doit s'afficher. Sinon, ne pas passer à la suite : relire les journaux du
backend (voir aussi les pièges courants plus bas).

Si FFmpeg est introuvable, le serveur démarre quand même et l'écrit dans ses journaux : seule la
génération de miniatures est refusée.

**Back-office** (http://localhost:3001) :

```powershell
cd backoffice
npm install
npm run dev
```

Le back-office appelle l'API par son relais `/api`. L'adresse de l'API (`API_URL`, par défaut
`http://localhost:3000`) est lue au démarrage de `npm run dev` : pour la changer, il suffit
d'arrêter le serveur et de le relancer. Dans une version compilée (`npm run build` puis
`npm start`), elle est lue au moment de la build, et il faut reconstruire.

**Application Android** :

1. Ouvrir le dossier `android/` dans Android Studio et laisser Gradle se synchroniser. Cette
   première ouverture crée `android/local.properties` (chemin du SDK), qui n'est pas versionné.
2. Si Android Studio le propose, installer les composants manquants (SDK Platform 37, outils de
   build).
3. Démarrer un émulateur (API 26 ou plus).
4. Lancer la configuration `app`.

**En ligne de commande**, `.\gradlew.bat installDebug` (depuis `android/`) ne fonctionne qu'à deux
conditions :
- le projet a déjà été ouvert une fois dans Android Studio, qui crée `local.properties` ;
- `JAVA_HOME` pointe vers le JDK fourni avec Android Studio :

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"   # pour ce terminal seulement
.\gradlew.bat installDebug
```

Pour rendre `JAVA_HOME` permanent, le définir dans les variables d'environnement de Windows, puis
fermer VS Code en entier (piège n° 2).

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
| « L'exécution de scripts est désactivée sur ce système » au lancement de `npm` ou `npx` | Politique PowerShell par défaut : `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` |
| `node`, `npm` ou `ffmpeg` introuvable juste après l'installation | PATH pas encore relu : fermer VS Code **en entier** et le rouvrir |
| Docker Desktop ne démarre pas | WSL 2 absent (`wsl --install`, puis redémarrer) ou virtualisation désactivée dans le BIOS |
| Le conteneur PostgreSQL ne démarre pas, ou le backend ne trouve pas la base `youtube` | Port 5432 déjà pris, souvent par un PostgreSQL installé sur la machine : arrêter ce service |
| Le backend, les migrations ou le seed échouent sur la connexion à la base ou à Redis | Docker Desktop n'est pas lancé, ou `docker compose up -d --wait` n'a pas été fait |
| Le backend ne compile pas : client Prisma introuvable | `npx prisma generate` n'a pas été lancé après le clone |
| Vidéos ou bannières qui ne s'affichent pas | `backend/storage/` vide : lancer `npm run seed:videos` puis `npm run seed:banners` |
| Miniatures grises avec le titre en texte | `npm run thumbnails:regenerate` n'a pas été lancé |
| `gradlew.bat` : « JAVA_HOME is not set » ou SDK introuvable | Ouvrir une fois `android/` dans Android Studio, et faire pointer `JAVA_HOME` vers son dossier `jbr` |
| L'application Android n'atteint pas l'API | Backend arrêté, ou téléphone réel avec `10.0.2.2` : mettre l'IP locale du PC |
| Le back-office appelle une mauvaise adresse d'API | `API_URL` est lue au démarrage : arrêter `npm run dev` et le relancer (version compilée : relancer `npm run build`) |
