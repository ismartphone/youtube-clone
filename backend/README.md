# Backend

API REST et serveur WebSocket du YouTube Clone. Il sert l'application Android et le back-office :
comptes, chaînes, catalogue, commentaires et « j'aime » en direct, offre Premium, fichiers vidéo.
L'accès aux vidéos Premium est vérifié ici, jamais seulement dans l'application.

NestJS (TypeScript), PostgreSQL avec Prisma 7, Redis. Architecture hexagonale en monolithe modulaire
([ADR-003](../docs/adr/ADR-003-backend-nestjs.md)) : chaque module de `src/modules/` est découpé en
`domain/`, `application/`, `infrastructure/` et `interfaces/`.

| Module | Rôle |
|---|---|
| `auth` | Inscription, connexion, JWT et refresh tokens ([ADR-008](../docs/adr/ADR-008-authentification-jwt.md)) |
| `channels` | Chaînes et abonnements gratuits (`FOLLOW`) |
| `videos` | Catalogue, fil, vues, « j'aime », historique, routes `/admin` du back-office, miniatures FFmpeg |
| `comments` | Commentaires |
| `realtime` | Passerelle WebSocket `/realtime`, salles par vidéo ([ADR-010](../docs/adr/ADR-010-websocket-salles-compteurs.md)) |
| `premium` | Paiement Stripe en mode test, achat simulé en repli, abonnement payant (`PREMIUM_SUBSCRIPTION`) ([ADR-014](../docs/adr/ADR-014-paiement-stripe-mode-test.md)) |

## Lancer

Prérequis : Node.js 24, FFmpeg, et PostgreSQL et Redis démarrés par `docker compose up -d --wait`
à la racine du dépôt. **Docker doit tourner avant le backend.**

```powershell
npm install
Copy-Item .env.example .env   # puis renseigner les secrets JWT
npx prisma migrate deploy     # crée les tables
npx prisma generate           # génère le client Prisma dans src/generated (non versionné)
npx prisma db seed            # données de démonstration (efface les données existantes)
npm run seed:videos           # vidéos de démonstration dans storage/ (FFmpeg, Internet)
npm run seed:banners          # bannières des chaînes, après seed:videos
npm run thumbnails:regenerate # vraies miniatures à la place des images génériques du seed
npm run seed:cc-videos        # vidéos Creative Commons, si les fichiers cc-*.mp4 sont fournis
npm run avatars:generate      # avatar (initiales) des chaînes du seed, qui n'en ont pas
npm run hls:generate          # échelle HLS multi-qualités des vidéos hébergées (quelques minutes)
npm run start:dev             # http://localhost:3000
```

Vérification : http://localhost:3000/videos doit renvoyer du JSON.

Le pas à pas complet, avec les comptes de démonstration, est dans le [README de la racine](../README.md).

Autres commandes :

| Commande | Rôle |
|---|---|
| `npm run build` | Compile dans `dist/` |
| `npm run start:prod` | Lance la version compilée |
| `npm run test` | Tests unitaires (Vitest) |
| `npm run test:e2e` | Tests de bout en bout |
| `npm run lint` | Analyse statique (oxlint) |
| `npm run thumbnails:regenerate` | Remplace les miniatures génériques (placehold.co) par une image extraite de la vidéo |
| `npm run hls:generate` | Génère l'échelle HLS (720p, 480p et 360p, jamais au-dessus de la source) des vidéos hébergées chez nous qui n'en ont pas : seed, Creative Commons, ou génération en tâche de fond interrompue. Quelques minutes pour tout le catalogue (ADR-017) |
| `npm run avatars:generate` | Génère un avatar (initiales sur fond de couleur) pour chaque chaîne qui n'en a pas. Les chaînes créées par `POST /channels` reçoivent déjà le leur |
| `npm run seed:cc-videos` | Ajoute les six vidéos Creative Commons à la base existante, sans rien effacer ni dupliquer. Les fichiers `cc-*.mp4` ne se retéléchargent pas : ils doivent être fournis à la main dans `storage/media/videos/`. Voir [docs/sources-videos.md](../docs/sources-videos.md) |

## Variables d'environnement

Lues dans `backend/.env` en développement (modèle : [`.env.example`](.env.example)), dans
l'environnement en production.

| Variable | Obligatoire | Rôle | Valeur par défaut |
|---|---|---|---|
| `DATABASE_URL` | oui | Connexion PostgreSQL | — |
| `REDIS_URL` | oui | Connexion Redis (compteurs, refresh tokens, jetons d'achat simulé) | — |
| `JWT_ACCESS_SECRET` | oui | Signature des access tokens | — |
| `JWT_REFRESH_SECRET` | oui | Signature des refresh tokens, distinct du précédent | — |
| `PORT` | non | Port de l'API et de la WebSocket | `3000` |
| `MEDIA_DIR` | non | Dossier des fichiers servis sous `/media` | `storage/media` |
| `FFMPEG_PATH` | non | Chemin de l'exécutable FFmpeg | `ffmpeg` du PATH |
| `FFPROBE_PATH` | non | Chemin de ffprobe (vérification des envois et des vidéos du seed) | à côté de `FFMPEG_PATH`, sinon `ffprobe` du PATH |
| `AVATAR_FONT` | non | Police (`.ttf`) des initiales des avatars générés à la création d'une chaîne | Arial sous Windows, DejaVu sous Linux |
| `STRIPE_SECRET_KEY` | non | Clé secrète Stripe **de test** (`sk_test_…`). Absente : achat simulé de repli ([ADR-014](../docs/adr/ADR-014-paiement-stripe-mode-test.md)). Ne quitte jamais le backend | — |
| `STRIPE_PUBLISHABLE_KEY` | si `STRIPE_SECRET_KEY` | Clé publiable Stripe de test (`pk_test_…`), transmise à l'application par `GET /me/premium/config` | — |

Une variable obligatoire manquante fait échouer le démarrage avec un message explicite. Les clés Stripe
se trouvent dans le tableau de bord Stripe, en mode test (Développeurs → Clés API) ; `backend/.env`
n'est jamais versionné. Au démarrage, le journal indique le mode retenu : « Paiement par Stripe en
mode test » ou « achat simulé (repli) ». Si FFmpeg
est introuvable, le serveur démarre quand même : seule la génération de miniatures est refusée
(réponse 503, [ADR-011](../docs/adr/ADR-011-ffmpeg-chemins-medias.md)).

## Base de données

La recherche (`GET /search`) est insensible aux accents grâce à l'extension PostgreSQL `unaccent`,
installée par la migration `20261103100000_add_unaccent_extension` (`CREATE EXTENSION IF NOT EXISTS
unaccent`). L'image `postgres` du `docker-compose.yml` la fournit. Pour une autre base, notamment au
déploiement, le rôle qui applique les migrations doit avoir le droit `CREATE EXTENSION`, sinon
`prisma migrate deploy` échoue sur cette migration.

Pour générer un secret JWT :

```powershell
node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
```
