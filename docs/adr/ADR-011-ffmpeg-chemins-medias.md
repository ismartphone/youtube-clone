# ADR-011 — FFmpeg en dépendance système et chemins relatifs pour les fichiers hébergés

## Statut
Accepté

## Contexte
Le back-office publie les vidéos (ADR-005), qui sont hébergées sur notre propre backend (ADR-002). Deux besoins apparaissent.

D'abord, une vidéo doit avoir une miniature, y compris quand l'administrateur n'en fournit pas, et les vidéos de démonstration du seed doivent être préparées (téléchargement, vérification, réencodage). Ces deux traitements demandent FFmpeg. Il faut choisir comment le backend l'obtient, et comment il se comporte s'il est absent.

Ensuite, il faut choisir comment enregistrer en base l'adresse des fichiers hébergés chez nous (miniatures générées, vidéos du stockage local).

Contraintes :
- le projet est développé sous Windows ;
- en développement, l'émulateur Android joint l'API par 10.0.2.2:3000, adresse qui n'existe que dans l'émulateur, alors que le navigateur du back-office la joint par localhost ;
- au déploiement, les vidéos passent par Cloudflare R2 avec un CDN devant (ADR-002) ;
- la base ne stocke que l'URL des fichiers vidéo, jamais les fichiers eux-mêmes.

## Décision
### FFmpeg, dépendance système du backend
FFmpeg est installé à part, et non comme paquet npm : par exemple `winget install Gyan.FFmpeg` sous Windows, et installé dans l'image au déploiement. Le serveur appelle la commande `ffmpeg` du PATH, surchargeable par la variable FFMPEG_PATH, et `ffprobe`, surchargeable par FFPROBE_PATH.

FFmpeg sert à deux usages :
- générer les miniatures : une image extraite à 10 % de la durée, recadrée en 1280x720 (720x1280 pour un Short) et enregistrée en JPEG dans le stockage local. La génération a lieu quand une vidéo est créée ou modifiée sans miniature depuis le back-office, par le bouton « Régénérer la miniature » et par la commande `npm run thumbnails:regenerate` ;
- préparer les vidéos de démonstration du seed, avec la commande `npm run seed:videos` : téléchargement depuis Pexels, vérification par ffprobe et réencodage en 720p.

Ce réencodage est une préparation hors ligne de données de démonstration. Il est distinct de l'absence de transcodage à l'exécution posée par l'ADR-002, où le serveur sert les fichiers tels quels : l'ADR-002 n'est donc pas contredit.

La commande `seed:videos` prépare des données de démonstration et n'est pas un chemin d'entrée au catalogue. Aucune route exposée ne permet d'envoyer une vidéo : l'ADR-005 n'est donc pas contredit.

### Absence de FFmpeg
Si FFmpeg est absent au démarrage, le serveur démarre quand même et écrit une erreur explicite dans les journaux. Seule la génération de miniatures est refusée, avec une réponse 503 et un message clair.

Ce choix évite de bloquer l'application mobile pour un outil qui ne sert qu'au back-office.

### Chemins relatifs pour les fichiers hébergés chez nous
Les fichiers hébergés chez nous sont enregistrés en base avec un chemin relatif, par exemple `/media/thumbnails/<uuid>.jpg` ou `/media/videos/<nom>.mp4`. Le backend les sert en statique sous `/media`.

Chaque client complète ce chemin avec l'adresse par laquelle il joint l'API :
- l'application Android, avec son adresse d'API (10.0.2.2:3000 sur l'émulateur) ;
- le back-office, avec son relais `/api` (Next.js).

Une URL complète (fichier hébergé ailleurs, futur CDN Cloudflare R2 de l'ADR-002) est gardée telle quelle.

Ce choix vient de ce qu'en développement l'émulateur et le navigateur ne joignent pas l'API par la même adresse : une URL complète enregistrée en base ne peut pas convenir aux deux.

### Mise en œuvre
- Sécurité : pour une URL externe, FFmpeg n'est autorisé qu'aux protocoles http, https, tcp et tls, ce qui empêche une URL de faire lire un fichier du serveur. Pour un fichier local `/media/...`, seul le protocole file est permis, et le chemin ne peut pas sortir du dossier de stockage.
- Le dossier de stockage (`backend/storage`, surchargeable par MEDIA_DIR) n'est pas versionné (.gitignore). Après un clone, les vidéos du seed se récupèrent avec `npm run seed:videos`.
- Chaque miniature générée a un nom unique (UUID) : une régénération produit un nouveau fichier, et l'ancien est supprimé. Les fichiers sont servis avec un cache de 7 jours.

## Alternatives
- Paquet npm ffmpeg-static : avantage, rien à installer à la main. Inconvénient, une dépendance lourde, avec un binaire d'environ 70 Mo téléchargé à l'installation de npm.
- Refuser de démarrer sans FFmpeg : avantage, plus strict. Inconvénient, toute l'API devient indisponible tant que FFmpeg n'est pas installé.
- URL complète configurable (variable PUBLIC_MEDIA_URL) : avantage, aucun changement côté clients. Inconvénient, en développement, les fichiers ne s'afficheraient que pour l'un des deux clients (émulateur ou navigateur).

## Conséquences
### Positives
- Pas de binaire lourd téléchargé à chaque installation de npm.
- L'API et l'application mobile restent disponibles même si FFmpeg n'est pas installé ; seule la génération de miniatures est refusée, avec un message clair.
- Une vidéo publiée sans miniature en reçoit une automatiquement.
- Les fichiers hébergés chez nous s'affichent à la fois dans l'émulateur et dans le navigateur du back-office.
- Les URL complètes, dont celles du futur CDN Cloudflare R2, restent possibles sans changement.
- Une URL externe ne peut pas faire lire un fichier du serveur, et un chemin local ne peut pas sortir du dossier de stockage.

### Négatives
- FFmpeg est à installer à la main sur chaque poste de développement, et à prévoir dans l'image au déploiement.
- Chaque client doit compléter les chemins relatifs avec son adresse d'API.
- Le dossier de stockage n'est pas versionné : après un clone, il faut relancer `npm run seed:videos` pour retrouver les vidéos du seed.
- La position de l'image est fixe (10 % de la durée) : « Régénérer la miniature » redonne la même image tant que la vidéo ne change pas.

## Liens
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement des vidéos sur notre propre backend ; c'est ce stockage, dossier local en développement puis Cloudflare R2 avec CDN au déploiement, qui contient les fichiers dont cet ADR fixe l'adresse en base, et dont les vidéos viennent de Pexels. Le réencodage du seed est une préparation hors ligne, qui ne contredit pas l'absence de transcodage à l'exécution posée par cet ADR.
- [ADR-005](ADR-005-ajout-videos-backoffice.md) : ajout des vidéos uniquement depuis le back-office ; c'est à la création ou à la modification d'une vidéo depuis le back-office que la miniature est générée. La commande `npm run seed:videos` prépare des données de démonstration et n'ouvre pas d'autre entrée au catalogue.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est ce backend qui appelle FFmpeg et sert les fichiers en statique sous `/media`.
- [ADR-009](ADR-009-stockage-tokens-backoffice.md) : stockage des tokens du back-office ; c'est cet ADR qui définit le relais `/api` de Next.js, par lequel le back-office complète les chemins relatifs.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est cette application qui complète les chemins relatifs avec son adresse d'API.
