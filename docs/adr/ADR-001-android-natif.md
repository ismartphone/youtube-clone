# ADR-001 — Application mobile native Android uniquement (Kotlin + Jetpack Compose)

## Statut
Accepté

## Contexte
Le sujet impose une application native et autorise « iOS ou/et Android ». Il faut choisir la ou les plateformes cibles et la stack mobile.

Contraintes :
- l'application doit être native (exigence du sujet) ;
- le lead développe sous Windows et n'a pas de Mac ;
- Xcode ne tourne que sur macOS.

## Décision
Développer uniquement l'application Android, en Kotlin avec Jetpack Compose. Aucune version iOS n'est réalisée.

Jetpack Compose est choisi plutôt que les vues XML : c'est une interface déclarative qui demande moins de code, l'écran suit les données, @Preview donne un aperçu direct, et c'est la recommandation actuelle de Google.

## Alternatives
- iOS seul (Swift + SwiftUI) : avantage, natif et autorisé par le sujet. Inconvénient, impossible sans Mac puisque Xcode ne tourne que sur macOS.
- Les deux plateformes (Android et iOS) : avantage, couvre les deux plateformes autorisées par le sujet. Inconvénient, la partie iOS est impossible sans Mac ni Xcode.
- Multiplateforme (type Flutter ou React Native) : avantages, une seule base de code pour iOS et Android, développement plus rapide, une seule équipe. Inconvénient, non natif. Option rejetée uniquement parce que le sujet impose du natif.
- Vues XML (Android classique) : avantages, technologie plus ancienne donc très documentée, avec beaucoup de réponses en ligne, et un éditeur visuel intégré à Android Studio. Inconvénients, plus verbeuses, et l'interface doit être synchronisée à la main avec les données. Option rejetée malgré cela au profit de Jetpack Compose.

## Conséquences
### Positives
- Respect de l'exigence du natif imposée par le sujet.
- Développement possible entièrement sous Windows, sans Mac.
- L'architecture reste transposable vers iOS, SwiftUI fonctionnant comme Compose.
- Toute la stack mobile est Android (Kotlin + Jetpack Compose).
- Moins de code d'interface grâce à Compose.
- Aperçu direct des écrans via @Preview.

### Négatives
- Pas de livrable iOS, à annoncer en début d'oral.

## Liens
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement des vidéos sur notre propre backend ; le lecteur natif ExoPlayer découle de ce choix.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; ce choix fait coexister Kotlin et TypeScript dans le projet.
- [ADR-004](ADR-004-temps-reel-websocket.md) : temps réel par WebSocket ; l'application gère les reconnexions et l'authentification de la connexion.
- [ADR-006](ADR-006-analytics-posthog.md) : mesures d'audience avec PostHog ; la bibliothèque Android de PostHog est intégrée dans l'application.
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; le paywall et la feuille de paiement s'affichent dans l'application.
- [ADR-008](ADR-008-authentification-jwt.md) : authentification par JWT ; l'application stocke les tokens chiffrés et les rafraîchit par l'intercepteur OkHttp.
- [ADR-010](ADR-010-websocket-salles-compteurs.md) : connexion WebSocket ; l'application l'ouvre avec le client WebSocket d'OkHttp et gère les reconnexions.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : chemins relatifs pour les fichiers hébergés ; l'application les complète avec son adresse d'API.
- [ADR-012](ADR-012-paiement-simule-backend.md) : achat Premium simulé ; l'application affiche la feuille de paiement simulée.
- [ADR-013](ADR-013-mesures-donnees-personnelles.md) : mesures d'audience et données personnelles ; l'interrupteur « Partager des statistiques d'usage » se trouve dans l'application.
- [ADR-014](ADR-014-paiement-stripe-mode-test.md) : paiement par Stripe en mode test ; l'application intègre le SDK Stripe et affiche la PaymentSheet.
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication depuis l'application mobile ; la barre d'onglets, la feuille de création et les écrans de publication se trouvent dans l'application.
