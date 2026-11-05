# ADR-027 — Adresse du serveur configurable dans l'application Android (version debug)

## Statut
Accepté

## Contexte
L'adresse de l'API est fixée à la compilation : `gradle.properties` (`apiBaseUrl=http://10.0.2.2:3000/`) alimente `BuildConfig.API_BASE_URL`. `10.0.2.2` n'existe que dans l'émulateur. Pour tester sur un téléphone physique, il faut aujourd'hui remplacer l'adresse par l'IP du PC et recompiler (`-PapiBaseUrl=...`).

Le 5 novembre 2026, le lead développeur demande de pouvoir régler cette adresse depuis l'application, pour tester sur téléphone sans recompiler. Le back-office n'est pas concerné : il a déjà `API_URL`.

Contraintes :
- version debug uniquement : aucune trace dans la release ;
- l'adresse actuelle reste la valeur par défaut ;
- le serveur reste seul juge de l'accès (ADR-007, ADR-008) ; rien ne change côté backend.

État du code au 5 novembre 2026 (vérifié) :
- **Lecteurs de l'adresse, tous à la compilation :**
  - trois constructeurs Retrofit dans `data/di/DataModule.kt` (API principale, `UploadApi`, `RefreshApi`, ce dernier avec son propre `OkHttpClient`) ;
  - `RealtimeSocket.kt` : `API_BASE_URL + "realtime"`, calculé à la construction de la classe ;
  - `data/remote/MediaUrls.kt` : `resolveMediaUrl` complète les chemins relatifs (`/media/...`) avec cette base. Elle est appelée par les mappers de DTO (vidéos, chaînes, commentaires, messagerie, playlists, posts, publicités, avatar du compte) et par `DownloadsRepositoryImpl`.
- **HTTP en clair :** `src/debug/res/xml/network_security_config.xml` n'autorise le HTTP que vers `10.0.2.2`. Un téléphone sur le réseau local (IP du PC, par exemple `192.168.x.x`) serait refusé. La release interdit le HTTP partout.
- **Room :** la seule base est `downloads.db` (`downloaded_videos`, ADR-019, ADR-022). Elle stocke des chemins de fichiers locaux, pas d'URL absolue : un changement d'adresse ne la périme pas. Pas de cache HTTP OkHttp.
- **Coil, ExoPlayer :** ils chargent l'URL qu'on leur donne, déjà résolue ; leurs caches sont indexés par URL, donc l'ancienne adresse n'est plus consultée. Le téléchargement Media3 utilise `DefaultHttpDataSource` et la clé `videoId` (ADR-022). Aucun des deux ne lit `API_BASE_URL`.
- **Mémoire de l'application :** les ViewModels, la liste des comptes de l'ADR-022 (avec l'adresse des avatars) et les écrans déjà chargés contiennent des URL absolues résolues avec l'ancienne adresse.
- **Session (ADR-008, ADR-022) :** les jetons sont émis par un serveur précis (clé de signature, refresh token dans son Redis). Sur un autre serveur, l'access token est refusé, le refresh échoue, et la session expire.
- **Notifications (ADR-020) :** le jeton FCM est enregistré dans la table `device_tokens` du serveur contacté. Un autre serveur ne le connaît pas avant le prochain enregistrement (connexion ou bascule de compte).
- **URL absolues renvoyées par le backend :** le back-office accepte des URL complètes pour les médias (`admin.bodies.ts`), par exemple `http://localhost:3000/...` en développement. Une telle URL est gardée telle quelle par `resolveMediaUrl` : elle n'est pas joignable depuis un téléphone, quelle que soit l'adresse réglée. Ce n'est pas un défaut de cette décision, mais un piège pour le test.
- **DataStore :** deux existent, `session` (jetons chiffrés, ADR-008) et `preferences` (réglages de l'appareil : onboarding, centres d'intérêt, `share_usage_stats`, lecture automatique).
- Il n'existe pas de route de santé dans le backend.

## Décision
Rendre l'adresse du serveur réglable dans l'application, en version debug uniquement.

- **Où :** Vous → Paramètres, entrée « Adresse du serveur ». Le message d'erreur « Impossible de joindre le serveur » (`R.string.error_network`) offre un lien direct vers ce réglage, en debug seulement.
- **Valeur par défaut :** l'adresse actuelle (`BuildConfig.API_BASE_URL`, issue de `apiBaseUrl`). Un bouton « Revenir à l'adresse par défaut » la rétablit.
- **Test de connexion :** un bouton indique tout de suite si le serveur répond, avant ou sans enregistrer l'adresse. Il utilise une route publique existante de l'API (aucune route ajoutée) et son propre appel, indépendant des clients configurés.
- **Stockage :** l'adresse saisie est écrite dans le DataStore `preferences` (réglage de l'appareil, non secret), pas dans `session`. Elle est partagée par tous les comptes de l'appareil, comme les autres préférences (ADR-022).
- **Une seule source de vérité :** un fournisseur d'adresse unique remplace les cinq lectures directes de `BuildConfig.API_BASE_URL` (trois Retrofit, `RealtimeSocket`, `resolveMediaUrl`). En release, il renvoie toujours la constante de compilation, sans lire de DataStore ; l'entrée de Paramètres, le lien depuis l'erreur et l'écriture de l'adresse n'existent qu'en debug.
- **Validation de la saisie :** adresse `http://` ou `https://` avec hôte, terminée par `/` (ajoutée si absente) ; sinon refus avec un message.
- **Application du changement :** au redémarrage de l'application, sans changement à chaud (décision 1 du lead).
- **Session :** chaque changement enregistré déconnecte tous les comptes et vide la liste des comptes ; les téléchargements sont conservés (décision 2 du lead).
- **Réseau debug :** le HTTP est autorisé vers toute adresse, en debug uniquement ; la release garde le HTTP interdit (décision 3 du lead).
- **Contrôle des URL localhost (demande complémentaire du lead, 5 novembre) :**
  - (a) vérifier que le catalogue (vidéos, miniatures, bannières, publicités) ne contient aucune URL absolue vers `localhost`, et convertir en chemins relatifs le cas échéant ;
  - (b) le back-office refuse à la saisie une URL absolue vers `localhost`, avec un message clair.
- **Hors périmètre :** backend (hors point (a) ci-dessus), release, liste de serveurs mémorisés, découverte automatique du serveur. Le back-office n'est modifié que pour le point (b).

## Alternatives
- **Reconstruire Retrofit et OkHttp à chaud** (les API deviennent des fournisseurs qui se recréent à chaque changement) :
  - Avantages : effet immédiat.
  - Inconvénients : les douze API Retrofit, `UploadApi` et `RefreshApi` sont des singletons Hilt injectés partout ; il faut les passer derrière un fournisseur, fermer les anciennes connexions, et traiter `RealtimeSocket` à part. Beaucoup de code touché pour un réglage de test.
- **Intercepteur OkHttp qui réécrit l'hôte à chaque requête :**
  - Avantages : un seul point, les Retrofit gardent leur base de compilation, effet immédiat pour l'API.
  - Inconvénients : ne couvre ni `RealtimeSocket` (son propre client), ni `resolveMediaUrl` (URL absolues déjà construites), ni Coil et Media3 (leurs propres clients) ; il faudrait les traiter un par un. Le client de `RefreshApi` est volontairement sans intercepteur. Risque d'une moitié de l'application sur l'ancien serveur.
- **Lire l'adresse au démarrage, application redémarrée après un changement :**
  - Avantages : aucun singleton recréé ; tout est cohérent au démarrage (API, WebSocket, médias, session) ; très peu de code.
  - Inconvénients : redémarrage demandé (l'application peut se relancer elle-même ou le demander à l'utilisateur) ; un changement n'est pas instantané.
- **Source set debug séparé (fournisseur et écran différents par variante) :**
  - Avantages : sépare nettement le code debug de la release ; le réglage n'existe pas du tout en release.
  - Inconvénients : seul, il ne règle rien : l'adresse resterait fixée à la compilation. Il reste utile pour y ranger l'écran, le stockage et le fournisseur d'adresse debug, à côté d'une version release constante.
- **Garder `-PapiBaseUrl=...` et recompiler :** avantage, aucun travail. Inconvénient : c'est précisément ce que le lead veut éviter.

## Conséquences
### Positives
- Test sur téléphone physique sans recompiler.
- L'adresse par défaut reste la valeur de compilation : l'émulateur continue de fonctionner sans réglage.
- Un fournisseur unique remplace cinq lectures dispersées de `BuildConfig.API_BASE_URL`.
- Aucun changement côté serveur ni dans le back-office ; release inchangée (HTTP interdit, adresse constante).

### Négatives
- Les cinq points de lecture doivent tous passer par le fournisseur ; un oubli laisserait une partie de l'application sur l'ancien serveur.
- Les URL absolues déjà résolues (listes en mémoire, adresse des avatars de la liste des comptes de l'ADR-022) sont périmées après un changement ; il faut recharger les écrans ou redémarrer.
- La session émise par un serveur n'est pas valable sur un autre ; le jeton FCM n'est enregistré que sur le serveur contacté (décision 2 du lead : déconnexion de tous les comptes à chaque changement).
- Un changement d'adresse demande un redémarrage de l'application (décision 1 du lead).
- Les médias enregistrés en base avec une URL absolue `localhost` (back-office) restent injoignables depuis un téléphone ; d'où le contrôle du catalogue et le refus à la saisie dans le back-office (demande complémentaire du lead), qui ajoutent un travail hors de l'application Android.
- Le HTTP en clair vers une adresse saisie élargit la configuration réseau debug (décision 3 du lead).
- Un risque de fuite dans la release si le code n'est pas bien isolé : à vérifier en compilant la variante `release`.

## Décisions du lead développeur (5 novembre 2026)
Pour les trois points, la recommandation a été suivie.
1. **Application au redémarrage de l'application**, sans changement à chaud. L'adresse est lue une fois au démarrage, ce qui couvre Retrofit, WebSocket, médias et session d'un coup, sans recréer de singleton. Le test de connexion, lui, répond immédiatement sans redémarrer.
2. **Déconnexion de tous les comptes et vidage de la liste des comptes à chaque changement enregistré** (y compris le retour à l'adresse par défaut) : jetons et clés Keystore effacés, comme le retrait d'un compte de l'ADR-022, puis redémarrage. Les téléchargements (fichiers locaux) sont conservés.
3. **HTTP autorisé vers toute adresse en debug uniquement** : `cleartextTrafficPermitted="true"` par défaut dans la config réseau debug. La release garde le HTTP interdit.

Demande complémentaire du lead, le 5 novembre : (a) vérifier que le catalogue (vidéos, miniatures, bannières, publicités) ne contient aucune URL absolue vers `localhost` et convertir en chemins relatifs le cas échéant ; (b) le back-office refuse à la saisie une URL absolue vers `localhost`, avec un message clair. Voir la section « Décision ».

## Liens
- [ADR-008](ADR-008-authentification-jwt.md) : jetons émis par un serveur précis ; session refusée par un autre.
- [ADR-022](ADR-022-plusieurs-comptes-appareil.md) : liste des comptes et avatars en mémoire ; réglage partagé par tous les comptes ; modèle de retrait d'un compte.
- [ADR-020](ADR-020-notifications-fcm.md) : jeton FCM enregistré sur le serveur contacté.
- [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) : téléchargements locaux, non périmés par le changement d'adresse.
- [ADR-010](ADR-010-websocket-salles-compteurs.md), [ADR-004](ADR-004-temps-reel-websocket.md) : WebSocket construit sur la même adresse.
- [ADR-002](ADR-002-hebergement-videos.md), [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : chemins relatifs des médias, résolus avec l'adresse.
- [ADR-001](ADR-001-android-natif.md) : application Android native.

Amendé le 5 novembre : décisions du lead consignées ; ajout du contrôle des URL localhost (catalogue et saisie du back-office).
