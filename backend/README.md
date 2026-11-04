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
| `playlists` | Playlists privées de l'utilisateur : créer, renommer, supprimer, ajouter et retirer des vidéos ([ADR-019](../docs/adr/ADR-019-je-naime-pas-telechargements-playlists-aide.md)) |
| `ads` | Publicités vidéo : import, liste et suppression par le back-office (`/admin/ads`), tirage au sort pour la lecture et le bandeau ([ADR-021](../docs/adr/ADR-021-publicites-preroll-bandeau.md)) |
| `notifications` | Jetons d'appareil (`POST` et `DELETE /me/devices`), notification FCM aux abonnés à la publication d'une vidéo ([ADR-020](../docs/adr/ADR-020-notifications-fcm.md)) |

## Lancer

Prérequis : Node.js 24, FFmpeg compilé avec le filtre whisper (sous-titres), et PostgreSQL et Redis démarrés par `docker compose up -d --wait`
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
npm run ads:import            # publicités déposées dans storage/media/ads/
npm run avatars:generate      # avatar (initiales) des chaînes du seed, qui n'en ont pas
npm run hls:generate          # échelle HLS multi-qualités des vidéos hébergées (quelques minutes)
npm run subtitles:generate    # sous-titres des vidéos hébergées, après le téléchargement du modèle
npm run start:dev             # http://localhost:3000
```

Vérification : http://localhost:3000/videos doit renvoyer du JSON.

**Après une migration et `npx prisma generate`, arrêter puis relancer `npm run start:dev`.** Sous
Windows, le mode watch ne recharge pas le client Prisma régénéré : il signale des erreurs de type
(« Property ... does not exist ») et le serveur continue de servir l'ancien code sans prévenir.
Arrêter le backend avec Ctrl+C dans son terminal, pour ne pas laisser de processus orphelin.

### Sous-titres automatiques

Les sous-titres sont transcrits par le filtre whisper de FFmpeg, avec le modèle `base` de whisper.cpp
([ADR-017](../docs/adr/ADR-017-hls-multi-qualites-sous-titres.md)). Le build FFmpeg « full » de Gyan
contient ce filtre : `ffmpeg -filters` doit afficher une ligne `whisper`. Le modèle (148 Mo) n'est
pas versionné ; il se télécharge une fois, dans `backend/models/` :

```powershell
New-Item -ItemType Directory -Force models
Invoke-WebRequest https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin -OutFile models/ggml-base.bin
```

Sans le filtre ou sans le modèle, le serveur démarre quand même : une erreur est journalisée et
les vidéos sont publiées sans sous-titres. La langue est détectée automatiquement. Sur une vidéo
sans parole (bruit d'ambiance, musique), le modèle peut inventer des répliques : c'est une limite connue
de la reconnaissance vocale, pas un défaut de notre code. Garde-fou : une vidéo de plus de 20 secondes
dont la transcription compte moins de 10 mots (annotations comme « [Music] » non comptées) est
considérée comme sans parole, et ne reçoit pas de sous-titres.

Le modèle `base` ne gère correctement ni le tagalog ni les paroles chantées : c'est une limite de la
reconnaissance vocale, pas de notre code. Sur l'import Creative Commons, la vidéo en tagalog (cc-03)
est prise pour de l'allemand, et le clip de rap (cc-06) reçoit des paroles en partie inventées. De
mauvais sous-titres se remarquant plus qu'une absence de sous-titres, ces deux vidéos n'en ont pas :
elles figurent dans `SUBTITLES_DISABLED_FOR` (`src/modules/videos/domain/subtitles.ts`), liste
respectée par la tâche de fond, `npm run subtitles:generate` et `seed:cc-videos`. Une nouvelle
vidéo dans ce cas est à y ajouter.

Mise en forme : deux lignes de 42 caractères au plus par réplique, et une seconde d'affichage au
moins ; une réplique trop brève rejoint sa voisine quand l'ensemble tient sur deux lignes.

Le pas à pas complet, avec les comptes de démonstration, est dans le [README de la racine](../README.md).

Autres commandes :

| Commande | Rôle |
|---|---|
| `npm run build` | Compile dans `dist/` |
| `npm run start:prod` | Lance la version compilée |
| `npm run test` | Tests unitaires du domaine (Vitest) : règles pures, sans base ni FFmpeg. Fichiers `*.spec.ts` à côté du code testé, sous `src/modules/*/domain/` |
| `npm run test:e2e` | Tests de bout en bout (`*.e2e-spec.ts`) : aucun n'est écrit pour l'instant, la commande échoue faute de fichier |
| `npm run lint` | Analyse statique (oxlint) |
| `npm run thumbnails:regenerate` | Remplace les miniatures génériques (placehold.co) par une image extraite de la vidéo |
| `npm run hls:generate` | Génère l'échelle HLS (720p, 480p et 360p, jamais au-dessus de la source) des vidéos hébergées chez nous qui n'en ont pas : seed, Creative Commons, ou génération en tâche de fond interrompue. Quelques minutes pour tout le catalogue (ADR-017) |
| `npm run subtitles:generate` | Génère les sous-titres WebVTT des vidéos hébergées chez nous qui n'en ont pas. Une vidéo à la fois ; une transcription est abandonnée au-delà de deux fois la durée de la vidéo, et de 5 minutes au plus. Les vidéos sans piste audio ou sans parole restent sans sous-titres et sont signalées à chaque lancement (ADR-017) |
| `npm run videos:localize` | Rapatrie dans le stockage local toute vidéo du catalogue dont le fichier ou la miniature est une URL externe (même procédé que `seed:videos` : ffprobe, réencodage 720p, vérification finale), puis régénère miniature, HLS et sous-titres. Une vidéo dont la source est injoignable est retirée du catalogue. L'application fonctionne ainsi sans Internet, avec le seul serveur local |
| `npm run avatars:generate` | Génère un avatar (initiales sur fond de couleur) pour chaque chaîne qui n'en a pas. Les chaînes créées par `POST /channels` reçoivent déjà le leur |
| `npm run ads:import` | Enregistre les publicités déposées à la main dans `storage/media/ads/` (ADR-021) : vérification ffprobe, miniature, échelle HLS, titre tiré du nom du fichier. Sans doublon : un fichier enregistré prend un nom généré et l'original est retiré du dossier ; un fichier refusé reste en place. Les fichiers ne sont pas versionnés : sur une autre machine, les déposer à la main |
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
| `WHISPER_MODEL` | non | Fichier du modèle whisper.cpp des sous-titres | `models/ggml-base.bin` |
| `AVATAR_FONT` | non | Police (`.ttf`) des initiales des avatars générés à la création d'une chaîne | Arial sous Windows, DejaVu sous Linux |
| `STRIPE_SECRET_KEY` | non | Clé secrète Stripe **de test** (`sk_test_…`). Absente : achat simulé de repli ([ADR-014](../docs/adr/ADR-014-paiement-stripe-mode-test.md)). Ne quitte jamais le backend | — |
| `STRIPE_PUBLISHABLE_KEY` | si `STRIPE_SECRET_KEY` | Clé publiable Stripe de test (`pk_test_…`), transmise à l'application par `GET /me/premium/config` | — |
| `FIREBASE_SERVICE_ACCOUNT` | non | Chemin de la clé de compte de service Firebase (`firebase-service-account.json`, jamais versionnée). Absente ou illisible : le serveur démarre sans notifications ([ADR-020](../docs/adr/ADR-020-notifications-fcm.md)) | — |

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
