# ADR-007 — Offre Premium avec paiement simulé

## Statut
Accepté, remplacé en partie par l'ADR-012 (paiement)

## Contexte
Il faut proposer une offre payante dans l'application et choisir comment la payer.

Contraintes :
- le sujet impose un paywall dans l'application mobile ;
- c'est un projet d'école, sans compte marchand ni possibilité d'encaisser réellement.

## Décision
Proposer une offre payante « Premium » dans l'application. Le paiement est simulé, sans transaction réelle, selon le parcours décrit dans l'ADR-012.

### Contenu de l'offre
- Abonnement mensuel, au prix fictif de 4,99 €.
- Sans publicité.
- Accès aux vidéos exclusives.
- Lecture en arrière-plan.

### Publicité
Les comptes gratuits voient une publicité simulée avant la lecture : un simple écran de quelques secondes, sans régie publicitaire réelle. Les comptes Premium ne la voient pas.

### Activation du Premium
Le serveur vérifie le jeton d'achat avant d'activer le Premium, selon le parcours décrit dans l'ADR-012. L'application ne décide jamais seule.

Le statut Premium est stocké côté serveur, dans l'entité PREMIUM_SUBSCRIPTION. Ce nom la distingue de FOLLOW, l'abonnement gratuit à une chaîne.

### Vidéos exclusives
Les vidéos exclusives sont marquées depuis le back-office, via le champ isPremium de l'entité VIDEO.

L'accès aux vidéos exclusives est vérifié par l'API, jamais seulement dans l'application.

## Alternatives
- Vrai paiement via Google Play Billing en production : inconvénients, il faut un compte développeur payant et une validation de Google, impossible dans le délai.
- Paiement par Stripe : inconvénient, interdit par les règles de Google pour du contenu numérique dans une application Android.
- Google Play Billing en mode test : écarté, voir l'ADR-012.

## Conséquences
### Positives
- Parcours d'achat simulé de bout en bout (voir l'ADR-012).
- Le paywall peut être démontré et les conversions mesurées.
- Aucun compte marchand nécessaire.

### Négatives
- Aucune transaction réelle, à annoncer à l'oral.

## Liens
- [ADR-006](ADR-006-analytics-posthog.md) : mesures d'audience et test A/B avec PostHog ; il mesure le passage en Premium et compare deux variantes de l'écran de l'offre payante sur le taux de conversion.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est son API qui vérifie le justificatif d'achat, stocke le statut Premium dans l'entité PREMIUM_SUBSCRIPTION et vérifie l'accès aux vidéos exclusives.
- [ADR-005](ADR-005-ajout-videos-backoffice.md) : ajout des vidéos uniquement depuis le back-office ; c'est aussi depuis le back-office que les vidéos exclusives sont marquées.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est dans cette application que s'affichent le paywall et la feuille de paiement simulée.
- [ADR-012](ADR-012-paiement-simule-backend.md) : achat Premium simulé par notre propre backend ; il remplace la partie paiement de cet ADR (mode test de Google Play Billing).
