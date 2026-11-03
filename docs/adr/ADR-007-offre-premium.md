# ADR-007 — Offre Premium

## Statut
Accepté, remplacé en partie par l'ADR-014 (paiement)

## Contexte
Il faut proposer une offre payante dans l'application et choisir comment la payer.

Contraintes :
- le sujet impose un paywall dans l'application mobile ;
- c'est un projet d'école, sans possibilité d'encaisser réellement.

## Décision
Proposer une offre payante « Premium » dans l'application. Le paiement suit le parcours décrit dans l'ADR-014.

### Contenu de l'offre
- 4,99 € pour un mois, sans renouvellement automatique ; aucun débit réel en mode test (voir l'ADR-014).
- Sans publicité.
- Accès aux vidéos exclusives.
- Lecture en arrière-plan.

### Publicité
Les comptes gratuits voient une publicité simulée avant la lecture : un simple écran de quelques secondes, sans régie publicitaire réelle. Les comptes Premium ne la voient pas.

### Activation du Premium
Le serveur vérifie le justificatif d'achat avant d'activer le Premium, selon le parcours décrit dans l'ADR-014. L'application ne décide jamais seule.

Le statut Premium est stocké côté serveur, dans l'entité PREMIUM_SUBSCRIPTION. Ce nom la distingue de FOLLOW, l'abonnement gratuit à une chaîne.

### Vidéos exclusives
Les vidéos exclusives sont marquées depuis le back-office, via le champ isPremium de l'entité VIDEO. Ce marquage reste réservé au back-office : l'écran de publication de l'application mobile ne le propose pas (ADR-015). Cet écran propose l'aperçu, le titre, la description, la catégorie, la visibilité et les commentaires (ADR-016).

Le marquage Premium est indépendant de la visibilité de la vidéo (ADR-016) : la visibilité dit qui voit l'entrée de la vidéo, Premium dit qui peut la lire. Il n'existe aucune règle croisée entre les deux.

L'accès aux vidéos exclusives est vérifié par l'API, jamais seulement dans l'application.

## Alternatives
- Vrai paiement via Google Play Billing en production : inconvénients, il faut un compte développeur payant et une validation de Google, impossible dans le délai.
- Paiement par Stripe : inconvénient, interdit par les règles de Google pour du contenu numérique dans une application Android. Ce rejet est revu par l'ADR-014, qui retient Stripe en mode test : Stripe n'est acceptable que parce que l'application n'est pas publiée sur le Play Store.
- Google Play Billing en mode test : écarté, voir l'ADR-012.

## Conséquences
### Positives
- Parcours d'achat de bout en bout (voir l'ADR-014).
- Le paywall peut être démontré et les conversions mesurées.

### Négatives
- Aucun débit réel d'argent, à annoncer à l'oral : en mode « stripe », un vrai paiement Stripe en mode test a lieu, sans débit réel (voir l'ADR-014).
- Un compte Stripe en mode test n'est nécessaire que pour le paiement Stripe ; sans clé, le repli simulé de l'ADR-014 prend le relais.

## Liens
- [ADR-006](ADR-006-analytics-posthog.md) : mesures d'audience et test A/B avec PostHog ; il mesure le passage en Premium et compare deux variantes de l'écran de l'offre payante sur le taux de conversion.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est son API qui vérifie le justificatif d'achat, stocke le statut Premium dans l'entité PREMIUM_SUBSCRIPTION et vérifie l'accès aux vidéos exclusives.
- [ADR-015](ADR-015-publication-depuis-mobile.md) : publication de vidéos, de Shorts et de posts depuis l'application mobile ; il remplace l'ADR-005, mais le marquage isPremium des vidéos exclusives reste réservé au back-office. Son écran de publication comprend l'aperçu, le titre, la description, la catégorie, la visibilité et les commentaires (ADR-016).
- [ADR-016](ADR-016-reglages-publication-video.md) : réglages de publication d'une vidéo (visibilité et commentaires) ; la visibilité est indépendante du marquage Premium, sans règle croisée, et ses règles sont vérifiées par l'API selon le même principe que l'accès aux vidéos exclusives.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est dans cette application que s'affichent le paywall et la feuille de paiement (voir l'ADR-014).
- [ADR-012](ADR-012-paiement-simule-backend.md) : achat Premium simulé par notre propre backend ; il a remplacé la partie paiement de cet ADR (mode test de Google Play Billing). Il est lui-même remplacé par l'ADR-014, son adaptateur simulé restant en repli.
- [ADR-014](ADR-014-paiement-stripe-mode-test.md) : paiement Premium par Stripe en mode test ; c'est lui qui décrit désormais le paiement de l'offre, et il revoit le rejet de Stripe figurant dans les Alternatives de cet ADR.
