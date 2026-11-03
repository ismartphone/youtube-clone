# Journal de développement

Journal reconstitué à partir de l'historique Git (46 commits) et des ADR de `docs/adr/`. Les heures
sont celles des commits. Il ne retrace que ce que ces sources enregistrent : une tâche non commitée,
une piste abandonnée ou un échange avec l'IA qui n'a laissé aucune trace écrite n'y figurent pas.

| Jour | Commits | Thème |
|---|---|---|
| [Jeudi 15 octobre](#jeudi-15-octobre) | 24 | Cadrage, socle des trois projets, authentification, temps réel |
| [Vendredi 16 octobre](#vendredi-16-octobre) | 8 | Animations, FFmpeg et vidéos locales, reprise des écrans d'après les maquettes |
| [Lundi 19 octobre](#lundi-19-octobre) | 10 | Onglet Vous, historique, Shorts, page de chaîne, bannières, connexion |
| [Mardi 3 novembre](#mardi-3-novembre) | 4 | Premium, mesures d'audience, test A/B |

Aucun commit les 17 et 18 octobre, ni du 20 octobre au 2 novembre.

---

## Jeudi 15 octobre

### Ce qui a été fait

**Cadrage (9 h 24).** Le premier commit pose le cadre avant toute ligne de code applicatif :
- les ADR-001 à ADR-007 et leur index : Android natif seul, vidéos hébergées chez nous, NestJS en
  hexagonal, temps réel par WebSocket, publication des vidéos depuis le seul back-office, PostHog,
  offre Premium ;
- le `CLAUDE.md` et le sous-agent `adr-architecte` ;
- le `docker-compose.yml` (PostgreSQL 17, Redis 8) et le projet NestJS avec Prisma 7 : schéma,
  migration initiale, seed.

**Backend, lecture du catalogue (10 h 00).** Module `videos` en lecture seule : liste paginée filtrable
par format et catégorie, détail d'une vidéo et d'une chaîne, comptage des vues dans Redis reporté en
base (ADR-004). L'URL d'une vidéo Premium est masquée tant que l'accès n'est pas vérifié. Les dates
de publication du seed sont étalées dans le temps, et un `.env.example` est ajouté.

**Android, socle (10 h 15 – 11 h 15).** Création du projet, fil d'accueil branché sur l'API, écran de
lecture avec ExoPlayer, onglet Shorts en défilement vertical.

**Authentification (11 h 24 – 12 h 02).** JWT côté backend (access token de 15 minutes, refresh token
de 7 jours à usage unique dans Redis, argon2), puis côté Android (intercepteur OkHttp, tokens
chiffrés par l'Android Keystore dans DataStore). L'ADR-008 est rédigé juste après, puis accepté.
L'ADR-007 passe en Accepté dans le même mouvement.

**Chaînes et abonnements (13 h 22).** Création de chaîne, abonnement gratuit (`FOLLOW`), fil des
abonnements côté backend ; page de chaîne et onglet Abonnements côté Android.

**Back-office (14 h 07 – 14 h 09).** Routes `/admin` protégées par le rôle ADMIN, puis le back-office
Next.js en MVC (connexion, tableau de bord, vidéos, catégories). L'ADR-009 fixe le stockage de ses
tokens dans le `localStorage`, avec un relais `/api` dans Next.js pour éviter le CORS.

**Temps réel (15 h 47 – 15 h 48).** Commentaires, « j'aime » et passerelle WebSocket côté backend
(nouvelle colonne `likeCount`), commentaires et compteurs en direct côté Android. L'ADR-010 précise
l'ADR-004 : JWT dans le handshake, une salle par vidéo, reconnexion avec délai croissant.

**Premier lancement (16 h 15 – 16 h 28).** Liste publique des catégories, écran d'accueil du premier
lancement avec choix des centres d'intérêt, renommage de l'application en « YouTube Clone »,
acceptation de l'ADR-010.

### Problèmes rencontrés et solutions

- **Statut des ADR incohérent entre le fichier et l'index.** Dès le premier commit, l'index annonçait
  l'ADR-007 « Accepté » alors que son fichier était encore « Proposé » ; c'est corrigé à 11 h 44.
  Le même écart se reproduit avec l'ADR-008 : l'index passe en « Accepté » à 12 h 02, le fichier
  seulement à 14 h 09. **Solution :** une règle est ajoutée au `CLAUDE.md` à 16 h 15 — accepter un
  ADR, c'est modifier son fichier **et** l'index, qui doivent toujours concorder. Les acceptations
  suivantes (ADR-009, ADR-010) touchent bien les deux fichiers dans le même commit.
- **Envoi des commentaires : écart avec l'ADR-004.** L'ADR-004 prévoyait que le client envoie ses
  commentaires sur la connexion WebSocket. L'implémentation choisit de les publier par l'API REST,
  pour garder la validation, l'authentification et l'écriture en base sur le chemin HTTP déjà
  testé. **Solution :** plutôt que de modifier l'ADR-004 en silence, l'ADR-010 documente l'écart
  comme volontaire, note qu'il affaiblit l'argument de l'ADR-004 contre les Server-Sent Events, et
  laisse l'ADR-004 accepté tel quel.
- **Protocole WebSocket.** Socket.IO ne parle pas le WebSocket standard attendu par le client
  OkHttp de l'application. **Solution :** adaptateur `ws` de NestJS, point d'entrée `/realtime` sur
  le port de l'API (ADR-010).
- **Compteur de « j'aime » et charge sur la base.** **Solution :** chaque ligne `LIKE` est écrite tout
  de suite, mais le compteur passe par un tampon Redis reporté en base toutes les 30 secondes,
  comme les vues ; l'affichage additionne la base et les variations en attente (ADR-010).
- **Limites assumées, consignées dans l'ADR-010 :** salles gardées en mémoire (une seule instance
  du backend), événements perdus pendant une coupure (l'écran relit l'état à la reconnexion),
  « j'aime » des Shorts pas encore branchés.

---

## Vendredi 16 octobre

### Ce qui a été fait

**Animations (8 h 59 – 9 h 08).** Écran de chargement animé au lancement, animations de navigation et
micro-animations.

**Miniatures et vidéos locales (14 h 42 – 14 h 48).** Génération des miniatures avec FFmpeg (image à
10 % de la durée, bouton « Régénérer la miniature », commande `npm run thumbnails:regenerate`), puis
hébergement local des vidéos du seed avec `npm run seed:videos` (téléchargement depuis Pexels,
vérification par ffprobe, réencodage en 720p). L'ADR-011 est rédigé avec le code, puis clarifié et
accepté.

**Maquettes et reprise des écrans (15 h 19 – 15 h 47).** Index des captures de référence
(`docs/maquettes-reference.md`), puis reprise de l'écran d'accueil et de l'écran de lecture
d'après ces maquettes.

### Problèmes rencontrés et solutions

- **FFmpeg : comment l'obtenir, et que faire s'il manque ?** Le paquet npm `ffmpeg-static` aurait
  téléchargé un binaire d'environ 70 Mo à chaque installation. **Solution :** FFmpeg devient une
  dépendance système (`winget install Gyan.FFmpeg`), surchargeable par `FFMPEG_PATH` ; s'il manque,
  le serveur démarre quand même et seule la génération de miniatures répond 503 (ADR-011).
- **Adresse des fichiers différente selon le client.** L'émulateur joint l'API par `10.0.2.2:3000`,
  le navigateur du back-office par `localhost` : une URL complète en base ne convient qu'à l'un des
  deux. **Solution :** la base stocke un chemin relatif (`/media/...`) que chaque client complète
  avec sa propre adresse d'API (ADR-011). La règle du `CLAUDE.md` passe de « l'URL des fichiers
  vidéo » à « l'URL ou le chemin relatif des fichiers ».
- **Contradiction de l'ADR-011 avec des ADR acceptés.** Le réencodage de `seed:videos` semblait
  contredire l'ADR-002 (« sans étape de transcodage »), et la commande elle-même pouvait passer pour
  une deuxième entrée au catalogue, contraire à l'ADR-005. **Solution, avant l'acceptation :**
  l'ADR-002 précise « sans transcodage à l'exécution » (le serveur sert les fichiers tels quels),
  et l'ADR-011 explique que le réencodage est une préparation hors ligne de données de
  démonstration, sans aucune route exposée pour envoyer une vidéo.
- **Sécurité de FFmpeg.** **Solution :** protocoles limités à http, https, tcp et tls pour une URL
  externe, protocole `file` seul pour un fichier local, et chemin empêché de sortir du dossier de
  stockage (ADR-011).
- **Droits sur les maquettes.** Les captures de référence viennent de Mobbin et ne doivent pas être
  redistribuées. **Solution :** le dossier `android/maquettes/` est exclu du dépôt, seul l'index est
  versionné.

---

## Lundi 19 octobre

### Ce qui a été fait

- **Onglet Vous (18 h 02 – 18 h 31).** Route de la chaîne de l'utilisateur connecté, reprise de
  l'onglet Vous, puis historique de visionnage et vidéos aimées côté backend, branchés dans l'onglet.
- **Shorts et page de chaîne (18 h 45 – 18 h 54).** Reprise de l'onglet Shorts et de la page de
  chaîne.
- **Bannières (19 h 17).** Bannière des chaînes (migration `add_channel_banner`) et commande
  `npm run seed:banners`, qui tire les bannières des vidéos du seed avec FFmpeg.
- **Shorts d'une chaîne (19 h 20).** Ils s'ouvrent dans le lecteur vertical.
- **Connexion (20 h 20).** Reprise de la connexion, de l'inscription et de l'onboarding.
- Ajout du saut de ligne final à l'index des ADR.

### Problèmes rencontrés et solutions

- **Le dossier de stockage n'est pas versionné.** Après un clone, ni les vidéos ni les bannières ne
  sont présentes. **Solution :** deux commandes à lancer dans l'ordre, `npm run seed:videos` puis
  `npm run seed:banners`, documentées dans `backend/README.md`.
- **Conséquence non vue sur le moment.** L'écran d'accueil du premier lancement, refait en un seul
  écran, ne contient plus le choix de refuser les mesures que prévoyait l'ADR-006. L'écart n'est
  relevé et traité que le 3 novembre, par l'ADR-013.

---

## Mardi 3 novembre

### Ce qui a été fait

**Offre Premium (9 h 11).** Module `premium` côté backend (achat en attente, jeton d'achat stocké
dans Redis, abonnement `PREMIUM_SUBSCRIPTION` d'un mois, accès aux vidéos exclusives vérifié par
l'API), paywall, feuille de paiement simulée et lecture en arrière-plan côté Android. Les ADR-012
et ADR-013 sont livrés et acceptés avec ce commit.

**Correction de la lecture en double (10 h 48).** Voir ci-dessous.

**Mesures d'audience (10 h 48).** Événements PostHog envoyés directement depuis l'application :
ouverture, écrans affichés, inscription, connexion, lecture, « j'aime », commentaire, abonnement,
parcours du paywall. Nouvel écran Paramètres avec l'interrupteur « Partager des statistiques
d'usage ». Clés lues dans `android/secrets.properties`, exclu du dépôt.

**Test A/B (10 h 55).** Drapeau PostHog `paywall-title` à deux variantes (« Passez à Premium » ou
« Premium pour 4,99 € par mois »), réparties à 50/50. Hypothèse et métrique dans
[`docs/ab-test-paywall.md`](ab-test-paywall.md).

### Problèmes rencontrés et solutions

- **Le paiement prévu par l'ADR-007 est impossible.** Le mode test de Google Play Billing suppose une
  application déposée sur la Play Console, un compte développeur payant, un produit et des testeurs
  configurés ; les anciens produits de test (`android.test.purchased`) n'existent plus. **Solution :**
  l'ADR-012 fait jouer au backend le rôle du fournisseur de paiement (jeton d'achat émis par
  `POST /me/premium/checkout`, vérifié par `POST /me/premium/subscribe`). Il ne remplace que la partie
  paiement de l'ADR-007, qui reste valable pour le reste ; le serveur reste seul juge de l'activation.
- **Les mesures ne sont plus anonymes.** Suivre un utilisateur entre ses sessions et mesurer les
  conversions par compte impose d'associer les événements à un identifiant : ils deviennent
  pseudonymes, donc des données personnelles au sens du RGPD, ce que l'ADR-006 excluait.
  **Solution :** l'ADR-013 remplace la section RGPD de l'ADR-006 — identification par le seul UUID
  du compte (jamais l'email, le nom ou le handle), visiteur anonyme, refus des mesures déplacé dans
  Paramètres. Il note aussi que le partage activé par défaut est un choix de désactivation et non un
  consentement préalable, à justifier à l'oral.
- **Lecture en double en Premium.** Pour permettre la lecture en arrière-plan, l'écran de lecture ne
  se mettait plus en pause sur `ON_STOP` : en ouvrant une autre vidéo par-dessus, la précédente
  continuait de jouer derrière. **Solution :** la pause n'est plus évitée que lorsque l'application
  passe en arrière-plan, et l'écran reprend la lecture en arrière-plan quand on y revient.
- **Test A/B sans réseau.** Le paywall ne doit jamais attendre PostHog. **Solution :** drapeaux chargés
  au démarrage et gardés en cache ; sans drapeau, sans réseau ou avec les mesures désactivées, la
  variante par défaut s'affiche immédiatement. Limite notée : avec le peu d'utilisateurs d'un projet
  d'école, le test démontre la mise en place, pas un résultat significatif.
