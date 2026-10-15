# ADR-003 — Backend en NestJS, architecture hexagonale et monolithe modulaire

## Statut
Accepté

## Contexte
Le projet a besoin d'un backend. Il faut choisir son framework et son organisation, ainsi que celle du back-office.

Contraintes :
- le sujet demande une API REST et un serveur WebSocket ;
- le back-office est en Next.js + TypeScript.

## Décision
Développer le backend en NestJS (TypeScript), en architecture hexagonale, sous forme de monolithe modulaire.

NestJS impose une structure (modules, contrôleurs, services) et fournit la validation, l'authentification et le WebSocket. Il utilise le même TypeScript que le back-office, ce qui permet de partager les types.

L'architecture hexagonale isole les règles métier du framework et de la base. Elles sont donc testables sans serveur.

Le back-office Next.js suit une organisation MVC. Elle suffit, puisque le back-office n'a aucune règle métier propre.

## Alternatives
- Express : avantage, très libre. Inconvénient, aucune structure imposée, chacun organise son projet à sa façon.
- Ktor (Kotlin) : avantage, même langage que l'application Android. Inconvénients, moins de ressources en ligne, et un troisième environnement à gérer.
- Microservices : inconvénients, inutiles à cette échelle, avec beaucoup de services à déployer et à surveiller.

## Conséquences
### Positives
- Structure imposée par NestJS (modules, contrôleurs, services).
- Validation, authentification et WebSocket fournis par le framework.
- Types partagés entre le backend et le back-office, tous deux en TypeScript.
- Règles métier isolées du framework et de la base, testables sans serveur.
- Les modules pourront être extraits en microservices plus tard si besoin.

### Négatives
- Plus de dossiers au départ.
- Deux langages dans le projet : Kotlin pour l'application et TypeScript pour le backend et le back-office.

## Liens
- [ADR-001](ADR-001-android-natif.md) : application native Android en Kotlin ; c'est ce choix qui fait coexister Kotlin et TypeScript dans le projet, et qui donne à Ktor l'avantage du même langage que l'application.
- [ADR-002](ADR-002-hebergement-videos.md) : hébergement des vidéos sur notre propre backend ; c'est ce backend qui gère le stockage des fichiers et la recherche via Prisma.
