# ADR-012 — Achat Premium simulé par notre propre backend

## Statut
Remplacé par l'ADR-014

## Contexte
ADR-007 prévoit que le paiement de l'offre Premium est simulé avec Google Play Billing en mode test. Ce mode test n'est pas utilisable dans le projet.

Le mode test de Play Billing suppose :
- l'application déposée sur la Play Console, au minimum en piste de test interne ;
- un compte développeur Google payant ;
- un produit d'abonnement créé dans la Play Console ;
- des comptes testeurs déclarés ;
- un appareil avec le Play Store connecté à un de ces comptes.

Les anciens produits de test réservés (android.test.purchased) ne sont plus disponibles dans les versions récentes de la bibliothèque.

Le projet n'a pas d'application publiée sur la Play Console. L'achat ne peut donc pas aboutir, et le serveur n'aurait aucun justificatif Google à vérifier.

Contraintes :
- la règle d'ADR-007 reste valable : le serveur vérifie le justificatif d'achat avant d'activer le Premium, l'application ne décide jamais seule ;
- le statut Premium est stocké côté serveur, dans l'entité PREMIUM_SUBSCRIPTION (ADR-007).

## Décision
Simuler l'achat Premium avec notre propre backend, qui joue le rôle du fournisseur de paiement.

Cet ADR remplace uniquement la partie « paiement simulé avec Google Play Billing en mode test » d'ADR-007. Le reste d'ADR-007 reste valable et inchangé.

### Parcours d'achat
1. POST /me/premium/checkout : le serveur crée un achat en attente pour l'utilisateur connecté et renvoie un jeton d'achat aléatoire, valable quelques minutes.
2. L'application affiche une feuille de paiement simulée, clairement marquée « Paiement simulé, aucun débit ». Elle rappelle l'offre et le prix de 4,99 €/mois, avec un bouton Confirmer.
3. POST /me/premium/subscribe avec ce jeton : le serveur vérifie que le jeton existe, appartient à l'utilisateur, n'a pas expiré et n'a pas déjà servi. Si c'est le cas, il crée un PREMIUM_SUBSCRIPTION actif pour un mois et enregistre le jeton dans purchaseToken (unique).

### Gestion de l'abonnement
- GET /me/premium renvoie l'état de l'abonnement.
- DELETE /me/premium l'annule.

### Ce qui ne change pas
Restent valables, comme décidé par ADR-007 :
- le contenu de l'offre : abonnement mensuel au prix fictif de 4,99 €, sans publicité, vidéos exclusives, lecture en arrière-plan ;
- la publicité simulée avant la lecture pour les comptes gratuits ;
- le statut Premium stocké côté serveur dans PREMIUM_SUBSCRIPTION, à ne pas confondre avec FOLLOW ;
- les vidéos exclusives marquées depuis le back-office via VIDEO.isPremium ;
- l'accès aux vidéos exclusives vérifié par l'API ;
- le serveur vérifie le justificatif d'achat avant d'activer le Premium, l'application ne décide jamais seule.

## Alternatives
- Google Play Billing en mode test (décision d'ADR-007) : avantage, parcours d'achat Google réel. Inconvénient, impossible sans application déposée sur la Play Console, compte développeur payant, produit et testeurs configurés.
- Vrai paiement Play Billing en production : déjà écarté par ADR-007 (compte développeur payant, validation Google, impossible dans le délai).
- Activation décidée par l'application seule, sans jeton vérifié par le serveur : avantage, plus simple. Inconvénients, contredit la règle d'ADR-007 selon laquelle l'application ne décide jamais seule, et le statut Premium pourrait être obtenu sans passer par le parcours d'achat.

## Conséquences
### Positives
- Parcours d'achat démontrable de bout en bout sur l'émulateur, sans compte Google ni Play Console.
- Le serveur reste seul juge de l'activation.
- Les événements « achat lancé » et « achat confirmé » restent mesurables avec PostHog (ADR-006).
- Aucun nouveau service ni dépendance.

### Négatives
- Aucun parcours Google réel ni transaction réelle, à annoncer à l'oral.
- Le jeton est émis par notre propre serveur : la vérification prouve que le parcours a été suivi, mais pas qu'un paiement a eu lieu.
- Passer un jour à Play Billing demandera de remplacer l'émission du jeton par la vérification du justificatif Google.

## Liens
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; cet ADR remplace uniquement sa partie paiement (mode test de Google Play Billing), ainsi que la phrase de ses Liens indiquant que Play Billing est intégré dans l'application. Le reste d'ADR-007 reste valable.
- [ADR-006](ADR-006-analytics-posthog.md) : mesures d'audience et test A/B avec PostHog ; il mesure les conversions et compare deux variantes de l'écran de l'offre payante.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est son API qui émet et vérifie le jeton d'achat.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est elle qui affiche la feuille de paiement simulée.
