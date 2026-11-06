# ADR-031 — Live partagé entre les applications de la classe : intégration du SDK commun (Crosslive)

## Statut
Proposé

## Contexte
Le 6 novembre 2026, jour du rendu, le lead développeur demande d'intégrer le SDK de live commun aux applications de la classe (dépôt `echimine/live-sdk`, public). Une fois intégré, nos utilisateurs voient les Lives de toutes les App membres, les rejoignent, discutent, réagissent, achètent le Produit épinglé et montent à l'écran comme Co-hôte ; ils peuvent aussi lancer leur propre Live, filmé par la caméra du téléphone.

Vocabulaire du SDK (`GLOSSARY.md`), repris tel quel dans cet ADR : **Live**, **App membre**, **App d'origine**, **Participant**, **Jeton de Participant**, **Hôte**, **Co-hôte**, **Spectateur**, **Animateur**, **Achat**, **Annonce d'achat**, **Source de produits**.

Source de vérité : le dépôt du SDK cloné dans `C:\Users\imade\Downloads\test\live-sdk`, **commit `9b2f639`, sans tag git**. En cas d'écart, son README fait foi.

### Ce que fournit le SDK (vérifié dans le dépôt)
- **Serveur commun** `https://live.echimine.fr`, média par LiveKit, exploité par echimine. Il ne connaît que les App membres enregistrées (`iss` + secret) ; le README liste `tiktok`, `twitch`, `instagram`, `snapcore`, `strava`. **Notre application n'y figure pas.**
- **Jeton de Participant** signé par le backend de chaque App membre : `POST /live/participant-token → { "token": "<JWT>" }`, HS256, claims `iss`, `sub`, `name` (1 à 30 caractères visibles), `picture` (https, facultatif), `iat` / `exp` (5 min au plus). Aucun rôle : le serveur commun décide seul qui est Hôte, Co-hôte ou Spectateur. L'exemple officiel (`examples/participant-token/server.mjs`) lit `LIVE_APP_ID` et `LIVE_TOKEN_SECRET`, tronque `name` par graphèmes (un emoji composé n'est jamais coupé) et n'envoie `picture` que si l'URL commence par `https://`.
- **SDK Kotlin**, modules `core` et `ui`. `LiveSdk.configure(serverUrl, tokenProvider)` ; le `tokenProvider` est appelé à **chaque connexion et reconnexion** et doit rendre un jeton frais, sans cache. Trois écrans Compose : `LiveListScreen(onOpenLive, onLaunch)`, `LiveLaunchScreen(onLaunched, onClose)`, `LiveScreen(liveId | live, onClose, productSource)` ; la navigation reste à l'application, chaque écran rend la main par ses callbacks (`kotlin/sample/MainActivity.kt` montre ce câblage). `onLaunch` est facultatif.
- **Thème** : `LiveSdkTheme(LiveTheme(accent, cornerRadius, fontFamily, label))`, `label` = libellé affiché en haut du Live, « Crosslive » par défaut.
- **Permissions** : `CAMERA`, `RECORD_AUDIO` et `INTERNET` déclarées par le module `ui` (via LiveKit) ; l'écran de lancement explique avant de demander (textes `LivePermissionTexts`) ; le Spectateur qui demande à monter les autorise d'abord.
- **App d'origine affichée** : les écrans affichent `pseudo · <iss>` (`ParticipantLine.kt`, `LiveChat.kt`) : la valeur brute de `iss` est **visible** par tous les Participants.
- **Achat** : « Confirmer l'achat » envoie `{ "type": "purchase", "productId" }` (fixture `protocol/fixtures/ws/client/purchase.json`) ; le serveur commun publie une Annonce d'achat dans le chat. Le glossaire le dit : « L'acte **simulé** […] ; aucun argent ne circule. »
- **Source de produits** : `ProductSource { query -> List<ProductSuggestion> }`, `ProductSuggestion(id, name, priceCents, currency, imageUrl?)`. `id` ne quitte jamais l'application. Recherche 300 ms après la frappe, 5 s au plus, 20 résultats au plus ; sont écartés les noms vides ou de plus de 80 caractères, les prix hors de 0 à 1 000 000 centimes, **une devise qui n'est pas un code ISO en majuscules** (`[A-Z]{3}`), **une image qui n'est pas en https**. Une exception affiche « Catalogue indisponible » ; la saisie manuelle reste possible.
- **Modération** (suppression, Shadowban, Bannissement) fournie par le SDK et le serveur commun.
- **Dépendance** documentée : JitPack `com.github.echimine.live-sdk:ui:<tag>`. LiveKit 2.29.2 exige le dépôt JitPack pour `com.github.davidliu:audioswitch`.

### Notre dépôt (vérifié)
- **Live shopping (ADR-029)** : notre serveur, flux = vidéo du catalogue lue en boucle, Stripe en mode test, seuil de 50 abonnés gardé côté serveur. Il place hors périmètre « la capture caméra et la diffusion réelle » et écarte « un serveur temps réel partagé » (section « Serveur partagé entre plusieurs applications d'étudiants »). L'ADR-015 (amendé le 5 novembre) répète que « la capture caméra et la diffusion réelle restent exclues ». CLAUDE.md, section « Hors périmètre », aussi.
- **Entrées existantes du direct** : onglet « En direct » de la feuille de création (`presentation/create/CreateSheet.kt`), écran de déblocage sous 50 abonnés puis formulaire de démarrage ; cartes de lives en cours (`presentation/live/LiveCard.kt`) en tête du fil (`home/HomeScreen.kt`) et sur la page de chaîne. Notre écran s'appelle aussi `LiveScreen` (`presentation/live/LiveScreen.kt`), comme celui du SDK.
- **Comptes** : `USER.displayName` (obligatoire) et `USER.avatarUrl` (facultatif, **chemin relatif** résolu par l'application avec `resolveMediaUrl`). Route d'auth : `JwtAuthGuard`. `@nestjs/jwt` est déjà une dépendance du backend.
- **Produits** : `GET /me/products` (ADR-029) rend `{ items: [{ id, name, price (centimes), currency, imageUrl (chemin relatif /media/product-images/…), description }] }`, liste vide pour un compte sans chaîne. `PRODUCT.currency` vaut `"eur"` **en minuscules** par défaut.
- **Android** : AGP 9.4.1, Gradle 9.6, Kotlin 2.4.21 intégré à AGP 9 (pas de plugin `org.jetbrains.kotlin.android`), cible JVM 11, Compose BOM 2026.02.01, coil 3.6.3, compileSdk 37, minSdk 26. Le SDK : AGP 8.11.1, Kotlin 2.2.20, plugin `kotlin-android`, JVM 17, Compose BOM 2025.10.00, coil 3.3.0, alias de catalogue absents du nôtre. Ses `build.gradle.kts` ne peuvent pas être inclus tels quels.
- **Nom affiché de l'application** : « Clone » (`app_name`). Règle de rédaction : aucun texte visible ne dit « YouTube », « cette application » ni « bientôt ».
- **Médias en développement** : servis en `http` ; en déploiement, R2 + CDN.

### Contraintes
- Application 100 % native (ADR-001) : le SDK est natif Kotlin/Compose, la contrainte est respectée.
- Le live shopping de l'ADR-029 reste tel quel.
- Secrets jamais dans le dépôt.
- Rien n'est ajouté qui n'ait été demandé (CLAUDE.md) ; on utilise les écrans fournis, sans réécrire la liste ou le Live avec l'API `core`.

## Décision
Intégrer le SDK commun : nos utilisateurs connectés participent aux Lives partagés de toutes les App membres, par les écrans du SDK et le serveur commun. Notre live shopping (ADR-029) est conservé à côté, inchangé.

### Deux lives distincts, deux entrées distinctes (D1, tranché par le lead)
Les deux lives ne sont pas mélangés :
- **Écran** : `LiveListScreen` est un écran entier ; mêler nos lives à sa liste obligerait à réécrire la liste avec l'API `core`, contre la consigne d'utiliser les écrans fournis.
- **Modèle** : identité multi-applications, Co-hôtes, mosaïque, caméra réelle et Achat simulé côté serveur commun ; chez nous, une chaîne, une vidéo du catalogue, un paiement Stripe vérifié.
- **Règles** : le seuil de 50 abonnés n'existe que chez nous et n'est vérifiable que sur notre serveur.
- **Clarté** : l'utilisateur doit savoir lequel il ouvre, en particulier avant d'acheter (paiement réel en mode test d'un côté, Achat simulé de l'autre).

**Emplacement, décidé par le lead :** une icône « Crosslive » dans la barre du haut de l'Accueil (le fil), à côté de la recherche, pour **tout compte connecté, avec ou sans chaîne**. Elle ouvre `LiveListScreen`, qui sert à la fois à regarder (liste, lien direct) et à lancer (`onLaunch` → `LiveLaunchScreen`). Raison : l'onglet « En direct » de la feuille de création n'est visible qu'aux comptes qui ont une chaîne, et sa zone fixe est occupée par notre live shopping, qu'on ne touche pas. La feuille de création et l'ADR-015 ne changent donc pas pour cette entrée. Les cartes `LiveCard` du fil et de la page de chaîne restent réservées à nos lives. Aucun onglet n'est ajouté à la barre du bas : un sixième onglet pour une fonction secondaire alourdirait la navigation de l'ADR-015.

Câblage, dans notre Navigation Compose : `LiveListScreen(onOpenLive = { id → LiveScreen(id) }, onLaunch = { → LiveLaunchScreen })`, `LiveLaunchScreen(onLaunched = { live → LiveScreen(live) }, onClose = retour)`, `LiveScreen(…, onClose = retour, productSource = notre catalogue)`. L'écran du SDK est importé sous un alias (son nom `LiveScreen` est déjà pris par notre écran).

### Capture caméra et diffusion réelle : dans le périmètre, uniquement par le SDK
La capture caméra et la diffusion réelle **entrent dans le périmètre, uniquement par le SDK et le serveur commun**. Notre serveur ne reçoit, ne transcode ni ne rediffuse aucun flux. Pour notre live shopping (ADR-029), rien ne change sur le fond : flux = fichier du catalogue lu en boucle. Voir « Effet sur les autres ADR ».

### Backend : `POST /live/participant-token`
- Réservée aux utilisateurs connectés (`JwtAuthGuard`) ; réponse `200 { "token": "<JWT>" }`.
- JWT **HS256** signé avec `@nestjs/jwt`, déjà présent (pas de nouvelle dépendance ; `jose`, utilisé par l'exemple officiel, n'est pas nécessaire), avec un secret **distinct** de nos secrets d'authentification.
- Claims :
  - `iss` = identifiant de notre App membre, lu dans `LIVE_SDK_APP_ID`. **Valeur provisoire décidée par le lead : `demo-android`**, l'App membre de test du serveur commun, le temps que notre application y soit déclarée (D3). Conséquence : nos Participants s'affichent « pseudo · demo-android » chez les autres App membres ;
  - `sub` = `USER.id` ;
  - `name` = `USER.displayName`, espaces de bord retirés, tronqué à 30 graphèmes avec `Intl.Segmenter` comme l'exemple officiel (un emoji composé n'est jamais coupé) ;
  - `picture` = avatar seulement si c'est une URL absolue en `https://` (D5), sinon absent : l'initiale du pseudo s'affiche ;
  - `iat` / `exp` : 5 minutes.
- Configuration : `LIVE_SDK_SERVER_URL`, `LIVE_SDK_APP_ID` et `LIVE_SDK_SECRET`, déjà remplies dans `backend/.env` (jamais commité) et ajoutées **vides** (pas commentées) à `backend/.env.example`. L'exemple officiel utilise d'autres noms (`LIVE_APP_ID`, `LIVE_TOKEN_SECRET`) ; nous gardons les nôtres. La route lit seulement `LIVE_SDK_APP_ID` (`iss`) et `LIVE_SDK_SECRET` (signature). `LIVE_SDK_SERVER_URL` n'est **pas lue** par la route, la signature n'en a pas besoin : elle est gardée dans `.env` à la demande du lead, comme référence de configuration. Sans `LIVE_SDK_APP_ID` ou `LIVE_SDK_SECRET`, la route répond une erreur claire (503) et le reste du serveur démarre normalement.
- Emplacement : dans le module `lives` (D8), dans une interface à part, sans toucher au live shopping.
- **Vérifié en réel (6 novembre)** : la route répond 200 avec `iss` = `demo-android`, `name` présent et `exp - iat` = 300 s ; 401 sans session. `GET https://live.echimine.fr/v1/lives` accepte notre jeton (200) et refuse un jeton altéré (401 `invalid_token`).

### Android
- **Dépendance** : deux modules locaux qui compilent les sources du clone (D2, décidé ; voir « Mode d'inclusion »).
- **Configuration** : `LiveSdk.configure(serverUrl = <adresse du serveur commun>, tokenProvider = …)` est appelé **à la première ouverture de Crosslive, pas au démarrage de l'application**. Raison : `configure` charge aussitôt la config du serveur commun avec un Jeton de Participant ; appelé au démarrage, il enverrait le nom de l'utilisateur à un tiers sans que celui-ci soit allé dans Crosslive. L'adresse vient d'une propriété Gradle `liveServerUrl` (par défaut `https://live.echimine.fr`), passée en constante de compilation comme `apiBaseUrl` ; l'application ne la reçoit pas du backend. Elle est distincte de `ServerAddress.baseUrl` (ADR-027) et n'est pas réglable dans l'application. Le `tokenProvider` appelle `POST /live/participant-token` avec le compte **actif** à chaque appel du SDK, sans cache ; sans compte connecté, ou sur refus, il lève une exception.
- **Visiteur** (D4) : l'icône « Crosslive » ouvre la connexion, comme l'achat Premium ; `LiveListScreen` n'est jamais ouvert sans compte.
- **Comptes multiples (ADR-022)** : le jeton suit le compte actif à chaque connexion. Une bascule de compte vide déjà la pile de navigation : un Live ouvert est fermé.
- **Permissions** : rien au démarrage. `LiveLaunchScreen` explique puis demande caméra et micro ; un Spectateur les autorise au moment de demander à monter. Textes : ceux de `LivePermissionTexts`, repris tels quels, aussi pour la fiche Play Store.
- **Thème** : `LiveSdkTheme(LiveTheme(accent = notre rouge, cornerRadius = celui de nos boutons, fontFamily = notre police (par défaut aujourd'hui), label = D6))` autour des trois écrans. Clair ou sombre suit le système.

### Produits : notre catalogue comme Source de produits
- `ProductSource` branchée sur `GET /me/products` (ADR-029), filtrée **localement** par la requête (minuscules, sans accents, comme la source factice du SDK). Un compte sans chaîne reçoit une liste vide (pas une exception : « Catalogue indisponible » serait faux).
- Correspondance : `id` = `PRODUCT.id` (ne quitte pas l'application) ; `name` ; `priceCents` = `price` ; `currency` = `currency` **mis en majuscules** (`"eur"` → `"EUR"`, sinon le SDK écarte tous nos produits) ; `imageUrl` = `resolveMediaUrl(imageUrl)` **si le résultat commence par `https://`**, sinon `null`.
- Conséquence : en développement (médias en `http`), nos produits passent **sans image** ; en déploiement (R2 + CDN en https), avec. La description n'est pas transmise (`ProductSuggestion` n'en a pas).

### Achat dans un Live partagé : simulé, sans paiement
**Différence explicite avec l'ADR-029.** Dans un Live partagé, « Confirmer l'achat » n'envoie que `productId` au serveur commun, qui publie une Annonce d'achat. Aucun paiement, aucun Stripe, aucune PaymentSheet, aucune ligne `LIVE_PURCHASE` chez nous, rien dans le back-office. C'est le comportement du SDK, à annoncer tel quel à l'oral.

### Données personnelles (ADR-013)
Le Jeton de Participant envoie au serveur commun, exploité par un tiers (echimine), l'identifiant (`USER.id`), le nom affiché et, s'il est en https, l'avatar de l'utilisateur. Le nom et l'avatar sont **vus par les utilisateurs des autres App membres** ; l'identifiant circule dans les messages du protocole (`author.id`), sans être affiché. Le chat, les Réactions et les Achats d'un Live partagé sont traités par le serveur commun, pas par nous. Aucun événement PostHog n'est ajouté (comme l'ADR-029, D12). Rien n'est envoyé pour un visiteur, ni pour un utilisateur connecté qui n'a jamais ouvert Crosslive : `LiveSdk.configure`, qui demande un premier jeton, n'est appelé qu'à la première ouverture de Crosslive.

### Hors périmètre maintenu
Enregistrement ou rediffusion des Lives partagés ; notifications de début de Live partagé (ADR-020 inchangé) ; mélange des Lives partagés avec le fil ou avec nos cartes `LiveCard` ; modération de notre côté (le SDK la fournit) ; mesures PostHog des Lives partagés ; page back-office des Lives partagés ; modification des sources du SDK.

### Ordre de livraison
Un commit par étape : (1) cet ADR ; (2) route backend et `.env.example` ; (3) dépendance et `LiveSdk.configure` ; (4) écrans, icône « Crosslive » du fil, thème ; (5) Source de produits. Chaque étape compile (`npm run build`, `gradlew.bat assembleDebug`).

## Alternatives
### Cohabitation
- **Deux entrées distinctes (retenu)** : voir Décision. Inconvénient : deux notions de « direct » à expliquer à l'oral.
- **Liste mélangée (nos lives et les Lives partagés)** : avantage, une seule porte d'entrée. Inconvénients : réécrire `LiveListScreen` avec l'API `core` (contraire à la consigne), deux modèles d'achat et deux règles d'accès sous une même apparence, risque de confusion au moment d'acheter. Écartée.
- **Remplacer notre live shopping par le SDK** : avantage, un seul système, caméra réelle. Inconvénients : abandon de l'achat Stripe vérifié et du seuil de 50 abonnés, livrés et acceptés (ADR-029) ; le lead veut garder l'ADR-029 tel quel. Écartée.
- **Entrée dans un nouvel onglet de la barre du bas** : avantage, très visible. Inconvénient, un sixième onglet pour une fonction secondaire, contraire à la barre de l'ADR-015. Écartée.
- **Entrée dans l'onglet « En direct » de la feuille de création** : avantage, le direct au même endroit. Inconvénients : onglet visible seulement des comptes avec chaîne, zone fixe occupée par notre live shopping, à modifier (et l'ADR-015 avec). Écartée par le lead.
- **Icône dans la barre du haut de l'Accueil (retenu, D1)** : avantages, visible de tout compte connecté, aucune modification de la feuille de création ni de notre live shopping. Inconvénient, une icône de plus sur le fil.

### Mode d'inclusion de la dépendance (D2)
- **Deux modules à nous, `android/live-sdk-core` et `android/live-sdk-ui`, avec nos fichiers de build AGP 9 dont les sourceSets pointent sur les sources du dossier cloné** (chemin réglé par la propriété Gradle `liveSdkDir`, par défaut `../../live-sdk` relatif à `android/`). **Retenu : la compilation l'a tranché.** `gradlew.bat assembleDebug` et `assembleRelease` passent. Il a suffi de déclarer `com.android.library` en `apply false` dans le build racine et d'ajouter à `settings.gradle.kts` le dépôt JitPack limité au groupe `com.github.davidliu` (audioswitch, exigé par LiveKit).
  - Avantages : aucune copie des sources ; compilé avec notre AGP, notre Kotlin et nos versions de Compose et coil (un écart d'API apparaît à la compilation, pas à l'exécution, et il n'y en a pas au commit `9b2f639`) ; JitPack n'est utilisé que pour audioswitch.
  - Inconvénients : tout poste qui compile doit avoir le clone à côté ; **la version du SDK n'est pas épinglée par notre dépôt** (le commit attendu, `9b2f639`, est noté à côté de `liveSdkDir`) ; versions de LiveKit, OkHttp et coroutines recopiées de son catalogue, à resynchroniser si le SDK évolue.
- **JitPack sur le hash d'un commit** (`com.github.echimine.live-sdk:ui:9b2f639`) : **alternative de repli** (poste sans clone, ou sources du SDK qui ne compileraient plus avec nos versions).
  - Avantages : une ligne de dépendance, version épinglée par notre dépôt, aucun clone nécessaire.
  - Inconvénients : aucun tag n'existe, JitPack doit construire le commit (`jitpack.yml`, JDK 17) au premier téléchargement ; artefacts compilés avec AGP 8 et Compose BOM 2025.10.00, consommés avec nos versions plus récentes (écart découvert à l'exécution seulement) ; dépendance réseau à JitPack pour tout le SDK.
- **Inclure les `build.gradle.kts` du SDK tels quels** (`includeBuild` ou `include`) : écartée, AGP 8 et plugin `kotlin-android` incompatibles avec notre AGP 9, alias de catalogue absents.
- **Copier les sources du SDK dans notre dépôt** : avantage, tout est épinglé. Inconvénient, une copie à resynchroniser à la main, et des sources tierces dans notre historique. Écartée.

### Identité
- **`sub` = `USER.id` (retenu)** : simple, stable, demandé par le lead. Inconvénient : notre identifiant interne (le même que celui envoyé à PostHog, ADR-013) circule chez un tiers et dans les messages reçus par les autres App membres.
- **`sub` = identifiant dérivé (HMAC de `USER.id`)** : avantage, l'identifiant interne ne sort pas. Inconvénient, une règle de plus, non demandée. Écartée sauf avis contraire du lead.

## Conséquences
### Positives
- Vrais Lives filmés, Co-hôtes, chat et Réactions entre toutes les applications de la classe, sans serveur média chez nous.
- Le live shopping de l'ADR-029 reste intact (achat Stripe vérifié, seuil de 50 abonnés).
- Une seule route backend, sans nouvelle dépendance ni table.
- Navigation, thème et permissions restent les nôtres.
- Notre catalogue de produits sert aussi aux Lives partagés.

### Négatives
- Identité provisoire : avec `iss` = `demo-android`, nos Participants s'affichent « pseudo · demo-android » chez les autres, et sont confondus avec ceux de l'application d'exemple du SDK et de toute autre App membre qui utiliserait la même identité de test, tant que l'identifiant et le secret définitifs ne sont pas attribués par echimine (D3).
- Deux « directs » coexistent : à expliquer à l'oral (lequel est réel, lequel paie).
- Achat simulé dans les Lives partagés, réel en mode test dans les nôtres : deux comportements différents derrière le même mot.
- Données personnelles (nom, avatar, identifiant) chez un tiers et visibles des autres applications.
- Dépendance à un serveur tiers et à sa disponibilité le jour de la démonstration.
- Modules locaux : le clone doit être présent et au bon commit sur tout poste qui compile ; version non épinglée par notre dépôt.
- En développement, ni avatar ni image de produit dans les Lives partagés (médias en `http`).
- Les écrans du SDK ne suivent pas notre travail TalkBack (ADR-030) : leur accessibilité dépend du SDK, et nous ne modifions pas ses sources.
- Les textes du SDK et la valeur affichée de `iss` échappent à notre `strings.xml` : la règle de rédaction s'applique au choix de `iss` et du libellé (D3, D6).
- Nouvelles permissions caméra et micro dans le manifeste fusionné de l'application.

## Effet sur les autres ADR
Appliqué par un humain à l'acceptation, pas avant.

### Effet sur l'ADR-029 (amendement sur place, il reste Accepté)
« Amendé le 6 novembre : la capture caméra et la diffusion réelle entrent dans le périmètre, uniquement par le SDK du live partagé et le serveur commun (ADR-031), jamais par notre serveur. Le live shopping de cet ADR est inchangé : flux = fichier du catalogue lu en boucle, paiement Stripe vérifié, seuil de 50 abonnés. La section « Serveur partagé entre plusieurs applications d'étudiants » et l'alternative « Serveur temps réel partagé comme serveur principal du live » restent vraies pour notre live shopping ; le serveur commun est intégré à côté, avec son propre protocole et ses propres écrans (ADR-031). Raison : ajustement de périmètre demandé par le lead développeur. »

### Effet sur l'ADR-015 (amendement sur place, il reste Accepté)
« Amendé le 6 novembre : la mention « la capture caméra et la diffusion réelle restent exclues » (amendement du 5 novembre) ne vaut plus que pour le live de la chaîne (ADR-029) ; un Live partagé, filmé par la caméra, se lance depuis l'icône « Crosslive » de la barre du haut de l'Accueil (ADR-031). La feuille de création, ses quatre onglets, le seuil de 50 abonnés et les textes de l'onglet « En direct » sont inchangés. Raison : ajustement de périmètre demandé par le lead développeur. » C'est le seul effet sur l'ADR-015 : l'entrée n'est pas dans la feuille de création, son texte « En savoir plus » reste exact (il parle de la diffusion depuis l'onglet « En direct »).

### Sans effet
ADR-013 (le flux vers le serveur commun est documenté ici, ses règles PostHog ne changent pas), ADR-020, ADR-022, ADR-027 (l'adresse configurable reste celle de notre serveur), ADR-030.

### CLAUDE.md (mis à jour par un humain à l'acceptation)
« Hors périmètre » (la capture caméra et la diffusion réelle passent par le SDK), Stack Android (SDK du live partagé, LiveKit), Backend (route `POST /live/participant-token`, variables `LIVE_SDK_SERVER_URL` (référence, non lue par la route), `LIVE_SDK_APP_ID`, `LIVE_SDK_SECRET`), Android (propriété Gradle `liveServerUrl`), Structure (`android/live-sdk-core`, `android/live-sdk-ui`, compilés depuis le clone `live-sdk` par `liveSdkDir`), section live (icône « Crosslive » de l'Accueil).

## Décisions ouvertes
- **D1 — Emplacement de l'entrée. Tranché par le lead** : icône « Crosslive » dans la barre du haut de l'Accueil, à côté de la recherche, pour tout compte connecté ; elle ouvre `LiveListScreen`.
- **D2 — Mode d'inclusion. Tranché par la compilation** : modules locaux pointant sur le clone (`assembleDebug` et `assembleRelease` passent) ; JitPack sur `9b2f639` reste le repli.
- **D3 — Valeur définitive de `iss`, à obtenir d'echimine.** Provisoirement `demo-android`, par décision du lead. La valeur est affichée à côté du pseudo (« Léa · <iss> ») chez tous les Participants. Proposition maintenue pour la valeur définitive : `clone`, qui reprend le nom affiché de l'application (« Clone ») et le format en minuscules des autres App membres, sans dire « YouTube ». Inconvénient : toutes les App membres sont des clones. Le passage à la valeur définitive est un simple changement de `LIVE_SDK_APP_ID` et `LIVE_SDK_SECRET` dans `backend/.env`, sans code.
- **D4 — Visiteur non connecté. Tranché par le lead** : l'icône ouvre la connexion ; aucune liste sans compte (pas de jeton possible).
- **D5 — Avatar (`picture`).** `USER.avatarUrl` est un chemin relatif : le backend ne connaît pas d'adresse publique pour le rendre absolu. Recommandation : envoyer `picture` seulement si `avatarUrl` est déjà une URL absolue en https, sinon l'omettre (l'initiale s'affiche), sans nouvelle variable d'environnement. Variante : une adresse publique des médias dans `.env` pour l'envoyer en déploiement.
- **D6 — Libellé du Live (`LiveTheme.label`) et nom de l'entrée.** Recommandation : garder « Crosslive », nom commun à toutes les App membres, et nommer l'entrée de la même façon : l'utilisateur distingue ainsi le Live partagé de notre « EN DIRECT ».
- **D7 — Seuil de 50 abonnés pour lancer un Live partagé.** Le serveur commun ne le connaît pas ; nous ne pourrions le vérifier que dans l'application (`onLaunch = null` sous 50), contre le principe « le serveur décide ». Recommandation : pas de seuil pour les Lives partagés. L'entrée étant dans le fil et non dans l'onglet « En direct », le texte « En savoir plus » de cet onglet reste exact et n'est pas modifié.
- **D8 — Emplacement de la route backend.** Recommandation : module `lives`, interface à part. Variante : un petit module dédié, plus net mais qui allonge la liste des modules de CLAUDE.md.
- **D9 — Caméra de l'émulateur.** Lancer un Live demande une caméra. L'émulateur Android propose une caméra virtuelle (scène virtuelle) ou la webcam de l'ordinateur. **À vérifier**, sans conclure ici : l'aperçu et la diffusion de `LiveLaunchScreen` fonctionnent-ils avec l'une ou l'autre ? Sinon, la démonstration du lancement se fait sur un téléphone réel.
- **D10 — Information de l'utilisateur.** Le nom et l'avatar partent chez un tiers et sont vus des autres applications. Recommandation : pas de nouvel écran (non demandé) ; le dire à l'oral et dans la fiche « Sécurité des données ». À confirmer par le lead.

## Liens
- [ADR-029](ADR-029-live-shopping.md) : notre live shopping, conservé à côté ; amendé à l'acceptation (capture caméra par le SDK seulement).
- [ADR-015](ADR-015-publication-depuis-mobile.md) : feuille de création, onglet « En direct », seuil de 50 abonnés, inchangés ; amendé à l'acceptation pour la seule mention de la capture caméra.
- [ADR-013](ADR-013-mesures-donnees-personnelles.md) : données personnelles ; le flux vers le serveur commun est décrit ici.
- [ADR-008](ADR-008-authentification-jwt.md), [ADR-022](ADR-022-plusieurs-comptes-appareil.md) : authentification et compte actif ; le jeton suit le compte actif.
- [ADR-027](ADR-027-adresse-serveur-configurable.md) : adresse de notre serveur, distincte de celle du serveur commun.
- [ADR-030](ADR-030-accessibilite-talkback.md) : TalkBack ; les écrans du SDK ne sont pas couverts.
- [ADR-014](ADR-014-paiement-stripe-mode-test.md) : paiement Stripe, absent des Lives partagés.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : chemins relatifs des médias (avatars, images de produits).
- [ADR-001](ADR-001-android-natif.md) : application native ; le SDK est natif Kotlin/Compose.
- [ADR-020](ADR-020-notifications-fcm.md) : aucune notification de début de Live partagé.
- SDK : `live-sdk/README.md`, `GLOSSARY.md`, `kotlin/README.md`, `kotlin/sample/`, `examples/participant-token/server.mjs`, `protocol/README.md` (commit `9b2f639`).
