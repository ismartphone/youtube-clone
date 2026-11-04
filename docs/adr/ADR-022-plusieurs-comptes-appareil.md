# ADR-022 — Plusieurs comptes sur l'appareil et changement de compte

## Statut
Accepté

## Contexte
L'application ne connaît aujourd'hui qu'un compte à la fois. L'ADR-008 conserve un seul jeu de jetons sur l'appareil : `TokenStore` (une entrée `tokens` dans le DataStore `session`), chiffrée en AES-GCM par `TokenCipher` avec une seule clé Keystore (`session_tokens`). Pour changer de compte, il faut se déconnecter puis se reconnecter avec le mot de passe.

Le 4 novembre 2026, le lead développeur demande que plusieurs comptes puissent être enregistrés sur l'appareil, avec une bascule immédiate de l'un à l'autre. C'est un changement de fond de l'ADR-008, d'où un nouvel ADR.

Contraintes :
- les jetons restent chiffrés avec le Keystore, et ceux d'un compte ne doivent jamais être lisibles depuis la session d'un autre ;
- côté serveur, rien ne change : refresh token à usage unique par compte, dans Redis (ADR-008) ;
- une seule installation de l'application : tous les comptes partagent le même bac à sable Android.

État du code au 4 novembre 2026, sur les points touchés :
- **Notifications (ADR-020)** : la table `device_tokens` a `token` en clé primaire, lié à un seul `userId`. L'enregistrement est un upsert qui réattribue le jeton au nouvel utilisateur (`update: { userId }`). À la déconnexion, `PushTokenRepositoryImpl.unregister()` tente le retrait côté serveur pendant 5 s au plus, puis appelle `deleteToken()`.
- **Téléchargements (ADR-019)** :
  - la table Room `downloaded_videos` a `videoId` en clé primaire et aucun champ de compte ;
  - un seul SimpleCache Media3 (`filesDir/downloads`) ;
  - la requête de téléchargement a pour identifiant `video.id`, et la clé de cache par défaut (l'URL).
- **PostHog (ADR-013)** : `PostHogAnalytics` appelle `identify` quand l'identifiant du compte change, et `reset` au retour à l'anonymat. `LogoutUseCase` appelle `reset`.
- **WebSocket (ADR-010)** : `LiveVideoClient` lit l'access token au moment de chaque connexion. La connexion n'existe que pendant l'écran de lecture.
- **Préférences** : le DataStore `preferences` contient `onboarding_completed`, `interest_category_ids`, `share_usage_stats` et `autoplay`, pour tout l'appareil. Pas de cache HTTP OkHttp configuré. Coil ne met en cache que des images publiques.
- **@handle** : il appartient à la chaîne (`Channel.handle`), pas au compte. Un compte sans chaîne n'en a pas.

## Décision

### Comportement
- Un appui sur l'avatar, en haut du fil ou dans l'onglet Vous, ouvre une feuille listant les comptes enregistrés sur l'appareil : avatar, nom, @handle, et une coche sur le compte actif. Pour un compte sans chaîne, la feuille affiche son nom et son adresse e-mail à la place du @handle, qui appartient à la chaîne.
- Sous la liste : « Ajouter un compte », qui ouvre la connexion, et « Gérer les comptes », qui permet d'en retirer un.
- 5 comptes au plus sur l'appareil.
- La bascule est immédiate, sans mot de passe. Fil, abonnements, historique et statut Premium suivent le compte choisi.
- Se déconnecter ne retire que le compte actif. S'il en reste, l'application bascule sur l'un d'eux ; sinon, on devient visiteur.
- Une bascule arrête la lecture en cours (y compris la lecture en arrière-plan Premium) et revient au fil, pile de navigation vidée.
- Serveur : rien ne change.

### Stockage des jetons

Option A, une entrée chiffrée par compte avec la clé Keystore actuelle :
- Avantages : changement minimal de `TokenCipher`.
- Inconvénients : une seule clé pour tous les comptes ; retirer un compte efface son entrée, mais la clé reste.

Option B, une entrée et une clé Keystore par compte (alias dérivé de l'identifiant du compte) :
- Avantages : retirer un compte détruit aussi sa clé, ce qui rend toute copie résiduelle de ses jetons illisible.
- Inconvénients : une clé Keystore par compte à gérer et à migrer.

**Option B retenue** (recommandation validée à l'acceptation). Seuls les jetons du compte actif sont déchiffrés et gardés en mémoire ; les autres entrées ne sont lues qu'au moment d'une bascule vers ce compte. La liste des comptes (identifiant, nom, @handle ou adresse e-mail, adresse de l'avatar) est stockée à part, sans les jetons. Ce ne sont pas des secrets, mais ce sont des données personnelles, exclues des sauvegardes comme le DataStore `session`.

**Portée réelle de « jamais lisibles depuis la session d'un autre » :**
- La séparation garantit trois choses :
  - chaque compte a sa propre entrée chiffrée et sa propre clé ;
  - seuls les jetons du compte actif sont chargés en mémoire et envoyés au serveur ;
  - aucun écran ni aucune requête ne lit les jetons d'un autre compte.
- Elle ne protège pas un compte de l'autre au sens du système. Tous les comptes vivent dans le même bac à sable de l'application : le code de l'application peut techniquement déchiffrer toutes les entrées, et c'est ce qu'il fait à chaque bascule sans mot de passe. Quiconque tient le téléphone déverrouillé avec l'application ouverte peut basculer vers n'importe quel compte enregistré. C'est le comportement voulu, le même que sur YouTube.
- Le Keystore protège contre la lecture des fichiers hors de l'application (fichier copié, sauvegarde), pas contre l'application elle-même.

**Migration :** au premier démarrage après la mise à jour, l'entrée `tokens` actuelle devient le premier compte enregistré, avec sa propre clé. L'ancienne entrée et l'ancienne clé `session_tokens` sont ensuite supprimées.

**Refresh token expiré d'un compte inactif :** un compte inactif ne renouvelle pas ses jetons. Au-delà de 7 jours, son refresh token a expiré, et la bascule sans mot de passe devient impossible. Le compte reste dans la liste, grisé, avec la mention « Reconnexion requise ». Un appui ouvre la connexion avec l'adresse e-mail préremplie. Il n'est jamais supprimé tout seul.

**Retirer un compte inactif** (« Gérer les comptes ») :
- suppression de ses jetons, de sa clé Keystore et de ses téléchargements ;
- un fichier partagé avec un autre compte est gardé ;
- aucun appel au serveur pour le jeton FCM, qui appartient au compte actif.

### Notifications (ADR-020)
- À chaque bascule, l'application enregistre le jeton FCM pour le nouveau compte. L'upsert existant réattribue le jeton au nouveau compte : l'ancien compte le perd du même coup, sans appel de retrait séparé.
- **Bascule hors réseau :** l'ancien compte garde le jeton, et l'appareil continuerait de recevoir ses notifications.
  - L'application appelle `deleteToken()` localement, comme la déconnexion hors ligne actuelle. L'ancien jeton meurt chez Firebase tout de suite, et le serveur le supprime au premier envoi refusé.
  - Le nouveau jeton est enregistré en attente, puis l'enregistrement est rejoué au retour du réseau. D'ici là, le nouveau compte ne reçoit rien.
  - Option écartée : retenter seulement l'enregistrement, sans `deleteToken()`. D'ici le retour du réseau, les notifications de l'ancien compte continueraient d'arriver.

### Téléchargements (ADR-019)
- Un téléchargement appartient au compte qui l'a fait. Une bascule ne l'efface pas et ne le montre pas à l'autre compte. Seule la déconnexion d'un compte (ou son retrait) supprime les siens.
- **Room :** ajout d'une colonne `userId` à `downloaded_videos`, et clé primaire composée (`userId`, `videoId`). Toutes les requêtes du DAO filtrent sur le compte actif.
- **Même vidéo téléchargée par deux comptes :**
  - Option 1, un seul fichier partagé dans le SimpleCache (identifiant et clé de cache inchangés = `videoId`), avec une ligne Room par compte. Le fichier n'est retiré de Media3 que lorsqu'il ne reste plus aucune ligne pour cette vidéo. Avantages : pas de doublon sur le disque, aucun changement dans le téléchargement Media3. Inconvénient : la suppression doit compter les lignes restantes avant de retirer le fichier.
  - Option 2, un téléchargement par compte (identifiant et clé de cache `userId:videoId`). Avantage : suppression indépendante. Inconvénients : fichier en double, et la lecture hors ligne doit utiliser la clé du compte.
  - **Option 1 retenue** (recommandation validée à l'acceptation). Les miniatures et sous-titres enregistrés à part suivent la même règle. `expiresAt` et `validatedAt` restent par ligne, donc par compte : le Premium de l'un ne prolonge pas l'autre.
- **Migration des téléchargements existants :** migration Room qui ajoute `userId` et attribue les lignes existantes au compte actif au moment de la mise à jour, puisque c'est lui qui les a faits. Sans session active à ce moment-là, les lignes et fichiers sont supprimés, comme à une déconnexion.
- Un téléchargement en cours pendant une bascule continue et reste au compte qui l'a lancé.
- La limite de 20 téléchargements (ADR-019) s'applique par compte, pas par appareil.
- Une bascule compte comme une connexion pour la revalidation des téléchargements du compte choisi (ADR-019).

### Autres points touchés
- **PostHog (ADR-013) :** à la bascule, `reset` puis `identify` du nouveau compte. Sans `reset`, les événements risquent d'être rattachés à l'ancien identifiant. Le refus des mesures (`share_usage_stats`) reste un réglage de l'appareil.
- **WebSocket (ADR-010) :** la bascule ferme l'écran de lecture, donc la connexion. Toute connexion suivante lit l'access token du nouveau compte. Rien d'autre à faire.
- **État en mémoire :** les ViewModels du fil, des abonnements et de l'onglet Vous gardent les données de l'ancien compte. Vider la pile de navigation et revenir au fil les recharge.
- **Préférences :** l'onboarding, les centres d'intérêt et la lecture automatique restent réglés pour l'appareil, comme aujourd'hui.
- **Nombre de comptes :** 5 au plus, pour que la feuille reste lisible.

### Libellé proposé pour l'ADR-008 à l'acceptation
« Accepté, remplacé en partie par l'ADR-022 (plusieurs comptes) », dans le fichier de l'ADR-008 et dans l'index. L'ADR-008 n'est pas modifié avant l'acceptation.

### Décisions du lead développeur (4 novembre 2026)
1. Compte sans chaîne : la feuille affiche son nom et son adresse e-mail à la place du @handle, qui appartient à la chaîne.
2. Session expirée d'un compte inactif : le compte reste dans la liste, grisé, avec « Reconnexion requise ». Un appui ouvre la connexion avec l'e-mail prérempli. Il n'est jamais supprimé tout seul.
3. Bascule hors réseau : `deleteToken()` local et enregistrement en attente, rejoué au retour du réseau.
4. Téléchargements existants : attribués au compte actif au moment de la migration, puisque c'est lui qui les a faits ; sans session, supprimés.
5. Préférences (onboarding, centres d'intérêt, lecture automatique) : par appareil, comme aujourd'hui.
6. 5 comptes au plus sur l'appareil.
7. Téléchargement en cours pendant une bascule : il continue et reste au compte qui l'a lancé.
8. Limite de 20 téléchargements : par compte, pas par appareil.
9. Une bascule compte comme une connexion pour la revalidation des téléchargements du compte choisi.
10. Retirer un compte inactif : suppression de ses jetons, de sa clé Keystore et de ses téléchargements (fichier gardé s'il est partagé avec un autre compte), sans appel au serveur pour le jeton FCM, qui appartient au compte actif.

## Alternatives
- **Garder un seul compte** (ADR-008 tel quel). Avantage : aucun travail. Inconvénient : il faut le mot de passe à chaque changement ; ce n'est pas la demande du lead.
- **Bascule qui redemande le mot de passe.** Avantage : séparation forte entre comptes. Inconvénient : contraire à la décision d'une bascule immédiate.
- **Jetons de tous les comptes en mémoire en permanence.** Avantage : bascule un peu plus rapide. Inconvénient : contraire à « seuls les jetons du compte actif sont chargés ».
- **Comptes Android du système (AccountManager).** Avantage : mécanisme officiel de plusieurs comptes. Inconvénient : beaucoup plus lourd (authenticator, service) et sans bénéfice pour la démonstration.

## Conséquences
### Positives
- On change de compte en un appui, sans mot de passe.
- Aucun changement côté serveur.
- Les téléchargements et les notifications restent attachés au bon compte.
- Retirer un compte détruit sa clé (option B).

### Négatives
- `TokenStore`, `TokenCipher` et l'intercepteur doivent gérer un compte actif parmi plusieurs, avec une migration de la session existante.
- Migration Room de `downloaded_videos` (colonne `userId`, clé composée) et suppression des fichiers comptée par vidéo.
- Une bascule doit enchaîner plusieurs actions dans l'ordre : arrêt de la lecture, jeton FCM, PostHog, pile de navigation. Un oubli mélange les données de deux comptes.
- La séparation entre comptes est logique, pas une barrière du système : quiconque tient l'application ouverte peut basculer.
- Un compte inactif plus de 7 jours demande à nouveau le mot de passe.
- La feuille d'aide (ADR-019) doit décrire les comptes multiples.

## Liens
- [ADR-008](ADR-008-authentification-jwt.md) : authentification par JWT ; cet ADR remplace en partie son stockage d'un seul jeu de jetons sur l'appareil. Jetons, durées, refresh à usage unique et serveur inchangés.
- [ADR-020](ADR-020-notifications-fcm.md) : jeton FCM réattribué à chaque bascule.
- [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) : téléchargements attachés à un compte ; suppression à la déconnexion de ce compte seulement.
- [ADR-013](ADR-013-mesures-donnees-personnelles.md), [ADR-006](ADR-006-analytics-posthog.md) : `reset` puis `identify` à chaque bascule.
- [ADR-010](ADR-010-websocket-salles-compteurs.md) : connexion WebSocket ouverte avec l'access token du compte actif.
- [ADR-024](ADR-024-mini-lecteur.md) : une bascule ferme aussi le mini-lecteur.
