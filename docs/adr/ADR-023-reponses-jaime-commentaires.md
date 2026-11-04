# ADR-023 — Réponses et « j'aime » sur les commentaires

## Statut
Accepté

## Contexte
Les commentaires sont plats aujourd'hui : on ne peut ni répondre à un commentaire, ni l'aimer. Le 4 novembre 2026, le lead développeur demande les réponses, les « j'aime » sur les commentaires, le cœur du créateur et deux tris.

État du code au 4 novembre 2026 :
- **Modèle :** `Comment { id, videoId, userId, content, createdAt }`, index (`videoId`, `createdAt` desc).
- **Lecture :** GET /videos/:videoId/comments, ouverte aux visiteurs, paginée par `page`/`pageSize`. Elle renvoie `total`, affiché comme nombre de commentaires sous la vidéo, et `commentsDisabled` (ADR-016).
- **Publication :** POST /videos/:videoId/comments, qui diffuse `comment:new` dans la salle de la vidéo (ADR-010). Un commentaire désactivé est refusé avec `CommentsDisabledError` (403).
- **Visibilité :** `canReachComments` applique la visibilité de la vidéo (ADR-016).
- **Compteur de « j'aime » des vidéos :** tampon Redis reporté en base toutes les 30 s, et diffusé en temps réel (ADR-004, ADR-010). « J'aime » et « Je n'aime pas » s'excluent via `LIKE.type` (ADR-019).
- **@handle :** il appartient à la chaîne (`Channel.handle`). Un utilisateur sans chaîne n'a pas de @handle.

## Décision

### Réponses
- Un seul niveau. Répondre à une réponse place la nouvelle réponse dans le même fil : son parent est le commentaire de premier niveau, comme sur YouTube.
- Sous chaque commentaire : « Répondre », et « N réponses », qui déplie le fil.
- Le champ de réponse préremplit le @handle de la personne à qui on répond. Si elle n'a pas de chaîne, donc pas de @handle, il préremplit son nom affiché suivi d'une espace : on voit ainsi à qui on répond. C'est du texte simple : ni lien, ni notification.
- Le compteur de commentaires de la vidéo (`total`) compte commentaires et réponses.
- **Modèle :** champ `parentId` facultatif sur COMMENT, qui renvoie à un COMMENT de la même vidéo, avec un index (`parentId`, `createdAt`). Le domaine remonte toujours au premier niveau : si le parent visé est lui-même une réponse, `parentId` prend le parent de celui-ci. Une migration Prisma.
- **Routes :** POST /videos/:videoId/comments accepte un `parentId` facultatif. GET /comments/:id/replies renvoie 10 réponses par page, avec « Afficher plus de réponses » dans l'application. La liste de la vidéo ne renvoie que les commentaires de premier niveau, chacun avec son nombre de réponses.
- Visibilité (ADR-016) et commentaires désactivés s'appliquent aux réponses comme aux commentaires.

### « J'aime » sur un commentaire
- Sous chaque commentaire et chaque réponse : un pouce avec son compteur, et un pouce vers le bas sans compteur, comme pour les vidéos (ADR-019).
- **Stockage :** nouvelle table COMMENT_LIKE (`userId`, `commentId`, `type` = `LIKE` ou `DISLIKE`), clé primaire composée (`userId`, `commentId`). C'est la contrainte d'unicité demandée.
- Le pouce vers le bas est donc stocké, sur le modèle de LIKE (ADR-019, option A). L'exclusion entre les deux est garantie par la base, et l'état s'affiche au retour sur l'écran. Il n'est jamais compté ni affiché.
- Un visiteur qui appuie est invité à se connecter.
- Routes sur le modèle de celles des vidéos : POST et DELETE /comments/:id/like, POST et DELETE /comments/:id/dislike.

### Compteur de « j'aime » des commentaires

Option A, colonne `likeCount` sur COMMENT, mise à jour dans la même transaction que la ligne COMMENT_LIKE :
- Avantages : toujours exacte ; indexable pour le tri « Top » ; aucun Redis.
- Inconvénient : une écriture de plus par « j'aime ».

Option B, tampon Redis reporté en base toutes les 30 s, comme les vidéos :
- Avantage : absorbe des rafales.
- Inconvénients : le tri « Top » se ferait sur une valeur en retard sur l'affichage ; plus de code. Le tampon des vidéos n'existe que pour le temps réel, absent ici.

Option C, compter les lignes à la lecture :
- Avantage : aucune colonne.
- Inconvénient : tri « Top » coûteux, puisqu'il faut compter pour chaque commentaire avant de trier.

**Option A retenue** (recommandation validée à l'acceptation). Les « j'aime » de commentaires ne passent pas par le temps réel, et le volume du projet est petit : un compteur en base suffit.

### Cœur du créateur
- Le propriétaire de la chaîne peut mettre un cœur sur un commentaire de sa vidéo, réponses comprises.
- Affichage comme sur YouTube : l'avatar du créateur avec un petit cœur, sous le commentaire.
- **Modèle :** champ `creatorHeartedAt` facultatif sur COMMENT (date ou rien).
- **Routes :** PUT et DELETE /comments/:id/heart, refusées à toute personne qui n'est pas propriétaire de la chaîne de la vidéo.

### Tri
- « Top commentaires », par défaut : « j'aime » décroissants. « Les plus récents » : date décroissante.
- **Départage à égalité :**
  - Top : date décroissante, puis `id`, pour un ordre total et stable.
  - Récents : `id` en second critère.
- Index (`videoId`, `parentId`, `likeCount` desc, `createdAt` desc) pour le tri Top.
- **Pagination du tri Top :** les compteurs bougent entre deux pages. On garde la pagination par page actuelle, et l'application retire les doublons (par `id`).
  - **Limite assumée :** un commentaire qui monte entre deux pages peut être sauté, ou ne jamais s'afficher, jusqu'au rechargement de la liste.
  - Option écartée : la pagination par curseur (`likeCount`, `createdAt`, `id`). Avec un catalogue petit, ce serait du travail pour rien.
- Les réponses d'un fil sont affichées de la plus ancienne à la plus récente.

### Temps réel
- Une réponse publiée arrive en direct dans la salle de la vidéo.
- Nouvel événement `comment:reply`, avec `parentId`. Il est sans ambiguïté, au prix d'un cas de plus dans la passerelle et dans `LiveVideoClient`.
- Option écartée : réutiliser `comment:new` avec `parentId`. Un client qui ignore `parentId` afficherait la réponse comme un commentaire de premier niveau.
- Le compteur de commentaires de la vidéo augmente aussi à la réception d'une réponse.
- Les « j'aime » de commentaires et le cœur ne passent pas par le temps réel.

### Commentaires désactivés (ADR-016)
- Ils bloquent aussi les réponses et les « j'aime », côté API (403, `CommentsDisabledError`) comme côté écran.
- Le cœur du créateur est bloqué aussi, sans exception.

### Décisions du lead développeur (4 novembre 2026)
1. Auteur sans chaîne : le champ de réponse préremplit son nom affiché suivi d'une espace, au lieu du @handle. Mieux que rien, et on voit à qui on répond.
2. Pagination du tri Top : pages actuelles, doublons retirés côté application. Le catalogue est petit : un curseur serait du travail pour rien. Limite : un commentaire qui monte entre deux pages peut être sauté, ou ne jamais s'afficher.
3. Réponses d'un fil : de la plus ancienne à la plus récente.
4. Cœur du créateur : possible aussi sur les réponses. Affichage à la YouTube : l'avatar du créateur avec un petit cœur, sous le commentaire.
5. Commentaires désactivés : le cœur est bloqué aussi, sans exception.
6. Événement temps réel : `comment:reply`.
7. 10 réponses par page, avec « Afficher plus de réponses ».

## Alternatives
- **Réponses imbriquées sur plusieurs niveaux.** Avantage : conversations plus fidèles. Inconvénient : affichage profond illisible sur mobile ; le lead a choisi un seul niveau.
- **Table séparée pour les réponses.** Avantage : la liste de premier niveau n'a rien à filtrer. Inconvénient : « j'aime », cœur, visibilité et temps réel doublés ; un `parentId` sur COMMENT suffit.
- **« J'aime » des commentaires dans la table LIKE**, avec une cible vidéo ou commentaire. Avantage : une seule table. Inconvénient : une clé étrangère facultative de chaque côté et des filtres partout ; LIKE porte déjà la règle des vidéos.
- **Pouce vers le bas non stocké (purement visuel).** Avantage : rien à écrire. Inconvénient : état perdu au rechargement ; contraire au comportement des vidéos (ADR-019).

## Conséquences
### Positives
- De vraies conversations sous les vidéos, et un tri qui fait remonter les meilleurs commentaires.
- Exclusion « j'aime » / « je n'aime pas » garantie par la base, comme pour les vidéos.
- Compteur exact en base, sans Redis ni temps réel à maintenir.
- Commentaires désactivés et visibilité couvrent tout, réponses et « j'aime » compris.

### Négatives
- Deux migrations Prisma : `parentId`, `likeCount` et `creatorHeartedAt` sur COMMENT, et la table COMMENT_LIKE. CLAUDE.md est mis à jour par un humain à l'acceptation (COMMENT_LIKE au modèle de données).
- `total` change de sens : il compte aussi les réponses.
- Le tri Top paginé par page peut sauter un commentaire dont le compteur monte entre deux pages, voire ne jamais l'afficher avant un rechargement.
- Nouveaux routes et nouvel événement temps réel ; l'écran des commentaires (feuille et section) est largement repris.
- Le @handle n'existe que pour les auteurs qui ont une chaîne ; pour les autres, la réponse est préremplie avec le nom affiché.
- L'aide (ADR-019) doit décrire les réponses, les « j'aime » et le cœur.

## Liens
- [ADR-010](ADR-010-websocket-salles-compteurs.md) : `comment:new` dans la salle de la vidéo ; nouvel événement pour les réponses. Les « j'aime » de commentaires n'y passent pas.
- [ADR-016](ADR-016-reglages-publication-video.md) : commentaires désactivés et visibilité, étendus aux réponses et aux « j'aime ».
- [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) : modèle LIKE / DISLIKE exclusifs et pouce vers le bas sans compteur, repris pour les commentaires.
- [ADR-004](ADR-004-temps-reel-websocket.md) : compteur Redis des vidéos, non repris ici.
- [ADR-003](ADR-003-backend-nestjs.md) : module `comments`, architecture hexagonale.
