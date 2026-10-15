# ADR-004 — Commentaires et compteurs en temps réel par une passerelle WebSocket NestJS

## Statut
Accepté

## Contexte
L'application doit afficher en temps réel les commentaires et les compteurs (vues, « j'aime »). Il faut choisir comment ces données arrivent jusqu'au client.

Contraintes :
- le sujet impose un serveur WebSocket ;
- le sujet impose du temps réel dans l'application.

## Décision
Diffuser les commentaires et les compteurs (vues, « j'aime ») en temps réel par une passerelle WebSocket NestJS.

La connexion reste ouverte dans les deux sens. Le serveur pousse les nouveaux commentaires sans qu'on les demande.

NestJS intègre les passerelles WebSocket nativement.

Les compteurs transitent par Redis, pour ne pas écrire en base à chaque événement.

## Alternatives
- Polling (redemander à l'API toutes les X secondes) : avantage, simple. Inconvénients, beaucoup de requêtes inutiles et un affichage en retard.
- Server-Sent Events : inconvénient, ils ne vont que dans un sens, du serveur vers le client, donc insuffisants pour envoyer un commentaire.

## Conséquences
### Positives
- Respect de l'exigence du sujet : un serveur WebSocket et du temps réel.
- Connexion ouverte dans les deux sens : le client envoie ses commentaires et reçoit ceux des autres sur la même connexion.
- Les nouveaux commentaires sont poussés par le serveur, sans requête du client.
- Pas de requêtes inutiles ni d'affichage en retard, contrairement au polling.
- Passerelle WebSocket fournie nativement par NestJS.
- Pas d'écriture en base à chaque événement, grâce à Redis.

### Négatives
- Il faut gérer les reconnexions côté application.
- Il faut gérer l'authentification de la connexion WebSocket.

## Liens
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; c'est ce framework qui fournit la passerelle WebSocket.
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est cette application qui doit gérer les reconnexions et l'authentification de la connexion.
