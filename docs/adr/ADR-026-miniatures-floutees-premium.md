# ADR-026 — Miniatures floutées des vidéos Premium pour les comptes sans abonnement

## Statut
Accepté

## Contexte
Les vidéos exclusives sont marquées par `VIDEO.isPremium`, depuis le back-office (ADR-007). Leur miniature s'affiche aujourd'hui nette pour tout le monde. L'accès à la lecture est vérifié par le serveur : un compte sans Premium arrive sur le paywall (ADR-007, ADR-014).

Le 4 novembre 2026, le lead développeur demande de garder la miniature de ces vidéos, mais floutée pour les comptes sans abonnement. Un cadenas et le badge Premium s'affichent par-dessus, pour qu'on comprenne que le contenu existe et qu'il est réservé.

Contraintes :
- minSdk 26 ;
- le flou de Compose (`Modifier.blur`) n'agit qu'à partir d'Android 12 (API 31) ;
- l'émulateur de démonstration est en Android 17, donc avec le vrai flou ;
- le statut Premium de l'utilisateur est connu de l'application, qui l'utilise déjà (lecture en arrière-plan, publicités, ADR-021). Un visiteur n'est pas Premium.

État du code au 4 novembre 2026 :
- Les suggestions, la recherche, l'historique (Vous et sa liste complète), les vidéos d'une playlist et les vidéos longues de la page de chaîne passent déjà par `VideoThumbnail`, via `VideoCard` ou `SuggestedVideoCard`.
- Seuls deux endroits affichaient l'image directement : les tuiles de Shorts de la page de chaîne (`ShortTile`) et la couverture d'une playlist (sa première vidéo).
- Tous passent maintenant par le composant commun `VideoThumbnailImage` (`presentation/common/PremiumThumbnail.kt`), qui applique le flou ou l'assombrissement, avec le cadenas.
- Le statut Premium du compte actif est fourni une seule fois à la racine (CompositionLocal `LocalIsPremiumViewer`, dans `AppRoot`).
- Non concernés : l'écran de lecture, les Shorts en plein écran et la publicité. Ce ne sont pas des listes, et le paywall s'y applique.

## Décision
Option A : flou appliqué par l'application, sans rien changer côté serveur.

- Pour un compte sans Premium, visiteur compris, la miniature d'une vidéo Premium est floutée.
- Par-dessus le flou : un cadenas au centre et le badge Premium.
- Un compte Premium voit la miniature nette.
- Sur Android 12 et plus : `Modifier.blur`. En dessous : l'image est seulement assombrie, en repli.
- Un seul composant de miniature, réutilisé partout où une miniature apparaît :
  - fil d'accueil ;
  - recherche ;
  - page de chaîne ;
  - suggestions ;
  - playlists ;
  - historique ;
  - carte d'une vidéo partagée dans une conversation (étape 2 de l'ADR-025, construite : elle utilise `VideoThumbnailImage`).
- Ce composant est `VideoThumbnailImage` (`presentation/common/PremiumThumbnail.kt`). Il lit le statut Premium du compte actif dans `LocalIsPremiumViewer`, fourni à la racine.
- Rien côté serveur, pas de migration.

### Limites assumées
1. **L'adresse de la miniature nette est envoyée aux comptes gratuits.** Un client modifié peut l'afficher sans flou. C'est acceptable : une miniature n'est pas un contenu protégé, et l'accès à la vidéo reste vérifié côté serveur (ADR-007).
2. **Sous Android 12, le rendu est dégradé.** L'image est seulement assombrie et reste lisible ; l'effet « réservé » repose alors sur le cadenas et le badge. L'émulateur de démonstration, en Android 17, a le vrai flou.

### Décisions du lead développeur (4 novembre 2026)
1. Garder la miniature des vidéos Premium, floutée pour les comptes sans abonnement, nette pour un compte Premium ; cadenas au centre et badge Premium par-dessus le flou.
2. Partout où la miniature apparaît : fil d'accueil, recherche, page de chaîne, suggestions, playlists, historique, et carte d'une vidéo partagée dans une conversation (ADR-025, étape 2, quand elle sera construite).
3. Option A retenue, pour le délai : flou Compose sur Android 12 et plus, assombrissement en repli en dessous ; tout côté application, un seul composant de miniature, rien côté serveur, pas de migration.
4. Limites écrites et acceptées : adresse de la miniature nette envoyée aux comptes gratuits ; rendu dégradé (assombri, lisible) sous Android 12.

## Alternatives
- **Option B, miniature floutée générée par FFmpeg côté serveur** (ADR-011) :
  - Avantages : rendu identique sur toutes les versions d'Android, et aucune image nette envoyée aux comptes gratuits.
  - Inconvénients : un nouveau champ sur VIDEO, une génération à chaque publication et à chaque marquage Premium, un rattrapage des vidéos existantes, et une réponse de l'API qui dépend du compte.
  - Écartée pour le délai.
- **Masquer complètement les vidéos Premium aux comptes gratuits :** avantage, rien à flouter. Inconvénient, le contenu réservé devient invisible et ne donne plus envie de l'offre ; contraire à la demande.
- **Miniature nette avec seulement le badge** (comme aujourd'hui) : avantage, aucun travail. Inconvénient, on ne distingue pas assez le contenu réservé.

## Conséquences
### Positives
- Le contenu Premium reste visible et clairement réservé, ce qui met en avant l'offre.
- Aucun changement côté serveur, aucune migration, aucune génération d'image.
- Un seul composant de miniature : le même rendu partout, et un seul endroit à modifier.

### Négatives
- La miniature nette reste accessible à un client modifié (limite 1).
- Sous Android 12, l'image est seulement assombrie (limite 2).
- Toute nouvelle miniature de vidéo dans une liste doit passer par `VideoThumbnailImage` ; une image affichée directement resterait nette.
- Le statut Premium fourni à la racine (`LocalIsPremiumViewer`) doit suivre l'achat, l'expiration et le changement de compte (ADR-022).
- La carte de partage de l'ADR-025 devra appliquer le même composant à sa construction.

## Liens
- [ADR-007](ADR-007-offre-premium.md) : vidéos exclusives marquées par `isPremium`, accès vérifié par le serveur ; le flou n'est qu'un affichage.
- [ADR-014](ADR-014-paiement-stripe-mode-test.md) : paiement Premium ; un achat rend les miniatures nettes.
- [ADR-025](ADR-025-messagerie-utilisateurs.md) : carte de vidéo partagée (étape 2), qui recevra le flou.
- [ADR-022](ADR-022-plusieurs-comptes-appareil.md) : le rendu suit le statut Premium du compte actif.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : FFmpeg, qu'aurait utilisé l'option B écartée.
- [ADR-001](ADR-001-android-natif.md) : Jetpack Compose, minSdk 26.

Amendé le 5 novembre : la carte de vidéo partagée de la messagerie (ADR-025, étape 2) est construite et utilise `VideoThumbnailImage` ; vérifié dans `ConversationScreen.kt`.
