# Projet — Clone de YouTube

Monorepo de trois projets : application Android native, backend, back-office web.
Rendu le 6 novembre 2026. Projet d'école, développé sous Windows.

## Règles générales

- Répondre en français.
- Les décisions d'architecture sont dans `docs/adr/`. **Lire les ADR concernés avant de proposer du code**, respecter les ADR acceptés, signaler toute contradiction.
- Ne jamais ajouter de fonctionnalité, de dépendance ou d'écran qui n'a pas été demandé.
- En cas de doute ou d'information manquante, poser la question au lieu de deviner.
- Le sous-agent `adr-architecte` (`.claude/agents/`) est le seul à rédiger les ADR. Seul un humain passe un ADR en « Accepté ».
- Passer un ADR en Accepté signifie modifier son fichier ET l'index `docs/adr/README.md`. Les deux doivent toujours concorder.
- Modifier un ADR accepté :
  - changement de décision sur le fond → un nouvel ADR qui remplace l'ancien (comme pour les ADR-005, 012 et 002) ;
  - précision, ou ajustement de périmètre explicitement demandé par le lead développeur → amendement sur place, avec une ligne datée en fin d'ADR, « Amendé le 4 novembre : … », et la raison. L'ADR reste « Accepté ».
- Pas d'hybride : l'application mobile est 100 % native (contrainte du sujet).

## Structure

```
android/      application Android (Kotlin + Jetpack Compose)
backend/      API REST + serveur WebSocket (NestJS)
backoffice/   administration web (Next.js)
docs/adr/     décisions d'architecture
```

`android/live-sdk-core` et `android/live-sdk-ui` compilent les sources du clone `live-sdk` placé à côté du projet (propriété Gradle `liveSdkDir`, commit `9b2f639`) : SDK du live partagé, ADR-031.

## Stack

**Android** — Kotlin, Jetpack Compose, Media3 ExoPlayer (lecture vidéo), Room (cache local),
Retrofit (API), OkHttp WebSocket (temps réel), Coil (images), Hilt (injection), Navigation Compose, DataStore,
Firebase Cloud Messaging (notifications de publication, ADR-020), Stripe PaymentSheet (paiement Premium, ADR-014 ; achats du live shopping, ADR-029),
SDK du live partagé Crosslive avec LiveKit (ADR-031).
Architecture **MVVM + Clean Architecture** : `presentation/` · `domain/` · `data/`.
minSdk 26.

**Backend** — NestJS (TypeScript), PostgreSQL, Prisma, Redis (cache, sessions, compteurs),
passerelle WebSocket NestJS, firebase-admin (envoi des notifications FCM), Stripe en mode test (Premium, ADR-014 ; achats du live shopping, ADR-029). Stockage vidéo : dossier local en développement, Cloudflare R2 + CDN au déploiement.
Architecture **hexagonale, monolithe modulaire** : `domain/` · `application/` · `infrastructure/` · `interfaces/`.
Modules : auth, channels (dont les abonnements), videos, comments, premium, realtime, playlists, notifications, ads, messaging, lives, products (les deux derniers : ADR-029).
Route `POST /live/participant-token` (module lives, ADR-031) : Jeton de Participant du live partagé ; variables `LIVE_SDK_APP_ID`, `LIVE_SDK_SECRET` et `LIVE_SDK_SERVER_URL` dans `backend/.env`.

**Back-office** — Next.js (TypeScript), Tailwind CSS.
Architecture **MVC** : `app/` · `controllers/` · `models/` · `views/` · `services/`.
Le back-office ne contacte jamais PostgreSQL directement : tout passe par l'API.

**Mesure** — PostHog (cloud Europe) pour les mesures d'audience et le test A/B.
Les événements partent de l'application directement vers PostHog, pas par le backend.

## Modèle de données

`USER` · `CHANNEL` · `VIDEO` · `CATEGORY` · `COMMENT` · `LIKE` · `FOLLOW` · `PREMIUM_SUBSCRIPTION` · `WATCH_HISTORY` · `POST` · `PLAYLIST` · `PLAYLIST_ITEM` · `DEVICE_TOKEN` · `AD` · `COMMENT_LIKE` · `CONVERSATION` · `MESSAGE` · `LIVE` · `PRODUCT` · `LIVE_MESSAGE` · `LIVE_PURCHASE` (les quatre dernières : ADR-029 ; `LIVE_RESTRICTION`, prévue pour la modération, n'est pas créée : étape 4 sautée)

- `LIKE.type` vaut `LIKE` ou `DISLIKE` : « J'aime » et « Je n'aime pas » s'excluent (ADR-019).
- `FOLLOW` = abonnement **gratuit** à une chaîne. `PREMIUM_SUBSCRIPTION` = abonnement **payant**. Ne jamais confondre les deux.
- `FOLLOW.notificationLevel` porte la cloche : `ALL` (vidéos et Shorts), `PERSONALIZED` (par défaut : vidéos classiques, sans les Shorts) ou `NONE` (ADR-020).
- `VIDEO.format` vaut `SHORT` ou `LONG` : Shorts et vidéos classiques partagent la même table.
- `DEVICE_TOKEN` = jeton FCM d'un appareil connecté, lié à `USER` (plusieurs appareils possibles) ; un jeton refusé par Firebase est supprimé. `VIDEO.notifiedAt` = date d'envoi des notifications de publication : une vidéo n'est annoncée qu'une fois (ADR-020).
- `AD` = publicité vidéo (ADR-021), lue avant les vidéos classiques et dans le bandeau sponsorisé des comptes non Premium, tirée au sort parmi toutes les lignes. Ce n'est pas une vidéo du catalogue : ni vue, ni page, ni recherche. Le serveur décide seul s'il en joint une.
- `COMMENT.parentId` porte les réponses, sur un seul niveau : une réponse à une réponse est rangée sous le commentaire de premier niveau. `COMMENT_LIKE` = avis sur un commentaire (`LIKE` ou `DISLIKE`, un par utilisateur) ; seuls les « j'aime » sont comptés, dans `COMMENT.likeCount`. `COMMENT.creatorHeartedAt` = cœur du propriétaire de la chaîne (ADR-023).
- `CONVERSATION` = conversation à deux, une seule par paire (identifiants rangés dans l'ordre) ; `MESSAGE` = message de 2000 caractères au plus, ni modifié ni supprimé ; `MESSAGE.videoId` = vidéo partagée, sans clé étrangère (supprimée, elle s'affiche « indisponible »), sa carte construite à chaque lecture pour celui qui lit. Lu / non lu par une date de lecture par participant sur `CONVERSATION`. `USER.messageNotificationsEnabled` = réglage « Notifications de messages » (ADR-025).
- Préférences de l'appareil (DataStore `preferences` de l'application, hors base) ajoutées par l'ADR-028 : `preferred_format` (`LONG` ou `SHORT`, absent sans choix) décide de l'onglet d'arrivée à chaque ouverture, choisi à l'étape 5 de l'onboarding et modifiable dans Paramètres ; `onboarding_feedback` = réglage « Sons et vibrations ».
- Live shopping (ADR-029) : `LIVE` = live d'une chaîne (titre, `videoId` = vidéo du catalogue utilisée comme flux, état `LIVE` ou `ENDED`, début, produit épinglé) ; un seul live en cours par chaîne, démarrage gardé côté serveur à 50 abonnés (`FOLLOW`). `PRODUCT` = produit rattaché à une chaîne (nom, prix en centimes, image en chemin relatif, description), géré depuis le back-office. `LIVE_MESSAGE` = message du chat d'un live (`CHAT`) ou annonce d'achat écrite par le serveur (`PURCHASE`), jamais le montant ni le moyen de paiement ; distinct de `COMMENT` et de `MESSAGE`. `LIVE_PURCHASE` = achat pendant un live, nom du produit et montant recopiés, référence du paiement unique (usage unique, vérifiée côté serveur comme `PREMIUM_SUBSCRIPTION.purchaseToken`). `LIVE_RESTRICTION` = bannissement ou masquage d'une personne dans un live : **non créée**, l'étape 4 (modération, bonus) a été sautée faute de temps. Les images des produits sont rangées dans le dossier `product-images/` du stockage (chemin relatif en base). Données de démonstration : `npm run seed:live-products` (cinq produits de Studio Libre, rejouable) et `npm run seed:live-fans` (49 abonnés fictifs). Un live se termine depuis l'application (diffuseur) ou par `POST /admin/lives/:id/end` (bouton « Terminer » du back-office). Les spectateurs ne sont pas stockés : ils sont comptés d'après la salle WebSocket du live.
- `VIDEO.isPremium` marque les vidéos exclusives. L'accès est **toujours vérifié côté serveur**, jamais seulement dans l'application.
- La base ne stocke que l'URL ou le chemin relatif des fichiers, jamais les fichiers eux-mêmes.

## Périmètre

**Socle prioritaire** — comptes et chaînes (inscription, connexion, profil, création de chaîne, page de chaîne, abonnement) ; vidéos et Shorts (fil d'accueil, écran de lecture, onglet Shorts en défilement vertical, compteur de vues).

**Ensuite** — « j'aime », commentaires en temps réel, offre payante, mesures et test A/B, recherche, historique.

**Live shopping (ADR-029)** — entre dans le périmètre et lève l'ancienne exclusion du « live ». Démarrer un live depuis l'onglet « En direct » (chaîne de 50 abonnés au moins), le regarder avec chat, compteur de spectateurs et réactions, produit épinglé et achat Stripe en mode test, pages Produits et Lives du back-office. Plan en quatre étapes, un commit par étape : 1) démarrer et regarder avec chat et compteur ; 2) réactions ; 3) produit épinglé et achat ; 4) modération par le diffuseur (bonus) : **sautée**, hors livraison. Le flux d'un live est un fichier existant du catalogue, lu en boucle et présenté comme un direct ; tout le reste est réel et tourne sur notre serveur.

**Live partagé Crosslive (ADR-031)** — icône « Crosslive » en haut de l'Accueil, pour tout compte connecté ; elle ouvre les écrans du SDK commun (liste, lancement, Live), distincts du live shopping. L'achat y est simulé par le serveur commun, sans paiement.

**Hors périmètre** — version iOS, monétisation des créateurs, mot de passe oublié, tendances, signalement, « à regarder plus tard ». La capture caméra et la diffusion réelle ne sont plus hors périmètre : uniquement par le SDK et le serveur commun du live partagé (ADR-031), jamais par notre serveur ; le live shopping garde son flux simulé (ADR-029).

## Conventions

- Code et noms de variables en anglais, commentaires et documentation en français.
- Kotlin : `PascalCase` pour les composables et les classes, `camelCase` pour le reste.
- TypeScript : `camelCase` pour les fonctions, `PascalCase` pour les types et les composants React.
- Un commit par unité de travail, message en français à l'impératif.
- Vérifier que le projet compile avant d'annoncer qu'une tâche est terminée
  (`gradlew.bat assembleDebug` pour Android, `npm run build` pour le backend et le back-office).

