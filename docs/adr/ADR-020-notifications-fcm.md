# ADR-020 — Notifications de publication par Firebase Cloud Messaging

## Statut
Accepté

## Contexte
CLAUDE.md place les notifications hors périmètre. Le 4 novembre 2026, le lead développeur les fait entrer dans le périmètre : un abonné à une chaîne est prévenu quand elle publie une vidéo. « Notifications » sera retiré du hors-périmètre à l'acceptation de cet ADR.

Contraintes :
- une notification doit arriver application fermée ; le WebSocket existant (ADR-004, ADR-010) ne vit que pendant que l'application est ouverte ;
- l'application reste 100 % native (ADR-001) ;
- FOLLOW (abonnement gratuit) et PREMIUM_SUBSCRIPTION (abonnement payant) ne se confondent pas ; la cloche porte sur FOLLOW ;
- une vidéo privée ou non répertoriée n'apparaît dans aucune liste publique (ADR-016) ;
- la permission POST_NOTIFICATIONS est déjà déclarée et demandée pour la notification de progression du service de téléchargement (ADR-019, décision 7), au clic sur « Télécharger » dans l'écran de lecture ;
- les mesures d'audience passent par PostHog ; Firebase Analytics a été écarté (ADR-006) ;
- règle suivie depuis le début du projet : pas de bouton mort, donc pas de deux options qui font la même chose.

État du code au 4 novembre 2026 :
- FOLLOW = (userId, channelId, createdAt), sans autre champ. Routes : POST et DELETE /channels/:id/follow, GET /channels/:id (statut d'abonnement), GET /me/subscriptions.
- Une vidéo est créée par deux chemins : POST /me/videos (publication depuis l'application, ADR-015 ; jamais Premium) et POST /admin/videos (back-office ; Premium possible). Sa visibilité ne change ensuite que par PATCH /admin/videos/:id (back-office uniquement, ADR-016). Il n'existe pas de publication programmée : `publishedAt` vaut la date de création.
- La déconnexion Android efface seulement les tokens localement ; aucune route backend de déconnexion.
- Le bouton S'abonner (FollowButton) est présent sur la page de chaîne, l'écran de lecture et l'onglet Shorts.
- Fichiers Firebase en place : `android/app/google-services.json` (versionné) et `backend/firebase-service-account.json` (ignoré par git). Le SDK Firebase n'est pas encore déclaré dans Gradle.

## Décision
Envoyer les notifications de publication par Firebase Cloud Messaging (FCM), le canal du système Android, seul capable de livrer une notification application fermée.

### Cloche
- À côté du bouton S'abonner, visible seulement quand on est abonné.
- Présente sous la vidéo sur l'écran de lecture et sur la page de chaîne. Pas de cloche sur l'onglet Shorts.
- Trois états :
  - **Toutes** : notification à chaque publication, vidéos et Shorts.
  - **Personnalisées** : notification pour les vidéos classiques (format LONG) seulement, pas pour les Shorts. C'est le réglage par défaut, le comportement le plus proche de ce qu'attend un abonné qui ne veut pas être prévenu à chaque Short.
  - **Aucune** : rien.
- Chez nous, « Personnalisées » veut dire « sans les Shorts ». C'est une décision produit assumée, pas une imitation de l'algorithme de YouTube. Motif : pas de deux options qui font la même chose (règle du bouton mort).
- Le menu de la cloche contient aussi « Se désabonner », conforme à la capture chaine-08 (références : chaine-08, chaine-09, chaine-10).

### Backend
- Nouveau champ sur FOLLOW portant le choix de la cloche (Toutes, Personnalisées, Aucune), ajouté par une migration Prisma, valeur par défaut Personnalisées.
- Une route pour modifier ce choix (par exemple PATCH /channels/:id/follow).
- Nouvelle table des jetons d'appareil, liée à USER : plusieurs appareils par utilisateur. Une route pour enregistrer un jeton, une pour le retirer. Un jeton refusé par Firebase est supprimé.
- **Moment de l'envoi** : à la création d'une vidéo publique (POST /me/videos, POST /admin/videos) et au passage ultérieur d'une vidéo privée ou non répertoriée à publique (PATCH /admin/videos/:id). Rien pour une vidéo privée ou non répertoriée.
- **Jamais deux fois** : un nouveau champ sur VIDEO, la date d'envoi des notifications, ajouté par une migration Prisma, est renseigné au premier envoi. Une vidéo qui l'a déjà ne notifie plus, même si elle repasse de privée à publique.
- **Destinataires** : les abonnés de la chaîne dont la cloche est sur Toutes, quel que soit le format, plus ceux dont la cloche est sur Personnalisées si la vidéo est au format LONG. Le créateur est exclu.
- **Vidéo Premium** (isPremium) : tous les abonnés sont notifiés, Premium ou non. La vidéo est visible dans le fil avec son badge Premium ; l'accès à la lecture reste vérifié côté serveur (ADR-007).
- **Contenu** : titre = « <Nom de la chaîne> a publié une nouvelle vidéo », ou « <Nom de la chaîne> a publié un nouveau Short » selon le format ; texte = titre de la vidéo ; données = identifiant de la vidéo ; image = miniature, toujours affichée en grande image quand on déplie la notification.
- **Type de message FCM** : le serveur envoie un message de données seulement (`data` : `type=video_published`, `videoId`, `channelName`, `title`, `format` (LONG ou SHORT, pour choisir le titre), `thumbnailUrl` ; `android.priority=high`), et c'est l'application (FirebaseMessagingService) qui construit la notification. Raison : la miniature est stockée en chemin relatif (`/media/...`) et seule l'application connaît l'adresse du serveur pour la compléter (`resolveMediaUrl`). Le contenu reste celui décidé ci-dessus.
- En production, on enverrait un message de notification avec une URL absolue de la miniature, ce qui suppose que le serveur connaisse sa propre adresse publique.
- **Envoi en arrière-plan**, après la réponse de la publication : la publication ne peut jamais échouer à cause de lui. En cas d'échec, on journalise.
- **Clé** : lue via la variable d'environnement `FIREBASE_SERVICE_ACCOUNT`, qui donne le chemin du fichier (`backend/firebase-service-account.json`, jamais versionné). Variable ou fichier absents : démarrage normal, envoi désactivé et journalisé, comme le repli Stripe (ADR-014).

### Android
- SDK Firebase Messaging (sans Firebase Analytics, écarté par l'ADR-006).
- Permission POST_NOTIFICATIONS demandée à la première activation d'une cloche, pas au démarrage. C'est la même permission que celle des téléchargements (ADR-019) : déjà accordée, elle n'est pas redemandée.
- Permission refusée : la cloche change quand même d'état, avec un court message proposant d'ouvrir les réglages.
- Jeton de l'appareil enregistré auprès du backend à la connexion. Jeton renouvelé par Firebase (onNewToken) : réenregistré à chaque changement si un utilisateur est connecté.
- À la déconnexion : retrait du jeton côté serveur tenté, et suppression locale du jeton (FirebaseMessaging deleteToken), qui couvre la déconnexion sans réseau.
- Un appui sur la notification ouvre directement la vidéo.

### Décisions du lead développeur (4 novembre 2026)
1. « Personnalisées » = vidéos classiques (LONG) seulement, sans les Shorts ; « Toutes » = vidéos et Shorts ; « Aucune » = rien. Décision produit assumée.
2. Notification à la création publique et au passage ultérieur à publique ; une date d'envoi sur VIDEO empêche toute double notification.
3. Vidéo Premium : tous les abonnés sont notifiés.
4. Shorts traités selon la cloche ; pas de cloche sur l'onglet Shorts.
5. Envoi en arrière-plan après la réponse de publication ; échec journalisé.
6. Clé lue via `FIREBASE_SERVICE_ACCOUNT` (chemin du fichier) ; absente : envoi désactivé et journalisé, démarrage normal.
7. Permission refusée : la cloche change d'état, avec un message proposant d'ouvrir les réglages.
8. Le menu de la cloche contient « Se désabonner ».
9. Jeton renouvelé : réenregistré à chaque changement si un utilisateur est connecté.
10. Déconnexion : retrait serveur tenté et deleteToken local.

## Alternatives
- Notification locale déclenchée par le WebSocket existant : avantage, aucune dépendance nouvelle. Inconvénient, le WebSocket n'existe que tant que l'application est ouverte ; une notification doit arriver application fermée.
- OneSignal : envisagé puis écarté ; il ne remplace pas FCM sur Android, il s'ajoute par-dessus.

## Conséquences
### Positives
- Un abonné est prévenu des nouvelles vidéos, même application fermée.
- Les trois états de la cloche ont chacun un effet distinct : aucun réglage mort.
- Les règles de visibilité (ADR-016) s'appliquent aux notifications : aucune vidéo privée ou non répertoriée n'est annoncée, et une vidéo ne notifie jamais deux fois.
- Une panne de Firebase ou une clé absente ne bloque jamais une publication.
- Plusieurs appareils par utilisateur sont pris en charge ; les jetons morts sont nettoyés ; une déconnexion sans réseau coupe quand même les notifications sur l'appareil.

### Négatives
- Dépendance à un service Google ; l'appareil doit avoir les services Google Play (un émulateur sans Google Play ne reçoit rien).
- Trois changements de schéma Prisma : champ sur FOLLOW, table des jetons d'appareil, date d'envoi sur VIDEO.
- Une clé secrète de plus à gérer côté backend, hors de git.
- Trois chemins doivent déclencher l'envoi : POST /me/videos, POST /admin/videos et PATCH /admin/videos/:id.
- « Personnalisées » s'écarte du sens qu'il a sur YouTube ; à expliquer à l'oral et dans l'aide (ADR-019).
- Un abonné non Premium peut être notifié d'une vidéo qu'il ne peut pas lire.
- Un message de données est moins bien garanti qu'un message de notification : il peut être retardé ou perdu sous les économiseurs de batterie agressifs de certains constructeurs.
- La permission POST_NOTIFICATIONS sert désormais deux usages (téléchargements, cloche).
- CLAUDE.md est mis à jour par un humain à l'acceptation : notifications retirées du hors-périmètre, champ de FOLLOW, table des jetons et date d'envoi sur VIDEO ajoutés au modèle de données, Firebase Messaging ajouté à la stack.

## Liens
- [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) : décision 7, permission POST_NOTIFICATIONS déjà demandée pour le service de téléchargement ; elle n'était pas la fonctionnalité « notifications », qui entre ici dans le périmètre. L'écran d'aide doit décrire la cloche.
- [ADR-016](ADR-016-reglages-publication-video.md) : visibilité ; pas de notification pour une vidéo privée ou non répertoriée ; passage à publique depuis le back-office seulement.
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication depuis l'application, l'un des chemins qui déclenchent l'envoi.
- [ADR-007](ADR-007-offre-premium.md) : vidéos exclusives ; notifiées à tous, lecture toujours vérifiée côté serveur.
- [ADR-006](ADR-006-analytics-posthog.md) : Firebase Analytics écarté ; seul Firebase Messaging est ajouté.
- [ADR-004](ADR-004-temps-reel-websocket.md), [ADR-010](ADR-010-websocket-salles-compteurs.md) : WebSocket, écarté pour les notifications car limité à l'application ouverte.
- [ADR-014](ADR-014-paiement-stripe-mode-test.md) : repli quand la clé est absente, repris pour `FIREBASE_SERVICE_ACCOUNT`.
- [ADR-001](ADR-001-android-natif.md), [ADR-003](ADR-003-backend-nestjs.md) : application native, backend hexagonal (module channels pour la cloche).

Amendé le 4 novembre : précision sur le type de message FCM retenu à l'implémentation (commit 07efd87). Le serveur envoie un message de données seulement, et l'application construit la notification, car la miniature est stockée en chemin relatif et seule l'application connaît l'adresse du serveur pour la compléter. Limite : un message de données peut être retardé ou perdu sous les économiseurs de batterie agressifs de certains constructeurs ; en production, on enverrait un message de notification avec une URL absolue de la miniature. Raison : précision demandée par le lead développeur sur le type de message FCM retenu à l'implémentation et sa limite.

Amendé le 4 novembre : titre de la notification changé (commit 90497b9). Il devient « <Nom de la chaîne> a publié une nouvelle vidéo » ou « <Nom de la chaîne> a publié un nouveau Short » selon le format, au lieu du seul nom de la chaîne ; le texte reste le titre de la vidéo, et la miniature s'affiche toujours en grande image quand on déplie la notification. Le format de la vidéo (LONG ou SHORT) s'ajoute donc aux données du message FCM. Raison : ajustement demandé par le lead développeur pour que la notification dise de quoi il s'agit.

Amendé le 5 novembre : la permission POST_NOTIFICATIONS est aussi demandée à l'étape « notifications » du parcours d'accueil (ADR-028). Raison : exigence du sujet demandée par le lead développeur. La cloche et les téléchargements gardent leur comportement, et une permission déjà accordée n'est pas redemandée.
