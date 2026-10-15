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

Ce choix permet un lecteur 100 % natif avec ExoPlayer, et rend le back-office réellement utile puisque c'est lui qui gère le catalogue.

### Stockage
- En développement : dossier local sur le serveur.
- Au déploiement : Cloudflare R2, avec un CDN devant pour la diffusion.

### Format
Les vidéos sont servies en MP4 progressif, lu directement par ExoPlayer. Le streaming adaptatif HLS est une évolution prévue, mais il n'a pas d'intérêt tant qu'il n'existe qu'une seule qualité par vidéo.

### Recherche
Requête SQL simple sur le titre de la vidéo et le nom de la chaîne, via Prisma. C'est suffisant pour un catalogue de cette taille, sans moteur de recherche dédié.

### Origine des vidéos
Les vidéos proviennent de Pexels et Pixabay, libres de droits pour un usage gratuit. Elles sont téléchargées puis importées dans notre stockage via le back-office, qui reste seul maître du catalogue.

## Alternatives
- API YouTube Data : avantages, vraies vidéos et vraie recherche. Inconvénients, elle ne fournit pas les fichiers vidéo, il faudrait donc intégrer le lecteur web de YouTube dans une WebView, ce qui est de l'hybride ; on ne peut pas administrer le contenu de YouTube ; les quotas journaliers sont limités.
- Banque de vidéos externe (type Pexels) appelée en direct par l'application : avantage, fichiers MP4 lisibles nativement. Inconvénient, contenu non administrable. On garde ces banques comme source des fichiers, mais pas comme catalogue.
- Streaming adaptatif HLS dès le départ : avantage, qualité ajustée au débit. Inconvénient, transcodage en plusieurs qualités à mettre en place, inutile avec une seule qualité. Reporté.
- Moteur de recherche dédié (type Elasticsearch ou Meilisearch) : avantage, recherche plein texte tolérante aux fautes. Inconvénient, un service de plus à héberger, disproportionné pour ce catalogue.

## Conséquences
### Positives
- Lecteur 100 % natif avec ExoPlayer.
- Back-office réellement utile, puisque c'est lui qui gère le catalogue.
- MP4 progressif simple à produire et à servir, sans étape de transcodage.
- Recherche sans infrastructure supplémentaire, directement dans la base via Prisma.

### Négatives
- Il faut un stockage de fichiers et un CDN au déploiement (Cloudflare R2), en plus du dossier local utilisé en développement.
- C'est à nous d'alimenter le catalogue avec des vidéos libres de droits (Pexels, Pixabay).
- Le contenu est moins riche que le vrai YouTube.
- Pas d'adaptation de la qualité au débit tant que HLS n'est pas en place.
- Recherche limitée à une correspondance simple sur le titre et le nom de la chaîne.

## Liens
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est ce choix qui impose un lecteur natif, ExoPlayer, côté Android.
