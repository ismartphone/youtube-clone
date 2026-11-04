# Architecture Decision Records

Liste des décisions d'architecture du projet et de leur statut. Seul un humain peut accepter ou remplacer un ADR.

| Numéro | Titre | Statut |
|---|---|---|
| [ADR-001](ADR-001-android-natif.md) | Application mobile native Android uniquement (Kotlin + Jetpack Compose) | Accepté |
| [ADR-002](ADR-002-hebergement-videos.md) | Hébergement des vidéos sur notre propre backend, sans API YouTube | Accepté |
| [ADR-003](ADR-003-backend-nestjs.md) | Backend en NestJS, architecture hexagonale et monolithe modulaire | Accepté |
| [ADR-004](ADR-004-temps-reel-websocket.md) | Commentaires et compteurs en temps réel par une passerelle WebSocket NestJS | Accepté |
| [ADR-005](ADR-005-ajout-videos-backoffice.md) | Ajout des vidéos uniquement depuis le back-office | Remplacé par l'ADR-015 |
| [ADR-006](ADR-006-analytics-posthog.md) | Mesures d'audience et test A/B avec PostHog | Accepté, remplacé en partie par l'ADR-013 (données personnelles) |
| [ADR-007](ADR-007-offre-premium.md) | Offre Premium | Accepté, remplacé en partie par l'ADR-014 (paiement) et l'ADR-021 (publicité) |
| [ADR-008](ADR-008-authentification-jwt.md) | Authentification par JWT avec access token court et refresh token | Accepté, remplacé en partie par l'ADR-022 (plusieurs comptes) |
| [ADR-009](ADR-009-stockage-tokens-backoffice.md) | Stockage des tokens du back-office en localStorage | Accepté |
| [ADR-010](ADR-010-websocket-salles-compteurs.md) | Authentification, salles par vidéo et diffusion des compteurs sur la connexion WebSocket | Accepté |
| [ADR-011](ADR-011-ffmpeg-chemins-medias.md) | FFmpeg en dépendance système et chemins relatifs pour les fichiers hébergés | Accepté |
| [ADR-012](ADR-012-paiement-simule-backend.md) | Achat Premium simulé par notre propre backend | Remplacé en grande partie par l'ADR-014 |
| [ADR-013](ADR-013-mesures-donnees-personnelles.md) | Mesures d'audience et données personnelles | Accepté |
| [ADR-014](ADR-014-paiement-stripe-mode-test.md) | Paiement Premium par Stripe en mode test | Accepté |
| [ADR-015](ADR-015-publication-depuis-mobile.md) | Publication de vidéos, de Shorts et de posts depuis l'application mobile | Accepté |
| [ADR-016](ADR-016-reglages-publication-video.md) | Réglages de publication d'une vidéo : visibilité et commentaires | Accepté |
| [ADR-017](ADR-017-hls-multi-qualites-sous-titres.md) | Diffusion HLS multi-qualités et sous-titres automatiques, produits hors ligne par FFmpeg | Accepté |
| [ADR-018](ADR-018-personnalisation-chaine-mobile.md) | Personnalisation de sa chaîne par le créateur depuis l'application mobile | Accepté |
| [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) | Boutons « Je n'aime pas », « Télécharger », « Enregistrer » et « Aide » : avis négatif, téléchargements Premium, playlists et écran d'aide | Accepté |
| [ADR-020](ADR-020-notifications-fcm.md) | Notifications de publication par Firebase Cloud Messaging | Accepté |
| [ADR-021](ADR-021-publicites-preroll-bandeau.md) | Publicités vidéo : pré-roll réel, bandeau sponsorisé | Accepté |
| [ADR-022](ADR-022-plusieurs-comptes-appareil.md) | Plusieurs comptes sur l'appareil et changement de compte | Accepté |
| [ADR-023](ADR-023-reponses-jaime-commentaires.md) | Réponses et « j'aime » sur les commentaires | Accepté |
| [ADR-024](ADR-024-mini-lecteur.md) | Mini-lecteur : lecteur unique remonté au niveau de l'ossature de l'application | Proposé |
| [ADR-025](ADR-025-messagerie-utilisateurs.md) | Messagerie entre utilisateurs : conversations à deux, partage de vidéos, notifications | Accepté |
