# ADR-005 — Ajout des vidéos uniquement depuis le back-office

> **Remplacé par l'[ADR-015](ADR-015-publication-depuis-mobile.md)** (publication de vidéos, de Shorts et de posts depuis l'application mobile). Le texte ci-dessous est conservé à titre historique : l'application mobile n'est plus en lecture seule sur les vidéos, et la publication depuis le back-office continue de fonctionner.

## Statut
Remplacé par l'ADR-015

## Contexte
Il faut choisir par où les vidéos entrent dans le catalogue.

Contraintes :
- le sujet demande une interface d'administration du contenu ;
- le rendu est le 6 novembre, le temps est limité.

## Décision
Ajouter les vidéos uniquement depuis le back-office. L'application mobile est en lecture seule sur les vidéos.

Cette lecture seule concerne uniquement les vidéos. L'application continue d'envoyer des commentaires, comme prévu par ADR-004.

L'import depuis le mobile n'apporte rien à la démonstration demandée, alors que l'administration du contenu est explicitement exigée.

## Alternatives
- Import depuis l'application, comme le vrai YouTube : avantage, plus réaliste. Inconvénients, il faut gérer les permissions, le sélecteur de fichiers, l'envoi d'un gros fichier en réseau mobile, la progression et les coupures, soit plusieurs jours de travail.

## Conséquences
### Positives
- Respect de l'exigence du sujet : l'administration du contenu passe par le back-office.
- Le catalogue est alimenté par l'administrateur.

### Négatives
- L'application ne permet pas de publier de vidéo.
- L'import depuis le mobile reste une évolution possible, à présenter comme telle.

## Liens
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement des vidéos sur notre propre backend ; c'est le back-office qui y est seul maître du catalogue et qui importe les vidéos.
- [ADR-004](ADR-004-temps-reel-websocket.md) : temps réel par WebSocket ; l'application y envoie des commentaires, ce que la lecture seule sur les vidéos ne remet pas en cause.
