# ADR-002 — Hébergement des vidéos sur notre propre backend, sans API YouTube

## Statut
Accepté

## Contexte
L'application doit diffuser des vidéos. Il faut choisir d'où viennent les fichiers vidéo et qui gère le catalogue.

Contraintes :
- le sujet demande un back-office pour administrer le contenu de l'application ;
- le sujet interdit l'hybride.

## Décision
Héberger nous-mêmes les fichiers vidéo, sur notre propre backend et notre propre stockage. L'API YouTube n'est pas utilisée.

Ce choix permet un lecteur 100 % natif avec ExoPlayer, et rend le catalogue administrable : le back-office et l'application mobile (pour le propriétaire d'une chaîne) peuvent y publier, comme décrit dans l'ADR-015.

### Stockage
- En développement : dossier local sur le serveur.
- Au déploiement : Cloudflare R2, avec un CDN devant pour la diffusion.

### Format
Les vidéos sont servies en MP4 progressif, lu directement par ExoPlayer. Le streaming adaptatif HLS est une évolution prévue, mais il n'a pas d'intérêt tant qu'il n'existe qu'une seule qualité par vidéo.

### Recherche
Requête SQL simple sur le titre de la vidéo et le nom de la chaîne, via Prisma. C'est suffisant pour un catalogue de cette taille, sans moteur de recherche dédié.

### Origine des vidéos
Les vidéos proviennent de Pexels, libres de droits pour un usage gratuit.

Le back-office n'envoie aucun fichier. Il enregistre une vidéo par son URL (http ou https) ou par son chemin relatif dans notre stockage (/media/videos/…).

Les fichiers de démonstration sont déposés dans le stockage par le seed (npm run seed:videos, voir ADR-011). Certaines vidéos de démonstration sont référencées par une URL externe.

Le propriétaire d'une chaîne peut aussi publier depuis l'application mobile : le fichier envoyé est écrit dans notre stockage local, selon les règles de l'ADR-015.

## Alternatives
- API YouTube Data : avantages, vraies vidéos et vraie recherche. Inconvénients, elle ne fournit pas les fichiers vidéo, il faudrait donc intégrer le lecteur web de YouTube dans une WebView, ce qui est de l'hybride ; on ne peut pas administrer le contenu de YouTube ; les quotas journaliers sont limités.
- Banque de vidéos externe (type Pexels) appelée en direct par l'application : avantage, fichiers MP4 lisibles nativement. Inconvénient, contenu non administrable. On garde ces banques comme source des fichiers, mais pas comme catalogue.
- Streaming adaptatif HLS dès le départ : avantage, qualité ajustée au débit. Inconvénient, transcodage en plusieurs qualités à mettre en place, inutile avec une seule qualité. Reporté.
- Moteur de recherche dédié (type Elasticsearch ou Meilisearch) : avantage, recherche plein texte tolérante aux fautes. Inconvénient, un service de plus à héberger, disproportionné pour ce catalogue.

## Conséquences
### Positives
- Lecteur 100 % natif avec ExoPlayer.
- Catalogue administrable : le back-office et l'application mobile (propriétaire de la chaîne) peuvent y publier (voir l'ADR-015).
- MP4 progressif simple à produire et à servir, sans transcodage à l'exécution : le serveur sert le fichier tel quel. La préparation hors ligne des fichiers de démonstration ne relève pas de cette décision.
- Recherche sans infrastructure supplémentaire, directement dans la base via Prisma.

### Négatives
- Il faut un stockage de fichiers et un CDN au déploiement (Cloudflare R2), en plus du dossier local utilisé en développement.
- Les vidéos de démonstration sont à fournir nous-mêmes, libres de droits (Pexels) ; le reste du catalogue dépend de ce que publient le back-office et les créateurs depuis l'application (ADR-015).
- Le contenu est moins riche que le vrai YouTube.
- Pas d'adaptation de la qualité au débit tant que HLS n'est pas en place.
- Recherche limitée à une correspondance simple sur le titre et le nom de la chaîne.

## Liens
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est ce choix qui impose un lecteur natif, ExoPlayer, côté Android.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : FFmpeg en dépendance système et chemins relatifs pour les fichiers hébergés ; le réencodage qu'il prévoit avec `npm run seed:videos` est une préparation hors ligne des vidéos de démonstration, qui ne remet pas en cause l'absence de transcodage à l'exécution.
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication de vidéos, de Shorts et de posts depuis l'application mobile ; le back-office et le propriétaire d'une chaîne, depuis l'application, publient dans ce stockage.
