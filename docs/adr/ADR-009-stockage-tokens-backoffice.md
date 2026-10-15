# ADR-009 — Stockage des tokens du back-office en localStorage

## Statut
Accepté

## Contexte
Le back-office s'authentifie auprès de l'API avec les JWT définis par ADR-008 : un access token valable 15 minutes et un refresh token valable 7 jours. Il faut choisir où le navigateur conserve ces deux tokens.

Contraintes :
- le back-office est un outil interne, accessible aux seuls administrateurs ;
- il est placé derrière une connexion ;
- il n'affiche pas de données personnelles sensibles.

## Décision
Stocker l'access token et le refresh token du back-office dans le localStorage du navigateur.

Ce choix est accepté parce que le back-office est un outil interne, réservé aux administrateurs, derrière une connexion, et qu'il n'affiche pas de données personnelles sensibles.

### Appels à l'API
Le back-office (Next.js) appelle l'API via un relais `/api` configuré dans Next.js. Les appels partent donc de la même origine, sans CORS à configurer côté backend.

### Connexion
À la connexion, le back-office vérifie le rôle avec GET /auth/me et n'enregistre les tokens que pour un compte ADMIN. L'API vérifie de son côté le rôle ADMIN sur chaque route /admin, avec l'AdminGuard.

### Renouvellement
Sur une réponse 401, le back-office renouvelle les tokens via /auth/refresh, puis rejoue la requête. Le renouvellement n'a lieu qu'une seule fois, même si plusieurs requêtes échouent en même temps. Si le renouvellement est refusé, la session est effacée et l'administrateur revient à la page de connexion.

### Déconnexion
La déconnexion efface les tokens du navigateur. Il n'existe pas de route de déconnexion côté serveur, comme déjà noté dans ADR-008.

## Alternatives
- Cookie HttpOnly : avantage, plus sûr contre l'injection de script, puisque le token n'est pas lisible en JavaScript. Inconvénients, il faudrait gérer le CSRF et changer la façon dont l'API émet les tokens.

## Conséquences
### Positives
- Pas de protection CSRF à mettre en place.
- La façon dont l'API émet les tokens reste inchangée.

### Négatives
- Moins sûr qu'un cookie HttpOnly contre l'injection de script : un script injecté dans le back-office pourrait lire les tokens.
- La déconnexion n'efface les tokens que dans le navigateur : faute de route de déconnexion côté serveur, un refresh token reste valable jusqu'à son expiration (7 jours).

## Liens
- [ADR-008](ADR-008-authentification-jwt.md) : authentification par JWT avec access token court et refresh token ; cet ADR précise où le back-office conserve ces tokens, alors que l'ADR-008 ne détaille que le stockage côté Android.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS et back-office en Next.js ; c'est l'API NestJS qui émet les tokens et vérifie le rôle ADMIN, et le back-office Next.js qui les stocke et les renvoie.
