# ADR-018 — Personnalisation de sa chaîne par le créateur depuis l'application mobile

## Statut
Accepté

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

Les règles ci-dessous découlent des ADR déjà acceptés. Les décisions qui suivent ont été prises par le lead développeur le 4 novembre 2026, à l'acceptation de cet ADR : il a validé toutes les recommandations, sans exception.

### Règles qui découlent des ADR acceptés
- Fichiers envoyés : validés par ffprobe, jamais par leur extension ni par le type MIME annoncé par le client ; nom de fichier généré par le serveur, jamais repris du client ; refus en 503 avec un message clair si FFmpeg ou ffprobe est absent (ADR-011, ADR-015).
- Chemins en base : `avatarUrl` et `bannerUrl` reçoivent un chemin relatif sous `/media`, comme les autres fichiers hébergés chez nous (ADR-011).
- Cache de 7 jours : chaque nouvelle photo ou bannière est enregistrée sous un nouveau nom de fichier ; un fichier n'est jamais réécrit sous le même nom.
- Handle : la normalisation (minuscules, sans « @ ») et le motif `^[a-z0-9._-]{3,30}$` de la création s'appliquent aussi à la modification, ainsi que l'unicité sans tenir compte de la casse.
- Propriétaire : seul le propriétaire de la chaîne peut la modifier depuis l'application, et c'est vérifié par l'API, jamais seulement dans l'application (même principe qu'ADR-007 et ADR-016).
- Textes d'interface : aucun texte visible ne mentionne YouTube ni n'annonce ce qui n'est pas développé.

### Décisions du lead développeur (4 novembre 2026)

#### Formats et poids des images
1. La photo et la bannière suivent la règle des images de posts : JPEG ou PNG, 5 Mo au plus, validées par ffprobe, avec le même mécanisme d'envoi commun.

#### Recadrage
2. Recadrage côté serveur par FFmpeg, comme les miniatures (ADR-011) :
   - photo de profil : carrée, 512×512 ;
   - bannière : 2048×569, soit le rapport 3,6 de l'affichage sur la page de chaîne (`BANNER_RATIO`).

#### Suppression de l'ancien fichier
3. L'ancienne photo ou l'ancienne bannière envoyée est supprimée du stockage au remplacement, comme une miniature régénérée (ADR-011). L'avatar généré (initiales sur fond de couleur) est en revanche conservé, pour pouvoir y revenir.

#### Retrait de la photo et de la bannière
4. Le créateur peut retirer sa photo : la chaîne revient à l'avatar généré, régénéré si le nom a changé depuis (ses initiales et sa couleur viennent du nom). Le créateur peut aussi retirer sa bannière : la chaîne revient à l'absence de bannière.

#### Règles du handle
5. Handle déjà pris : la disponibilité est vérifiée à l'enregistrement, et un message clair s'affiche sous le champ.
6. Le changement de handle est illimité : aucune limite de fréquence, donc aucune date de changement à conserver.
7. L'ancien handle est libéré tout de suite pour les autres chaînes : aucune réservation, donc aucun ancien handle à conserver.

#### Longueur du nom et de la description
8. Les limites de la création s'appliquent : nom de 1 à 50 caractères, description de 1000 caractères au plus.

#### Emplacement de l'écran dans l'application
9. Un seul écran « Personnaliser la chaîne » pour les quatre éléments, ouvert depuis la page de chaîne vue par son propriétaire et depuis l'écran Vous.

#### Back-office
10. Le back-office garde la modification de la bannière (PATCH /admin/channels/:id), rien de plus : il ne modifie ni la photo, ni le nom, ni la description, ni le handle. Il continue de passer par l'API, sans accès direct à PostgreSQL.

#### Liens fondés sur le handle
11. Vérifié : toute la navigation, dans l'application comme dans le back-office, passe par l'identifiant de la chaîne et non par le handle. Changer de handle ne casse donc aucun lien.

## Alternatives
- Laisser la chaîne figée après sa création, avec la bannière réservée au back-office : avantage, aucun travail. Inconvénient, le créateur garde l'avatar généré et ne peut corriger ni son nom ni son handle ; décision du lead développeur du 4 novembre de l'ouvrir dans l'application.
- Recadrage côté serveur par FFmpeg : avantage, même résultat quel que soit le client, et l'outil est déjà en place (ADR-011). Inconvénient, le créateur ne choisit pas la zone conservée. Retenu.
- Recadrage côté client : avantage, le créateur choisit la zone conservée. Inconvénient, un écran de recadrage à développer dans l'application, et le serveur doit quand même valider l'image reçue. Écarté.
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
- Les images occupent de l'espace dans le stockage ; les anciens fichiers envoyés sont supprimés au remplacement, mais l'avatar généré est conservé.
- Un handle modifiable peut changer entre deux visites : tout lien ou affichage futur qui s'appuierait sur le handle plutôt que sur l'identifiant de la chaîne deviendrait faux. Vérifié à l'acceptation, aucune navigation actuelle ne s'appuie sur le handle.
- Les modèles de l'application, la page de chaîne et l'écran Vous sont à modifier.
- Incohérence connue : le back-office accepte une bannière en webp, contrairement à l'application (JPEG ou PNG). Laissée en l'état sur décision du lead développeur.

## Liens
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication depuis l'application mobile ; ses règles d'envoi de fichiers (ffprobe, nom généré par le serveur, 503 sans FFmpeg) et celles des images de posts (JPEG ou PNG, 5 Mo) servent de base à l'envoi de la photo et de la bannière. Son amendement du 4 novembre fixe la règle de rédaction des textes d'interface.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : FFmpeg en dépendance système et chemins relatifs ; la photo et la bannière sont enregistrées en chemin relatif sous `/media`, servies avec un cache de 7 jours, d'où un nouveau nom de fichier à chaque changement. FFmpeg génère déjà l'avatar par défaut, et recadre la photo et la bannière.
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement sur notre propre backend ; les images envoyées sont écrites dans le même stockage, dossier local en développement, Cloudflare R2 avec CDN au déploiement.
- [ADR-007](ADR-007-offre-premium.md) et [ADR-016](ADR-016-reglages-publication-video.md) : même principe de vérification côté serveur, ici pour réserver la modification au propriétaire de la chaîne. L'ADR-016 réserve aussi au back-office certaines modifications après publication ; ici, le back-office garde la modification de la bannière, rien de plus.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS, architecture hexagonale ; les règles de modification d'une chaîne appartiennent au domaine du module channels.
- [ADR-001](ADR-001-android-natif.md) : application native Android ; l'écran de personnalisation fait partie de cette application, sans composant web.
- [ADR-006](ADR-006-analytics-posthog.md) et [ADR-013](ADR-013-mesures-donnees-personnelles.md) : le handle et le nom affiché ne sont jamais envoyés à PostHog ; leur modification ne touche pas aux mesures.
