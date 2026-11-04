# ADR-025 — Messagerie entre utilisateurs : conversations à deux, partage de vidéos, notifications

## Statut
Accepté

## Contexte
Le 4 novembre 2026, le lead développeur demande une messagerie entre utilisateurs : conversations à deux, partage de vidéos dans une conversation, notifications des messages reçus. Il veut faire un point après l'étape 1 : les trois étapes doivent être livrables séparément, et l'étape 1 doit se démontrer seule.

État du code au 4 novembre 2026 :
- **Passerelle WebSocket (ADR-004, ADR-010)** : `backend/src/modules/realtime`, chemin `/realtime`.
  - Le JWT est vérifié une seule fois, au handshake ; un visiteur se connecte sans jeton.
  - Messages du client : `join` et `leave`, avec un `videoId`.
  - Salles par vidéo, en mémoire (`InMemoryVideoRooms`), gardées par `VideoRoomGuardService`, qui refuse la salle d'une vidéo privée à tout autre que son propriétaire (ADR-016).
  - La connexion ne sert qu'à recevoir : commentaires et « j'aime » sont envoyés par REST, puis diffusés (`VideoRoomPublisher`).
- **Android** : `LiveVideoClient` n'ouvre la connexion que tant que l'écran de lecture la demande.
- **Notifications (ADR-020)** : module `notifications`. Envoi FCM de messages de données (`firebase-push-sender.ts`), jetons dans `device_tokens`, envoi en arrière-plan, échec journalisé.
- **Partage** : `presentation/common/ShareVideo.kt` ouvre directement le partage Android (titre et nom de la chaîne). Il est appelé depuis l'écran de lecture et l'onglet Shorts. Les posts n'ont pas de bouton Partager et ne contiennent pas de vidéo : POST = texte et image facultative.
- **Paramètres** : `SettingsScreen.kt` contient la lecture automatique et le partage des statistiques d'usage.
- **@handle** : il appartient à la chaîne (`Channel.handle`, unique), pas au compte. Un utilisateur sans chaîne n'a pas de @handle et ne serait pas trouvable par une recherche par @handle : d'où la recherche par nom affiché (décision 1).

## Décision

### Choix de périmètre : pas de demande d'ami
- On démarre une conversation avec n'importe quel utilisateur trouvé par la recherche, sans invitation ni acceptation.
- Conséquences assumées :
  - n'importe qui peut écrire à n'importe qui ;
  - il n'y a ni blocage, ni signalement (le signalement est hors périmètre dans CLAUDE.md), ni filtrage des messages indésirables.
- C'est un choix de périmètre pour la démonstration, pas un oubli.

### Choix de périmètre : recherche par nom affiché, pas de handle sur les comptes
- On n'ajoute pas de handle aux comptes (décision du lead). Le @handle reste celui de la chaîne.
- La recherche d'un destinataire se fait sur le nom affiché (`USER.displayName`). Chaque résultat montre :
  - l'avatar ;
  - le nom ;
  - le @handle de la chaîne quand l'utilisateur en a une, pour départager les homonymes.
- Un utilisateur sans chaîne est donc trouvable et peut écrire et recevoir des messages. Chez l'autre, il apparaît avec le nom et l'avatar de son compte.
- C'est un choix de périmètre, comme l'absence de demande d'ami. Conséquence : deux homonymes sans chaîne ne se distinguent que par leur avatar.

### Étape 1 — conversations et messages (se démontre seule)

**Données :**
- CONVERSATION (`id`, `userAId`, `userBId`, `lastMessageAt`, `createdAt`), à deux personnes seulement.
  - Unicité de la paire : les deux identifiants sont rangés dans l'ordre (le plus petit en A).
  - Contrainte unique (`userAId`, `userBId`) : une seule conversation par paire, et pas de conversation avec soi-même.
- MESSAGE (`id`, `conversationId`, `senderId`, `content`, `createdAt`), index (`conversationId`, `createdAt` desc). Un message fait 2000 caractères au plus, limite vérifiée par l'API.
- Suppression et modification d'un message envoyé : hors périmètre. Masquer une conversation de la liste : hors périmètre aussi.
- **Lu / non lu** :
  - Option a, une date de dernière lecture par participant sur CONVERSATION (`readAtA`, `readAtB`). Avantages : une seule écriture à l'ouverture ; le nombre de non-lus se calcule par comparaison de dates.
  - Option b, un champ `readAt` sur chaque MESSAGE. Avantage : précision par message (« vu »). Inconvénient : une écriture par message lu, pour un « vu » non demandé.
  - **Option a retenue** (recommandation validée à l'acceptation).
- Une migration Prisma. Nouveau module backend `messaging`.

**Routes (REST) :**
- GET /me/conversations : liste paginée, triée par dernier message.
- POST /me/conversations : avec un `userId`, renvoie la conversation existante ou la crée.
- GET /conversations/:id/messages : paginée.
- POST /conversations/:id/messages : envoi.
- POST /conversations/:id/read : marquage comme lu à l'ouverture.
- GET /me/conversations/unread-count : total des non-lus, pour la pastille.
- GET /users/search?q=… : recherche par nom affiché, réservée aux comptes connectés. Elle renvoie l'identifiant, le nom, l'avatar et le @handle de la chaîne s'il existe. Elle exclut l'utilisateur lui-même.
- Toutes ces routes vérifient que l'utilisateur est l'un des deux participants. Pour un tiers, la conversation est traitée comme inexistante (404), comme une vidéo privée (ADR-016).

**Envoi :** il reste en REST, comme les commentaires (ADR-010).
- Avantages : validation, enregistrement, réponse d'erreur et notification se font au même endroit, au même format que le reste de l'API ; la WebSocket reste en réception seule.
- Inconvénient : un aller-retour HTTP par message, sans conséquence à cette échelle.

**Salles généralisées sans casser celles des vidéos :**
- Option 1, une seconde passerelle. Écartée par le lead.
- Option 2, un nouveau type de salle sur la même passerelle :
  - `join`/`leave` acceptent soit `{ videoId }` (inchangé), soit `{ conversationId }` ;
  - la clé de salle est préfixée (`video:<id>`, `conversation:<id>`) dans `InMemoryVideoRooms`, renommé en salles génériques ;
  - un garde par type de salle : celui des vidéos reste tel quel ; celui des conversations refuse tout `join` d'un visiteur ou d'un non-participant, sans révéler l'existence de la conversation.
  - Les messages vidéo existants (`comment:new`, `views:update`, `likes:update`) ne changent pas ; l'application en place n'est pas touchée.
- **Option 2 retenue** (recommandation validée à l'acceptation).
- Nouvel événement serveur `message:new`, diffusé dans la salle de la conversation.

**Pastille de non-lus et arrivée en direct (Android) :**
- **Pas de connexion temps réel permanente** (décision du lead) : elle est coûteuse pour peu.
- Le nombre de non-lus (GET /me/conversations/unread-count) est relu à l'ouverture de l'application et à chaque retour au premier plan.
- **Temps réel seulement quand un écran de messagerie est ouvert :**
  - l'écran d'une conversation rejoint la salle de cette conversation et reçoit `message:new` ;
  - l'écran des conversations doit recevoir les nouveaux messages de toutes les conversations.
- Pour l'écran des conversations : une salle par utilisateur, `user:<id>`, rejointe tant que l'écran est ouvert. Le serveur y publie aussi chaque `message:new` du destinataire. Son garde n'admet que l'utilisateur lui-même.
  - Avantages : un seul `join` ; une nouvelle conversation créée par l'autre arrive aussi.
  - Option écartée : rejoindre la salle de chaque conversation affichée. Autant de `join` que de conversations, et une nouvelle conversation n'arriverait pas en direct.
- Ailleurs dans l'application, la pastille n'est pas en direct : c'est accepté par le lead.
- Comme aujourd'hui, le jeton n'est vérifié qu'au handshake. Les connexions restent limitées à la durée d'un écran.

**Écrans :**
- Icône de messagerie dans la barre du haut, à gauche de l'avatar, avec la pastille du nombre de messages non lus.
- Liste des conversations : avatar, nom, dernier message, date, pastille de non-lu.
- Conversation : bulles et champ de saisie en bas. Marquage comme lu à l'ouverture, et à chaque message reçu pendant que l'écran est ouvert.
- Nouvelle conversation : recherche d'un utilisateur par nom affiché. Chaque résultat montre l'avatar, le nom, et le @handle de la chaîne s'il y en a une.
- Un visiteur qui touche l'icône est invité à se connecter.

### Étape 2 — partage d'une vidéo
- Le bouton Partager d'une vidéo (écran de lecture, Shorts) ouvre d'abord une feuille « Envoyer à ». Elle montre les 5 conversations les plus récentes, puis un lien pour chercher quelqu'un d'autre, puis le partage Android habituel (`shareVideo`).
- **Modèle :** champ `videoId` facultatif sur MESSAGE (contenu texte facultatif quand une vidéo est jointe), avec une migration.
- **Carte dans la conversation :** miniature, titre et nom de la chaîne ; un appui ouvre la vidéo.
- **La carte est construite par le serveur à la lecture des messages, jamais figée au moment du partage :**
  - vidéo devenue privée (pour quelqu'un d'autre que son propriétaire) ou supprimée : carte « Vidéo indisponible », sans miniature ni titre, pour ne rien révéler (ADR-016) ;
  - vidéo non répertoriée : visible, puisque le partage par lien est son usage (ADR-016) ;
  - vidéo Premium : la carte s'affiche, et l'accès est vérifié par le serveur à l'ouverture (ADR-007). Un non-Premium arrive sur le paywall, comme aujourd'hui.
- Un Short partagé s'ouvre dans le lecteur des Shorts.
- **Posts :** hors périmètre pour l'instant. Ils n'ont pas de bouton Partager, et en ajouter un ouvrirait un autre chantier.

### Étape 3 — notification d'un message
- Message reçu : notification FCM selon le modèle de l'ADR-020 (message de données, `device_tokens`, envoi en arrière-plan, échec journalisé, jamais d'échec de l'envoi du message).
- Titre : nom de l'expéditeur. Texte : le message, ou « a partagé une vidéo ». Données : `type=message_received` et `conversationId`. Un appui ouvre directement la conversation.
- **Conversation déjà ouverte :** le serveur envoie toujours la notification. C'est l'application qui ne l'affiche pas si cette conversation précise est à l'écran. Le serveur n'a pas à suivre qui regarde quoi, et cela fonctionne aussi en arrière-plan.
- **Réglage « Notifications de messages » dans Paramètres :** stocké côté serveur (champ sur USER, migration, route pour le modifier). C'est le serveur qui décide d'envoyer, comme pour l'ADR-020, et le réglage suit le compte sur tous ses appareils.
  - Option écartée : un réglage dans l'application (DataStore), qui ignorerait la notification reçue. L'envoi aurait lieu quand même, et le réglage serait par appareil.
- **Plusieurs comptes (ADR-022, accepté) :** le jeton FCM de l'appareil n'appartient qu'au compte actif. Seul le compte actif reçoit les notifications de messages. Un message pour un compte inactif de l'appareil n'est pas notifié ; il se voit à la prochaine bascule, par la pastille.

### Mise en œuvre
- Ordre retenu par le lead : ADR-022 (comptes), ADR-023 (commentaires), puis l'étape 1 de cette messagerie, sur `main`, puis ADR-024 (mini-lecteur, sur sa branche dédiée).
- Le lead fait un point après l'étape 1, qui se démontre seule. Les étapes 2 et 3 n'ajoutent que des éléments : `videoId` sur MESSAGE et la carte pour l'étape 2, l'envoi FCM et le réglage pour l'étape 3. Elles ne modifient pas l'étape 1.

### Décisions du lead développeur (4 novembre 2026)
1. Pas de handle sur les comptes. La recherche d'un destinataire se fait sur le nom affiché ; le résultat montre l'avatar, le nom, et le @handle de la chaîne s'il y en a une. Choix de périmètre.
2. Pas de connexion temps réel permanente. Le nombre de non-lus est relu à l'ouverture de l'application et à chaque retour au premier plan. Temps réel seulement quand l'écran des conversations ou une conversation est ouvert.
3. Avec l'ADR-022, seul le compte actif reçoit les notifications de messages.
4. Étape 1 mise en œuvre après l'ADR-023 et avant l'ADR-024, sur `main`.
5. Message : 2000 caractères au plus. Suppression et modification d'un message : hors périmètre.
6. Posts : hors périmètre pour l'instant (pas de bouton Partager ; en ajouter un ouvrirait un autre chantier). Retirés de l'étape 2.
7. « Envoyer à » : les 5 conversations les plus récentes, puis un lien pour chercher quelqu'un d'autre.
8. Conversation déjà ouverte : le serveur envoie toujours la notification ; l'application ne l'affiche pas si cette conversation précise est à l'écran.
9. Réglage des notifications de messages : stocké côté serveur.
10. Masquer une conversation : hors périmètre.
11. Temps réel de la liste des conversations : une salle par utilisateur (`user:<id>`).

## Alternatives
- **Seconde passerelle WebSocket dédiée aux messages :** avantage, aucun risque sur les salles de vidéos ; inconvénient, deux connexions, deux authentifications. Écartée par le lead.
- **Envoi des messages par la WebSocket :** avantage, un aller-retour de moins ; inconvénient, une seconde voie d'écriture avec ses propres validations et erreurs, contraire au modèle de l'ADR-010 où la WebSocket ne fait que recevoir.
- **Demandes d'ami ou d'autorisation avant d'écrire :** avantage, protège contre les messages non voulus ; inconvénient, un parcours de plus ; écarté par le lead.
- **Conversations de groupe :** avantage, plus général ; inconvénient, modèle de participants, salles et non-lus plus lourds ; non demandé.
- **Service de messagerie tiers** (Firebase Firestore, Stream) : avantage, temps réel fourni ; inconvénient, une seconde source de données hors de PostgreSQL, et une dépendance de plus.

## Conséquences
### Positives
- Une messagerie démontrable dès l'étape 1, sur la passerelle et le modèle REST existants.
- Les salles de vidéos et leurs événements restent inchangés.
- Le partage dans une conversation respecte la visibilité et le Premium, vérifiés par le serveur.
- Les notifications réutilisent l'infrastructure de l'ADR-020.

### Négatives
- Nouvelles tables CONVERSATION et MESSAGE, nouveau module `messaging`, plusieurs migrations (étape 1, puis `videoId` à l'étape 2, réglage à l'étape 3). CLAUDE.md est mis à jour par un humain à l'acceptation.
- `InMemoryVideoRooms` et la passerelle sont généralisés : risque de régression sur les salles de vidéos, à tester.
- Sans demande d'ami, ni blocage, ni signalement : n'importe qui peut écrire à n'importe qui.
- La recherche par nom affiché renvoie des homonymes. Le @handle ne les départage que s'ils ont une chaîne.
- Sans connexion permanente, la pastille n'est pas en direct hors des écrans de messagerie. Elle est relue à l'ouverture et au retour au premier plan.
- Avec plusieurs comptes (ADR-022), un compte inactif ne reçoit aucune notification.
- Les salles restent en mémoire : un seul processus backend (limite déjà présente pour les vidéos).
- L'aide (ADR-019) doit décrire la messagerie.

## Liens
- [ADR-004](ADR-004-temps-reel-websocket.md), [ADR-010](ADR-010-websocket-salles-compteurs.md) : passerelle WebSocket, salles, envoi par REST ; généralisées à une salle par conversation.
- [ADR-020](ADR-020-notifications-fcm.md) : notifications FCM, modèle repris à l'étape 3.
- [ADR-016](ADR-016-reglages-publication-video.md) : visibilité, appliquée aux vidéos partagées.
- [ADR-007](ADR-007-offre-premium.md) : accès Premium vérifié par le serveur à l'ouverture d'une vidéo partagée.
- [ADR-022](ADR-022-plusieurs-comptes-appareil.md) : seul le compte actif reçoit les notifications.
- [ADR-008](ADR-008-authentification-jwt.md) : access token vérifié au handshake WebSocket ; à reconsidérer en cas de connexion longue.
- [ADR-003](ADR-003-backend-nestjs.md) : nouveau module `messaging`, architecture hexagonale.
