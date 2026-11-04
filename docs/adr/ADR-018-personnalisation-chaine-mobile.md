# ADR-018 — Personnalisation de sa chaîne par le créateur depuis l'application mobile

## Statut
Proposé

## Contexte
Un créateur publie déjà des vidéos, des Shorts et des posts depuis l'application mobile (ADR-015). En revanche, il ne peut pas modifier l'apparence ni l'identité de sa chaîne une fois qu'elle est créée :
- la photo de profil est l'avatar généré par FFmpeg à la création de la chaîne (POST /channels), les initiales du nom sur un fond de couleur, rangé sous `/media/avatars/<channelId>.png`. Le domaine (`channels/domain/avatar.ts`) prévoit déjà qu'une photo choisie par le créateur le remplacera dans `avatarUrl` ;
- la bannière en haut de la page de chaîne ne peut être posée, remplacée ou effacée que depuis le back-office (PATCH /admin/channels/:id), par une URL http(s) ou un chemin `/media/banners/...` ;
- le nom, la description et le handle sont fixés à la création de la chaîne et ne peuvent plus être modifiés.

Le 4 novembre 2026, le lead développeur décide de permettre au créateur de modifier ces éléments depuis l'application, sur sa propre chaîne.

Faits existants dans le code :
- une seule chaîne par utilisateur (`ownerId` unique sur CHANNEL) ;
- CHANNEL porte `name`, `handle`, `description`, `avatarUrl` et `bannerUrl` ;
- le handle est unique sans tenir compte de la casse : il est stocké en minuscules, sans « @ », et respecte le motif `^[a-z0-9._-]{3,30}$` (`channels/domain/channel.ts`) ;
- à la création, le nom fait de 1 à 50 caractères et la description 1000 caractères au plus (`CreateChannelBody`) ;
- les images des posts sont déjà envoyées depuis l'application et validées par ffprobe, jamais par leur extension ni par le type MIME annoncé : JPEG ou PNG, 5 Mo au plus (`posts/domain/post.ts`, `MAX_POST_IMAGE_BYTES`). Le mécanisme d'envoi est commun (`shared/interfaces/http/file-upload.ts`).

Contraintes :
- l'application reste 100 % native (ADR-001) ;
- les fichiers sont hébergés chez nous, dans un dossier local en développement, sur Cloudflare R2 avec un CDN au déploiement (ADR-002) ;
- la base ne stocke que des chemins relatifs ou des URL, jamais les fichiers (ADR-011, CLAUDE.md) ;
- les fichiers sous `/media` sont servis en statique avec un cache de 7 jours (ADR-011) : un fichier remplacé doit changer de nom, sinon les clients gardent l'ancien en cache ;
- l'envoi de fichiers par les utilisateurs est une surface d'attaque, déjà encadrée par l'ADR-015 : validation par ffprobe, taille maximale, nom de fichier généré par le serveur, refus en 503 sans FFmpeg ni ffprobe (ADR-011) ;
- comme pour l'accès Premium (ADR-007) et la visibilité (ADR-016), une règle d'accès est toujours vérifiée côté serveur, jamais seulement dans l'application ;
- le backend suit une architecture hexagonale (ADR-003) : les règles de modification d'une chaîne appartiennent au domaine du module channels ;
- aucun texte visible par l'utilisateur ne mentionne YouTube ni n'annonce ce qui n'est pas développé (règle de rédaction du 4 novembre, voir l'amendement de l'ADR-015) ;
- le rendu est le 6 novembre 2026, le temps est limité.

## Décision
Le créateur peut modifier depuis l'application mobile, sur sa propre chaîne :
1. la photo de profil, qui remplace l'avatar généré (initiales sur fond de couleur) ;
2. la bannière, l'image en haut de la page de chaîne ;
3. le nom et la description ;
4. le handle (@nom).

Les autres points ci-dessous ne sont pas encore tranchés par le lead développeur. Ils sont présentés comme options à arbitrer avant le passage de cet ADR en « Accepté ». Ce qui suit les options découle des contraintes déjà acceptées, et non d'un choix nouveau.

### Règles qui découlent des ADR acceptés
- Fichiers envoyés : validés par ffprobe, jamais par leur extension ni par le type MIME annoncé par le client ; nom de fichier généré par le serveur, jamais repris du client ; refus en 503 avec un message clair si FFmpeg ou ffprobe est absent (ADR-011, ADR-015).
- Chemins en base : `avatarUrl` et `bannerUrl` reçoivent un chemin relatif sous `/media`, comme les autres fichiers hébergés chez nous (ADR-011).
- Cache de 7 jours : chaque nouvelle photo ou bannière est enregistrée sous un nouveau nom de fichier ; un fichier n'est jamais réécrit sous le même nom.
- Handle : la normalisation (minuscules, sans « @ ») et le motif `^[a-z0-9._-]{3,30}$` de la création s'appliquent aussi à la modification, ainsi que l'unicité sans tenir compte de la casse.
- Propriétaire : seul le propriétaire de la chaîne peut la modifier depuis l'application, et c'est vérifié par l'API, jamais seulement dans l'application (même principe qu'ADR-007 et ADR-016).
- Textes d'interface : aucun texte visible ne mentionne YouTube ni n'annonce ce qui n'est pas développé.

### Points à arbitrer par le lead développeur

#### Formats et poids des images
- Option A : reprendre la règle des images de posts, JPEG ou PNG, 5 Mo au plus, validée par ffprobe, avec le même mécanisme d'envoi commun.
- Option B : d'autres formats ou un autre poids, à préciser. À noter : le back-office accepte aujourd'hui une bannière locale en `.webp` (`/media/banners/...`), ce que l'envoi des posts n'accepte pas.

#### Recadrage
Photo carrée, bannière en bandeau (l'application l'affiche aujourd'hui au rapport 3,6 sur la page de chaîne).
- Option A : recadrage côté serveur par FFmpeg, comme les miniatures (ADR-011).
- Option B : recadrage côté client, dans l'application, avant l'envoi.
- Option C : aucun recadrage, l'image est affichée recadrée à l'écran.
- Dimensions de sortie à fixer si l'option A ou B est retenue.

#### Suppression de l'ancien fichier
- Option A : l'ancienne photo ou l'ancienne bannière envoyée est supprimée du stockage au remplacement, comme une miniature régénérée (ADR-011).
- Option B : l'ancien fichier est conservé.
- Cas particulier de l'avatar généré : `avatar.ts` dit qu'il n'a pas à disparaître quand une photo le remplace. À confirmer.

#### Retrait de la photo
- Le créateur peut-il retirer sa photo ? Si oui, revient-on à l'avatar généré (initiales sur fond de couleur) ?
- Si le nom change, l'avatar généré (dont les initiales et la couleur viennent du nom) est-il régénéré ?
- Le créateur peut-il retirer sa bannière (retour à l'absence de bannière) ?

#### Règles du handle
- Conflit : message affiché quand le handle demandé est déjà pris ; vérification de disponibilité pendant la saisie ou seulement à l'enregistrement.
- Fréquence de changement : aucune limite, ou une limite (à titre de référence, la plateforme dont s'inspire le projet autorise deux changements par 14 jours). Une limite demanderait de conserver la date des derniers changements.
- Ancien handle : libéré immédiatement pour les autres chaînes, ou réservé pendant une durée à fixer. Une réservation demanderait de conserver les anciens handles.

#### Longueur du nom et de la description
- Option A : reprendre les limites de la création, nom de 1 à 50 caractères, description de 1000 caractères au plus.
- Option B : d'autres limites, à préciser.

#### Emplacement de l'écran dans l'application
- Option A : depuis la page de chaîne vue par son propriétaire.
- Option B : depuis l'écran Vous.
- Option C : les deux.
- Organisation : un seul écran pour les quatre éléments, ou des modifications séparées.

#### Back-office
- Le back-office modifie déjà la bannière d'une chaîne (PATCH /admin/channels/:id). Garde-t-il ce droit, et doit-il aussi pouvoir modifier la photo, le nom, la description ou le handle, par exemple pour la modération ?
- Le back-office continue dans tous les cas de passer par l'API, sans accès direct à PostgreSQL.

## Alternatives
- Laisser la chaîne figée après sa création, avec la bannière réservée au back-office : avantage, aucun travail. Inconvénient, le créateur garde l'avatar généré et ne peut corriger ni son nom ni son handle ; décision du lead développeur du 4 novembre de l'ouvrir dans l'application.
- Recadrage côté serveur par FFmpeg : avantage, même résultat quel que soit le client, et l'outil est déjà en place (ADR-011). Inconvénient, le créateur ne choisit pas la zone conservée. À arbitrer.
- Recadrage côté client : avantage, le créateur choisit la zone conservée. Inconvénient, un écran de recadrage à développer dans l'application, et le serveur doit quand même valider l'image reçue. À arbitrer.
- Réécrire la photo ou la bannière sous le même nom de fichier : avantage, pas d'ancien fichier à gérer. Inconvénient, les clients gardent l'ancienne image jusqu'à 7 jours à cause du cache de `/media` ; écarté.

## Conséquences
### Positives
- Le créateur peut donner une vraie identité visuelle à sa chaîne depuis son téléphone, sans passer par le back-office.
- Le parcours créateur montré en démonstration est plus complet : création de la chaîne, personnalisation, publication.
- L'envoi des images réutilise les règles et le mécanisme déjà en place pour les posts : validation par ffprobe, nom généré par le serveur, refus en 503 sans FFmpeg.
- Un nouveau nom de fichier à chaque changement évite qu'un client affiche l'ancienne image pendant 7 jours.
- Le changement de handle n'a pas d'effet sur les mesures d'audience : le handle n'est jamais envoyé à PostHog (ADR-006, ADR-013).

### Négatives
- Nouvelle surface d'attaque : deux nouveaux types de fichiers envoyés par les utilisateurs (photo, bannière).
- Les images occupent de l'espace dans le stockage, d'autant plus si les anciens fichiers sont conservés.
- Un handle modifiable peut changer entre deux visites : tout lien ou affichage qui s'appuierait sur le handle plutôt que sur l'identifiant de la chaîne deviendrait faux.
- Si une limite de fréquence ou une réservation des anciens handles est retenue, de nouvelles données sont à stocker, avec une migration Prisma.
- Les modèles de l'application, la page de chaîne et, selon l'arbitrage, l'écran Vous et le back-office sont à modifier.
- Plusieurs points restent à trancher avant le développement, dans un délai court (rendu le 6 novembre).

## Liens
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication depuis l'application mobile ; ses règles d'envoi de fichiers (ffprobe, nom généré par le serveur, 503 sans FFmpeg) et celles des images de posts (JPEG ou PNG, 5 Mo) servent de base à l'envoi de la photo et de la bannière. Son amendement du 4 novembre fixe la règle de rédaction des textes d'interface.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : FFmpeg en dépendance système et chemins relatifs ; la photo et la bannière sont enregistrées en chemin relatif sous `/media`, servies avec un cache de 7 jours, d'où un nouveau nom de fichier à chaque changement. FFmpeg génère déjà l'avatar par défaut, et pourrait recadrer les images selon l'arbitrage.
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement sur notre propre backend ; les images envoyées sont écrites dans le même stockage, dossier local en développement, Cloudflare R2 avec CDN au déploiement.
- [ADR-007](ADR-007-offre-premium.md) et [ADR-016](ADR-016-reglages-publication-video.md) : même principe de vérification côté serveur, ici pour réserver la modification au propriétaire de la chaîne. L'ADR-016 réserve aussi au back-office certaines modifications après publication ; la question se pose ici pour la bannière.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS, architecture hexagonale ; les règles de modification d'une chaîne appartiennent au domaine du module channels.
- [ADR-001](ADR-001-android-natif.md) : application native Android ; l'écran de personnalisation fait partie de cette application, sans composant web.
- [ADR-006](ADR-006-analytics-posthog.md) et [ADR-013](ADR-013-mesures-donnees-personnelles.md) : le handle et le nom affiché ne sont jamais envoyés à PostHog ; leur modification ne touche pas aux mesures.
