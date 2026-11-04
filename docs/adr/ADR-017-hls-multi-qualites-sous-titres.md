# ADR-017 — Diffusion HLS multi-qualités et sous-titres automatiques, produits hors ligne par FFmpeg

## Statut
Accepté

## Contexte
Le menu du lecteur de l'application reçoit deux ajouts : un sélecteur de qualité et des sous-titres.

L'ADR-002 sert les vidéos en MP4 progressif, une seule qualité par fichier, et a écarté le streaming adaptatif HLS : il n'avait pas d'intérêt tant qu'il n'existait qu'une seule qualité par vidéo. Ce raisonnement reste juste. C'est le besoin qui change le 3 novembre 2026 : un sélecteur de qualité suppose plusieurs qualités par vidéo, donc l'échelle de qualités que l'ADR-002 annonçait comme évolution prévue. Il s'agit d'une évolution du besoin, non d'une erreur de l'ADR-002.

Les sous-titres n'existent pas aujourd'hui : aucune vidéo n'en est fournie, et il faut donc les produire.

Contraintes :
- l'application reste 100 % native (ADR-001) : la lecture passe par Media3 ExoPlayer ;
- les fichiers sont hébergés chez nous, dans un dossier local en développement, sur Cloudflare R2 avec un CDN au déploiement (ADR-002) ;
- le serveur sert les fichiers en statique sous `/media`, sans transcodage à la lecture (ADR-002, ADR-011) ;
- FFmpeg est une dépendance système du backend, et le serveur démarre sans lui (ADR-011) ;
- la base ne stocke que des chemins relatifs ou des URL, jamais les fichiers (ADR-011) ;
- les vidéos entrent au catalogue par le seed et ses commandes, par le back-office, et par l'envoi depuis l'application, validé par ffprobe (ADR-011, ADR-015) ;
- l'accès Premium (ADR-007) et la visibilité (ADR-016) sont toujours vérifiés côté serveur ;
- le rendu est le 6 novembre 2026, le temps est limité : les deux ajouts doivent pouvoir être livrés séparément ;
- aucun service externe de transcription n'est utilisé.

## Décision
Ajouter au lecteur une diffusion HLS multi-qualités et des sous-titres générés automatiquement. Les deux sont produits hors ligne par FFmpeg, une seule fois, au moment de l'import ou de l'envoi. Le serveur continue de servir des fichiers statiques sous `/media`.

Les deux parties sont indépendantes et peuvent être livrées séparément. Si la transcription ne donne rien d'exploitable, les sous-titres sont abandonnés et la diffusion HLS est gardée.

### Vidéos concernées
- Seules les vidéos dont le fichier MP4 est hébergé chez nous reçoivent une échelle HLS et des sous-titres.
- Une vidéo référencée par une URL externe depuis le back-office n'a ni HLS ni sous-titres : elle est lue en MP4, comme aujourd'hui.

### Envoi depuis l'application : génération en tâche de fond
- À l'envoi depuis l'application (ADR-015), la vidéo est publiée tout de suite en MP4. La génération HLS et celle des sous-titres se font ensuite en tâche de fond, sans allonger la requête d'envoi.
- Tant que la génération n'est pas terminée, la vidéo est lue en MP4 et sans sous-titres.

### Partie 1 — Diffusion HLS multi-qualités

#### Génération
- À l'import (seed et ses commandes) et à l'envoi depuis l'application (ADR-015, en tâche de fond), FFmpeg génère une échelle HLS de la vidéo.
- Segments d'environ 4 secondes, un manifeste principal et un manifeste par qualité, rangés sous `/media/hls/<identifiant aléatoire>/`. Cet identifiant est généré par le serveur et distinct de celui de la vidéo, comme le nom des fichiers MP4 envoyés depuis l'application (ADR-015).
- Qualités possibles : 720p, 480p, 360p. Seules sont générées celles qui ne dépassent pas la source : une vidéo n'est jamais agrandie. La comparaison porte sur le petit côté de l'image : la hauteur pour une vidéo horizontale, la largeur pour un Short vertical. Certaines vidéos n'ont donc qu'une ou deux qualités.
- L'échelle est plafonnée à 720p : la qualité 1080p, prévue au départ, est retirée. La démonstration se fait sur l'émulateur Android, dont le décodeur logiciel plante sur le flux 1080p (code de sortie 139, erreur de segmentation, observé deux fois). C'est un choix de fiabilité sur émulateur : le décodeur matériel d'un vrai appareil n'a pas cette limite. Une seule vidéo du catalogue était en 1080p, et l'écart ne se voit pas sur un écran de téléphone.
- Les vidéos du seed, réencodées en 720p (ADR-011), reçoivent les trois qualités.
- Une commande npm génère le HLS manquant pour les vidéos déjà en base.
- C'est un transcodage hors ligne, fait une fois. Il n'y a aucun transcodage à la lecture : le serveur sert les segments et les manifestes en statique sous `/media`, comme les autres fichiers.

#### Un échec ne bloque pas la publication
Si la génération HLS échoue, la vidéo reste publiée et lue en MP4, et l'échec est journalisé.

#### MP4 conservé en repli
- Le MP4 progressif existant est conservé.
- Le modèle VIDEO porte les deux chemins relatifs : celui du MP4 (`videoUrl`, existant) et celui du manifeste HLS principal (`hlsUrl`, nouveau champ facultatif, ajouté par une migration Prisma).
- L'API renvoie les deux.
- L'application lit le HLS quand il existe, le MP4 sinon.
- L'accès Premium (ADR-007) et la visibilité (ADR-016) s'appliquent de la même manière aux deux formats.

#### Lecteur Android
- Media3 lit le HLS nativement (HlsMediaSource) et adapte la qualité au débit.
- Le menu de qualité agit sur le sélecteur de pistes du lecteur : « Auto » laisse le lecteur décider, une qualité choisie lui est imposée.
- Le menu liste les qualités réellement disponibles pour la vidéo, et non une liste figée.

### Partie 2 — Sous-titres automatiques

#### Génération
- La transcription est faite par FFmpeg compilé avec le filtre whisper (whisper.cpp intégré), sans service externe. Le FFmpeg installé sur le poste de développement (build Gyan 9.0.2 « full ») contient ce filtre (`--enable-whisper`), vérifié avec `ffmpeg -filters`.
- Le modèle Whisper retenu est le modèle base. Il est téléchargé dans un dossier non versionné.
- À l'import et à l'envoi (en tâche de fond), FFmpeg transcrit la piste audio, avec détection automatique de la langue. Le filtre whisper ne sait pas produire de WebVTT (il sort du texte, du SRT ou du JSON) : il produit un SRT, que le backend convertit en WebVTT. Le fichier WebVTT est stocké sous `/media/subtitles`.
- Nouveau champ `subtitleUrl` sur VIDEO, facultatif, ajouté par une migration Prisma.
- Une commande npm génère les sous-titres manquants pour les vidéos déjà en base.

#### Garde-fou contre les répliques inventées
- Sur un fond sonore sans parole (bruit de rue, musique), la reconnaissance vocale invente des phrases. C'est une limite connue de la technique, pas un défaut du code.
- Constat : sur un clip Pexels de 30 s de bruit de rue, whisper a produit « We should put that. That's our station. » entre des annotations « [Music] » et « (crickets chirping) ».
- Règle : une vidéo de plus de 20 secondes dont la transcription compte moins de 10 mots est considérée comme sans parole et ne reçoit pas de sous-titres. Pour une vidéo de 20 s ou moins, un seul mot suffit.
- Les annotations de bruit entre crochets ou parenthèses, et le symbole ♪, ne comptent pas comme des mots.
- La vraie parole d'une vidéo de 37 s (cc-04) passe le garde-fou.

#### Limite de la reconnaissance vocale : vidéos sans sous-titres
- Le modèle base de whisper ne gère correctement ni le tagalog ni les paroles chantées. C'est une limite de la technique, pas du code.
- Constats sur l'import Creative Commons : pour cc-03 (« 11 SPECTATORS! » de Barret gaming, en tagalog), la langue a été détectée comme de l'allemand, avec un texte sans rapport (« Löcke ich schatz nalang! ») ; pour cc-06 (clip de rap français de Hamxton), les paroles sont en partie inventées.
- Décision : ces deux vidéos n'ont pas de sous-titres. De mauvais sous-titres se remarquent plus qu'une absence de sous-titres.
- Mise en œuvre : une liste `SUBTITLES_DISABLED_FOR` dans le domaine (cc-03 et cc-06), respectée par la transcription en tâche de fond, par la commande `npm run subtitles:generate` et par l'import Creative Commons.
- Les quatre autres vidéos Creative Commons (anglais et français parlés) ont des sous-titres exploitables.

#### Longueur des répliques
- Règle du sous-titrage : deux lignes à l'écran au plus, environ 40 caractères par ligne. Trois lignes masquent l'image.
- Le filtre whisper est appelé avec l'option `max_len=80`, qui coupe les répliques entre les mots.
- La conversion SRT → WebVTT répartit ensuite chaque réplique sur deux lignes équilibrées de 42 caractères au plus. Une réplique encore trop longue est scindée en plusieurs, son temps étant réparti au prorata des caractères.
- Quand whisper fait chevaucher deux répliques (constaté : une réplique de 10 s suivie d'une autre commençant au même instant), la première s'arrête au début de la suivante.
- Ensuite, une réplique de moins d'une seconde rejoint la précédente quand l'ensemble tient sur deux lignes. Si la précédente est pleine, elle rejoint la suivante quand l'ensemble tient ; ce second recours est un choix d'implémentation. Sinon, elle est allongée à une seconde, quitte à retarder un peu la suivante.
- Mesures sur cc-01 (27 min) : plus aucune réplique sous une seconde, un retard maximal d'environ 1,2 s par rapport au temps donné par whisper, et seules 28 répliques sur 568 retardées de plus d'une demi-seconde. Sur cc-05 : un retard maximal d'environ 1 s.

#### La génération ne bloque jamais la publication
- La transcription a un temps maximal : deux fois la durée de la vidéo, et cinq minutes au plus. Au-delà, elle est abandonnée.
- Si la génération échoue ou dépasse ce temps, la vidéo reste publiée sans sous-titres, et l'échec est journalisé.
- Si le FFmpeg installé ne contient pas le filtre whisper, les vidéos sont publiées sans sous-titres, et une erreur est journalisée.

#### Lecteur Android
- Un bouton active ou désactive les sous-titres.
- Le menu indique que les sous-titres sont « générés automatiquement ».
- Seul le lecteur des vidéos classiques propose le bouton CC, désactivé au démarrage comme sur YouTube, et la mention « générés automatiquement » dans son menu.
- Les Shorts reçoivent des sous-titres générés, mais le lecteur vertical des Shorts n'a ni bouton ni menu de sous-titres : ils n'y sont pas affichés. Ce point est laissé en l'état.

### Suppression d'une vidéo
Quand une vidéo est supprimée, son dossier HLS et son fichier de sous-titres sont supprimés du stockage, ainsi que son fichier MP4 quand il est hébergé chez nous. Cela corrige un défaut actuel : jusqu'ici, le MP4 d'une vidéo supprimée restait sur le disque.

### Limite connue : les fichiers sous `/media` ne sont pas protégés
L'identifiant aléatoire du dossier HLS, comme le nom des MP4 généré par le serveur, relève de l'obscurité, pas de la sécurité. L'API vérifie l'accès Premium (ADR-007) et la visibilité (ADR-016) avant de remettre le chemin d'un fichier, mais le fichier lui-même est servi en statique sans contrôle : quelqu'un qui obtient l'adresse peut la rejouer. La limite vaut pour les segments HLS, les MP4 et les sous-titres.

La vraie réponse serait des URL signées ou un relais authentifié devant les fichiers. Elle n'est pas mise en place, faute de temps avant le rendu.

### Menu du lecteur : lecture automatique
Le menu du lecteur comprend aussi un interrupteur « Lecture automatique », branché sur le même réglage que Vous → Paramètres ; les deux restent synchronisés. Ce n'est pas une décision d'architecture, il est mentionné ici parce qu'il fait partie du même menu.

### Limite connue : pas de Chromecast ni de diffusion sur télé
Le lecteur ne propose ni Chromecast ni diffusion sur une télé. Le serveur de développement tourne en local et n'est joignable ni depuis une télé ni depuis Internet : la connexion à la télé aboutirait, puis la lecture échouerait. Un bouton mort est pire qu'un bouton absent : il n'est donc pas affiché.

## Alternatives
- Laisser le MP4 seul, avec un sélecteur de qualité qui ne fait rien : avantage, aucun travail côté serveur. Inconvénient, un bouton sans effet, qui se remarque en démonstration ; même principe que pour les réglages non affichés de l'ADR-016.
- Une seule qualité réencodée (par exemple une version basse en plus du MP4) : avantage, plus simple et moins d'espace disque. Inconvénient, pas d'adaptation au débit, et un sélecteur réduit à deux choix figés qui ne reflète pas les qualités réelles de la vidéo.
- Plusieurs MP4 progressifs, un par qualité, sans HLS : avantage, pas de segments ni de manifestes. Inconvénient, pas d'adaptation automatique au débit : « Auto » n'aurait pas de sens, et changer de qualité oblige à recharger un autre fichier.
- DASH au lieu de HLS : avantage, lu aussi par Media3. Inconvénient, il n'apporte rien de plus ici que HLS pour le même travail de génération ; HLS est le format que l'ADR-002 annonçait comme évolution prévue.
- Transcodage à la volée, à la lecture : avantage, aucun fichier supplémentaire sur le disque. Inconvénient, un calcul lourd à chaque lecture, contraire au principe de l'ADR-002 où le serveur sert des fichiers statiques, et incompatible avec un CDN devant Cloudflare R2.
- Service de transcription externe (API de reconnaissance vocale en ligne) : avantage, rien à compiler ni à télécharger, qualité potentiellement meilleure. Inconvénient, un service externe de plus, avec ses clés et ses coûts, et l'audio des vidéos envoyé à un tiers ; écarté au profit d'une transcription locale.
- Faire bloquer la publication par la génération HLS ou celle des sous-titres : avantage, toute vidéo publiée aurait son échelle de qualités et ses sous-titres. Inconvénient, un échec ou une lenteur de l'encodage ou de la transcription empêcherait de publier, et l'envoi depuis l'application attendrait la fin des traitements.
- Nommer le dossier HLS d'après l'identifiant de la vidéo (`/media/hls/<id>/`) : avantage, aucun identifiant de plus. Inconvénient, le chemin se déduit de l'identifiant, connu de quiconque voit l'entrée de la vidéo dans une liste.
- URL signées ou relais authentifié devant les fichiers : avantage, le fichier lui-même est protégé, et pas seulement la remise de son chemin. Inconvénient, du travail en plus ; écarté faute de temps, la limite est documentée.
- Garder la qualité 1080p dans l'échelle : avantage, une qualité de plus pour les sources 1080p, que le décodeur matériel d'un vrai appareil lit sans difficulté. Inconvénient, le décodeur logiciel de l'émulateur Android, utilisé pour la démonstration, plante sur ce flux (code de sortie 139) ; une seule vidéo du catalogue est concernée, et l'écart ne se voit pas sur un écran de téléphone.
- Chromecast ou diffusion sur télé : avantage, fonction attendue d'un clone de YouTube. Inconvénient, le serveur de développement n'est pas joignable depuis une télé : la lecture échouerait après la connexion.

## Conséquences
### Positives
- Le sélecteur de qualité agit réellement, et liste les qualités vraiment disponibles pour chaque vidéo.
- La qualité s'adapte au débit en mode « Auto », ce que le MP4 progressif ne permettait pas.
- Le serveur reste un simple serveur de fichiers statiques sous `/media` : aucun transcodage à la lecture, et les fichiers HLS peuvent passer par le CDN Cloudflare R2 au déploiement comme les autres.
- Le MP4 conservé en repli permet de lire une vidéo dont le HLS n'a pas été généré.
- Aucune vidéo n'est agrandie : pas de qualité affichée qui ne serait qu'une source étirée.
- Des sous-titres sans service externe, sans clé ni coût, sans envoyer l'audio à un tiers.
- La publication n'est jamais bloquée, ni par le HLS ni par les sous-titres : le MP4 sert de repli.
- L'envoi depuis l'application n'est pas allongé : la génération se fait en tâche de fond, après la publication.
- La suppression d'une vidéo libère enfin son MP4 hébergé chez nous, en plus de son dossier HLS et de ses sous-titres.
- La limite de protection des fichiers sous `/media` est écrite, et la réponse attendue (URL signées ou relais authentifié) est identifiée.
- Les deux parties sont livrables séparément : un échec de la transcription n'emporte pas la diffusion HLS.
- Les vidéos déjà en base peuvent être rattrapées par les deux commandes npm.
- Pas de bouton de diffusion sur télé qui échouerait en démonstration.
- L'échelle plafonnée à 720p évite le plantage du décodeur logiciel de l'émulateur sur le flux 1080p : la démonstration est plus fiable.

### Négatives
- L'espace disque est multiplié : chaque vidéo garde son MP4 et ajoute une échelle HLS jusqu'à trois qualités, plus un fichier WebVTT. Mesures, faites sur l'échelle à quatre qualités, avant son plafonnement à 720p : une vidéo 1080p de 3 min 53 (63 Mo) donnait une échelle de quatre qualités de 280 Mo ; une vidéo 360p de 15 min (60 Mo) donne une seule qualité, de 102 Mo. Estimation pour tout le catalogue : environ 1,1 Go de HLS pour environ 330 Mo de MP4. Avec l'échelle plafonnée, ces chiffres sont désormais inférieurs ; ils n'ont pas été mesurés à nouveau.
- Le temps de traitement à l'import et à l'envoi augmente : plusieurs encodages par vidéo, plus une transcription. Mesures (préréglage veryfast, machine de 16 cœurs), faites sur l'échelle à quatre qualités, avant son plafonnement à 720p : 1 min 51 d'encodage pour la vidéo 1080p de 3 min 53, 41 s pour la vidéo 360p de 15 min. Estimation pour tout le catalogue : environ 8 minutes d'encodage, transcription non comprise. Avec l'échelle plafonnée, ces temps sont désormais inférieurs ; ils n'ont pas été mesurés à nouveau.
- Aucune qualité 1080p n'est proposée, même pour une source 1080p lue sur un vrai appareil dont le décodeur matériel l'aurait supportée.
- Après un choix de qualité dans le menu, le lecteur repart à la même position pour recréer ses décodeurs, au prix d'une courte remise en mémoire tampon. Sans cela, le décodeur logiciel de l'émulateur affichait une image brouillée lors d'un changement de résolution en cours de lecture.
- Une vidéo envoyée depuis l'application est d'abord lue en MP4, sans choix de qualité ni sous-titres, jusqu'à la fin de la génération en tâche de fond.
- Le backend dépend d'un FFmpeg compilé avec le filtre whisper, et non plus seulement d'un FFmpeg quelconque (ADR-011) : il faut s'assurer que la version installée sur chaque poste et dans l'image de déploiement contient ce filtre. Sans lui, les vidéos sont publiées sans sous-titres.
- Le filtre whisper ne produit pas de WebVTT : une conversion du SRT en WebVTT est à écrire et à maintenir.
- Une transcription abandonnée au bout du temps maximal laisse la vidéo sans sous-titres.
- Un modèle Whisper est à télécharger sur chaque poste et au déploiement, dans un dossier non versionné.
- La qualité de la transcription est variable : erreurs de mots, détection de langue imparfaite, vidéos sans parole ou avec musique seule ; d'où la mention « générés automatiquement ».
- Le garde-fou contre les répliques inventées retire aussi les sous-titres d'un vrai dialogue très bref : une vidéo de plus de 20 secondes dont la parole compte moins de 10 mots perd ses sous-titres.
- La liste `SUBTITLES_DISABLED_FOR` des vidéos privées de sous-titres est tenue à la main : une nouvelle vidéo dans une langue mal reconnue, ou chantée, aurait de mauvais sous-titres tant qu'on ne l'y ajoute pas.
- La durée minimale d'une seconde retarde légèrement quelques répliques par rapport au temps donné par whisper (au plus environ 1,2 s mesuré sur cc-01).
- Les Shorts ont des sous-titres générés, mais ils n'y sont pas affichés : le lecteur vertical des Shorts n'a ni bouton ni menu de sous-titres. Seul le lecteur des vidéos classiques les propose.
- Au déploiement, le CDN Cloudflare R2 sert beaucoup plus de fichiers par vidéo (segments, manifestes, sous-titres) qu'un seul MP4.
- L'identifiant aléatoire du dossier HLS n'est que de l'obscurité : comme pour tout fichier servi en statique sous `/media` (MP4, sous-titres), la vérification de l'API ne protège que la remise du chemin, pas le fichier lui-même. Une adresse obtenue peut être rejouée par n'importe qui. Limite connue, faute d'URL signées ou de relais authentifié.
- Les vidéos référencées par une URL externe n'ont ni choix de qualité ni sous-titres.
- Le modèle VIDEO reçoit deux nouveaux champs (`hlsUrl`, `subtitleUrl`), avec une migration Prisma ; l'API, les modèles de l'application et son lecteur doivent les prendre en compte.
- Deux formats de lecture coexistent dans l'application (HLS et MP4), avec deux chemins à tester.

## Liens
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement des vidéos ; cet ADR fait évoluer son format de diffusion. Le MP4 progressif reste en repli, HLS multi-qualités s'y ajoute, et le serveur continue de servir des fichiers statiques sans transcodage à la lecture.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : FFmpeg en dépendance système et chemins relatifs ; FFmpeg gagne deux usages (échelle HLS, transcription), il doit désormais contenir le filtre whisper (sans lui, publication sans sous-titres et erreur journalisée), et les nouveaux fichiers suivent la règle des chemins relatifs sous `/media` (`/media/hls`, `/media/subtitles`).
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication depuis l'application mobile ; l'envoi validé par ffprobe publie la vidéo tout de suite en MP4, puis déclenche en tâche de fond la génération HLS et celle des sous-titres, sans allonger la requête. Le nom des MP4 envoyés, généré par le serveur, relève de la même limite d'obscurité que le dossier HLS.
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; l'accès aux vidéos exclusives s'applique de la même manière au HLS et au MP4.
- [ADR-016](ADR-016-reglages-publication-video.md) : réglages de publication ; la visibilité s'applique de la même manière au HLS et au MP4.
- [ADR-001](ADR-001-android-natif.md) : application native Android ; la lecture HLS, le sélecteur de pistes et les sous-titres passent par Media3 ExoPlayer, sans composant web.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est lui qui lance FFmpeg à l'import et à l'envoi, et qui expose les nouveaux champs.
