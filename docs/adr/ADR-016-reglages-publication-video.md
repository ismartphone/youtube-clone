# ADR-016 — Réglages de publication d'une vidéo : visibilité et commentaires

## Statut
Proposé

## Contexte
L'ADR-015 permet à un créateur de publier une vidéo ou un Short depuis l'application mobile. L'écran de publication ne propose que l'aperçu, le titre, la description et la catégorie : toute vidéo publiée est publique et ouverte aux commentaires.

YouTube propose de nombreux réglages à la publication. Il faut choisir lesquels reprendre.

Contraintes :
- le rendu est le 6 novembre 2026, le temps est limité ;
- seuls les réglages qui portent une vraie règle d'accès sont retenus ; un réglage purement décoratif n'apporte rien à la démonstration ;
- comme pour l'accès Premium (ADR-007), une règle d'accès est toujours vérifiée côté serveur, jamais seulement dans l'application ;
- le back-office ne contacte jamais PostgreSQL directement : tout passe par l'API ;
- le backend suit une architecture hexagonale (ADR-003) : les règles d'accès appartiennent au domaine ;
- les commentaires sont publiés par l'API REST et diffusés par la passerelle WebSocket (ADR-004, ADR-010).

## Décision
Ajouter deux réglages de publication à une vidéo (vidéo classique comme Short) : la visibilité et l'activation des commentaires.

### Visibilité
- Nouveau champ sur VIDEO, à trois valeurs : Publique, Non répertoriée, Privée. Il est ajouté par une migration Prisma, avec la valeur par défaut Publique.
- Une vidéo non publique (non répertoriée ou privée) n'apparaît dans aucune liste publique : fil d'accueil, recherche, fil d'abonnements, onglet Shorts, suggestions, page de chaîne vue par un autre utilisateur.
- Une vidéo non répertoriée reste accessible par son lien direct (GET /videos/:id), sans apparaître dans les listes.
- Une vidéo privée n'est accessible que par son propriétaire. Le back-office administrateur garde l'accès, pour la modération.
- Le propriétaire voit toujours ses vidéos sur sa page de chaîne, quelle que soit leur visibilité, avec une pastille indiquant l'état.

### Commentaires
- Nouveau champ sur VIDEO : commentaires activés ou désactivés, ajouté par la même migration Prisma, avec la valeur par défaut activés.
- Commentaires désactivés : le bloc commentaires n'apparaît pas dans l'application, et l'API refuse la publication d'un commentaire. Le refus vaut pour tout chemin de publication : la route REST POST /videos/:id/comments (seul chemin d'envoi aujourd'hui, ADR-010) comme la passerelle WebSocket (ADR-004) si elle venait à recevoir des commentaires.

### Vérification côté serveur
Toutes ces règles sont vérifiées par l'API, comme l'accès aux vidéos exclusives (ADR-007). L'application et le back-office se contentent d'afficher ce que l'API leur renvoie.

### Application mobile
Les deux réglages sont choisis sur l'écran de publication de l'ADR-015. Ils sont présentés dans une liste repliée sous le titre et la description, et non tous dépliés.

### Back-office
Le back-office peut lire et modifier les deux réglages d'une vidéo, en passant par l'API.

### Réglages mis de côté
Les cinq autres réglages proposés par YouTube sont volontairement mis de côté : audience « contenu destiné aux enfants », tags, lieu, promotion rémunérée, mention « contenu généré ou modifié par IA ». Ce sont des champs d'affichage sans règle d'accès. Ils pourront être repris s'il reste du temps. Il s'agit d'un choix de périmètre, non d'un oubli.

### Réglages exclus définitivement
- Playlists : hors périmètre du projet, et elles demanderaient une nouvelle entité.
- Collaborations et remix : sans signification dans notre modèle de données.

Un bouton qui ne fait rien se remarque en démonstration : ces réglages ne sont donc pas affichés du tout.

## Alternatives
- Un simple booléen « public » au lieu de trois états : avantage, plus simple. Inconvénient, il ne distingue pas une vidéo partageable par lien (non répertoriée) d'une vidéo réservée à son propriétaire (privée).
- Filtrer les vidéos non publiques dans l'application : avantage, aucune modification des requêtes du backend. Inconvénient, l'API renverrait quand même les vidéos privées à n'importe quel client ; contraire à la règle selon laquelle l'accès est toujours vérifié côté serveur (ADR-007).
- Supprimer la vidéo au lieu de la rendre privée : avantage, aucun nouveau champ. Inconvénient, la suppression est définitive ; on perd le fichier, les commentaires, les « j'aime » et le compteur de vues, sans retour possible.
- Masquer seulement le bloc commentaires dans l'application, sans refus de l'API : avantage, aucune règle côté serveur. Inconvénient, un client qui appelle l'API directement peut toujours commenter.
- Reprendre tous les réglages de YouTube : avantage, écran de publication plus réaliste. Inconvénient, la plupart sont des champs d'affichage sans règle d'accès, coûteux à développer dans le délai, et certains n'ont aucun sens dans notre modèle.

## Conséquences
### Positives
- Un créateur peut publier une vidéo sans la rendre visible de tous, et la partager par lien.
- Un créateur peut fermer les commentaires d'une vidéo.
- Les règles sont vérifiées par l'API : un client modifié ne peut ni lister ni lire une vidéo privée, ni commenter une vidéo aux commentaires désactivés.
- L'écran de publication reste léger : deux réglages seulement, repliés sous le titre et la description.
- Le périmètre retenu et écarté est documenté, ce qui permet de le justifier à l'oral.

### Négatives
- Chaque requête de liste publique doit filtrer sur la visibilité. Oublier une seule liste (fil d'accueil, recherche, abonnements, Shorts, suggestions, page de chaîne) laisse fuiter des vidéos non publiques.
- Chaque lecture d'une vidéo (GET /videos/:id) doit vérifier la visibilité et l'identité du demandeur, en plus de l'accès Premium.
- Si un cache Redis des listes est ajouté plus tard, il devra tenir compte de la visibilité, et être invalidé quand elle change.
- Une nouvelle migration Prisma sur la table VIDEO, avec deux champs.
- Les index existants des listes (fil d'accueil, Shorts, page de chaîne) ne portent pas sur la visibilité ; ils restent utilisables, mais le filtrage s'ajoute à chaque requête.
- L'écran de publication de l'application, la page de chaîne (pastille) et l'écran de lecture (bloc commentaires) doivent être modifiés, ainsi que le back-office.

## Liens
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication depuis l'application mobile ; c'est sur son écran de publication que les deux réglages sont ajoutés, ce qui complète la liste de ses champs (aperçu, titre, description, catégorie). Sa phrase « la vidéo publiée apparaît dans le fil et sur la page de la chaîne » ne vaut plus que pour une vidéo publique.
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; même principe de vérification de l'accès par l'API, jamais seulement dans l'application. Ses liens énumèrent les champs de l'écran de publication mobile, que cet ADR complète.
- [ADR-004](ADR-004-temps-reel-websocket.md) : commentaires en temps réel par une passerelle WebSocket NestJS ; le refus des commentaires désactivés vaut aussi pour elle.
- [ADR-010](ADR-010-websocket-salles-compteurs.md) : salles par vidéo ; les commentaires sont publiés par la route REST, c'est donc là que le refus s'applique.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS, architecture hexagonale ; les règles de visibilité et de commentaires appartiennent au domaine du module videos.
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement des vidéos ; sa recherche par requête SQL simple doit filtrer les vidéos non publiques.
