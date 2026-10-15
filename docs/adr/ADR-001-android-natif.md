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
- Aucun ADR lié pour le moment.
