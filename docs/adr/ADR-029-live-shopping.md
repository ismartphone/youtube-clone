# ADR-029 — Live shopping : direct simulé sur un fichier du catalogue, chat, produit épinglé et achat en direct

## Statut
Accepté

## Contexte
Le sujet ajoute une nouvelle exigence, le « Challenge 3 — le live shopping » : un créateur passe en direct, ses spectateurs discutent, réagissent et peuvent acheter un produit présenté pendant le live. Le 5 novembre 2026, le lead développeur demande de formaliser cette exigence avant de la développer. Le rendu du projet est le 6 novembre 2026, soit demain.

Contraintes :
- le live est aujourd'hui exclu : CLAUDE.md (section « Hors périmètre ») et l'ADR-015 le disent ;
- l'application reste 100 % native (ADR-001) ;
- le serveur reste seul juge de l'accès et du paiement (ADR-007, ADR-014, ADR-016) ;
- le back-office ne contacte jamais PostgreSQL directement : tout passe par l'API ;
- rien n'est ajouté qui n'ait été demandé (CLAUDE.md) : ce qui est volontairement laissé de côté est listé plus bas.

État du code au 5 novembre 2026 (vérifié) :
- **Direct, état actuel :** l'onglet « En direct » de la feuille de création affiche l'écran de déblocage de l'ADR-015 (`CreateSheet.kt`, `CreateViewModel.kt`). Le seuil de 50 abonnés est une constante côté application ; le nombre d'abonnés vient de `GET /channels/{id}` ; aucune route backend n'existe pour le direct. À 50 abonnés, l'écran n'ouvre rien : il invite à continuer de publier.
- **Passerelle WebSocket (`backend/src/modules/realtime`) :**
  - chemin `/realtime`, JWT vérifié une seule fois au handshake, visiteur possible sans jeton (ADR-010) ;
  - `join` / `leave` avec un seul identifiant de salle parmi `videoId`, `conversationId`, `userId` ; clés préfixées `video:`, `conversation:`, `user:` dans `InMemoryRooms` ; un `join` refusé ne fait rien et ne répond rien ;
  - à la fermeture de la connexion, `handleDisconnect` appelle `leaveAll` ;
  - **le serveur n'envoie aucun ping** : seul le client Android en envoie un toutes les 20 secondes (`RealtimeSocket.kt`) ; une connexion morte sans fermeture propre reste donc dans la salle jusqu'à ce que le système ferme la socket ;
  - `InMemoryRooms.send` envoie le même message à tous les membres de la salle : il n'existe pas d'envoi par destinataire pour une salle de vidéo ou de conversation (`members(room)` donne pourtant l'utilisateur de chaque socket) ;
  - les commentaires, « j'aime » et messages sont envoyés par REST et diffusés ensuite ; la WebSocket ne sert qu'à recevoir (ADR-010, ADR-025).
- **Commentaires (`modules/comments`) :** aucune modération n'existe aujourd'hui (ni suppression d'un commentaire, ni blocage).
- **Premium et Stripe (`modules/premium`) :**
  - le port `PaymentGateway` est propre au Premium : `start(userId)` crée un paiement au prix fixe `PREMIUM_PRICE`, `verifyPayment(userId, reference)` le compare à ce même prix ;
  - l'adaptateur Stripe crée un PaymentIntent avec l'identifiant de l'utilisateur en métadonnées, puis le relit chez Stripe (statut `succeeded`, montant, devise, métadonnées) ; la référence est enregistrée dans `purchaseToken`, unique en base, ce qui garantit l'usage unique (ADR-014) ;
  - l'adaptateur simulé garde un jeton dans Redis, lu et effacé en une seule opération (`GETDEL`), en repli quand `STRIPE_SECRET_KEY` est absente ;
  - le montant réellement payé est enregistré avec l'achat (`amount`, `currency`).
- **Adresse du serveur (ADR-027) :** `ServerAddress.baseUrl` est unique et sert à tout : trois constructeurs Retrofit, `RealtimeSocket` (`ServerAddress.baseUrl + "realtime"`) et `resolveMediaUrl`. Elle n'existe qu'en version debug, est lue une fois au démarrage, et son changement déconnecte tous les comptes.
- **Back-office (Next.js, MVC) :** pages vidéos, catégories, chaînes, paiements, publicités. La section publicités (`admin-ads.controller.ts`) gère déjà liste, ajout avec fichier, modification et suppression : c'est le modèle d'une page Produits.
- **Données de démonstration :** `prisma/seed.ts` ne crée que 2 abonnements (FOLLOW) ; aucune chaîne n'approche 50 abonnés.
- **Mesures :** les événements PostHog partent de l'application directement vers PostHog (ADR-006, ADR-013) ; aucun événement n'existe pour le direct.

## Décision
Ajouter le live shopping : un créateur démarre un live depuis l'application, ses spectateurs le regardent, discutent, réagissent, et peuvent acheter le produit épinglé.

### Levée de l'exclusion du « live »
**Cet ADR lève l'exclusion du « live » inscrite dans la section « Hors périmètre » de CLAUDE.md.** Le live shopping entre dans le périmètre. Ce qui reste exclu, c'est la capture caméra et la diffusion réelle (voir ci-dessous). CLAUDE.md est reformulé en conséquence à l'acceptation de cet ADR.

Il rend caduques, pour le direct, trois phrases de l'ADR-015 : « le live reste hors périmètre » (Contexte), « la diffusion en direct elle-même reste hors périmètre » (limite de la feuille de création) et « atteindre 50 abonnés ne débloque pas de diffusion en direct » (Conséquences négatives). L'ADR-015 est amendé sur place le 5 novembre pour ces trois phrases (il reste « Accepté »). La suite de l'ADR-015 reste valable : le seuil de 50 abonnés, les textes de l'écran de déblocage sous 50 abonnés, la feuille de création et ses quatre onglets. À partir de 50 abonnés, l'onglet « En direct » affiche un formulaire (titre et choix d'une vidéo).

### Hors périmètre : capture caméra et diffusion réelle
Capturer la caméra du téléphone puis la diffuser à des spectateurs demande une chaîne d'ingestion et de rediffusion (serveur RTMP ou WebRTC, transcodage, distribution) : c'est un projet à elle seule.

Le flux vidéo d'un live est donc **un fichier existant du catalogue, lu en boucle et présenté comme un direct**. La vidéo n'est ni filmée ni transmise en continu. À l'oral, il faut le dire tel quel.

Tout le reste est réel : le chat, le compteur de spectateurs, les réactions, l'achat et la modération. Le live tourne sur **notre serveur**, avec nos salles WebSocket.

### Entités
Les noms de tables et de champs sont en anglais, comme le reste du code ; les migrations Prisma sont livrées étape par étape (voir « Ordre de livraison »).
- **LIVE** : `id`, `channelId`, `title`, `videoId` (la vidéo du catalogue utilisée comme flux), `status` (`LIVE` ou `ENDED`), `startedAt`, `endedAt` facultatif, `pinnedProductId` facultatif.
  - Un seul live en cours par chaîne (vérifié par le cas d'usage, dans une transaction).
  - `videoId` : si la vidéo est supprimée plus tard, la ligne du live est conservée (`videoId` vide), car le live terminé reste lisible dans le back-office.
- **PRODUCT** : `id`, `channelId`, `name`, `price` (en centimes) et sa devise, `imageUrl` (chemin relatif `/media/products/...`, comme les autres fichiers), `description`, `createdAt`.
- **LIVE_MESSAGE** : `id`, `liveId`, `userId`, `kind` (`CHAT` ou `PURCHASE`), `content`, `createdAt`, `deletedAt` facultatif. Un message `PURCHASE` est l'annonce d'un achat, écrite par le serveur.
- **LIVE_PURCHASE** : `id`, `liveId`, `buyerId`, `productId` facultatif (vide si le produit est supprimé ensuite), `productName`, `amount`, `currency`, `paymentReference` unique, `createdAt`. Le nom et le montant sont recopiés au moment de l'achat : l'historique reste juste si le produit change ou disparaît, comme pour PREMIUM_SUBSCRIPTION (ADR-014).
- **LIVE_RESTRICTION** (cinquième table, nécessaire à la modération ; elle n'était pas dans la liste de la demande, le lead développeur l'a acceptée) : `liveId`, `userId`, `type` (`BAN` ou `HIDE`), `createdAt`, clé primaire (`liveId`, `userId`). **Créée avec l'étape 4 seulement**, par la migration Prisma de l'étape 4 et pas avant : pas de table pour du code qui pourrait ne pas exister.

### Démarrer un live
- Depuis l'onglet « En direct » de la feuille de création, pour une chaîne qui compte **au moins 50 abonnés** (FOLLOW, abonnement gratuit). La règle est gardée **côté serveur** à la création du live (le nombre est compté sur FOLLOW), pas seulement dans l'application : un client modifié ne peut pas la contourner. Sous 50, l'écran de l'ADR-015 est inchangé.
- À partir de 50, l'écran remplace l'invitation à continuer de publier par un petit formulaire : un titre et le choix d'une vidéo du catalogue comme flux.
- Un live en cours apparaît **en tête du fil d'accueil**, avec une pastille rouge « EN DIRECT » et le nombre de spectateurs, et sur la **page de la chaîne**.
- **Données de démonstration :** le seed donne 50 abonnés à une chaîne (voir D13), pour pouvoir démontrer le démarrage sans créer 50 comptes à la main.
- Routes : `POST /me/lives` (titre, `videoId` ; refus si moins de 50 abonnés, si un live est déjà en cours, ou si la vidéo n'est pas admissible, voir D1) ; `POST /lives/:id/end` (propriétaire) ; `GET /lives` (lives en cours, publique) ; `GET /lives/:id` (état complet du live, voir ci-dessous).

### Regarder un live
- **Écran dédié :** la vidéo en haut, le chat en dessous. Le live est un écran à part, pas l'écran de lecture d'une vidéo : pas de bouton de lecture/pause ni de barre de progression, pas de publicité avant lecture (voir D15).
- **Salle par live :** la passerelle accepte un quatrième type de salle, `{ "liveId": "..." }`, clé `live:<id>`, sur le modèle de l'ADR-025 (clés préfixées, un garde par type). Un visiteur peut rejoindre la salle (lecture seule, comme dans l'ADR-010) ; le `join` d'un live terminé ou inexistant est refusé sans rien révéler.
- **Chat :** envoi par REST (`POST /lives/:id/messages`), validation puis écriture en base, puis diffusion `live:message` à la salle : c'est le modèle de l'ADR-010 (la WebSocket ne fait que recevoir). Réservé aux comptes connectés. À l'arrivée, l'application lit les derniers messages (`GET /lives/:id/messages`, paginé) ; après une reconnexion, elle relit l'état du live, le produit épinglé et les derniers messages, car les événements diffusés pendant la coupure sont perdus (ADR-010).
- **Spectateurs réellement comptés** à partir des entrées et sorties de la salle `live:<id>` :
  - un spectateur est une socket présente dans la salle ; la sortie est un `leave`, une fermeture de l'écran ou la fermeture de la connexion ;
  - **multi-appareils :** un même compte connecté sur deux appareils compte pour 1 (comptage par identifiant d'utilisateur, connu de `InMemoryRooms`) ; les visiteurs non connectés n'ont pas d'identité, chaque connexion compte pour 1 ;
  - **déconnexions propres :** sur un `leave`, une fermeture de l'écran ou une fermeture de connexion, le compteur **baisse immédiatement** ; le battement n'intervient pas ;
  - **déconnexions brutales :** sans fermeture propre (réseau coupé, application tuée), la socket reste dans la salle jusqu'à ce que le système la ferme, ce qui peut prendre longtemps ; or le serveur n'envoie aucun ping aujourd'hui. Pour que le compteur soit vrai, la passerelle reçoit un battement serveur de 30 secondes (ping périodique, fermeture des sockets qui ne répondent pas). Il ne sert qu'à ces coupures brutales. Il part **avec l'étape 1** (le compteur en dépend), vaut pour toutes les salles et demande des tests de non-régression (voir Conséquences et « Ordre de livraison ») ;
  - le compteur est diffusé (`live:viewers`) à chaque changement, au plus une fois par intervalle court pour ne pas inonder la salle ; les valeurs exactes sont fixées en D4 ;
  - le même compteur alimente `GET /lives` et `GET /lives/:id` ; sur le fil d'accueil et la page de chaîne, il est lu à l'ouverture de l'écran et au retour au premier plan, sans connexion permanente (décision du lead dans l'ADR-025) ;
  - les salles étant en mémoire, le compteur n'est valable que pour une seule instance du backend (limite déjà présente, ADR-010) ; au redémarrage du serveur, les salles se vident et les clients se reconnectent avec le délai croissant de l'ADR-010.
- **Réactions :** des émojis qui remontent à l'écran, **diffusés à tous**. Envoi par REST (`POST /lives/:id/reactions`, avec un émoji pris dans une liste fixe côté serveur), diffusion `live:reaction` à la salle. Une réaction n'est **pas stockée** : elle n'existe que le temps de l'affichage, et n'est donc ni dans l'historique ni rejouée à la reconnexion. Réservé aux comptes connectés.
- **Position de lecture :** voir D3.

### Produit épinglé et achat
- **Épingler :** le diffuseur épingle **un** de ses produits pendant le live (`PUT /lives/:id/pinned-product`, `DELETE` pour le retirer). La carte (nom, prix, image) apparaît **en direct chez tous** (`live:pinned`, envoyé aussi au retrait). Un spectateur qui arrive ou se reconnecte la lit dans `GET /lives/:id`. Seuls les produits de sa propre chaîne peuvent être épinglés (`GET /me/products` donne la liste au diffuseur).
- **Achat avec Stripe en mode test, comme le Premium (ADR-014) :**
  - `POST /lives/:id/checkout` : le serveur crée un PaymentIntent pour le produit **actuellement épinglé** d'un live en cours. Le montant est lu en base par le serveur, **jamais reçu du client**. Les métadonnées portent l'acheteur, le live et le produit. Il renvoie le `client_secret` ; l'application ouvre la PaymentSheet Stripe ;
  - `POST /lives/:id/purchases` : reçoit la référence du paiement et la **vérifie côté serveur** auprès de Stripe : statut `succeeded`, devise, montant égal à celui que le serveur avait fixé, métadonnées désignant bien l'utilisateur qui appelle, le live et le produit. L'achat n'est enregistré que si la vérification réussit ;
  - **usage unique :** la référence est enregistrée dans `paymentReference`, unique en base : un même paiement ne sert qu'une fois, comme `purchaseToken` pour le Premium ;
  - **repli simulé :** quand `STRIPE_SECRET_KEY` est absente, le mode simulé de l'ADR-014 reprend le relais (jeton Redis lu et effacé en une opération), pour que la démonstration reste possible sans réseau ;
  - le montant est enregistré tel que payé (`amount_received` en mode Stripe, prix figé au démarrage en mode simulé), avec le nom du produit.
- **Qui est l'acheteur :** le compte connecté qui paie. Un visiteur est invité à se connecter. Le créateur ne peut pas acheter son propre produit (voir D7).
- **Livraison :** hors périmètre. Aucune adresse, aucun transporteur, aucun suivi de commande. L'achat est un paiement enregistré, consultable dans le back-office. Cela s'annonce à l'oral : on démontre un parcours d'achat, pas une logistique.
- **Quantité et stock :** la demande ne mentionne pas de stock ; PRODUCT n'en porte pas. Un achat = une unité, sans limite de quantité disponible (voir D7).
- **Annonce dans le chat :** chaque achat réussi écrit un message `PURCHASE` diffusé à tous (`live:message`) : « Camille vient d'acheter Casque audio ». **Jamais le montant, jamais le moyen de paiement**, ni dans le texte ni dans les données diffusées. Le message est écrit en base : il apparaît aussi dans l'historique du chat à la reconnexion. Le prénom affiché est le **premier mot** du nom affiché du compte (`displayName`) ; un nom d'un seul mot donne ce mot (voir D9). Le compte de démonstration « Camille Spectatrice » n'est pas renommé.
- Le module premium n'est **pas modifié** : son port est propre au Premium (prix fixe). Le live shopping a son propre port de paiement, dans son propre module, qui réutilise les mêmes clés Stripe et les mêmes principes (voir D8).

### Modération par le diffuseur (bonus de l'énoncé, étape 4)
Le diffuseur est le propriétaire de la chaîne du live. Toutes les actions sont vérifiées par le serveur.
- **Supprimer un message pour tout le monde** (`DELETE /lives/:id/messages/:messageId`) : le message reçoit `deletedAt`, et `live:message-deleted` est diffusé à la salle ; les applications le retirent de l'écran. Il n'est **plus jamais renvoyé** par l'historique ni après une reconnexion. La ligne reste en base.
- **Bannir** (`PUT /lives/:id/restrictions/:userId`, type `BAN`) : la personne ne peut plus écrire dans ce live et **le sait** : son envoi reçoit un refus explicite (403) et elle reçoit `live:restriction`, envoyé à ses seules connexions ; à la reconnexion, `GET /lives/:id` lui indique qu'elle est bannie. Ses messages déjà envoyés restent visibles, sauf suppression par le diffuseur (D10). Elle reste dans la salle et continue de voir le live et le chat.
- **Masquer discrètement** (même route, type `HIDE`) : la personne continue d'écrire et **voit ses messages**, mais personne d'autre ne les voit. Il n'y a ni refus ni événement qui le lui dise.
  - Diffusion : un message d'une personne masquée n'est envoyé **qu'aux connexions de son auteur** (et au diffuseur, voir D10), jamais à toute la salle. C'est possible, mais demande un envoi par destinataire, que `InMemoryRooms` n'a pas aujourd'hui pour les salles (il connaît pourtant l'utilisateur de chaque socket). Cet ajout à la passerelle est fait **avec l'étape 4**, pas avant, et pour le masquage discret seulement.
  - Historique et reconnexion : `GET /lives/:id/messages` ne renvoie les messages d'une personne masquée qu'à elle-même et au diffuseur. Après reconnexion, elle retrouve ses propres messages, les autres non.
  - Les messages qu'elle avait écrits **avant** d'être masquée sont masqués aussi : le masquage s'applique à toute son activité dans ce live, passée et future (D10).
- **Les annonces d'achat ne sont pas modérées** : un message `PURCHASE` est écrit par le serveur, il est toujours visible (D9).
- Le bannissement et le masquage valent pour ce live seulement (D10). Ils ne sont pas levables dans cette version.
- Cet ADR n'ajoute aucune modération aux commentaires des vidéos ni à la messagerie : la phrase de l'ADR-025 (« ni blocage, ni signalement ») reste vraie pour la messagerie.

### Back-office
Toujours par l'API, jamais directement par PostgreSQL. Deux pages dans `app/(admin)/`, avec leurs contrôleurs, modèles, vues et services :
- **Produits :** liste, ajout avec image, modification, suppression. Un produit est rattaché à une chaîne choisie à l'ajout. Routes `GET/POST /admin/products`, `PATCH/DELETE /admin/products/:id`, `PUT /admin/products/:id/image`, réservées au rôle ADMIN. L'image suit le modèle des publicités : validée par ffprobe, jamais par son extension ni son type MIME annoncé ; nom de fichier généré par le serveur ; chemin relatif en base (ADR-011). Prix en centimes, en euro, d'au moins **0,50 €** (50 centimes), minimum de Stripe pour l'euro (source : documentation Stripe, « Devises prises en charge », section « Montants de paiement maximum et minimum », https://docs.stripe.com/currencies#minimum-and-maximum-charge-amounts, consultée le 5 novembre 2026). Le serveur refuse un prix inférieur. Les produits du seed ont des prix normaux, entre 15 et 60 €.
  - Suppression d'un produit épinglé : il est retiré du live (`live:pinned` de retrait). Les achats déjà faits sont conservés (nom et montant recopiés).
- **Lives :** liste des lives (titre, chaîne, début, fin, état) avec leurs achats (acheteur, produit, montant, date, référence du paiement). `GET /admin/lives`, réservé au rôle ADMIN. Ces données sont personnelles et financières, comme les paiements Premium (ADR-014).
- Les créateurs ne gèrent **pas** leurs produits depuis l'application : choix de périmètre, comme l'ADR-005 réservait les vidéos au back-office (D11).
- Le back-office refuse à la saisie une URL absolue vers `localhost` pour l'image, comme l'impose l'ADR-027 pour les médias.

### Accès aux vidéos : conséquences
Une vidéo utilisée comme flux est lue par tous les spectateurs du live. Règle retenue (D1) : seules les vidéos **publiques**, **non Premium**, **de la chaîne du diffuseur** peuvent servir de flux, vérifié par le serveur au démarrage. Sans cette règle, un live ferait lire à tous une vidéo privée (ADR-016) ou exclusive (ADR-007) : le serveur ne tiendrait plus seul l'accès. La vidéo reste dans le catalogue ; son compteur de vues, ses commentaires et ses « j'aime » ne sont pas touchés par le live.

### Serveur partagé entre plusieurs applications d'étudiants (limite documentée)
Un serveur temps réel partagé est envisagé ; ses détails sont inconnus. Constat : s'y brancher demanderait une adresse temps réel distincte (`ServerAddress.baseUrl` est unique et sert aussi à l'API et aux médias, ADR-027), ce serveur devrait parler notre protocole, et tout le monde y serait visiteur, faute de pouvoir vérifier nos jetons (ADR-008, ADR-010). Limite documentée : aucun travail n'est fait, aucune seconde adresse n'est ajoutée ; le live tourne sur notre serveur.

### Mesures (PostHog)
Aucun événement n'est ajouté par cet ADR. Des événements seraient utiles pour mesurer l'audience et la conversion du live, mais ajouter des mesures est une décision à part : voir D12, avec une liste proposée à valider.

### Ordre de livraison
Un commit par étape, dans cet ordre. Chaque étape se démontre seule et n'en modifie pas une précédente.
1. **Démarrer et regarder un live, avec chat et compteur de spectateurs.** Tables LIVE et LIVE_MESSAGE (première migration), module backend `lives`, salle `live:<id>`, battement serveur de la passerelle (30 s, pour les coupures brutales seulement ; une déconnexion propre baisse le compteur immédiatement), compteur de spectateurs, chat. Android : formulaire de démarrage, pastille « EN DIRECT » sur l'accueil et la page de chaîne, écran du live. Seed : 50 abonnés pour une chaîne (comptes fictifs crédibles, voir D13). **Après cette modification de la passerelle, vérifier que la messagerie (ADR-025) fonctionne encore** (et les salles de vidéos).
2. **Réactions.** Route, événement et animation. Aucune migration.
3. **Produit épinglé et achat avec annonce.** Tables PRODUCT et LIVE_PURCHASE et champ `pinnedProductId` (deuxième migration), module `products`, port de paiement du live, PaymentSheet. Back-office : pages Produits et Lives. Annonce dans le chat.
4. **Modération.** Table LIVE_RESTRICTION et champ `deletedAt` (troisième migration, la seule qui crée LIVE_RESTRICTION), trois actions. Passerelle : envoi par destinataire, pour le masquage discret seulement ; **après cette modification, vérifier de nouveau que la messagerie (ADR-025) fonctionne**. **Cette étape saute si le temps manque** : les étapes 1 à 3 forment un ensemble complet.

Les deux ajouts à la passerelle ne sont **pas faits ensemble** : le battement serveur part avec l'étape 1, l'envoi par destinataire avec l'étape 4.

## Alternatives
- **Diffusion réelle (caméra du téléphone, RTMP ou WebRTC, rediffusion HLS en direct) :**
  - Avantage : un vrai direct.
  - Inconvénient : une chaîne d'ingestion, de transcodage et de distribution, un projet à elle seule, impossible en un jour. Écartée.
- **Flux simulé par un fichier du catalogue lu en boucle (retenu) :**
  - Avantages : réutilise le stockage et la lecture existants (ADR-002, ADR-011, ADR-017), aucun nouveau composant ; tout le reste du live est réel.
  - Inconvénients : ce n'est pas un vrai direct, à annoncer à l'oral ; la vidéo choisie doit respecter les règles d'accès (D1).
- **Chat envoyé par la WebSocket :** avantage, un aller-retour de moins. Inconvénient, une seconde voie d'écriture avec ses propres validations, contraire à l'ADR-010 et à l'ADR-025. Écarté ; le chat et les réactions passent par REST.
- **Réutiliser COMMENT pour le chat :** avantage, rien de nouveau en base. Inconvénients : un chat est un flux court et dense, sans réponses, sans « j'aime » ni tri ; il a sa propre modération (suppression, bannissement, masquage) ; les règles `commentsEnabled` et de visibilité d'une vidéo (ADR-016) n'ont aucun sens pour un live. Écarté : LIVE_MESSAGE.
- **Stocker les réactions :** avantage, rejouables. Inconvénient, un volume d'écritures élevé pour une information qui n'a de valeur qu'à l'instant. Écarté.
- **Compter les spectateurs avec Redis :** avantage, valable avec plusieurs instances. Inconvénient : les salles sont déjà en mémoire (ADR-010), le compteur dérivé de ces salles est exact par construction ; Redis y ajouterait une seconde source de vérité à tenir cohérente. Écarté, avec la même limite d'une seule instance.
- **Étendre le port de paiement du Premium au lieu d'en créer un :** avantage, un seul port. Inconvénient, il faudrait y faire passer un montant et des métadonnées variables : la modification toucherait le module premium (prix fixe, adaptateurs, tests) pour une exigence qui n'en dépend pas, à la veille du rendu. Écarté ; D8.
- **Webhooks Stripe plutôt que relecture du PaymentIntent :** écarté pour la même raison que dans l'ADR-014 (délai), la relecture côté serveur suffit.
- **Livraison, panier, quantités, stock géré :** non demandés ; écartés (D7).
- **Gestion des produits par les créateurs dans l'application :** avantage, parcours complet. Inconvénient, un écran de plus (formulaire, image, liste) hors de la demande. Écarté ; le back-office suffit (D11).
- **Serveur temps réel partagé comme serveur principal du live :** écarté (voir « Serveur partagé »).

## Conséquences
### Positives
- Le live shopping est démontrable de bout en bout : démarrer, regarder, discuter, réagir, acheter, modérer.
- Il réutilise l'existant : la passerelle et ses salles (ADR-010, ADR-025), l'envoi par REST, Stripe en mode test avec jeton vérifié côté serveur et à usage unique (ADR-014), les principes du back-office de publicités.
- Le serveur reste seul juge : la règle des 50 abonnés, le montant payé, l'acheteur, l'épinglage et la modération sont vérifiés par l'API ; les règles d'accès aux vidéos (ADR-007, ADR-016) restent intactes (D1).
- Le compteur de spectateurs est vrai par construction (dérivé des salles) et le battement serveur le rend fiable ; ce battement profite aussi aux salles de vidéos et de conversations.
- L'historique des achats est exact même si un produit change ou disparaît (nom et montant recopiés).
- Le plan en quatre étapes laisse un ensemble complet livrable sans l'étape 4.
- Les limites sont écrites : diffusion simulée, pas de livraison, pas de stock, serveur partagé non pris en charge.

### Négatives
- **Charge très élevée pour un seul jour (rendu le 6 novembre 2026, demain).** L'ADR couvre trois migrations Prisma, deux nouveaux modules backend, une modification de la passerelle, un nouvel écran Android complet (vidéo, chat, réactions, carte produit, PaymentSheet), des changements de l'accueil, de la page de chaîne et de la feuille de création, deux pages de back-office et le seed. Les étapes 1 et 3 sont le cœur de la démonstration ; l'étape 4 est la première à sauter, puis l'étape 2 si nécessaire. Le risque de livrer l'ensemble de façon fragile est réel : mieux vaut trois étapes solides que quatre étapes bancales.
- Le battement serveur modifie la passerelle pour toutes les salles : risque de régression sur les vidéos et la messagerie, à tester.
- La passerelle doit gagner un envoi par destinataire (masquage discret, notification de bannissement), à l'étape 4 : même risque, d'où le contrôle de la messagerie après chaque modification de la passerelle.
- Les salles restent en mémoire : un seul processus backend ; un redémarrage du serveur vide les salles et remet le compteur à zéro le temps de la reconnexion.
- Le flux n'est pas un vrai direct : la vidéo, son début et sa durée sont ceux d'un fichier. Rien dans l'application ne le dit, ce qui est cohérent avec la règle de rédaction (aucun texte visible ne parle de maquette), mais il faut l'annoncer à l'oral.
- Pas de livraison, pas de stock, une unité par achat : un produit peut être « vendu » sans limite.
- Réserve de l'ADR-014 : Stripe n'est acceptable que parce que l'application n'est pas publiée ; elle s'applique de la même façon ici (ici, un bien physique serait plus facile à justifier que le Premium, mais aucune livraison n'existe).
- Deux adaptateurs de paiement de plus à maintenir (Stripe et repli simulé), dans le module du live, en plus de ceux du Premium.
- Le seed demande 49 comptes fictifs de plus pour atteindre 50 abonnés ; ils apparaissent dans la recherche d'utilisateurs de la messagerie (ADR-025) et dans le back-office, d'où l'exigence de noms plausibles et d'avatars générés (D13).
- La modération n'est pas levable (pas de « débannir » ni de « démasquer ») et ne vaut que pour un live.
- Aucune modération des commentaires des vidéos : le live n'en ouvre pas.
- L'ADR-015 est amendé sur place le 5 novembre : l'onglet « En direct » ne se contente plus d'inviter à publier à partir de 50 abonnés.
- L'aide (ADR-019) devra décrire le live, si le temps le permet.
- Aucun test automatique n'est annoncé dans cet ADR ; les règles du domaine (seuil, compteur, achat, modération) méritent des tests unitaires, comme les autres modules.

## Décisions validées
Le lead développeur a répondu le 5 novembre 2026. Sauf mention contraire, la décision est la recommandation ci-dessous, acceptée telle quelle. Les points D9, D13 et D14 sont tranchés autrement ; ils sont précisés en tête de leur entrée, et le reste de leur texte est l'analyse d'origine.

**D1. Quelle vidéo peut servir de flux, et que devient le live si elle change ?**
- Recommandation : une vidéo de la chaîne du diffuseur, publique et non Premium, quel que soit son format ; vérifié par le serveur à la création du live. Si la vidéo devient privée, Premium ou est supprimée pendant le live, le live est terminé de façon paresseuse (à la prochaine lecture de son état, sans crochet dans le module des vidéos).
- Conséquences : aucune fuite de vidéo privée ou exclusive (ADR-007, ADR-016) ; un live peut rester « en direct » quelques instants après le changement, jusqu'à la prochaine lecture. Autoriser les vidéos privées ou Premium comme flux obligerait à décider qui peut regarder le live (les abonnés Premium seulement ?) : c'est une règle d'accès de plus, non demandée.

**D2. Comment finit un live, et que devient-il ensuite ?**
- Recommandation : le diffuseur le termine (bouton « Terminer »). Un live terminé disparaît de l'accueil et de la page de chaîne, sa salle est fermée (`live:ended`), il reste lisible dans la liste des lives du back-office avec ses achats. Pas de rediffusion. En plus, un bouton « Terminer » dans le back-office, pour ne pas laisser un live bloqué en démonstration (petit ajout à approuver).
- Conséquences : sans fin automatique, un live dont le créateur a fermé l'application reste en direct (la vidéo tourne en boucle sans lui). Une fin automatique après une durée fixe est possible, mais la durée serait inventée.

**D3. Où en est la lecture quand on arrive dans le live ?**
- Recommandation : tout le monde voit le même moment. Position = (heure actuelle − `startedAt`) modulo la durée de la vidéo ; l'application démarre le lecteur à cette position et le relance en boucle. Aucun contrôle de lecture.
- Conséquences : la présentation « direct » est crédible, avec un seul calcul côté application. Alternative plus simple : chacun commence au début, sans synchronisation. Limite de la recommandation : un saut de position au démarrage et un léger décalage entre appareils (quelques secondes), sans conséquence sur les règles.

**D4. Règles précises du compteur de spectateurs.**
- Recommandation : compte unique par compte connecté (multi-appareils), une unité par visiteur connecté ; le diffuseur compte comme un spectateur s'il regarde ; diffusion au plus une fois par seconde ; battement serveur toutes les 30 secondes, avec fermeture des sockets sans réponse. Les valeurs de durée sont des propositions.
- Conséquences : un diffuseur qui regarde son live s'ajoute au chiffre (le retirer demande une exception) ; deux visiteurs derrière la même connexion comptent deux fois, et un visiteur reconnecté peut compter un instant deux fois, faute d'identité ; une coupure brutale n'est retirée du compteur qu'après le délai du battement (environ une minute au plus avec ces valeurs).

**D5. Règles du chat.**
- Décision : message de 300 caractères au plus (plus court que les 2000 de l'ADR-025, pour un chat rapide) ; au plus un message par seconde et par compte ; 50 messages lus à l'arrivée ; visiteurs en lecture seule. Valeurs validées par le lead développeur, non issues de la demande.
- Conséquences : sans limite de débit, un compte peut inonder le chat ; avec elle, un refus (429) à gérer dans l'application.

**D6. Règles des réactions.**
- Recommandation : liste fixe de six émojis (cœur, applaudissements, rire, surprise, feu, pouce) ; un compte connecté seulement ; au plus quelques envois par seconde et par compte ; un compte banni du chat peut encore réagir (le bannissement porte sur l'écriture de messages).
- Conséquences : une liste libre d'émojis ouvrirait la validation et l'affichage ; une réaction n'est jamais stockée, donc jamais rejouée ; un banni peut encore « parler » par émojis si l'on garde cette règle.

**D7. Achat : stock, quantité, livraison, auto-achat.**
- Recommandation : une unité par achat ; pas de stock ni de livraison (PRODUCT sans champ de stock) ; devise unique (euro), comme le Premium ; le créateur ne peut pas acheter son propre produit.
- Conséquences : un parcours d'achat sans logistique, à annoncer à l'oral ; sans stock, un produit est vendable sans fin. Ajouter un stock (décrémenté à l'achat, refus à zéro, gestion de la concurrence) ajoute un champ, une règle transactionnelle et un cas d'erreur : non demandé.

**D8. Port de paiement et cas limites de l'achat.**
- Recommandation : un port de paiement propre au module du live, qui réutilise les mêmes clés Stripe et le même mode simulé ; le module premium n'est pas modifié. Un paiement réussi dont le live s'est terminé, ou dont le produit a été désépinglé entre le démarrage et la confirmation, est **enregistré quand même** (l'argent est pris) ; l'annonce dans le chat n'est écrite que si le live est encore en cours.
- Conséquences : aucun risque de régression sur le Premium, au prix d'un code de paiement dupliqué en partie ; refuser un paiement déjà réglé serait pire qu'une annonce manquante.

**D9. Annonce d'achat.**
- **Décision du lead développeur (écarte la recommandation ci-dessous) :** le prénom affiché est le premier mot du nom affiché (« Camille vient d'acheter Casque audio »). Le compte de démonstration n'est pas renommé. Un nom d'un seul mot donne ce mot.
- Recommandation écartée : le nom affiché complet du compte (« Camille Spectatrice vient d'acheter Casque audio » avec le compte de démonstration, et non « Camille ») ; le message est écrit en base et visible de tous, même d'un acheteur masqué ou banni.
- Conséquences : couper au premier mot donnerait « Camille » dans l'exemple de la demande, mais est faux pour d'autres noms (noms composés, un seul mot). Garder la forme de l'exemple exige de décider d'une règle de troncature, ou de nommer le compte de démonstration « Camille ». L'annonce d'un acheteur masqué est visible de tous : le masquage ne cache que ses messages.

**D10. Détails de la modération.**
- Recommandation : bannissement et masquage limités au live en cours ; un banni garde ses anciens messages visibles ; un masqué voit tous ses messages (anciens compris, qui disparaissent pour les autres) ; le diffuseur voit les messages masqués avec une marque discrète ; pas de levée du bannissement ni du masquage dans cette version.
- Conséquences : un bannissement à l'échelle de la chaîne demanderait un autre modèle ; sans marque, le diffuseur ne voit pas qui il a masqué ; rendre ses anciens messages invisibles est plus cohérent pour le diffuseur, mais un message déjà lu par d'autres disparaît de leur écran seulement au rechargement (aucun événement n'est diffusé pour ne pas révéler le masquage).

**D11. Produits gérés uniquement par le back-office.**
- Recommandation : oui, comme le marquage Premium des vidéos (ADR-007). Un produit est rattaché à une chaîne choisie par l'administrateur.
- Conséquences : pas d'écran de gestion de produits dans l'application, donc moins de travail ; un créateur ne peut pas ajouter ses produits lui-même.

**D12. Événements PostHog : en ajouter ou non ?**
- Question posée : ne rien ajouter est une option valable. S'il y en a, proposition, à valider avant toute implémentation : `live_viewed` (identifiant du live), `live_started` (identifiant du live, côté créateur), `live_reaction_sent`, `live_message_sent`, `live_product_viewed`, `live_purchase_started`, `live_purchase_confirmed` (identifiants du live et du produit, jamais de montant). Ils respectent l'ADR-013 (identifiant de compte seulement) et le réglage « Partager des statistiques d'usage ».
- Conséquences : sans événements, aucune mesure de conversion du live ; avec, du code de plus à tester dans une journée très chargée.

**D13. Données de démonstration.**
- **Décision du lead développeur :** les 49 comptes fictifs sont acceptés, mais **crédibles** : noms plausibles (pas de « Utilisateur 12 »), avatars générés comme ceux des chaînes du seed. Vérifié dans le code : les chaînes du seed reçoivent un avatar PNG (initiales blanches sur fond de couleur) dessiné par FFmpeg (`FfmpegAvatarGenerator`, appelé par `ensureCcAvatar` dans `prisma/cc-videos.ts`), stocké sous `/media/avatars/<handle>.png` ; `USER` a aussi un champ `avatarUrl`. Les comptes fictifs suivent le même procédé. Ils sont visibles dans la recherche de la messagerie et le back-office. Produits du seed : prix normaux entre 15 et 60 €.
- Recommandation de départ : le seed donne 50 abonnés à la chaîne « Studio Libre » (49 comptes fictifs en plus de « Camille Spectatrice »), et crée un ou deux produits de démonstration.
- Conséquences : le démarrage d'un live se démontre sans manipulation ; les comptes fictifs encombrent la recherche de la messagerie ; le seed est aussi rejoué en développement, il doit rester rapide.

**D14. Serveur partagé : que faire maintenant ?**
- **Décision du lead développeur :** limite documentée, aucun travail, aucune seconde adresse. La prémisse « changer seulement l'adresse du serveur temps réel » est retirée (voir la section « Serveur partagé »).

**D15. Choix de périmètre à confirmer.**
- Recommandation : pas de publicité avant lecture dans un live (l'ADR-021 la réserve aux vidéos classiques) ; pas de mini-lecteur pour un live (quitter l'écran ferme la salle ; cohabitation avec le lecteur unique de l'ADR-024 à vérifier à l'implémentation) ; pas de notification FCM au démarrage d'un live (ADR-020) ; pas de rediffusion ; un live par chaîne ; l'accueil et la page de chaîne lisent les lives à l'ouverture, sans connexion permanente.
- Conséquences : chacun de ces éléments est une fonctionnalité absente de la demande ; les ajouter allongerait une livraison déjà très chargée.

## Liens
- [ADR-015](ADR-015-publication-depuis-mobile.md) : feuille de création, onglet « En direct » et seuil de 50 abonnés ; cet ADR lève son exclusion du direct (voir « Levée de l'exclusion du live ») ; l'ADR-015 est amendé sur place le 5 novembre.
- [ADR-004](ADR-004-temps-reel-websocket.md), [ADR-010](ADR-010-websocket-salles-compteurs.md) : passerelle WebSocket, salles, envoi par REST, reconnexion ; une salle `live:<id>` s'y ajoute, et l'ADR-010 est respecté (la WebSocket ne fait que recevoir). Le battement serveur (étape 1) et l'envoi par destinataire (étape 4) sont des ajouts à la passerelle, livrés séparément.
- [ADR-025](ADR-025-messagerie-utilisateurs.md) : généralisation des salles par type et gardes par type, reprise pour les lives ; pas de contradiction sur « ni blocage » (la modération du live n'existe que dans un live).
- [ADR-014](ADR-014-paiement-stripe-mode-test.md), [ADR-012](ADR-012-paiement-simule-backend.md), [ADR-007](ADR-007-offre-premium.md) : Stripe en mode test, jeton vérifié côté serveur et à usage unique, repli simulé, montant fixé par le serveur ; repris pour l'achat, sans modifier le module premium.
- [ADR-016](ADR-016-reglages-publication-video.md), [ADR-007](ADR-007-offre-premium.md) : visibilité et accès Premium d'une vidéo, vérifiés par le serveur ; ils déterminent les vidéos admissibles comme flux (D1).
- [ADR-021](ADR-021-publicites-preroll-bandeau.md) : la publicité avant lecture n'est pas jouée dans un live (D15).
- [ADR-023](ADR-023-reponses-jaime-commentaires.md) : commentaires des vidéos, non réutilisés pour le chat ; non modifiés.
- [ADR-027](ADR-027-adresse-serveur-configurable.md) : adresse unique du serveur ; raison pour laquelle un serveur partagé n'est pas pris en charge.
- [ADR-005](ADR-005-ajout-videos-backoffice.md), [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : gestion par le back-office ; chemins relatifs et validation des images par ffprobe, repris pour les produits.
- [ADR-006](ADR-006-analytics-posthog.md), [ADR-013](ADR-013-mesures-donnees-personnelles.md) : mesures ; aucun événement ajouté sans validation (D12).
- [ADR-020](ADR-020-notifications-fcm.md), [ADR-024](ADR-024-mini-lecteur.md) : pas de notification au démarrage ni de mini-lecteur pour un live (D15).
- [ADR-003](ADR-003-backend-nestjs.md) : architecture hexagonale ; nouveaux modules `lives` et `products`.
- [ADR-001](ADR-001-android-natif.md) : application native Android ; écran du live, PaymentSheet.

Amendé le 5 novembre : écart mineur constaté à l'étape 3 : le choix du produit à épingler côté diffuseur est une boîte de dialogue (AlertDialog) et non une feuille de paiement dessinée comme le Premium ; accepté tel quel par le lead développeur.

Amendé le 5 novembre : l'étape 4 (modération par le diffuseur, « bonus » de l'énoncé) est sautée sur décision du lead développeur, le temps étant consacré au challenge d'accessibilité ; la table LIVE_RESTRICTION n'est donc pas créée et l'envoi par destinataire n'est pas ajouté à la passerelle ; les décisions D10 (modération) restent documentées comme non livrées. Les limites de l'étape 2 (plafond de 12 émojis jamais vu en action, chevauchement des émojis d'une même rafale, refus du serveur ignoré sans message, barre de réactions absente pour un visiteur, comportement voulu) et de l'étape 3 (repli de paiement simulé non testé de bout en bout, produit supprimé en plein paiement enregistré avec productId vide) sont acceptées par le lead développeur.

Amendé le 6 novembre : la capture caméra et la diffusion réelle entrent dans le périmètre, uniquement par le SDK du live partagé et le serveur commun (ADR-031), jamais par notre serveur. Le live shopping de cet ADR est inchangé : flux = fichier du catalogue lu en boucle, paiement Stripe vérifié, seuil de 50 abonnés. La section « Serveur partagé entre plusieurs applications d'étudiants » et l'alternative « Serveur temps réel partagé comme serveur principal du live » restent vraies pour notre live shopping ; le serveur commun est intégré à côté, avec son propre protocole et ses propres écrans (ADR-031). Raison : ajustement de périmètre demandé par le lead développeur.
