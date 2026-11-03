# ADR-014 — Paiement Premium par Stripe en mode test

## Statut
Accepté

## Contexte
Le sujet du projet demande l'utilisation d'une bibliothèque de paiement.

L'ADR-012 a remplacé Google Play Billing par un achat simulé par notre propre backend. Cet achat simulé n'utilise aucune bibliothèque de paiement.

Contraintes :
- Google Play Billing est impossible : l'application n'est pas publiée sur la Play Console ;
- Stripe en mode test est gratuit et immédiat ;
- la démonstration doit rester possible même sans réseau ;
- restent valables de l'ADR-007 : le contenu de l'offre, le statut stocké côté serveur dans PREMIUM_SUBSCRIPTION, l'accès aux vidéos exclusives vérifié par l'API, le serveur seul juge de l'activation.

## Décision
Payer l'offre Premium par Stripe en mode test.

Un paiement donne un mois de Premium, sans renouvellement automatique.

Cet ADR remplace l'ADR-012.

### Application Android
- SDK Android officiel com.stripe:stripe-android et sa PaymentSheet.
- L'application n'embarque pas la clé publiable en dur : elle la lit à l'ouverture du paywall, par GET /me/premium/config.
- Selon le mode renvoyé par GET /me/premium/config, l'application ouvre la PaymentSheet Stripe (mode « stripe ») ou la feuille de paiement simulée de l'ADR-012 (mode « simulated »). C'est ce qui garantit le repli en démonstration.

### Backend
Un adaptateur Stripe se place derrière le port de paiement existant du module premium (architecture hexagonale, ADR-003).

L'adaptateur simulé de l'ADR-012 est conservé. Il sert de repli quand STRIPE_SECRET_KEY est absente, pour que la démonstration reste possible même sans réseau.

### Routes
- GET /me/premium/config : renvoie le mode actif : « stripe », avec la clé publiable, ou « simulated », quand STRIPE_SECRET_KEY est absente.
- POST /me/premium/checkout : crée un PaymentIntent de 4,99 € en EUR, avec l'identifiant de l'utilisateur dans les métadonnées, et renvoie le client_secret.
- POST /me/premium/subscribe : reçoit l'identifiant du PaymentIntent et le récupère auprès de Stripe. L'abonnement n'est accordé que si :
  - le statut est « succeeded » ;
  - le montant et la devise correspondent ;
  - les métadonnées désignent bien l'utilisateur qui appelle ;
  - ce PaymentIntent n'a pas déjà servi.

Un même PaymentIntent ne peut servir qu'une fois. Mise en œuvre : l'identifiant du PaymentIntent est enregistré dans la colonne purchaseToken de PREMIUM_SUBSCRIPTION, déjà unique. En mode « simulated », purchaseToken contient le jeton simulé, comme dans l'ADR-012.

Le montant est fixé par le serveur, jamais reçu du client.

### Clés
- STRIPE_SECRET_KEY et STRIPE_PUBLISHABLE_KEY dans backend/.env, non versionné.
- La clé secrète ne quitte jamais le backend.

### Ce qui ne change pas
Restent valables, comme décidé par l'ADR-007 :
- le contenu de l'offre : 4,99 € pour un mois, sans renouvellement automatique, sans publicité, vidéos exclusives, lecture en arrière-plan ;
- le statut Premium stocké côté serveur dans PREMIUM_SUBSCRIPTION, à ne pas confondre avec FOLLOW ;
- l'accès aux vidéos exclusives vérifié par l'API ;
- le serveur seul juge de l'activation.

Restent valables, comme décidé par l'ADR-012 :
- GET /me/premium ;
- DELETE /me/premium.

## Alternatives
- Google Play Billing : écarté, impossible sans publication sur la Play Console.
- Garder l'achat simulé de l'ADR-012 seul : il ne répond pas à la demande du sujet d'utiliser une bibliothèque de paiement. Il reste néanmoins en repli.
- Abonnement récurrent Stripe (Stripe Billing) : écarté pour des raisons de délai.

## Conséquences
### Positives
- Le projet utilise une bibliothèque de paiement, comme le demande le sujet.
- Le serveur reste seul juge de l'activation : il vérifie le PaymentIntent auprès de Stripe, le montant est fixé par lui, et un PaymentIntent ne sert qu'une fois.
- La clé secrète reste sur le backend ; la clé publiable n'est pas embarquée en dur dans l'application.
- La démonstration reste possible sans réseau grâce au repli sur l'adaptateur simulé de l'ADR-012.

### Négatives
- Réserve : pour un contenu numérique distribué sur le Play Store, Google impose sa propre facturation. Stripe n'est acceptable ici que parce que l'application n'est pas publiée.
- Deux adaptateurs de paiement à maintenir derrière le même port : Stripe et le repli simulé.

## Liens
- [ADR-012](ADR-012-paiement-simule-backend.md) : achat Premium simulé par notre propre backend ; cet ADR le remplace. Son adaptateur simulé reste en repli, et GET /me/premium et DELETE /me/premium restent valables.
- [ADR-007](ADR-007-offre-premium.md) : offre Premium ; son contenu, le stockage du statut dans PREMIUM_SUBSCRIPTION, la vérification par l'API de l'accès aux vidéos exclusives et le serveur seul juge de l'activation restent valables. Cet ADR rend caduc le rejet de Stripe figurant dans ses Alternatives.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS, architecture hexagonale ; l'adaptateur Stripe se place derrière le port de paiement du module premium.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est elle qui intègre le SDK Stripe et affiche la PaymentSheet.
