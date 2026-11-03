# ADR-010 — Authentification, salles par vidéo et diffusion des compteurs sur la connexion WebSocket

## Statut
Accepté

## Contexte
ADR-004 a choisi une passerelle WebSocket NestJS pour diffuser en temps réel les commentaires et les compteurs (vues, « j'aime »). Il laisse à traiter l'authentification de la connexion WebSocket et les reconnexions côté application, et ne précise ni comment un client reçoit les événements d'une seule vidéo, ni comment le compteur de « j'aime » passe par Redis.

Contraintes :
- l'authentification repose sur les JWT définis par ADR-008 (access token valable 15 minutes, renouvelé via /auth/refresh) ;
- l'application Android utilise le client WebSocket d'OkHttp ;
- les compteurs transitent par Redis, pour ne pas écrire en base à chaque événement (ADR-004).

## Décision
Préciser le fonctionnement de la connexion WebSocket définie par ADR-004 :
- le JWT est transmis dans le handshake de la connexion WebSocket ;
- un visiteur peut se connecter en lecture seule ;
- chaque client rejoint une salle par vidéo ;
- les commentaires sont envoyés par l'API REST, et la WebSocket ne sert qu'à recevoir ;
- à la publication d'un commentaire, le serveur le diffuse à la salle (événement `comment:new`) ;
- les compteurs de vues et de « j'aime » sont diffusés en direct dans la salle (`views:update`, `likes:update`), via Redis ;
- le compteur de « j'aime » est stocké dans Redis et écrit en base périodiquement.

### Protocole
La passerelle utilise le WebSocket standard, via l'adaptateur `ws` de NestJS (`@nestjs/platform-ws`), et non Socket.IO : l'application Android utilise le client WebSocket d'OkHttp, qui parle le WebSocket standard. Le point d'entrée est `/realtime`, sur le même port que l'API REST.

### Handshake
Le client envoie l'en-tête `Authorization: Bearer <access token>`. Sans en-tête, il est connecté comme visiteur. Si le token est présent mais invalide ou expiré, le serveur ferme la connexion avec le code 4401 ; l'application renouvelle alors le token via /auth/refresh, avec le même mécanisme que pour l'API REST, puis se reconnecte.

### Salles
Le client envoie `{ "event": "join", "data": { "videoId": ... } }` pour rejoindre la salle d'une vidéo, et `leave` pour la quitter. Le serveur envoie `{ "event": "comment:new" | "views:update" | "likes:update", "data": ... }`.

Les salles sont gardées en mémoire dans le serveur. Ce fonctionnement n'est valable que pour une seule instance du backend.

### Envoi des commentaires et des « j'aime »
La connexion WebSocket ne sert qu'à recevoir. Les commentaires sont publiés par POST /videos/:id/comments, et les « j'aime » par POST et DELETE /videos/:id/like, sur l'API REST. Le serveur les diffuse ensuite à la salle de la vidéo.

Ce choix est volontaire : la validation, l'authentification et l'écriture en base restent sur le chemin HTTP déjà testé, et la connexion temps réel ne gère que la diffusion.

Il s'écarte des conséquences de ADR-004, qui indiquent que le client envoie ses commentaires et reçoit ceux des autres sur la même connexion. ADR-004 reste accepté tel quel ; cet ADR le complète.

### Compteur de « j'aime »
Chaque ligne LIKE (qui a aimé quoi) est écrite immédiatement en base ; seul le compteur passe par Redis. Une colonne `likeCount` est ajoutée à la table videos, alimentée par un tampon Redis reporté en base par lot toutes les 30 secondes, comme le compteur de vues. Les compteurs affichés additionnent la valeur en base et les variations en attente dans Redis.

### Côté Android
La connexion est ouverte tant que l'écran de lecture est visible, et fermée quand on le quitte. La reconnexion est automatique, avec un délai croissant : 1 seconde, puis doublé à chaque tentative, au plus 30 secondes. Un ping toutes les 20 secondes détecte les connexions mortes. Après une reconnexion, l'écran relit les compteurs et la première page des commentaires, car les événements diffusés pendant la coupure sont perdus.

## Alternatives
- Socket.IO : inconvénient, il ne parle pas le WebSocket standard attendu par le client WebSocket d'OkHttp utilisé par l'application Android.
- Server-Sent Events : avantage, plus simple. Inconvénients, sens unique et pas de salles natives.
- Interrogation périodique de l'API : avantage, très simple. Inconvénients, beaucoup de requêtes inutiles et un affichage en retard.

## Conséquences
### Positives
- L'authentification de la connexion WebSocket, laissée à traiter par ADR-004, réutilise les JWT et le renouvellement via /auth/refresh de ADR-008.
- Un visiteur reçoit les commentaires et les compteurs en direct sans être connecté.
- Chaque client ne reçoit que les événements de la vidéo qu'il regarde.
- Les reconnexions côté application, laissées à traiter par ADR-004, sont gérées : délai croissant, ping et relecture de l'état après la coupure.
- Pas d'écriture en base à chaque « j'aime » pour le compteur, comme pour les vues.
- La validation, l'authentification et l'écriture en base des commentaires restent sur le chemin HTTP déjà testé ; la connexion temps réel ne gère que la diffusion.

### Négatives
- Les salles sont gardées en mémoire : avec plusieurs instances du backend, il faudrait relayer les événements entre elles (par exemple par Redis pub/sub), ce qui n'est pas fait.
- Les événements diffusés pendant une coupure sont perdus : l'application doit relire les compteurs et la première page des commentaires après chaque reconnexion.
- La colonne `likeCount` en base n'est à jour que toutes les 30 secondes ; les compteurs affichés doivent additionner la valeur en base et les variations en attente dans Redis.
- N'importe quel client, visiteur compris, peut rejoindre la salle d'une vidéo exclusive. C'est une limite assumée : il n'y a pas de fuite, puisque les événements diffusés ne contiennent jamais l'URL de la vidéo.
- Écart volontaire avec ADR-004 : ses conséquences indiquent que le client envoie ses commentaires et reçoit ceux des autres sur la même connexion ; ici, l'envoi passe par l'API REST et la connexion WebSocket ne sert qu'à recevoir. Cet écart affaiblit l'argument de ADR-004 contre les Server-Sent Events, écartés parce qu'ils ne permettent pas d'envoyer un commentaire.

## Liens
- [ADR-004](ADR-004-temps-reel-websocket.md) : temps réel par une passerelle WebSocket NestJS ; cet ADR le complète (authentification, salles, compteurs, reconnexions) et s'en écarte volontairement sur l'envoi des commentaires, qui passe par l'API REST ; ADR-004 reste accepté tel quel.
- [ADR-008](ADR-008-authentification-jwt.md) : authentification par JWT avec access token court et refresh token ; c'est l'access token de cet ADR qui est transmis dans le handshake, et son mécanisme de renouvellement qui est utilisé après une fermeture 4401.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est ce framework qui fournit la passerelle WebSocket et l'adaptateur `ws`.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est cette application qui ouvre la connexion avec le client WebSocket d'OkHttp et gère les reconnexions.
