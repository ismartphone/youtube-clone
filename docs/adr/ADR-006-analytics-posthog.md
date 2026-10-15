# ADR-006 — Mesures d'audience et test A/B avec PostHog

## Statut
Accepté

## Contexte
Il faut choisir comment mesurer l'audience et mener des tests A/B dans l'application.

Contraintes :
- le sujet demande des analytics et des tests A/B dans l'application.

## Décision
Utiliser PostHog pour les mesures d'audience et le test A/B.

Un seul outil couvre les deux besoins. PostHog fournit une bibliothèque Android officielle, et son offre gratuite suffit pour un projet d'école.

### Hébergement
PostHog en cloud, région Europe.

Les événements partent de l'application directement vers PostHog. C'est un choix assumé, qui évite de faire transiter les événements par notre backend.

### Événements mesurés
- Vidéo lancée.
- Temps de visionnage.
- Abonnement à une chaîne.
- Écran de l'offre affiché.
- Passage en Premium.

### Test A/B
Un seul test est prévu : deux variantes de l'écran de l'offre payante, comparées sur le taux de conversion.

### RGPD
Les événements sont anonymes et ne contiennent aucune donnée personnelle. L'utilisateur peut refuser les mesures depuis l'écran d'accueil.

## Alternatives
- Firebase Analytics avec Remote Config : avantages, gratuit et très répandu. Inconvénients, deux outils à configurer et des rapports moins souples.
- Solution maison avec nos propres tables d'événements : avantage, contrôle total. Inconvénients, tout est à construire, sans tableaux de bord.

## Conséquences
### Positives
- Un seul outil pour les mesures d'audience et le test A/B.
- Bibliothèque Android officielle.
- Offre gratuite suffisante pour un projet d'école.

### Négatives
- Dépendance à un service externe.

## Liens
- [ADR-001](ADR-001-android-natif.md) : application native Android (Kotlin + Jetpack Compose) ; c'est dans cette application qu'est intégrée la bibliothèque Android de PostHog.
- [ADR-003](ADR-003-backend-nestjs.md) : backend en NestJS ; les événements de mesure ne passent pas par ce backend.
