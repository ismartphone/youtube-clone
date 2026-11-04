# ADR-019 — Boutons « Je n'aime pas », « Télécharger », « Enregistrer » et « Aide » : avis négatif, téléchargements Premium, playlists et écran d'aide

## Statut
Accepté

## Contexte
Quatre boutons de l'application affichent aujourd'hui un message « Bientôt disponible » au lieu d'agir : « Je n'aime pas », « Télécharger » et « Enregistrer » sous la vidéo (écran de lecture), et « Aide » dans l'écran Vous. Le 4 novembre 2026, le lead développeur a décidé de les rendre fonctionnels.

Le même jour, il a fixé une règle de rédaction : aucun texte visible par l'utilisateur ne mentionne YouTube, ne dit « dans cette application » ni n'annonce ce qui n'est pas développé ; un bouton sans fonction n'a pas sa place. Le message « Bientôt disponible » contrevient lui-même à cette règle.

Contraintes :
- le rendu est le 6 novembre 2026 et nous sommes le 4 : il reste deux jours ;
- l'application reste 100 % native (ADR-001) ; la lecture passe par Media3 ExoPlayer, déjà présent avec son module HLS (ADR-017) ;
- l'accès Premium et la visibilité sont toujours vérifiés côté serveur, jamais seulement dans l'application (ADR-007, ADR-016) ;
- le statut Premium est stocké dans PREMIUM_SUBSCRIPTION : un mois, sans renouvellement automatique, avec une date de fin (`expiresAt`) ; il peut être résilié par DELETE /me/premium (ADR-007, ADR-012, ADR-014) ;
- aujourd'hui, LIKE est une table (userId, videoId), de clé primaire composée ; chaque ligne est écrite immédiatement en base, et seul le compteur `likeCount` de VIDEO passe par un tampon Redis reporté en base toutes les 30 secondes (ADR-004, ADR-010) ; les « j'aime » sont envoyés par POST et DELETE /videos/:id/like et diffusés par l'événement `likes:update` (ADR-010) ;
- les fichiers sous `/media` (MP4, segments HLS, sous-titres) sont servis en statique sans contrôle : l'API ne protège que la remise de leur chemin (limite connue de l'ADR-017) ;
- une vidéo référencée par une URL externe n'a ni HLS ni sous-titres (ADR-017) ;
- CLAUDE.md place les playlists hors périmètre, ainsi que « à regarder plus tard » et les notifications ;
- le back-office ne contacte jamais PostgreSQL directement (CLAUDE.md) ;
- aucune fonctionnalité, dépendance ou écran non demandé ne doit être ajouté (CLAUDE.md).

## Décision
Rendre fonctionnels les quatre boutons. Les quatre parties sont indépendantes et peuvent être livrées séparément.

Une partie qui ne serait pas terminée au rendu voit son bouton retiré, et non laissé avec un message « Bientôt disponible », conformément à la règle de rédaction du 4 novembre (décision 1).

### Ordre de livraison et charge estimée
Estimation de l'agent ; ordre confirmé par le lead développeur (décision 2) :

| Partie | Charge | Pourquoi |
|---|---|---|
| 4. Aide | Légère | Un écran statique, textes dans l'application, aucun appel réseau. |
| 1. Je n'aime pas | Légère à moyenne | Une migration, deux routes, aucun compteur à afficher ; le passage « j'aime » ↔ « je n'aime pas » touche le compteur `likeCount` existant. |
| 3. Enregistrer (playlists) | Moyenne à lourde | Deux tables, un module backend complet (sept routes), une feuille et deux écrans dans l'application. |
| 2. Télécharger | **Lourde** | Lecture hors ligne : service Android de téléchargement, métadonnées locales, lecteur sans réseau, fin de l'abonnement, espace disque. C'est la partie la plus risquée pour le 6 novembre. |

Ordre retenu : Aide, puis « Je n'aime pas », puis playlists, puis téléchargements. Les téléchargements passent en dernier et seront coupés si ça coince.

### Partie 1 — « Je n'aime pas »

#### Comportement (décidé par le lead développeur)
- L'avis négatif est enregistré par le serveur.
- Son nombre n'est jamais affiché.
- Il s'exclut avec « J'aime » : donner l'un retire l'autre.

#### Modèle de données : option A retenue
Le modèle de données de CLAUDE.md change dans les deux cas.

- **Option A — colonne de type sur LIKE.** LIKE reçoit un champ `type` à deux valeurs (`LIKE`, `DISLIKE`), ajouté par une migration Prisma avec la valeur par défaut `LIKE` pour les lignes existantes. La clé primaire (userId, videoId) reste inchangée.
  - Avantages : l'exclusion est garantie par la base elle-même, puisqu'il ne peut exister qu'une ligne par utilisateur et par vidéo ; passer de l'un à l'autre est une simple mise à jour de la ligne. Une seule table, un seul dépôt.
  - Inconvénients : toutes les lectures de LIKE doivent filtrer sur `type = LIKE` (« j'aime » de l'utilisateur, liste des vidéos aimées, état « aimé » renvoyé par GET /videos/:id) ; un oubli compterait un avis négatif comme un « j'aime ». Ces lectures sont aujourd'hui regroupées dans un seul dépôt Prisma du module videos. Le nom LIKE devient trompeur : renommer l'entité (par exemple en REACTION) serait plus juste, mais toucherait davantage de code.
- **Option B — table DISLIKE distincte** (userId, videoId), de même forme que LIKE.
  - Avantages : aucune lecture existante de LIKE ne change ; le compteur `likeCount` et la liste des vidéos aimées restent intacts.
  - Inconvénients : l'exclusion n'est pas garantie par la base ; elle doit être assurée par le code, dans une transaction qui supprime la ligne de l'autre table. Sans transaction, deux requêtes simultanées peuvent laisser un utilisateur à la fois dans LIKE et DISLIKE. Deux tables presque identiques à maintenir.

**Recommandation de l'agent : option A.** L'exclusion entre les deux avis est la règle centrale de la décision, et l'option A la confie à la base au lieu du code. Le risque d'oubli de filtre est limité parce que les lectures de LIKE sont concentrées dans un seul dépôt. **Retenue par le lead développeur ; l'entité garde le nom LIKE** (décision 3).

#### Routes
- POST /videos/:id/dislike : donne un avis négatif ; retire le « j'aime » éventuel.
- DELETE /videos/:id/dislike : retire l'avis négatif.
- POST /videos/:id/like (existant, ADR-010) retire désormais l'avis négatif éventuel.
- GET /videos/:id renvoie, en plus de l'état « aimé » existant, l'état « je n'aime pas » de l'utilisateur connecté, et lui seul.
- Mêmes vérifications que pour « J'aime » : utilisateur connecté, et vidéo privée d'un autre traitée comme inexistante (404, ADR-016).

#### Compteur
- Aucun compteur d'avis négatifs : ni colonne dénormalisée, ni tampon Redis, ni champ renvoyé par l'API. Puisque le nombre n'est jamais affiché, rien ne le calcule ni ne l'expose.
- Quand un « j'aime » est remplacé par un avis négatif, le compteur `likeCount` est décrémenté dans Redis et `likes:update` est diffusé, comme pour un retrait de « j'aime » (ADR-010).
- Aucun événement temps réel n'est diffusé pour les avis négatifs.

#### Application
Le bouton « Je n'aime pas » est un bouton à bascule, sans nombre. Un visiteur non connecté reçoit le même traitement que sur « J'aime ».

### Partie 2 — « Télécharger » : lecture hors ligne réservée au Premium

#### Comportement (décidé par le lead développeur)
- Une vidéo téléchargée se lit hors ligne, dans l'application uniquement.
- Le téléchargement est réservé aux abonnés Premium.
- Un écran « Téléchargements » est ajouté dans Vous.

#### Vérification côté serveur
- Nouvelle route, par exemple POST /videos/:id/download. Elle vérifie que l'utilisateur est connecté, que son Premium est actif, que la vidéo lui est accessible (visibilité, ADR-016 ; accès aux vidéos exclusives, ADR-007) et qu'elle est téléchargeable (voir les vidéos externes). Elle renvoie alors l'adresse du fichier à télécharger et la date de fin du Premium (`expiresAt`).
- L'application ne lance jamais un téléchargement sans cette réponse.
- Limite, de même nature que celle de l'ADR-017 : les fichiers sous `/media` n'étant pas protégés, un client modifié qui connaît une adresse peut télécharger sans passer par cette route. La route protège la remise de l'adresse, pas le fichier.

#### Bibliothèque : Media3
- Téléchargement par le DownloadManager de Media3, exécuté dans un DownloadService (service au premier plan), avec un cache dédié (SimpleCache) dans le stockage privé de l'application, sans éviction automatique.
- La lecture hors ligne passe par le même ExoPlayer, qui lit depuis ce cache.
- Ces classes font partie de Media3, déjà utilisé ; aucune bibliothèque tierce n'est ajoutée. Les modules Media3 exacts à déclarer sont à vérifier à la compilation.
- Contrainte Android : un service au premier plan affiche obligatoirement une notification de progression, et Android 13 et plus demande l'autorisation d'afficher des notifications. Ce n'est pas la fonctionnalité « notifications » mise hors périmètre par CLAUDE.md, mais une obligation du système. Accepté par le lead développeur (décision 7).

#### Format téléchargé : option A (MP4) retenue
- **Option A — MP4 progressif.** Le fichier `videoUrl` hébergé chez nous est téléchargé tel quel.
  - Avantages : un seul fichier, le chemin le plus simple dans Media3 ; fonctionne pour toute vidéo hébergée, y compris une vidéo envoyée depuis l'application dont le HLS n'est pas encore généré (ADR-017).
  - Inconvénients : la qualité est celle de la source, sans choix ; fichier plus lourd qu'une qualité réduite.
- **Option B — HLS, une seule qualité.** Une seule piste de l'échelle HLS (ADR-017) est téléchargée. Laquelle : la plus haute disponible (720p au plus), une qualité fixe plus légère (360p ou 480p), ou un choix proposé à l'utilisateur.
  - Avantages : taille maîtrisée ; même chemin de lecture (HLS) qu'en ligne ; Media3 sait télécharger une piste HLS.
  - Inconvénients : beaucoup de petits fichiers (segments) ; code de sélection de piste à écrire ; impossible tant que le HLS n'est pas généré.
- **Option C — HLS quand il existe, MP4 sinon.**
  - Avantage : couvre tous les cas.
  - Inconvénient : deux chemins de téléchargement et de lecture hors ligne à écrire et à tester, dans un délai de deux jours.

**Recommandation de l'agent : option A (MP4), pour le délai.** Un seul chemin, toutes les vidéos hébergées couvertes. L'option B est le meilleur produit si le temps le permet. **Retenue par le lead développeur** (décision 5).

#### Fin de l'abonnement Premium : option A retenue
L'abonnement dure un mois, sans renouvellement automatique : sa date de fin est connue à l'avance. L'application peut donc la faire respecter même hors ligne, sauf en cas de résiliation anticipée (DELETE /me/premium) ou de modification de l'horloge du téléphone.

- **Option A — lecture bloquée à la date de fin, fichiers supprimés à la vérification suivante.** L'application garde localement `expiresAt` ; au-delà, la lecture hors ligne est refusée. À la prochaine connexion, elle relit GET /me/premium et supprime les téléchargements si aucun Premium n'est actif.
- **Option B — suppression immédiate** dès que l'application constate la fin du Premium.
- **Option C — fichiers conservés mais illisibles** tant que le Premium n'est pas repris ; aucun nouveau téléchargement nécessaire en cas de réabonnement, mais de l'espace disque occupé pour rien.
- Aucun délai maximal hors ligne n'est fixé : la recommandation retenue (option A) s'appuie sur `expiresAt` et rattrape une résiliation à la connexion suivante.

**Recommandation de l'agent : option A.** Elle fonctionne hors ligne grâce à la date de fin connue, et rattrape les résiliations à la connexion suivante. **Retenue par le lead développeur** (décision 6).

Limite dans tous les cas : l'application seule fait respecter cette règle sur le téléphone. Les fichiers sont dans le stockage privé de l'application, sans chiffrement ni DRM : sur un téléphone débridé, ils peuvent être extraits. Une vraie protection (DRM) est hors de portée du délai.

#### Vidéos externes
Une vidéo référencée par une URL externe n'est pas hébergée chez nous (ADR-017). Options :
- **non téléchargeable** : la route refuse, et le bouton n'est pas affiché pour ces vidéos (un bouton sans fonction n'a pas sa place) ;
- **téléchargeable en MP4** depuis l'adresse externe : techniquement possible avec Media3, mais nous copierions sur le téléphone un fichier hébergé par un tiers, dont nous ne maîtrisons ni la disponibilité ni les droits.

Recommandation de l'agent : non téléchargeable. **Retenue par le lead développeur, bouton masqué** (décision 8).

#### Vidéos exclusives, privées, non répertoriées
- **Exclusive (isPremium)** : un abonné Premium y a déjà accès ; le téléchargement suit naturellement.
- **Non répertoriée** : accessible par son lien (ADR-016) ; téléchargeable selon la même règle.
- **Privée** : seul son propriétaire y a accès (ADR-016). Le propriétaire, abonné Premium, peut télécharger sa propre vidéo privée (décision 9).
- **Vidéo devenue privée, supprimée ou devenue exclusive après le téléchargement** : hors ligne, l'application ne peut pas le savoir. Retenu (décision 10) : à chaque connexion, l'application envoie la liste de ses vidéos téléchargées au serveur, qui renvoie celles qui restent accessibles ; les autres sont supprimées du téléphone. Cela évite qu'une vidéo devenue privée reste lisible, dans l'esprit de l'ADR-016, mais ajoute une route et du travail.

#### Écran « Téléchargements » et lecture hors ligne
- Entrée « Téléchargements » dans Vous, avec la liste des vidéos téléchargées ou en cours (progression), la taille de chacune et l'espace total occupé ; suppression d'un téléchargement.
- L'écran doit fonctionner sans réseau : titre, miniature et chaîne de chaque vidéo sont gardés localement (Room, déjà dans la stack ; miniature enregistrée avec la vidéo).
- La lecture hors ligne se fait depuis cet écran, avec un lecteur qui n'appelle pas l'API : ni commentaires, ni compteurs temps réel, ni connexion WebSocket. Une lecture hors ligne n'est ni comptée comme une vue ni ajoutée à l'historique (décision 13).
- L'application doit pouvoir s'ouvrir sans réseau et mener à cet écran, ce qui suppose de vérifier son comportement au démarrage hors ligne (session, renouvellement du token, ADR-008).

#### Espace disque
- Avant de lancer un téléchargement, l'application compare la taille annoncée du fichier à l'espace libre et refuse avec un message clair si l'espace manque.
- L'espace occupé est affiché dans l'écran « Téléchargements ».
- Limite : 20 vidéos téléchargées (décision 11).

#### Utilisateur non Premium et visiteur
Décision 15 : pour un utilisateur connecté sans Premium, « Télécharger » ouvre l'écran de l'offre Premium existant ; la route renvoie un refus. Pour un visiteur, même traitement que sur « J'aime ».

#### Offre Premium
Le téléchargement s'ajoute au contenu de l'offre décrit par l'ADR-007 (sans publicité, vidéos exclusives, lecture en arrière-plan). L'écran de l'offre devra le mentionner.

### Partie 3 — « Enregistrer » : playlists

#### Levée du hors-périmètre
**Cet ADR lève les playlists du hors-périmètre de CLAUDE.md, et seulement elles.** « À regarder plus tard » reste hors périmètre (décision 16). À l'acceptation de cet ADR, CLAUDE.md est mis à jour par un humain : périmètre, modèle de données et liste des modules.

#### Comportement (décidé par le lead développeur)
Playlists complètes : créer, nommer, renommer, supprimer une playlist ; ajouter et retirer des vidéos ; consulter ses playlists et leur contenu.

#### Modèle de données
- **PLAYLIST** : identifiant, propriétaire, titre, date de création, date de modification. Pas de champ de visibilité : les playlists sont privées seulement (décision 17).
- **PLAYLIST_ITEM** : playlist, vidéo, date d'ajout. Pas de position : ordre d'ajout, sans réordonnancement (décision 18). Clé primaire (playlistId, videoId) : une vidéo ne figure qu'une fois dans une playlist.
- Propriétaire : l'utilisateur (USER) (décision 17).
- Titre obligatoire, non vide, de 100 caractères au plus ; vérifié par le serveur (décision 20).
- Suppression d'une playlist : ses lignes PLAYLIST_ITEM sont supprimées avec elle (suppression en cascade).

#### Routes
- GET /me/playlists : mes playlists (titre, nombre de vidéos, miniature de la première vidéo).
- POST /me/playlists : créer.
- GET /me/playlists/:id : contenu d'une playlist.
- PATCH /me/playlists/:id : renommer.
- DELETE /me/playlists/:id : supprimer.
- POST /me/playlists/:id/videos : ajouter une vidéo.
- DELETE /me/playlists/:id/videos/:videoId : retirer une vidéo.
- Une playlist d'un autre utilisateur est traitée comme inexistante (404), selon la même logique que l'ADR-016.
- Ajouter une vidéo suppose qu'elle soit accessible à l'utilisateur (ADR-016) ; une vidéo privée d'un autre est refusée comme inexistante.
- Côté backend, un nouveau module `playlists` en architecture hexagonale (ADR-003) (décision 21).

#### Application
- « Enregistrer » ouvre une feuille listant les playlists de l'utilisateur avec une case à cocher par playlist (cocher ajoute, décocher retire), et une action « Nouvelle playlist ».
- Dans Vous : une section « Playlists » et un écran de détail d'une playlist (renommer, supprimer, retirer une vidéo, lancer une vidéo).
- Un visiteur reçoit le même traitement que sur « J'aime ».
- Le back-office ne reçoit rien pour les playlists : ce n'est pas demandé.
- L'écran de publication ne propose pas d'ajouter la vidéo à une playlist : ce n'est pas demandé, et l'exclusion de ce réglage par l'ADR-016 reste valable.

#### Règles retenues
- **Visibilité** : playlists privées seulement, seul leur propriétaire les voit (décision 17). Les playlists publiques, visibles sur la page de chaîne, auraient demandé un champ de visibilité, un propriétaire de type chaîne, une section sur la page de chaîne et une route publique ; écartées.
- **Ordre des vidéos** : ordre d'ajout, sans réordonnancement (décision 18). L'ordre manuel, avec une colonne de position et un glisser-déposer, est écarté.
- **Vidéo devenue privée** : alignée sur l'historique de visionnage (ADR-016, décision 19) : la ligne est conservée, la vidéo est filtrée à la lecture et n'apparaît plus que pour son propriétaire ; si elle redevient publique, elle réapparaît. Vidéo non répertoriée : reste visible dans une playlist privée (l'utilisateur y avait accès).
- **Vidéo supprimée** : ses lignes PLAYLIST_ITEM disparaissent avec elle (suppression en cascade, comme pour LIKE).
- **Vidéo exclusive** : elle apparaît dans la playlist ; l'accès Premium est vérifié à la lecture, comme partout (ADR-007).
- **Limites**, vérifiées par le serveur : 50 playlists par utilisateur, 200 vidéos par playlist, titre de 100 caractères au plus (décision 20).

### Partie 4 — « Aide »

#### Comportement (décidé par le lead développeur)
- Un écran d'aide intégré à l'application, ouvert depuis Vous.
- Quelques questions fréquentes, sur quatre sujets : publier, Premium, abonnements, sous-titres.
- Les textes sont dans l'application (ressources Android), sans appel réseau : l'écran fonctionne aussi hors ligne.

#### Exactitude des textes
Les réponses ne décrivent que ce qui existe réellement dans l'application, et respectent la règle de rédaction du 4 novembre. Points d'attention relevés dans les ADR existants :
- **Publier** : la visibilité et les commentaires se choisissent à la publication ; leur modification ensuite ne se fait pas depuis l'application (ADR-016). La réponse ne doit pas laisser croire le contraire.
- **Premium** : contenu exact de l'offre (ADR-007, ADR-014) : 4,99 € pour un mois, sans renouvellement automatique, sans publicité, vidéos exclusives, lecture en arrière-plan ; et les téléchargements seulement si la partie 2 est livrée.
- **Abonnements** : distinguer clairement l'abonnement gratuit à une chaîne (FOLLOW) de l'abonnement payant Premium (PREMIUM_SUBSCRIPTION), comme l'exige CLAUDE.md.
- **Sous-titres** : générés automatiquement, disponibles sur les vidéos classiques mais pas affichés dans les Shorts (ADR-017), et absents de certaines vidéos (vidéos externes, vidéos sans parole, langues mal reconnues).

Aucun formulaire de contact ni lien externe : ce n'est pas demandé.

### Mises à jour faites à l'acceptation
- CLAUDE.md est mis à jour par un humain à l'acceptation : playlists retirées du hors-périmètre ; PLAYLIST, PLAYLIST_ITEM et le type sur LIKE ajoutés au modèle de données ; module `playlists` ajouté.
- L'index `docs/adr/README.md`.

### Décisions du lead développeur (4 novembre 2026)
Le lead développeur a validé toutes les recommandations, à l'acceptation de cet ADR.

Générales
1. Une partie non terminée au rendu voit son bouton retiré.
2. Ordre de livraison : Aide, puis « Je n'aime pas », puis playlists, puis téléchargements. Les téléchargements, de loin le plus gros morceau, passent en dernier et seront coupés sans regret si ça coince.

« Je n'aime pas »
3. Option A : colonne `type` sur LIKE. L'entité garde le nom LIKE.
4. Le nombre d'avis négatifs n'est jamais affiché, ni au créateur ni au back-office, et n'est calculé nulle part. C'est une décision produit documentée, pas un oubli : c'est le comportement réel de YouTube depuis 2021.

« Télécharger »
5. Format téléchargé : MP4.
6. Fin du Premium : la lecture est bloquée à `expiresAt`, même hors ligne, puis les fichiers sont supprimés à la connexion suivante.
7. La notification de progression imposée par Android pour le service de téléchargement est acceptée : c'est une contrainte du système, pas la fonctionnalité « notifications » mise hors périmètre.
8. Les vidéos externes ne sont pas téléchargeables ; le bouton est masqué pour elles.
9. Le propriétaire d'une vidéo privée, abonné Premium, peut la télécharger.
10. À chaque connexion, l'application revérifie l'accès aux vidéos téléchargées et supprime les autres.
11. Limite : 20 vidéos téléchargées.
12. Les sous-titres sont téléchargés avec la vidéo.
13. Une lecture hors ligne ne compte ni comme vue ni dans l'historique.
14. Les téléchargements sont supprimés à la déconnexion du compte.
15. Sans Premium, « Télécharger » ouvre l'écran de l'offre Premium.

Précision du lead développeur : les fichiers téléchargés vont dans le stockage privé de l'application, pas dans la galerie ; ils n'apparaissent ni dans Google Photos ni dans un explorateur de fichiers.

« Enregistrer »
16. « À regarder plus tard » reste hors périmètre.
17. Playlists privées seulement, rattachées à l'utilisateur (USER).
18. Ordre d'ajout, sans réordonnancement.
19. Une vidéo devenue privée est filtrée à la lecture, comme dans l'historique.
20. Limites : 50 playlists par utilisateur, 200 vidéos par playlist, titre de 100 caractères au plus.
21. Nouveau module backend `playlists`.

« Aide »
22. Les textes de l'aide sont rédigés par l'agent de développement d'après les ADR, et relus par le lead développeur.

## Alternatives
- Laisser les quatre boutons avec le message « Bientôt disponible » : avantage, aucun travail. Inconvénient, contraire à la règle du 4 novembre : le message annonce ce qui n'est pas développé, et un bouton sans fonction n'a pas sa place.
- Retirer les quatre boutons : avantage, aucun travail et règle respectée. Inconvénient, quatre fonctions attendues d'une plateforme vidéo disparaissent ; c'est le repli retenu pour une partie non terminée au rendu.
- « Je n'aime pas » sans enregistrement serveur (état local à l'application) : avantage, aucune migration. Inconvénient, l'avis est perdu en changeant de téléphone, et le lead développeur a décidé qu'il est enregistré par le serveur.
- « Je n'aime pas » avec compteur affiché : écarté par le lead développeur ; le nombre n'est jamais affiché.
- Téléchargement vers la galerie ou les fichiers du téléphone (hors de l'application) : avantage, plus simple, sans lecteur hors ligne. Inconvénient, le fichier échappe à toute règle : il resterait lisible après la fin du Premium et pourrait être partagé ; contraire à la décision d'une lecture hors ligne dans l'application uniquement.
- Téléchargement sans Media3, par un téléchargement HTTP fait à la main : avantage, aucun composant de plus à découvrir. Inconvénient, il faudrait réécrire la reprise, la progression, le service en arrière-plan et la lecture depuis le cache, que Media3 fournit déjà.
- Table DOWNLOAD côté serveur (qui a téléchargé quoi, sur quel appareil) : avantage, contrôle et révocation côté serveur. Inconvénient, non demandé et lourd dans le délai ; la vérification à la remise de l'adresse et la relecture du Premium suffisent pour la démonstration.
- DRM (Widevine) sur les téléchargements : avantage, vraie protection des fichiers sur le téléphone. Inconvénient, hors de portée du délai ; la limite est documentée.
- Playlists stockées seulement dans l'application (Room) : avantage, aucun travail backend. Inconvénient, perdues en changeant de téléphone et impossibles à rendre publiques.
- « Enregistrer » réduit à une liste unique « à regarder plus tard » : avantage, beaucoup plus léger. Inconvénient, ce n'est pas la décision du lead développeur (playlists complètes), et « à regarder plus tard » est hors périmètre.
- Aide sous forme de page web ouverte dans un navigateur : avantage, textes modifiables sans nouvelle version. Inconvénient, sort de l'application native, demande un hébergement et ne fonctionne pas hors ligne ; le lead développeur a décidé d'un écran intégré avec des textes dans l'application.
- Aide servie par l'API : avantage, textes modifiables sans nouvelle version. Inconvénient, une route et un stockage de plus pour quelques textes fixes, et l'écran ne fonctionnerait plus hors ligne.

## Conséquences
### Positives
- Plus aucun bouton ne répond « Bientôt disponible » : la règle de rédaction du 4 novembre est respectée.
- L'avis négatif est enregistré par le serveur sans aucun compteur à maintenir : ni colonne, ni tampon Redis, ni événement temps réel.
- L'exclusion entre « J'aime » et « Je n'aime pas » est garantie par la base (option A).
- Les téléchargements donnent un avantage concret de plus à l'offre Premium, démontrable sans réseau.
- L'accès au téléchargement est vérifié par le serveur (Premium, visibilité, vidéos exclusives), dans la continuité des ADR-007 et ADR-016.
- Media3, déjà utilisé, fournit le téléchargement, sa reprise et la lecture depuis le cache : aucune bibliothèque tierce ajoutée.
- La date de fin du Premium étant connue à l'avance (pas de renouvellement automatique), l'expiration peut être appliquée même hors ligne.
- Les playlists sont enregistrées côté serveur et suivent l'utilisateur d'un téléphone à l'autre.
- Les règles de visibilité (ADR-016) s'appliquent aux playlists comme à l'historique de visionnage.
- L'aide fonctionne hors ligne et décrit exactement ce qui existe.
- Les quatre parties sont livrables séparément : un retard sur les téléchargements n'emporte pas les trois autres.

### Négatives
- Le calendrier est serré : deux jours pour quatre fonctions, dont une lourde (téléchargements) et une moyenne à lourde (playlists).
- Le modèle de données de CLAUDE.md change : type sur LIKE, plus PLAYLIST et PLAYLIST_ITEM ; deux migrations Prisma.
- Chaque lecture de LIKE doit filtrer sur le type ; un oubli compterait un avis négatif comme un « j'aime ». L'entité garde le nom LIKE, devenu un peu trompeur.
- Le passage d'un « j'aime » à un avis négatif doit décrémenter `likeCount` dans Redis et diffuser `likes:update` : un chemin de plus dans la logique existante de l'ADR-010.
- La protection des téléchargements est limitée : les fichiers sous `/media` restent servis sans contrôle (ADR-017), et les fichiers téléchargés ne sont ni chiffrés ni protégés par DRM ; seule l'application fait respecter la fin du Premium sur le téléphone.
- Une résiliation anticipée (DELETE /me/premium) ou une horloge modifiée ne sont prises en compte qu'à la connexion suivante.
- Le service de téléchargement impose une notification de progression et, sur Android 13 et plus, une demande d'autorisation.
- La lecture hors ligne demande un parcours sans réseau : métadonnées gardées dans Room, lecteur sans API ni WebSocket, démarrage de l'application hors ligne à vérifier.
- Les téléchargements occupent de l'espace sur le téléphone ; il faut vérifier l'espace libre et l'afficher.
- Les vidéos externes ne sont pas téléchargeables : le bouton doit être masqué pour elles.
- L'écran de l'offre Premium doit être mis à jour pour mentionner les téléchargements, dans ses deux variantes du test A/B (ADR-006).
- Les playlists ajoutent un module backend, sept routes, une feuille et deux écrans dans l'application.
- Chaque lecture d'une playlist doit filtrer les vidéos devenues privées, comme l'historique (ADR-016) ; un oubli laisserait fuiter une vidéo privée.
- CLAUDE.md est mis à jour par un humain à l'acceptation : playlists retirées du hors-périmètre, PLAYLIST, PLAYLIST_ITEM et le type sur LIKE ajoutés au modèle de données, module `playlists` ajouté.
- Les textes de l'aide doivent être tenus à jour si le comportement de l'application change.

## Liens
- [ADR-004](ADR-004-temps-reel-websocket.md) : temps réel et compteurs par Redis ; le compteur de « j'aime » reste inchangé, et aucun compteur d'avis négatifs n'est ajouté.
- [ADR-010](ADR-010-websocket-salles-compteurs.md) : routes POST et DELETE /videos/:id/like, compteur `likeCount` reporté depuis Redis, événement `likes:update` ; POST /videos/:id/like retire désormais l'avis négatif, et le passage d'un « j'aime » à un avis négatif décrémente le compteur et le diffuse. Aucun événement n'est diffusé pour les avis négatifs.
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; le téléchargement s'ajoute au contenu de l'offre, et l'accès aux vidéos exclusives reste vérifié par l'API, téléchargement compris.
- [ADR-014](ADR-014-paiement-stripe-mode-test.md) : paiement Premium ; un mois sans renouvellement automatique, d'où une date de fin connue à l'avance qui permet d'appliquer l'expiration hors ligne. GET /me/premium sert à revérifier le statut.
- [ADR-012](ADR-012-paiement-simule-backend.md) : DELETE /me/premium, resté valable ; une résiliation anticipée n'est prise en compte par les téléchargements qu'à la connexion suivante.
- [ADR-016](ADR-016-reglages-publication-video.md) : visibilité ; elle s'applique au téléchargement, aux avis négatifs et aux playlists (vidéo privée d'un autre traitée comme inexistante, vidéo devenue privée filtrée à la lecture comme dans l'historique). Son exclusion du réglage « playlist » de l'écran de publication reste valable, bien que les playlists sortent du hors-périmètre.
- [ADR-017](ADR-017-hls-multi-qualites-sous-titres.md) : diffusion HLS et MP4 en repli, vidéos externes sans HLS, fichiers sous `/media` non protégés ; ces points déterminent le format téléchargé, le cas des vidéos externes et la limite de protection des téléchargements. Les sous-titres absents des Shorts sont à refléter dans l'aide.
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication depuis l'application ; l'aide sur la publication doit le décrire fidèlement.
- [ADR-006](ADR-006-analytics-posthog.md) : test A/B de l'écran de l'offre Premium ; les deux variantes doivent mentionner les téléchargements.
- [ADR-008](ADR-008-authentification-jwt.md) : authentification par JWT ; le démarrage de l'application hors ligne, nécessaire à la lecture des téléchargements, doit tenir compte du renouvellement du token.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS, architecture hexagonale ; les nouvelles routes et l'éventuel module `playlists` en suivent l'organisation.
- [ADR-001](ADR-001-android-natif.md) : application native Android ; téléchargement et lecture hors ligne par Media3, écrans en Jetpack Compose.
