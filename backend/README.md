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
| `premium` | Achat simulé et abonnement payant (`PREMIUM_SUBSCRIPTION`) ([ADR-012](../docs/adr/ADR-012-paiement-simule-backend.md)) |

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

## Variables d'environnement

Lues dans `backend/.env` en développement (modèle : [`.env.example`](.env.example)), dans
l'environnement en production.

| Variable | Obligatoire | Rôle | Valeur par défaut |
|---|---|---|---|
| `DATABASE_URL` | oui | Connexion PostgreSQL | — |
| `REDIS_URL` | oui | Connexion Redis (compteurs, refresh tokens, jetons d'achat) | — |
| `JWT_ACCESS_SECRET` | oui | Signature des access tokens | — |
| `JWT_REFRESH_SECRET` | oui | Signature des refresh tokens, distinct du précédent | — |
| `PORT` | non | Port de l'API et de la WebSocket | `3000` |
| `MEDIA_DIR` | non | Dossier des fichiers servis sous `/media` | `storage/media` |
| `FFMPEG_PATH` | non | Chemin de l'exécutable FFmpeg | `ffmpeg` du PATH |
| `FFPROBE_PATH` | non | Chemin de ffprobe, pour `seed:videos` | à côté de `FFMPEG_PATH`, sinon `ffprobe` du PATH |

Une variable obligatoire manquante fait échouer le démarrage avec un message explicite. Si FFmpeg
est introuvable, le serveur démarre quand même : seule la génération de miniatures est refusée
(réponse 503, [ADR-011](../docs/adr/ADR-011-ffmpeg-chemins-medias.md)).

Pour générer un secret JWT :

```powershell
node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
```
