# ADR-024 — Mini-lecteur : lecteur unique remonté au niveau de l'ossature de l'application

## Statut
Accepté

## Contexte
**Contradiction avec CLAUDE.md :** CLAUDE.md classe le mini-lecteur « Hors périmètre ». Le 4 novembre 2026, le lead développeur le fait entrer dans le périmètre. À l'acceptation de cet ADR, CLAUDE.md devra le retirer du hors-périmètre ; c'est le lead qui s'en charge.

Aujourd'hui, le lecteur vit dans l'écran de lecture et meurt quand on le quitte :
- `WatchViewModel` crée l'ExoPlayer et le libère dans `onCleared`.
- `WatchScreen.kt` l'affiche dans une `PlayerView` (AndroidView), détachée à la sortie de l'écran (`onRelease`).
- Chaque vidéo ouverte empile un nouvel écran `WatchRoute` (`openVideo` dans AppNavigation.kt), donc un nouveau ViewModel et un nouvel ExoPlayer. Au retour, l'écran précédent reprend son propre lecteur (`onScreenStarted`).

Ce qui vit aujourd'hui dans l'écran de lecture :
- **Compteur de vues et historique :** vue enregistrée une fois, au premier démarrage effectif (`onIsPlayingChanged`, `recordViewOnce`).
- **Qualité et sous-titres :** `trackSelectionParameters`. Changer de qualité fait `stop`/`prepare`/`seekTo`, à cause du décodeur de l'émulateur.
- **Lecture automatique :** compte à rebours `AutoplayCountdown` à la fin de la vidéo.
- **Publicité pré-roll (ADR-021) :** `AdPlaybackController`, avec son propre ExoPlayer.
- **Salle WebSocket de la vidéo (ADR-010) :** ouverte tant que l'écran de lecture est affiché.
- **Libération du décodeur en arrière-plan :** dans `onScreenStopped`, un Premium qui quitte l'application garde le son, piste vidéo coupée (`setTrackTypeDisabled`) et miniature posée par-dessus. Sinon, `stop` et miniature, puis `prepare`/`seekTo` au retour. La miniature reste jusqu'à `onRenderedFirstFrame`. Raison : sur l'émulateur, une surface recréée avec un décodeur ouvert donne une image corrompue.
- **Son en arrière-plan Premium (ADR-007) :** `BackgroundPlayback`, un singleton Hilt, enveloppe le lecteur de l'écran dans une `MediaSession` confiée à `PlaybackService` (un `MediaSessionService`). La session prend n'importe quel `Player`.
- **Plein écran :** il passe en paysage (commit 6388f2f, `rememberFullscreen`), et Retour en sort déjà.
- **Autres lecteurs :** le lecteur hors ligne (`OfflinePlayerViewModel`), les Shorts (`ShortsViewModel`) et l'aperçu du bandeau sponsorisé (`SponsoredAdPreview`) ont chacun leur ExoPlayer.

## Décision
Remonter le lecteur au niveau de l'ossature de l'application, au-dessus de la navigation. Une seule instance de lecteur, partagée entre l'écran de lecture et le mini-lecteur. Le lecteur n'est jamais recréé en passant de l'un à l'autre.

### Comportement
- Un glissement vers le bas sur le lecteur le réduit en mini-lecteur, ancré en bas, juste au-dessus de la barre d'onglets.
- La lecture ne s'interrompt jamais, ni l'image ni le son.
- Contenu du mini-lecteur : la vidéo en petit à gauche ; à droite, le titre sur une ligne et le nom de la chaîne ; puis lecture/pause et une croix.
- Un appui dessus rouvre l'écran de lecture à la position exacte.
- Un glissement latéral le ferme et arrête la lecture, comme la croix.
- On navigue normalement pendant ce temps (fil, recherche, chaîne, Vous), et la barre d'onglets reste utilisable.
- Ouvrir une autre vidéo remplace celle du mini-lecteur.
- Retour depuis l'écran de lecture réduit en mini-lecteur, au lieu de fermer.

### Cas particuliers
- Pas de mini-lecteur pour les Shorts.
- **Annonce (ADR-021) :** pas de réduction pendant une annonce, jamais, même une fois « Ignorer » disponible. Glissement et Retour sont ignorés pendant toute l'annonce (décision du lead).
- **Plein écran paysage :** le glissement vers le bas sort d'abord du plein écran, comme Retour aujourd'hui.
- **Écrans plein espace :** le mini-lecteur est masqué sur l'onglet Shorts, la publication, le paywall et en plein écran.
- **Premium :** il garde le son en arrière-plan comme aujourd'hui ; le mini-lecteur n'y change rien.
- **Changement de compte (ADR-022) :** une bascule arrête la lecture, donc ferme le mini-lecteur.

### Propriétaire du lecteur

Option A, ViewModel lié à l'activité (un « lecteur en cours » partagé par l'écran de lecture et le mini-lecteur) :
- Avantages : durée de vie de l'interface ; survit aux rotations ; libéré proprement dans `onCleared` ; proche du code actuel.
- Inconvénient : tout ce qui touche au lecteur doit passer par ce ViewModel commun.

Option B, singleton injecté par Hilt :
- Avantages : accessible partout ; survit même à l'activité.
- Inconvénients : aucune libération automatique ; il faut libérer à la main, sinon le décodeur reste ouvert ; risque de fuite de l'activité via la vue.

Option C, `MediaSessionService` de Media3 qui possède le lecteur, et `MediaController` côté interface :
- Avantages : architecture recommandée par Media3 ; la lecture en arrière-plan devient naturelle.
- Inconvénients : le `MediaController` est asynchrone et ne donne pas directement l'ExoPlayer (sélection de pistes, `onRenderedFirstFrame`). C'est une refonte de `BackgroundPlayback`, de `PlaybackService` et de toutes les parades du décodeur ; trop lourd dans le délai.

**Recommandation : option A.** C'est compatible avec le son en arrière-plan Premium : `BackgroundPlayback.start(player)` reçoit le même lecteur qu'aujourd'hui. Il vient seulement d'un autre propriétaire, et `PlaybackService` ne change pas.

### La vue
- **Option 1, une seule `PlayerView` posée à la racine,** au-dessus du NavHost. Elle ne fait que changer de taille et de position : zone 16:9 de l'écran de lecture, ou vignette du mini-lecteur. L'écran de lecture réserve la place et en communique la position.
  - Avantages : la surface n'est jamais détruite. Elle est seulement redimensionnée, ce qui ne réinitialise pas le décodeur. C'est ce qui évite l'image corrompue sans couper la piste vidéo.
  - Inconvénients : la vue doit rester au même endroit de l'arbre Compose, sans quoi l'AndroidView est recréée. Elle doit suivre les animations de navigation de l'écran de lecture. Les contrôles Media3 sont masqués en mini (`useController = false`).
- **Option 2, deux vues qui se passent le lecteur** (`PlayerView.switchTargetView`).
  - Avantage : chaque emplacement garde sa vue, plus simple à mettre en page.
  - Inconvénient : chaque passage change de surface avec le décodeur ouvert, exactement le cas qui corrompt l'image sur cet émulateur. Il faudrait la parade actuelle (couper la piste ou `stop`/`prepare`/`seekTo`, miniature jusqu'à la première image), donc une coupure d'image à chaque passage. Contraire à « la lecture ne s'interrompt jamais ».
- **Recommandation : option 1.**

### L'écran de lecture
- Option a, l'écran de lecture reste une destination `WatchRoute`. Réduire dépile l'écran, le lecteur continue à la racine, et rouvrir renavigue vers `WatchRoute` avec la même vidéo sans la recharger.
- Option b, l'écran de lecture devient une couche posée au-dessus du NavHost, hors navigation.
  - Avantage : il ne meurt pas en réduction, donc les commentaires et l'état restent.
  - Inconvénient : les ouvertures par `WatchRoute` (notification de l'ADR-020, paywall) sont à adapter.
- **Recommandation : option b.**
- Dans les deux cas, la pile actuelle de vidéos (A, puis suggestion B, puis Retour vers A) disparaît : Retour réduit (question 1).

### Ce qui change de cycle de vie
- **Compteur de vues et historique :** enregistrés par le propriétaire du lecteur, une fois par vidéo, quel que soit l'affichage.
- **Qualité et sous-titres :** réglages du lecteur partagé ; ils survivent à la réduction. Le menu reste dans l'écran de lecture.
- **Lecture automatique :** gérée par le propriétaire du lecteur (question 2 : continue-t-elle en mini-lecteur ?).
- **Publicité pré-roll :** l'annonce se joue dans l'écran de lecture, sans réduction possible. Son ExoPlayer distinct (`AdPlaybackController`) peut rester lié à l'écran de lecture : il est terminé avant toute réduction.
- **Salle WebSocket :** recommandation, la fermer à la réduction et la rouvrir à la réouverture. Commentaires et compteurs ne s'affichent que dans l'écran de lecture.
- **Libération du décodeur en arrière-plan :** elle ne dépend plus de l'écran de lecture, mais de l'activité (`ON_STOP`) dès qu'une vidéo est en cours, en plein écran comme en mini-lecteur. Les parades actuelles s'appliquent toujours à la sortie de l'application.
- **Lecteur hors ligne (ADR-019) :** pas de mini-lecteur (question 3). L'ouvrir ferme le mini-lecteur.
- **Écrans masqués :** quand le mini-lecteur est masqué (Shorts, publication, paywall), la lecture est mise en pause (question 4), sinon deux sons se superposent avec les Shorts.

### Mise en œuvre
- Ordre retenu par le lead : ADR-022 (comptes), puis ADR-023 (commentaires), puis cet ADR. Le changement de compte et les commentaires restent sur `main`.
- Le mini-lecteur est développé sur une branche dédiée, pas sur `main`. Il n'est fusionné que quand tout est testé : lecture, Shorts, annonce, hors ligne, son en arrière-plan du Premium.
- Si la branche dérape ou casse un lecteur existant, elle est abandonnée.

### Questions au lead développeur
1. **Pile de vidéos :** vidéo A, puis suggestion B, puis Retour. Aujourd'hui, on revient à A. Avec le lecteur unique, Retour réduit B en mini-lecteur, et A n'est plus dans une pile. Accepté ?
2. **Lecture automatique en mini-lecteur :** la vidéo suivante s'enchaîne-t-elle aussi en mini-lecteur, et avec une annonce avant elle pour un non Premium ? Si oui, il faut rouvrir l'écran de lecture pour l'annonce, puisqu'une annonce ne se réduit pas.
3. **Lecteur hors ligne :** sans mini-lecteur (recommandé) ?
4. **Écrans où le mini-lecteur est masqué** (Shorts, publication, paywall) : pause, puis reprise au retour (recommandé) ? Ou fermeture ?
5. **Bandeau sponsorisé (ADR-021) :** il se lit tout seul en haut du fil. Avec un mini-lecteur actif, deux vidéos tournent en même temps (deux décodeurs) ; le bandeau reste en sourdine. Faut-il suspendre le bandeau quand le mini-lecteur est présent ?
6. **Salle WebSocket :** fermée à la réduction et rouverte à la réouverture (recommandé) ?

## Alternatives
- **Garder le lecteur dans l'écran de lecture et en créer un second pour le mini-lecteur** à la même position. Avantage : peu de refonte. Inconvénients : rechargement, coupure du son et de l'image à chaque passage ; contraire à la décision.
- **Mini-lecteur par Picture-in-Picture Android.** Avantage : fourni par le système. Inconvénient : fenêtre hors de l'application, sans navigation dedans, et sans le comportement demandé (ancré au-dessus des onglets).
- **Ne pas faire de mini-lecteur** (hors périmètre selon CLAUDE.md). Avantage : aucun risque sur les lecteurs existants. Inconvénient : ce n'est plus la demande du lead.

## Conséquences
### Positives
- On continue de regarder en naviguant, sans coupure.
- Une seule surface, jamais recréée en passage plein lecteur ↔ mini-lecteur, d'où pas d'image corrompue sur l'émulateur.
- Le son en arrière-plan Premium reste inchangé (`BackgroundPlayback` accepte le même lecteur).
- La branche dédiée protège `main` : abandon possible sans dégât.

### Négatives
- Refonte profonde : `WatchViewModel` est découpé entre le propriétaire du lecteur (activité) et l'écran de lecture. Vues, historique, qualité, sous-titres, lecture automatique et libération du décodeur changent de place.
- La vue unique à la racine doit suivre exactement la zone de l'écran de lecture, y compris pendant les animations et le plein écran paysage.
- La pile de vidéos change de comportement (question 1).
- Plus de lecteurs simultanés possibles (mini-lecteur, bandeau sponsorisé, annonce) : charge sur les décodeurs, à surveiller sur l'émulateur.
- Risque sur tous les lecteurs existants, d'où la branche dédiée et la liste de tests avant fusion.
- L'aide (ADR-019) doit décrire le mini-lecteur.
- CLAUDE.md est mis à jour par un humain à l'acceptation : mini-lecteur retiré du hors-périmètre.

## Liens
- [ADR-007](ADR-007-offre-premium.md) : lecture en arrière-plan Premium, conservée avec le nouveau propriétaire du lecteur.
- [ADR-021](ADR-021-publicites-preroll-bandeau.md) : pré-roll jamais réductible ; bandeau sponsorisé à lecture automatique (question 5).
- [ADR-010](ADR-010-websocket-salles-compteurs.md) : salle WebSocket de la vidéo, liée à l'écran de lecture visible.
- [ADR-017](ADR-017-hls-multi-qualites-sous-titres.md) : qualités et sous-titres, réglages du lecteur partagé.
- [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) : lecteur hors ligne, sans mini-lecteur.
- [ADR-020](ADR-020-notifications-fcm.md) : l'appui sur une notification ouvre l'écran de lecture, à adapter si celui-ci quitte la navigation.
- [ADR-022](ADR-022-plusieurs-comptes-appareil.md) : une bascule de compte ferme le mini-lecteur.
- [ADR-001](ADR-001-android-natif.md) : Media3 ExoPlayer et Jetpack Compose.
