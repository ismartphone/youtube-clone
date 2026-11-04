# ADR-021 — Publicités vidéo : pré-roll réel, bandeau sponsorisé

## Statut
Accepté

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
- Pendant la publicité : mention « Annonce », barre de progression visible mais non déplaçable, aucun contrôle de lecture (ni pause, ni déplacement dans la barre).
- Avant 5 s : « L'annonce peut être ignorée dans N s ». À partir de 5 s : bouton « Ignorer » en bas à droite.
- Un lien « Supprimer les publicités » reste affiché, car un lien vers l'offre Premium n'est pas un contrôle de lecture. Il est discret (un simple texte, pas un bouton) et placé loin du bouton « Ignorer », pour éviter les clics par erreur. C'est le moment où l'utilisateur est le plus agacé, donc le plus réceptif.
- Une bande cliquable en bas à gauche, comme sur YouTube, porte l'initiale et le nom de l'annonceur, ainsi que le libellé « Visiter le site ». Elle est placée à l'opposé du bouton « Ignorer ». Le nom de l'annonceur est le domaine de l'URL de destination : aucun champ annonceur n'existe. Un appui ouvre l'URL de destination dans le navigateur du téléphone et met l'annonce en pause. Au retour dans l'application, elle reprend où elle en était. La même bande figure sur l'annonce lancée depuis le bandeau.
- Non ignorée, elle va jusqu'au bout, puis la vidéo démarre.
- Tant que son HLS n'est pas généré, la publicité est lue en MP4, comme une vidéo (ADR-017).
- Jamais de publicité pour un compte Premium, ni sur les Shorts, ni avant une vidéo téléchargée lue hors ligne.

### Bandeau sponsorisé
- En haut du fil d'accueil, pour les non Premium uniquement ; un Premium ne le voit jamais.
- Miniature en grand, mention « Sponsorisé », titre de la publicité.
- Un appui sur la carte ouvre l'URL de destination dans le navigateur du téléphone.
- La lecture passe par un bouton « Lecture » sur la vignette, qui sépare les deux gestes. Il lance la publicité en plein lecteur, avec le même « Ignorer » et la même bande « Visiter le site ». À la fin, ignorée ou terminée, on revient au fil.
- Sous la carte, un lien discret « Supprimer les publicités » qui ouvre l'offre Premium.

### Backend
- Nouveau module `ads` : il porte la table AD, les routes du back-office et le tirage au sort. Le module `videos` l'utilise par un port, pour la publicité avant lecture et pour la route du bandeau, puisque c'est lui qui sait si le spectateur est Premium.
- Nouvelle table AD : titre, fichier, HLS, miniature, durée, date, URL de destination (`destinationUrl`). Une migration Prisma pour la table, puis une seconde pour `destinationUrl` ; d'autres publicités s'ajoutent ensuite sans migration.
- Le titre est saisi à l'import et n'est jamais déduit du nom du fichier : il s'affiche tel quel sur le bandeau, en haut du fil, et c'est la première chose qu'on voit.
- `destinationUrl` est saisie à l'import. Elle est validée : http ou https, adresse complète. Elle n'est nullable en base que pour la ligne antérieure à son ajout.
- Plusieurs publicités possibles : une est tirée au sort à chaque lecture. Une seule existe aujourd'hui.
- Même traitement que les vidéos : fichier vérifié par ffprobe, version HLS générée, miniature extraite. La piste d'image de couverture intégrée au fichier fourni est écartée.
- **Décision côté serveur** : la réponse de lecture d'une vidéo inclut la publicité tirée au sort, ou rien (compte Premium, Short). L'application ne décide rien.
- Le bandeau passe par une route qui renvoie une publicité tirée au sort, ou rien pour un Premium.
- Une publicité n'est pas une vidéo du catalogue : elle vit dans sa propre table, pas dans VIDEO. Elle n'a donc ni vue comptée, ni page, ni place dans la recherche, le fil ou une chaîne.
- Supprimer une publicité supprime aussi ses fichiers (MP4, HLS, miniature), comme une vidéo. Sans aucune publicité en base : ni pré-roll ni bandeau.
- Commande `npm run ads:import -- "<fichier>" "<titre>" "<URL du site>"`, sur le modèle de `seed:cc-videos` : elle vérifie le fichier de `backend/storage/media/ads/` par ffprobe et l'enregistre sans doublon, avec son titre et son URL de destination. Sans argument, elle liste les fichiers à enregistrer. C'est par elle que le fichier fourni entre en base. Le fichier n'étant pas versionné, il se dépose à la main dans ce dossier sur une autre machine, comme les vidéos CC ; c'est documenté dans le README du backend.

### Back-office
- Page « Publicités » : liste, import de fichier (avec titre et URL de destination), modification du titre et de l'URL de destination côte à côte (PATCH /admin/ads/:id), suppression.
- Même traitement que les vidéos (ffprobe, HLS, miniature), en passant par l'API. L'import envoie le fichier à l'API : c'est nouveau, la création de vidéo du back-office (POST /admin/videos) ne reçoit pas de fichier aujourd'hui.

### Mesures PostHog (envoyées par l'application, ADR-006)
- Pré-roll : `ad_started`, `ad_skipped` (avec le temps écoulé), `ad_completed`, avec l'identifiant de la publicité.
- Pré-roll, lien « Supprimer les publicités » : `remove_ads_clicked_preroll`, avec l'identifiant de la publicité.
- Bandeau : `sponsored_ad_shown`, `sponsored_ad_clicked`, `remove_ads_clicked`, chacun avec l'identifiant de la publicité. `sponsored_ad_clicked` mesure l'appui sur le bandeau qui ouvre le site, et non plus le lancement de l'annonce.
- Bande « Visiter le site » : `ad_clicked`.
- `ad_clicked` et `sponsored_ad_clicked` portent `ad_id` et `placement`, qui vaut `preroll`, `sponsored_player` ou `home_banner`.
- Les deux liens « Supprimer les publicités » ont des noms distincts pour comparer lequel des deux emplacements convertit le mieux.
- Le refus des mesures (ADR-013) s'applique : rien ne part s'il est désactivé.

### Décisions du lead développeur (4 novembre 2026)
1. Nouveau module backend `ads` (table AD, routes du back-office, tirage au sort), utilisé par le module `videos` par un port pour la publicité avant lecture et la route du bandeau.
2. Événements du bandeau : `sponsored_ad_shown`, `sponsored_ad_clicked`, `remove_ads_clicked`, avec l'identifiant de la publicité. Le sens de `sponsored_ad_clicked` a été ajusté par l'amendement du 4 novembre : il mesure désormais l'ouverture du site.
3. « Aucun contrôle » signifie aucun contrôle de lecture. Pendant le pré-roll, un lien discret « Supprimer les publicités » (texte, loin de « Ignorer ») reste, mesuré par `remove_ads_clicked_preroll`.
4. Le fichier fourni entre en base par `npm run ads:import` (ffprobe, sans doublon) ; sur une autre machine, il se dépose à la main dans `backend/storage/media/ads/`, documenté dans le README du backend.
5. Supprimer une publicité supprime ses fichiers ; sans publicité en base, ni pré-roll ni bandeau.
6. Tant que le HLS n'est pas généré, la publicité est lue en MP4.

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
- Une migration Prisma (table AD) ; CLAUDE.md est mis à jour par un humain à l'acceptation (AD ajouté au modèle de données, module `ads` ajouté).
- Une seconde migration pour `destinationUrl`, nullable en base pour la seule ligne antérieure : le code doit tolérer une publicité sans URL.
- Le nom de l'annonceur est déduit du domaine de l'URL, faute de champ dédié.
- Un nouveau module backend et une commande `npm run ads:import` à maintenir ; le fichier de publicité, non versionné, se dépose à la main sur chaque machine.
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
- [ADR-003](ADR-003-backend-nestjs.md) : backend hexagonal, monolithe modulaire ; nouveau module `ads`, utilisé par le module `videos` par un port.

Amendé le 4 novembre : titre saisi à l'import (`npm run ads:import -- "<fichier>" "<titre>" "<URL du site>"`) et modifiable depuis la page Publicités (PATCH /admin/ads/:id), jamais déduit du nom du fichier ; nouveau champ `AD.destinationUrl`, validé (http ou https, adresse complète) ; bande « Visiter le site » en bas à gauche pendant l'annonce, qui ouvre le site et met l'annonce en pause jusqu'au retour dans l'application ; sur le bandeau, un appui sur la carte ouvre le site et la lecture passe par un bouton « Lecture », ce qui remplace « un appui la lance en plein lecteur » ; nouvel événement `ad_clicked`, et `sponsored_ad_clicked` mesure désormais l'ouverture du site, tous deux avec `ad_id` et `placement`. Raison : ajustements de périmètre demandés par le lead développeur ; pour le titre, il s'affiche tel quel sur le bandeau, en haut du fil, et c'est la première chose qu'on voit.
