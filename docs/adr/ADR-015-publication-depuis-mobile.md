# ADR-015 — Publication de vidéos, de Shorts et de posts depuis l'application mobile

## Statut
Accepté

## Contexte
L'ADR-005 réserve la publication des vidéos au back-office : l'application mobile est en lecture seule sur les vidéos. Un créateur ne peut donc rien publier depuis son téléphone.

Il s'agit de permettre à un créateur de publier sans ordinateur, comme le fait YouTube, et de montrer en démonstration le parcours complet d'un créateur depuis l'application.

Contraintes :
- l'application reste 100 % native (ADR-001) ;
- le stockage des vidéos est un dossier local en développement, Cloudflare R2 avec un CDN au déploiement (ADR-002) ;
- la miniature est générée par le service FFmpeg existant (ADR-011), réutilisé tel quel ;
- l'envoi de fichiers par des utilisateurs ouvre une surface d'attaque nouvelle : le fichier, son nom et le type annoncé viennent du client ;
- le live reste hors périmètre ;
- la publication depuis le back-office doit continuer de fonctionner.

## Décision
Un créateur publie des vidéos, des Shorts et des posts depuis l'application mobile. Cet ADR remplace l'ADR-005.

### Barre d'onglets de l'application
- Onglets : Accueil, Shorts, +, Abonnements, Vous.
- L'onglet Bibliothèque est supprimé : son contenu est déjà dans Vous.
- Le + est au centre, mis en valeur.

### Feuille de création
- Le + ouvre une feuille qui monte du bas, avec quatre onglets en bas : Vidéo, Short, En direct, Post.
- En direct n'est pas grisé : il affiche l'écran de déblocage du direct, sur le modèle de YouTube. La condition pour débloquer le direct est d'avoir au moins 50 abonnés (FOLLOW, abonnement gratuit).
  - L'écran montre la progression du créateur avec son vrai nombre d'abonnés (par exemple « 3 abonnés sur 50 ») et une barre de progression.
  - Deux boutons, comme sur YouTube : « Créer des Shorts », qui bascule sur l'onglet Short de la feuille, et « En savoir plus », qui explique la règle.
  - Un utilisateur sans chaîne voit l'invitation à en créer une, comme sur les autres onglets ; un visiteur non connecté voit l'invitation à se connecter.
  - À partir de 50 abonnés, l'écran indique que le direct arrive bientôt.
  - Le seuil de 50 est une constante du domaine de l'application. Le nombre d'abonnés vient de la page de chaîne existante (GET /channels/{id}), chargée à l'ouverture de l'onglet. Aucune route backend n'est ajoutée.
  - Limite : le seuil est réel et calculé sur les abonnés, mais la diffusion en direct elle-même reste hors périmètre (CLAUDE.md) : un serveur de streaming est un projet à lui seul.
- Un utilisateur sans chaîne voit une invitation à en créer une.

### Vidéo et Short
- Le fichier est choisi avec le sélecteur de média Android (photo picker), qui n'exige aucune permission de stockage sur Android 13 et plus.
- Short : vidéo verticale de 60 secondes au plus.
- Vidéo : pas de contrainte d'orientation.
- Un fichier qui ne convient pas est refusé clairement.
- Écran de publication : aperçu, titre, description, catégorie, ainsi que la visibilité et l'activation des commentaires, présentées dans une liste repliée (ADR-016). Le format SHORT ou LONG vient de l'onglet choisi.
- Envoi multipart avec progression réelle, annulable, et message clair en cas d'échec.

### Backend
- Route d'envoi authentifiée, réservée au propriétaire d'une chaîne.
- Le fichier est écrit dans le stockage local, et la VIDEO créée est rattachée à la chaîne de son propriétaire.
- Taille maximale : 200 Mo par vidéo.
- Le fichier est validé par ffprobe, jamais par son extension ni par le type MIME annoncé par le client.
- Le nom du fichier est généré par le serveur, jamais repris du client.
- La miniature est générée par le service FFmpeg existant (ADR-011), réutilisé tel quel.
- La vidéo est publiée tout de suite en MP4. L'envoi déclenche ensuite, en tâche de fond, la génération de l'échelle HLS et celle des sous-titres, sans allonger la requête ; un échec de l'une ou de l'autre ne bloque pas la publication (ADR-017).
- Si ffprobe ou FFmpeg est absent du serveur, l'envoi depuis l'application est refusé avec une erreur HTTP 503 et un message clair. Le serveur démarre quand même, comme pour les miniatures (ADR-011).
- Une vidéo publiée en visibilité publique apparaît dans le fil et sur la page de la chaîne. Une vidéo non répertoriée ou privée n'apparaît dans aucune liste publique (ADR-016).

### Post
- Nouvelle entité POST : texte, image facultative, auteur (la chaîne), date. Elle est ajoutée par une migration Prisma.
- Les posts sont gérés par un module backend `posts`, séparé du module `channels`.
- L'image facultative est validée par ffprobe, jamais par son extension ni par le type MIME annoncé par le client. Elle est limitée à 5 Mo et stockée sous `/media/posts`, avec un nom de fichier généré par le serveur.
- Écran de rédaction : texte, bouton image, bouton Publier désactivé tant que le texte est vide.
- Les posts s'affichent dans un onglet Posts de la page de chaîne.
- Création réservée au propriétaire de la chaîne, lecture publique.

### Back-office
- La publication depuis le back-office continue de fonctionner.
- Un administrateur peut supprimer une vidéo ou un post.

## Alternatives
- Garder la publication réservée au back-office (ADR-005) : avantage, plus simple. Inconvénient, aucun parcours créateur dans l'application.
- Demander la permission READ_MEDIA_VIDEO et parcourir la galerie soi-même : écartée, le photo picker n'exige aucune permission.
- Valider le fichier par son extension ou son type MIME : écartée, ces informations viennent du client et sont falsifiables.
- Envoi direct vers Cloudflare R2 par URL présignée : écarté pour l'instant, le stockage est local en développement (ADR-002).

## Conséquences
### Positives
- Un créateur publie depuis son téléphone, sans ordinateur.
- Le parcours complet d'un créateur peut être montré en démonstration depuis l'application.
- Aucune permission de stockage à demander sur Android 13 et plus, grâce au photo picker.
- La surface d'attaque nouvelle est maîtrisée : validation par ffprobe, taille maximale, nom de fichier généré par le serveur.
- La génération des miniatures réutilise le service FFmpeg existant, sans nouveau traitement.
- La publication depuis le back-office est conservée.
- L'onglet En direct montre au créateur une règle réelle, sa progression vers 50 abonnés, et l'oriente vers les Shorts, sans nouvelle route backend.

### Négatives
- Surface d'attaque nouvelle : des utilisateurs envoient des fichiers au serveur.
- Les vidéos envoyées occupent l'espace disque du stockage local.
- Une nouvelle table POST, avec sa migration Prisma.
- Le catalogue n'est plus alimenté par le seul administrateur.
- Atteindre 50 abonnés ne débloque pas de diffusion en direct : l'écran annonce seulement que le direct arrive bientôt, la diffusion restant hors périmètre.
- La page de chaîne est chargée à chaque ouverture de l'onglet En direct pour connaître le nombre d'abonnés.

## Liens
- [ADR-005](ADR-005-ajout-videos-backoffice.md) : ajout des vidéos uniquement depuis le back-office ; cet ADR le remplace.
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement des vidéos sur notre propre backend ; c'est dans son stockage local que les fichiers envoyés depuis l'application sont écrits, et c'est lui qui fait écarter pour l'instant l'envoi direct vers Cloudflare R2.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : FFmpeg en dépendance système et chemins relatifs ; son service de miniatures est réutilisé tel quel, et ffprobe valide les vidéos envoyées et les images des posts ; sans ffprobe ni FFmpeg, l'envoi depuis l'application est refusé en 503.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est dans cette application que se trouvent la barre d'onglets, la feuille de création et les écrans de publication.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est ce backend qui expose la route d'envoi et la création des posts.
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; les champs de l'écran de publication mobile sont l'aperçu, le titre, la description, la catégorie, la visibilité et les commentaires (ADR-016), le marquage isPremium n'en fait pas partie.
- [ADR-016](ADR-016-reglages-publication-video.md) : réglages de publication d'une vidéo ; il ajoute la visibilité et l'activation des commentaires à l'écran de publication, et restreint l'apparition dans le fil et sur la page de la chaîne aux vidéos publiques.
- [ADR-017](ADR-017-hls-multi-qualites-sous-titres.md) : diffusion HLS multi-qualités et sous-titres automatiques ; l'envoi depuis l'application déclenche leur génération en tâche de fond, après la publication en MP4.
