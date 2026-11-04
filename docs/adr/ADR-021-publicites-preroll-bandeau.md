# ADR-021 — Publicités vidéo : pré-roll réel, bandeau sponsorisé

## Statut
Proposé

## Contexte
L'ADR-007 (section « Publicité ») prévoit pour les comptes gratuits une publicité simulée avant la lecture : un simple écran de quelques secondes, sans régie réelle. Le code actuel le fait ainsi : `AdOverlay` (WatchScreen.kt) affiche un compte à rebours de 5 s (`AD_SECONDS`, `adSecondsLeft` dans WatchViewModel), sans bouton pour passer, et c'est l'application qui décide d'afficher la publicité (`isPremium()`).

Le 4 novembre 2026, le lead développeur demande de vraies publicités vidéo, avant la lecture et dans un bandeau du fil d'accueil. Il juge lui-même que c'est un changement de fond (de vrais fichiers au lieu d'un écran simulé, une nouvelle table, une page de back-office, un bandeau) : d'où un nouvel ADR, et non un amendement de l'ADR-007.

Contraintes :
- l'accès lié au Premium est toujours décidé côté serveur, jamais seulement dans l'application (ADR-007) ;
- le back-office ne contacte jamais PostgreSQL : tout passe par l'API ;
- les fichiers vidéo sont vérifiés par ffprobe, diffusés en HLS avec le MP4 en repli, et la base ne stocke que des chemins relatifs (ADR-011, ADR-017) ;
- les mesures partent de l'application directement vers PostHog, et l'utilisateur peut les refuser (ADR-006, ADR-013) ;
- une vidéo téléchargée se lit hors ligne, sans serveur (ADR-019) ;
- aucune régie publicitaire réelle : les publicités sont des fichiers hébergés chez nous.

Fichier fourni : `backend/storage/media/ads/Pub vendezvotrevoiture.fr Frank leboeuf.mp4` (20 s, 480p, H.264/AAC). Il contient en plus une image de couverture PNG intégrée comme seconde piste vidéo, à écarter. Le dossier `backend/storage` est ignoré par git : le fichier n'est pas versionné.

## Décision
Remplacer l'écran simulé par de vraies publicités vidéo, hébergées chez nous, choisies par le serveur.

### Pré-roll
- Un compte non Premium voit une publicité avant chaque vidéo, en plein lecteur. Les visiteurs non connectés aussi : ils ne sont pas Premium.
- Avant chaque vidéo, y compris celle enchaînée par la lecture automatique.
- Pendant la publicité : mention « Annonce », barre de progression visible mais non déplaçable, aucun contrôle.
- Avant 5 s : « L'annonce peut être ignorée dans N s ». À partir de 5 s : bouton « Ignorer » en bas à droite.
- Non ignorée, elle va jusqu'au bout, puis la vidéo démarre.
- Jamais de publicité pour un compte Premium, ni sur les Shorts, ni avant une vidéo téléchargée lue hors ligne.

### Bandeau sponsorisé
- En haut du fil d'accueil, pour les non Premium uniquement ; un Premium ne le voit jamais.
- Miniature en grand, mention « Sponsorisé », titre de la publicité.
- Un appui lance la publicité en plein lecteur, avec le même « Ignorer » ; à la fin (ignorée ou terminée), retour au fil.
- Sous la carte, un lien discret « Supprimer les publicités » qui ouvre l'offre Premium.

### Backend
- Nouvelle table AD : titre, fichier, HLS, miniature, durée, date. Une migration Prisma aujourd'hui ; d'autres publicités s'ajoutent ensuite sans migration.
- Plusieurs publicités possibles : une est tirée au sort à chaque lecture. Une seule existe aujourd'hui.
- Même traitement que les vidéos : fichier vérifié par ffprobe, version HLS générée, miniature extraite. La piste d'image de couverture intégrée au fichier fourni est écartée.
- **Décision côté serveur** : la réponse de lecture d'une vidéo inclut la publicité tirée au sort, ou rien (compte Premium, Short). L'application ne décide rien.
- Le bandeau passe par une route qui renvoie une publicité tirée au sort, ou rien pour un Premium.
- Une publicité n'est pas une vidéo du catalogue : elle vit dans sa propre table, pas dans VIDEO. Elle n'a donc ni vue comptée, ni page, ni place dans la recherche, le fil ou une chaîne.

### Back-office
- Page « Publicités » : liste, import de fichier, suppression.
- Même traitement que les vidéos (ffprobe, HLS, miniature), en passant par l'API. L'import envoie le fichier à l'API : c'est nouveau, la création de vidéo du back-office (POST /admin/videos) ne reçoit pas de fichier aujourd'hui.

### Mesures PostHog (envoyées par l'application, ADR-006)
- Pré-roll : `ad_started`, `ad_skipped` (avec le temps écoulé), `ad_completed`, avec l'identifiant de la publicité.
- Bandeau : affichage, appui, appui sur « Supprimer les publicités ».
- Le refus des mesures (ADR-013) s'applique : rien ne part s'il est désactivé.

### Questions ouvertes (à trancher par le lead développeur)
1. **Module backend** : nouveau module `ads`, ou intégration au module `videos` (traitement ffprobe/HLS/miniature) ou `premium` ?
2. **Noms des événements du bandeau** : seuls ceux du pré-roll sont fixés (par exemple `ad_banner_shown`, `ad_banner_clicked`, `ad_remove_clicked` ?).
3. **Lien « Supprimer les publicités » pendant le pré-roll** : l'écran actuel en a un ; « aucun contrôle » le retire-t-il, ou reste-t-il comme sur le bandeau ?
4. **Entrée du fichier fourni en base** : importé par la page « Publicités » du back-office, ou par le seed ? Le fichier n'étant pas versionné, comment est-il présent sur une autre machine ?
5. **Suppression d'une publicité** : ses fichiers (MP4, HLS, miniature) sont-ils supprimés du disque ? Sans aucune publicité en base, la réponse est « rien » : pas de pré-roll ni de bandeau.
6. **Publicité dont le HLS n'est pas encore généré** : lue en MP4, comme une vidéo (ADR-017) ?

## Alternatives
- Garder l'écran simulé de l'ADR-007 : avantage, aucun travail. Inconvénient, ce n'est plus la demande du lead développeur ; pas de vraie publicité ni de « Ignorer » à démontrer.
- Décision d'afficher la publicité dans l'application (comme aujourd'hui) : avantage, aucune modification de l'API. Inconvénient, contraire au principe de l'ADR-007 selon lequel ce qui dépend du Premium est décidé par le serveur ; le lead a choisi la décision côté serveur.
- Stocker les publicités dans VIDEO avec un marqueur : avantage, réutilise la table et ses traitements. Inconvénient, chaque liste, la recherche et le compteur de vues devraient exclure ce marqueur ; le lead a choisi une table AD à part.

## Conséquences
### Positives
- Une vraie publicité vidéo, avec « Ignorer », démontre l'avantage concret du Premium.
- Le serveur décide seul : un client modifié ne peut pas faire passer un compte gratuit pour un Premium.
- Table AD à part : aucune liste, recherche ni compteur de vues à filtrer.
- Ajouter une publicité ne demande ni migration ni nouvelle version de l'application.
- La lecture hors ligne (ADR-019) n'est pas touchée : sans serveur, pas de publicité.
- Taux d'abandon et de clics mesurables dans PostHog.

### Négatives
- Une migration Prisma (table AD) ; CLAUDE.md est mis à jour par un humain à l'acceptation (AD ajouté au modèle de données).
- La réponse de lecture d'une vidéo change de forme (publicité incluse ou non), et une route de plus pour le bandeau.
- Un import de fichier par le back-office, qui n'existe pas aujourd'hui pour les vidéos.
- Le fichier fourni contient une piste d'image à écarter : le traitement doit choisir la bonne piste vidéo.
- Un enchaînement de vidéos (lecture automatique) impose une publicité à chaque fois, ce qui allonge la démonstration en compte gratuit.
- Le code actuel (`AdOverlay`, `adSecondsLeft`, `AD_SECONDS`) est à remplacer, et la décision Premium retirée de l'application.
- Les fichiers sous `/media` restent servis sans contrôle (limite de l'ADR-017).
- À l'acceptation, l'ADR-007 doit passer en « remplacé en partie par l'ADR-021 (publicité) », dans son fichier et dans l'index ; c'est au lead développeur de le faire.

## Liens
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; cet ADR remplace en partie sa section « Publicité » (écran simulé de quelques secondes). « Sans publicité » reste dans le contenu de l'offre. À l'acceptation, son statut devient « remplacé en partie par l'ADR-021 ».
- [ADR-006](ADR-006-analytics-posthog.md), [ADR-013](ADR-013-mesures-donnees-personnelles.md) : mesures PostHog envoyées par l'application, refus possible ; le lien « Supprimer les publicités » ouvre l'écran de l'offre, soumis au test A/B.
- [ADR-017](ADR-017-hls-multi-qualites-sous-titres.md) : HLS multi-qualités avec MP4 en repli, appliqué aux publicités.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : ffprobe, FFmpeg et chemins relatifs, appliqués aux fichiers de publicité.
- [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) : lecture hors ligne des vidéos téléchargées, sans publicité ; l'aide doit décrire les publicités.
- [ADR-016](ADR-016-reglages-publication-video.md) : visibilité ; sans effet sur les publicités, qui ne sont pas des vidéos du catalogue.
- [ADR-003](ADR-003-backend-nestjs.md) : backend hexagonal ; le choix du module est la question ouverte 1.
