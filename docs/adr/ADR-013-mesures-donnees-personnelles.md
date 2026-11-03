# ADR-013 — Mesures d'audience et données personnelles

## Statut
Accepté

## Contexte
L'ADR-006 dit dans sa section RGPD : « Les événements sont anonymes et ne contiennent aucune donnée personnelle. L'utilisateur peut refuser les mesures depuis l'écran d'accueil. »

Ces deux phrases ne correspondent plus au projet.

Le projet doit associer les événements PostHog à l'utilisateur connecté. Il faut pouvoir suivre un même utilisateur entre ses sessions et mesurer les conversions du paywall par compte.

Associer un événement à un identifiant de compte le rend pseudonyme, et non plus anonyme. Au sens du RGPD, c'est une donnée personnelle.

Par ailleurs, l'écran d'accueil du premier lancement a été refait en un seul écran : visuel, nom, phrase, boutons Commencer et Se connecter. Il ne contient pas de choix de refus des mesures.

## Décision
Cet ADR complète et précise la section RGPD de l'ADR-006, qu'il remplace. Le reste de l'ADR-006 reste valable.

### Identification auprès de PostHog
L'utilisateur connecté est identifié auprès de PostHog par son seul identifiant interne, l'UUID de son compte.

Jamais l'email, jamais le nom affiché, jamais le handle de chaîne.

Un visiteur, non connecté, reste anonyme.

### Refus des mesures
Un interrupteur « Partager des statistiques d'usage » se trouve dans l'onglet Vous, rubrique Paramètres. Il est activé par défaut.

Désactivé, plus aucun événement ne part vers PostHog.

Il remplace le choix de refus sur l'écran d'accueil prévu par l'ADR-006.

## Alternatives
- Événements totalement anonymes, sans association au compte : avantage, aucune donnée personnelle. Inconvénient, impossible de suivre un même utilisateur entre ses sessions ni de mesurer les conversions par compte.
- Identification par l'email : écartée, c'est une donnée personnelle directement identifiante envoyée à un service externe.
- Choix de refus sur l'écran d'accueil (prévu par l'ADR-006) : écarté, l'écran d'accueil reste un écran unique et simple. Le réglage dans Paramètres reste accessible à tout moment.

## Conséquences
### Positives
- Suivi d'un même utilisateur et mesure des conversions par compte.
- Aucune donnée directement identifiante (email, nom, handle) envoyée à PostHog.
- L'utilisateur peut couper les mesures à tout moment.

### Négatives
- Les événements des comptes connectés sont pseudonymes, donc des données personnelles au sens du RGPD.
- Le partage est activé par défaut : c'est un choix de désactivation et non un consentement préalable, à annoncer et justifier à l'oral.
- Un visiteur qui se connecte passe d'un identifiant anonyme à son identifiant de compte.

## Liens
- [ADR-006](ADR-006-analytics-posthog.md) : mesures d'audience et test A/B avec PostHog ; cet ADR remplace sa section RGPD. Le reste de l'ADR-006 reste valable.
- [ADR-008](ADR-008-authentification-jwt.md) : authentification par JWT ; c'est elle qui fournit l'identifiant du compte connecté envoyé à PostHog.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est dans cette application qu'est intégrée la bibliothèque PostHog et que se trouve l'interrupteur « Partager des statistiques d'usage ».
