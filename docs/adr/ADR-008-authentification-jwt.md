# ADR-008 — Authentification par JWT avec access token court et refresh token

## Statut
Accepté, remplacé en partie par l'ADR-022 (plusieurs comptes)

## Contexte
Le sujet impose une authentification. Il faut choisir comment les utilisateurs se connectent à l'API et comment cette connexion est conservée.

Contraintes :
- le sujet impose une authentification ;
- l'API est consommée par l'application Android, et plus tard par le back-office.

## Décision
Authentifier les utilisateurs par JWT, avec deux jetons :
- un access token court, valable 15 minutes ;
- un refresh token, valable 7 jours.

Un token court limite les dégâts en cas de vol. Le refresh évite de redemander le mot de passe. Une API sans état pourra servir d'autres clients.

### Refresh token
Le refresh token est à usage unique : il est consommé à chaque échange. Il est stocké dans Redis.

### Rôles
L'access token porte un champ role, parmi VISITOR, USER, CREATOR et ADMIN.

### Côté Android
L'access token est rafraîchi automatiquement par un intercepteur OkHttp.

Les tokens sont stockés chiffrés sur le téléphone : chiffrement AES-GCM avec une clé de l'Android Keystore, et stockage dans DataStore.

### Mots de passe
Les mots de passe sont hachés avec argon2.

### Secrets
Deux secrets signent les tokens : JWT_ACCESS_SECRET et JWT_REFRESH_SECRET. Ils sont gérés hors du dépôt.

## Alternatives
- Sessions avec cookies : avantages, le navigateur gère automatiquement le stockage et l'envoi du cookie, et un cookie HttpOnly n'est pas lisible en JavaScript. Inconvénient, mal adaptées à une application mobile native et à une API sans état. Option rejetée parce qu'elle est mal adaptée à un client mobile natif.
- Access token long sans refresh : avantage, plus simple. Inconvénient, un vol de token donne un accès durable.

## Conséquences
### Positives
- Un token court limite les dégâts en cas de vol.
- L'utilisateur n'a pas à ressaisir son mot de passe grâce au refresh.
- API sans état, qui pourra servir d'autres clients, dont le back-office.

### Négatives
- Pas de route de déconnexion côté serveur pour l'instant : un refresh token reste valable jusqu'à son expiration.
- Deux secrets à gérer hors du dépôt (JWT_ACCESS_SECRET et JWT_REFRESH_SECRET).

## Liens
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est ce framework qui fournit l'authentification et dont l'API émet et vérifie les tokens.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est dans cette application que les tokens sont stockés chiffrés et rafraîchis par l'intercepteur OkHttp.
- [ADR-004](ADR-004-temps-reel-websocket.md) : temps réel par WebSocket ; il laisse à traiter l'authentification de la connexion WebSocket, qui est traitée par l'ADR-010.
- [ADR-009](ADR-009-stockage-tokens-backoffice.md) : stockage des tokens du back-office en localStorage ; il précise où le back-office conserve les tokens de cet ADR.
- [ADR-010](ADR-010-websocket-salles-compteurs.md) : authentification, salles par vidéo et diffusion des compteurs sur la connexion WebSocket ; l'access token de cet ADR y est transmis dans le handshake pour authentifier la connexion WebSocket.
