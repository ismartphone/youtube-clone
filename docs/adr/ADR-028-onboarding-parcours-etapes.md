# ADR-028 — Onboarding : parcours d'accueil en étapes décrites par des données

## Statut
Accepté

## Contexte
Le sujet ajoute l'exigence « Challenge 1 — l'onboarding » : un long parcours d'accueil avec animations, sound design, vibrations, mesures, et le paywall à la fin. Le 5 novembre 2026, le lead développeur demande de documenter cette décision. Elle remplace sur le fond l'écran d'accueil à un seul écran, sans autre changement de périmètre : pas de nouvel écran en dehors de ce parcours.

### Où la décision « un seul écran » est actée aujourd'hui
Elle n'a pas d'ADR propre. Elle n'est actée que dans :
- l'[ADR-013](ADR-013-mesures-donnees-personnelles.md) : son Contexte décrit l'écran refait « en un seul écran : visuel, nom, phrase, boutons Commencer et Se connecter », et ses Alternatives écartent un choix de refus des mesures sur l'écran d'accueil parce que « l'écran d'accueil reste un écran unique et simple » ;
- le code Android, dans `presentation/onboarding/` : `OnboardingScreen.kt` (commentaire « Un seul écran ; les centres d'intérêt se choisissent ensuite depuis l'onglet Vous »), `AppStartViewModel.kt` et `navigation/AppRoot.kt`, où l'écran est affiché à la place de l'application tant que la préférence `onboarding_completed` est fausse.

### État du code au 5 novembre 2026, sur les points touchés
- **Écran unique** : `OnboardingScreen` (Commencer, Se connecter). « Commencer » enregistre `onboarding_completed` ; « Se connecter » l'enregistre aussi, puis ouvre la connexion au-dessus du fil. Il est affiché par `AppRoot`, hors de `AppNavigation` : depuis l'écran d'accueil, aucune route (connexion, inscription, paywall) n'est atteignable avant la fin.
- **Centres d'intérêt** : `InterestsScreen` n'est accessible que depuis l'onglet Vous. Le choix est facultatif (une sélection vide s'enregistre). Il est stocké dans le DataStore `preferences` (`interest_category_ids`, ADR-022) et `GetHomeFeedUseCase` place en premier les vidéos de ces catégories dans le fil d'accueil (vidéos classiques seulement ; les Shorts ont leur onglet).
- **Format préféré** : n'existe pas. Il n'y a ni préférence ni usage.
- **Préférences** : le DataStore `preferences` contient `onboarding_completed`, `interest_category_ids`, `share_usage_stats` et `autoplay`, par appareil (ADR-022).
- **Mesures** : `PostHogAnalytics` initialise PostHog dès le démarrage si `share_usage_stats` est vrai, ce qui est la valeur par défaut. Événements du paywall existants : `paywall_viewed`, `purchase_started`, `purchase_confirmed`, `paywall_dismissed`, portant `paywall_variant` (test A/B, `docs/ab-test-paywall.md`). `paywall_dismissed` est émis dans `onCleared()` de `PaywallViewModel`.
- **Paywall** : `PaywallScreen(onClose, onLoginRequired, onPurchased)`. Un visiteur ne peut pas acheter : « S'abonner » appelle `onLoginRequired` (ADR-014 : l'achat est lié au compte).
- **Inscription** : `RegisterScreen(onRegistered, onLoginClick, onContinueAsVisitor)`.
- **Notifications** : `POST_NOTIFICATIONS` est déclarée et demandée au clic sur « Télécharger » (ADR-019) et à la première activation d'une cloche (ADR-020). Le jeton FCM est enregistré à la connexion.
- **Sons et vibrations** : aucun code, aucune ressource `res/raw`, pas de permission `VIBRATE` déclarée.

### Contraintes
- aucune dépendance ajoutée (CLAUDE.md) ;
- application 100 % native (ADR-001), minSdk 26 ;
- les événements partent de l'application directement vers PostHog (ADR-006) et respectent le refus des mesures (ADR-013) ;
- le statut Premium est décidé par le serveur seul (ADR-007, ADR-014) ;
- règle suivie depuis le début du projet : pas de bouton mort, pas de réglage sans effet (ADR-020) ;
- rendu du projet le 6 novembre 2026.

## Décision
Remplacer l'écran d'accueil unique par un parcours de 10 étapes **décrit par une liste de données**, affiché par **un seul composant**.

### Principe : ajouter une étape = ajouter une ligne de données
Le parcours est une liste ordonnée d'étapes typées, dans un seul fichier de données de `presentation/onboarding/`. Un seul composant d'affichage, commun à toutes les étapes, fournit la barre de progression en haut, le bouton « Suivant », le lien « Passer » discret, la transition glissée et l'apparition en cascade. Il choisit le contenu central selon le type de l'étape.

Chaque étape porte : un identifiant stable (par exemple `interests`, utilisé comme nom d'étape dans les mesures), son type, ses textes (ressources `strings.xml`) et, selon le type, ses paramètres. Ajouter une étape de ces types ne demande ni nouveau fichier ni nouvel écran.

| Type | Contenu | Paramètres portés par la donnée |
|---|---|---|
| Présentation | visuel, titre, texte | visuel (logo animé, ou miniatures du catalogue, voir D10), textes |
| Choix multiple | puces à cocher | source des options, minimum de choix, clé de réponse |
| Choix unique | options exclusives | liste des options, clé de réponse |
| Chargement animé | animation + texte, avance seul | durée |
| Récapitulatif | résumé des réponses | clés de réponse à afficher |
| Action | écran standard + une action système ou un écran existant | identifiant de l'action |

Le sixième type, **Action**, est la réponse à « comment les étapes spéciales s'intègrent sans casser le principe ». L'étape reste une ligne de données ; ce qui est spécial est une **action nommée** (`REQUEST_NOTIFICATIONS`, `REGISTER`, `PAYWALL`), résolue par une petite table dans le ViewModel du parcours. Le composant ne connaît aucune règle métier : il affiche l'étape et appelle l'action.

Les réponses des étapes de choix sont rangées par **clé** (`INTERESTS`, `PREFERRED_FORMAT`) dans l'état du ViewModel. Le récapitulatif lit ces clés. Chaque choix est **enregistré à la validation de son étape** dans le DataStore `preferences` (ADR-022), pas à la fin du parcours.

### Le parcours, 10 étapes

| N° | Identifiant | Type | Contenu |
|---|---|---|---|
| 1 | `welcome` | Présentation | Bienvenue, logo animé |
| 2 | `millions_of_videos` | Présentation | « Des millions de vidéos » ; mosaïque de vraies miniatures du catalogue (D10) |
| 3 | `scrolling_shorts` | Présentation | « Des Shorts qui défilent » ; quelques vraies miniatures verticales de Shorts (D10) |
| 4 | `interests` | Choix multiple | centres d'intérêt, catégories de l'API (`GetCategoriesUseCase`), 3 au minimum |
| 5 | `format` | Choix unique | « Vous préférez quel format ? » : deux options exclusives, « Plutôt des vidéos » et « Plutôt des Shorts » ; l'onglet d'arrivée suit le choix (D2) |
| 6 | `preparing_feed` | Chargement animé | « Nous préparons votre fil… », 2 secondes |
| 7 | `feed_ready` | Récapitulatif | « Votre fil est prêt », choix des étapes 4 et 5 |
| 8 | `notifications` | Action | explication, puis demande de permission système |
| 9 | `account` | Action | création de compte, avec « Plus tard » |
| 10 | `paywall` | Action | paywall existant |

À chaque étape : barre de progression en haut, « Suivant », « Passer » discret.

### Comportement des étapes spéciales
- **Étape 8, notifications** : l'explication s'affiche d'abord. « Suivant » déclenche la demande de permission système `POST_NOTIFICATIONS`, puis passe à l'étape suivante quelle que soit la réponse. « Passer » n'affiche pas la demande, ce qui ne consomme pas l'unique invite du système. Sous Android 12 et moins, la permission n'existe pas : pas de demande. Le texte ne promet que ce que l'ADR-020 fournit (une notification quand une chaîne suivie publie).
- **Étape 9, création de compte** : l'action `REGISTER` réutilise l'écran d'inscription existant et ses callbacks. « Plus tard » joue le rôle de « Passer » (même événement de mesure). Un lien « J'ai déjà un compte » ouvre la connexion existante, pour garder l'accès à la connexion que l'ancien écran donnait par « Se connecter ».
- **Étape 10, paywall** : l'action `PAYWALL` réutilise l'écran de l'offre existant : même contenu, même test A/B, mêmes quatre événements, même paiement (ADR-007, ADR-014). Il n'est pas réécrit.
- **Hébergement** : le parcours reste affiché par `AppRoot`, hors de `AppNavigation`. Les écrans existants (inscription, connexion, paywall) sont donc **hébergés comme contenu** de l'étape, par leurs callbacks, et non ouverts par navigation (D5, retenu). L'événement `paywall_dismissed` n'y dépend pas de `onCleared()` : règle détaillée en D5.

### Les choix servent vraiment
- **Centres d'intérêt (étape 4)** : enregistrés dans `interest_category_ids`, déjà utilisé par le fil d'accueil (`withInterestsFirst`). Aucun changement de ce côté. Le minimum de 3 ne vaut que dans le parcours : « Passer » laisse le choix vide, comme aujourd'hui, et l'écran Vous garde son choix facultatif. Si l'API renvoie moins de 3 catégories, le minimum devient leur nombre ; si elles ne se chargent pas, on peut réessayer ou « Passer ».
- **Format préféré (étape 5)** : choix unique entre deux options, « Plutôt des vidéos » (`LONG`) et « Plutôt des Shorts » (`SHORT`). Enregistré dans une nouvelle préférence `preferred_format` (`LONG` ou `SHORT`), par appareil, à la validation de l'étape. Sans choix (« Passer »), la préférence reste absente. Usage : l'onglet affiché à l'arrivée dans l'application (D2).

### Animations
- Transition glissée entre étapes, dans le sens du parcours (avant), sans changement brutal.
- Apparition en cascade des éléments de chaque étape, appliquée par le composant commun : une étape n'a pas à la décrire.
- Écran de chargement avec une animation propre, qui avance seule pendant 2 secondes puis passe à l'étape suivante.
- Animations faites avec Compose, sans dépendance.

### Sons et vibrations
- **Sons** : trois courts bips discrets, générés une fois avec FFmpeg (déjà installé comme outil de développement, ADR-011) et rangés dans `res/raw` : passage d'étape, validation d'un choix, réussite finale. FFmpeg n'est pas une dépendance de l'application : seuls les fichiers générés sont embarqués. Lecture par `SoundPool`, de la plateforme Android.
- **Vibrations** : légère à chaque appui, plus marquée à la fin. API de la plateforme.
- **Réglage** dans Paramètres pour couper les sons et les vibrations, stocké dans le DataStore `preferences` (par appareil, ADR-022), activé par défaut.
- **Mode silencieux** : aucun son n'est joué quand le téléphone n'est pas en mode sonnerie normale.
- Détails à valider : D8.

### Mesures (PostHog, ADR-006 et ADR-013)
Événements envoyés directement depuis l'application, par l'`Analytics` existant :

| Événement | Quand | Propriétés |
|---|---|---|
| `onboarding_started` | le parcours s'ouvre | aucune |
| `onboarding_step_viewed` | une étape s'affiche | numéro de l'étape (1 à 10), nom de l'étape |
| `onboarding_step_skipped` | « Passer » (ou « Plus tard » à l'étape 9) | numéro, nom |
| `onboarding_completed` | le parcours se termine | aucune |
| `onboarding_abandoned` | le parcours est quitté sans être terminé | numéro et nom de la dernière étape vue |

Plus les événements du paywall existants, émis par l'écran hébergé à l'étape 10 : `paywall_viewed`, `purchase_started`, `purchase_confirmed`, `paywall_dismissed`.

- **Entonnoir PostHog** : `onboarding_started`, puis les `onboarding_step_viewed` filtrés par numéro, puis `onboarding_completed`. Le nom stable de l'étape est l'identifiant de la donnée (`interests`, `paywall`, etc.), pas le texte affiché. Le dernier maillon se prolonge avec `paywall_viewed` puis `purchase_confirmed`.
- **Aucune donnée personnelle** : les propriétés se limitent à un numéro et un nom d'étape. Les choix de l'utilisateur (catégories, format) ne sont pas envoyés.
- **Refus des mesures** : le parcours passe par la même `Analytics` que le reste de l'application. Si `share_usage_stats` est faux, rien ne part.
- **Avant que l'utilisateur ait pu régler ce choix** : l'ADR-013 place l'interrupteur « Partager des statistiques d'usage » dans Paramètres, activé par défaut. Au tout premier lancement, l'application n'a pas encore pu le proposer, et `PostHogAnalytics` initialise PostHog dès le démarrage. Les événements du parcours partent donc **avant tout réglage possible**, avec un identifiant anonyme de visiteur et sans aucune donnée personnelle (profils créés uniquement à l'identification). C'est déjà le cas aujourd'hui pour « Application Opened » et l'écran « Onboarding ». C'est cohérent avec l'ADR-013, qui assume un choix de désactivation et non un consentement préalable, mais la fenêtre est plus longue : 10 étapes au lieu d'un écran. Voir D3.
- **Passage au compte** : à l'étape 9, l'identification remplace l'identifiant anonyme par l'UUID (ADR-013). L'entonnoir peut donc être coupé en deux identifiants autour de l'étape 9 ; à vérifier dans PostHog.

### Décisions du lead développeur
Le 5 novembre 2026, le lead développeur a tranché D1 à D10. D1, D3, D4, D6, D7, D8 et D9 : **la recommandation de l'ADR est retenue telle quelle**. D2, D5 et D10 sont **modifiées par le lead** ; leur texte ci-dessous donne la décision retenue (les options écartées sont conservées pour mémoire).

**D1. Permission de notifications dans le parcours (contradiction avec l'ADR-020).**
L'ADR-020 dit : « Permission POST_NOTIFICATIONS demandée à la première activation d'une cloche, pas au démarrage ». Le parcours la demande à l'étape 8, avant toute cloche et avant tout compte.
- Option A : amender l'ADR-020 sur place (« Amendé le 5 novembre : … », avec la raison : exigence du sujet demandée par le lead). L'ADR reste Accepté ; la cloche et les téléchargements gardent leur comportement, et une permission déjà accordée n'est pas redemandée.
- Option B : ne pas demander la permission à l'étape 8 (explication seule). Contraire à la demande du lead.
- **Retenu : A.** C'est un ajustement de périmètre explicitement demandé, le cas prévu par la règle d'amendement. L'amendement de l'ADR-020 est appliqué à l'acceptation (voir plus bas).

**D2. Usage du format préféré (étape 5). Modifiée par le lead.** La règle « pas de deux options qui font la même chose » (ADR-020) s'applique. Aujourd'hui l'application a deux onglets distincts : le fil d'accueil (vidéos classiques) et l'onglet Shorts.
- **Retenu (modification du lead)** : l'étape 5 est un **choix unique à deux options**, « Plutôt des vidéos » (`LONG`) et « Plutôt des Shorts » (`SHORT`). L'option « les deux », qui aurait fait la même chose qu'une autre (bouton mort), est supprimée. Le choix décide de **l'onglet affiché à l'arrivée** dans l'application à la fin du parcours : Accueil pour « Plutôt des vidéos », Shorts pour « Plutôt des Shorts ». Sans choix (« Passer »), l'application s'ouvre sur l'onglet Accueil, comme aujourd'hui. Aucune fonction nouvelle. Valeur stockée : `preferred_format` = `LONG` ou `SHORT`, absente si l'étape est passée. Le récapitulatif (étape 7) affiche le format choisi, s'il y en a un.
- Option B (écartée) : ordre du fil d'accueil. Inutile : le fil d'accueil ne contient que des vidéos classiques.
- Option C (écartée) : enregistré et affiché dans le récapitulatif seulement. Le choix ne « servirait » pas.
- Option D (écartée) : étagère de Shorts dans le fil d'accueil. Fonction nouvelle, non demandée.
- Point à vérifier à l'implémentation : que l'application sait s'ouvrir sur l'onglet Shorts à l'arrivée ; si ce n'est pas le cas, l'ajouter au démarrage reste dans le périmètre de cette décision, sans nouvel écran.

**D3. Mesures avant que l'utilisateur ait pu les refuser.**
- Option A : garder l'ADR-013 tel quel (envoi par défaut, refus dans Paramètres) et l'annoncer à l'oral. Données : identifiant anonyme, numéros et noms d'étapes seulement.
- Option B : n'envoyer aucun événement du parcours avant l'étape 8 ou 9. Casse l'entonnoir.
- Option C : ajouter une phrase d'information et un moyen de refuser dès l'étape 1. Cela touche à ce que l'ADR-013 a écarté (« l'écran d'accueil reste un écran unique et simple »), dont la raison disparaît avec ce parcours.
- **Retenu : A**, avec la phrase de l'ADR-013 sur le choix de désactivation et non de consentement préalable.

**D4. Définition de `onboarding_abandoned`.** Android n'avertit pas fiablement qu'un utilisateur ne reviendra pas : processus tué, application passée en arrière-plan, retour plus tard.
- Option A : émis quand l'application passe en arrière-plan avec le parcours non terminé. Peut compter à tort un utilisateur qui revient quelques secondes plus tard.
- Option B : émis au prochain lancement si le parcours n'a pas été terminé, avec la dernière étape vue, enregistrée dans le DataStore. Plus fiable, mais en retard et jamais émis si l'application n'est pas rouverte.
- **Retenu : A**, plus simple, avec la limite documentée. L'entonnoir repose de toute façon sur `onboarding_step_viewed`. À noter : un parcours interrompu repart de l'étape 1 au lancement suivant (`onboarding_completed` reste faux), donc `onboarding_started` peut être émis plusieurs fois pour un même utilisateur ; PostHog compte les utilisateurs uniques.

**D5. Hébergement des écrans d'inscription, de connexion et de paywall. Retenu : A, avec une règle explicite pour `paywall_dismissed`.** Le parcours est affiché hors de `AppNavigation`, où se trouvent ces routes.
- **Option A, retenue** : héberger les écrans existants comme contenu de l'étape, par leurs callbacks, avec la barre de progression du parcours. Une seule source de vérité pour le paywall et son test A/B. Ces écrans ont leur propre barre de titre à adapter.
- Option B (écartée) : déplacer le parcours dans le graphe de navigation. La barre de progression disparaîtrait pendant l'inscription et le paywall, ce qui contredit « à chaque étape ».
- **Règle de `paywall_dismissed` (décision du lead).** Aujourd'hui `PaywallViewModel` l'émet dans `onCleared()`, qui ne partirait pas à la sortie de l'étape : un ViewModel créé hors du graphe de navigation est lié à l'activité (comme `AccountsViewModel` dans `AppRoot`). L'événement **ne dépend donc plus de `onCleared()`** à l'étape 10 : il est émis **explicitement quand on quitte l'étape**, par un appel du parcours à une méthode du `PaywallViewModel` (par exemple `onLeft()`), quel que soit le chemin :
  - « Suivant » ou « Passer » à l'étape 10 ;
  - retour système vers l'étape 9 ;
  - abandon du parcours pendant l'étape 10 (application passée en arrière-plan, D4) ;
  - sortie après un achat réussi : voir ci-dessous.
  - **Pas de double envoi** : la méthode est idempotente (un indicateur « déjà envoyé » dans le `PaywallViewModel`), et `onCleared()` appelle la même méthode comme filet de sécurité pour l'écran ouvert hors du parcours. Un événement `paywall_dismissed` correspond à au plus un `paywall_viewed`.
  - **Pas d'envoi après un achat réussi, ni pour un compte déjà Premium** : c'est la condition actuelle de `onCleared()` (achat non confirmé et compte non Premium), conservée à l'identique. Un achat réussi se mesure par `purchase_confirmed` ; un membre déjà Premium n'a rien abandonné. L'événement n'a donc de sens que pour un paywall vu, non converti, par un compte non Premium.
  - **Jamais envoyé** si l'étape 10 n'a pas été atteinte : `paywall_viewed` n'est émis qu'à la création du `PaywallViewModel`, qui ne doit être créé qu'à l'affichage de l'étape 10 (pas à l'avance), afin de ne pas fausser le test A/B.
  - Limites documentées : processus tué sur l'étape 10, aucun événement (comme aujourd'hui avec `onCleared()`) ; si l'utilisateur revient après un abandon (arrière-plan) et achète ensuite, `paywall_dismissed` a déjà été compté puis `purchase_confirmed` est émis, sans nouveau `paywall_viewed`.
  - Ces événements portent la propriété `source` = `onboarding` (D7).

**D6. Paywall pour un visiteur ou un compte déjà Premium.** Un visiteur qui a choisi « Plus tard » à l'étape 9 arrive sur un paywall où il ne peut pas acheter sans compte. Un compte déjà Premium (connecté à l'étape 9) n'a rien à acheter.
- Visiteur : option A, afficher le paywall et envoyer vers la connexion existante au clic sur « S'abonner » (comportement actuel de `onLoginRequired`) ; option B, ne pas afficher l'étape 10, ce qui sort des 10 étapes demandées.
- Compte déjà Premium : option A, afficher l'état « déjà membre » existant ; option B, sauter l'étape.
- **Retenu : A dans les deux cas**, qui garde 10 étapes et réutilise le paywall tel quel.

**D7. Propriété `source` sur les événements du paywall.** Le paywall vu à la fin du parcours est un passage presque obligatoire, non une demande de l'utilisateur. Il gonflerait `paywall_viewed` et ferait baisser le taux de conversion du test A/B (`docs/ab-test-paywall.md`), qui mesure `purchase_confirmed` / `paywall_viewed`.
- Option A : ajouter une propriété `source` (`onboarding` ou `app`) aux quatre événements existants, pour filtrer. Les événements gardent leur nom et leurs autres propriétés.
- Option B : ne rien changer. Le test A/B est mélangé.
- **Retenu : A.** Ce n'est pas un nouvel événement, mais cela touche ceux de l'ADR-006, ce que le lead accepte. La valeur `app` est portée par l'écran ouvert hors du parcours.

**D8. Sons et vibrations.** Retenu : toutes les recommandations ci-dessous (un seul interrupteur, portée du parcours, `Vibrator` et `VIBRATE`, son de réussite à la sortie de l'étape 10).
- Un ou deux réglages (sons, vibrations) ? Le lead a demandé « un réglage pour couper sons et vibrations » ; **recommandation : un seul interrupteur**.
- Portée : le parcours seulement, puisque les sons et les vibrations ne sont demandés que là. Conséquence : le réglage de Paramètres n'agit que sur un parcours vu une fois, ce qui frôle un réglage sans effet (règle de l'ADR-020). Options : accepter (recommandation, c'est la demande du lead), étendre les sons au reste de l'application (fonction non demandée), ou permettre de rejouer le parcours (non demandé). **Question au lead.**
- Au tout premier lancement, le réglage n'est pas encore accessible : les sons et vibrations sont actifs par défaut pendant le parcours, comme les mesures (D3).
- Vibrations en mode silencieux : le lead demande de respecter le mode silencieux. **Recommandation :** sons coupés dès que le mode n'est pas « sonnerie normale » ; vibrations coupées en mode silencieux, permises en mode vibreur.
- Mécanisme des vibrations : `Vibrator` de la plateforme, qui demande la permission `VIBRATE` (normale, accordée à l'installation, à ajouter au manifeste), avec une intensité réglable pour la vibration finale plus marquée ; ou le retour haptique de Compose, sans permission mais sans contrôle de l'intensité. **Recommandation : `Vibrator` et `VIBRATE`.** Ce n'est pas une dépendance, mais c'est un ajout au manifeste à signaler.
- « Son de réussite à la fin » : à l'étape 7 (« Votre fil est prêt ») ou à la sortie de l'étape 10 ? **Recommandation : à la sortie du parcours (fin de l'étape 10)**, comme la vibration marquée. **Question au lead.**

**D9. Détails de comportement.** Retenu tel quel, y compris le retour système et l'étape 6 purement visuelle.
- **Étape 6** : le chargement avance seul en 2 secondes. « Suivant » et « Passer » sont masqués, car un bouton « Passer » sur une animation de 2 secondes n'a pas de sens. Cette étape est purement visuelle : elle ne charge rien. **Recommandation : l'assumer ainsi**, avec un texte qui ne prétend pas calculer un vrai fil. **À confirmer.**
- **« Passer »** saute l'étape courante et passe à la suivante ; il ne quitte pas le parcours. Aux étapes 4 et 5, il laisse le choix vide ; le récapitulatif n'affiche alors que les choix faits.
- **`onboarding_completed`** (l'événement comme la préférence) : le parcours est terminé quand on sort de l'étape 10, quelle que soit la façon (achat, fermeture, « Passer »). Si l'utilisateur quitte avant, la préférence reste fausse et le parcours repart de l'étape 1 ; les choix déjà enregistrés sont cochés d'avance.
- **Retour système** : retour à l'étape précédente, sauf à l'étape 1. Non demandé explicitement : à confirmer.
- **Comptes existants** : un appareil où `onboarding_completed` est déjà vrai ne revoit pas le parcours. Il n'y a pas de migration.

**D10. Visuels des étapes 2 et 3. Modifiée par le lead : pas d'images.** Aucun fichier d'illustration n'est embarqué ni fourni. Les visuels sont composés des **vraies miniatures du catalogue** :
- **Étape 2, « Des millions de vidéos »** : mosaïque de vignettes de vidéos classiques.
- **Étape 3, « Des Shorts qui défilent »** : quelques miniatures verticales de Shorts.
- **Source : l'API existante, aucun nouvel endpoint.** `GET /videos` (`VideoApi.getVideos` côté Android) convient : il accepte `format` (`LONG` ou `SHORT`), `page` et `pageSize` (50 au plus, 20 par défaut), et renvoie pour chaque vidéo `thumbnailUrl`, `format` et `isPremium`. Vérifié dans `videos.controller.ts` : la route n'a aucun garde d'authentification, elle est donc **accessible à l'utilisateur non connecté** (ce qu'est le visiteur pendant les étapes 2 et 3), et elle ne renvoie que les vidéos publiques (`publicOnly`). Une liste ne contient jamais l'URL des fichiers vidéo, seulement la miniature : aucun accès Premium n'est en jeu (ADR-007). Deux appels, un par format (`format=LONG`, `format=SHORT`), en première page.
- **Chargement** : lancé au premier affichage du parcours (étape 1) par le ViewModel du parcours, pour que les visuels soient prêts à l'étape 2. Résultat gardé en mémoire pour la durée du parcours, non stocké. Les images sont chargées par Coil, déjà utilisé dans l'application.
- **Catalogue vide ou injoignable** : aucun bouton mort, aucun écran cassé, aucun message d'erreur ni bouton « Réessayer » sur ces étapes. Le visuel se replie sur une composition dessinée en code (rectangles aux couleurs de l'application, de même forme que la mosaïque ou les vignettes verticales), et le titre, le texte, « Suivant » et « Passer » fonctionnent comme sur toute étape. S'il y a moins de miniatures que d'emplacements, la mosaïque n'en affiche que le nombre disponible, complété par ces mêmes rectangles. Une miniature qui échoue à se charger est remplacée par un rectangle neutre. Pendant le chargement, le visuel de repli est affiché, puis remplacé sans bloquer l'utilisateur.
- Le parcours ne dépend donc pas du réseau pour avancer, sauf pour les catégories de l'étape 4 (qui ont déjà leur « Réessayer » ou « Passer »).

## Alternatives
- **Un écran par étape, en dur** (un composable et une route par étape) : avantage, chaque écran est libre. Inconvénients : ajouter une étape coûte un fichier et une route, barre de progression, animations et mesures sont à répéter dix fois. Écarté : c'est ce que le principe de la liste de données évite.
- **Parcours dans le graphe de navigation** : avantage, les écrans existants gardent leur ViewModel par route. Inconvénient : perd la barre de progression sur les étapes 9 et 10 (voir D5).
- **Parcours piloté par le serveur** (étapes décrites par l'API) : avantage, étapes modifiables sans nouvelle version. Inconvénients : un module backend et une dépendance réseau pour un écran de premier lancement, hors de ce qui est demandé.
- **Bibliothèque d'onboarding, de sons ou d'animations** : écartée, aucune dépendance ajoutée.
- **Garder l'écran unique** : avantage, aucun travail. Inconvénient : ne répond pas à l'exigence du sujet.

## Conséquences
### Positives
- Ajouter ou réordonner une étape d'un type existant est une ligne de données.
- Barre de progression, transitions, cascade, sons, vibrations et mesures sont écrits une fois, dans le composant commun.
- Le paywall, son test A/B et ses événements sont réutilisés, pas dupliqués.
- Les centres d'intérêt choisis alimentent déjà le fil ; le parcours donne un accès au choix dès le premier lancement.
- Un entonnoir PostHog complet, de l'ouverture à l'achat, sans donnée personnelle.
- Aucune dépendance ajoutée.

### Négatives
- Le composant commun et le type Action sont plus abstraits qu'un écran en dur ; les étapes spéciales (inscription, paywall) demandent un hébergement soigné (D5).
- L'ADR-013 n'est plus exact sur « l'écran d'accueil reste un écran unique » ; l'ADR-020 est en tension sur la permission de notifications (D1).
- Les événements du parcours partent avant tout réglage possible des mesures (D3).
- `onboarding_abandoned` est approximatif (D4), et `onboarding_started` peut être compté plusieurs fois par utilisateur.
- Le paywall vu en fin de parcours fausse le test A/B sans la propriété `source` (D7).
- Le réglage des sons n'agit que sur un parcours vu une fois (D8).
- Les visuels des étapes 2 et 3 dépendent du catalogue et du réseau ; un repli dessiné en code les remplace quand ils manquent (D10).
- `PaywallViewModel` change : `paywall_dismissed` passe par une méthode explicite et idempotente, avec `onCleared()` en filet de sécurité (D5).
- Ajout de la préférence `preferred_format` (`LONG` ou `SHORT`), d'un réglage sons et vibrations, de trois fichiers `res/raw` et de la permission `VIBRATE` (D8).
- Un utilisateur qui n'a jamais vu l'ancien écran n'est pas concerné, mais un appareil déjà passé par l'ancien écran ne voit pas le nouveau parcours.
- Charge de travail importante avant le rendu du 6 novembre 2026.
- CLAUDE.md est mis à jour par un humain à l'acceptation : préférences `preferred_format` et réglage sons et vibrations ajoutés au DataStore `preferences`.

### Effet sur l'ADR-013 (appliqué à l'acceptation, le 5 novembre 2026)
Statut « Accepté, remplacé en partie par l'ADR-028 (écran d'accueil) », dans le fichier de l'ADR-013 et dans l'index, comme l'ADR-006 l'a été par l'ADR-013. Seuls sont remplacés l'écran d'accueil « en un seul écran » (Contexte) et la raison donnée pour écarter un choix de refus à l'écran d'accueil (Alternatives). Le reste de l'ADR-013 reste valable : identification par l'UUID, interrupteur dans Paramètres, activé par défaut.

### Effet sur l'ADR-020 (appliqué à l'acceptation, le 5 novembre 2026)
Amendement sur place, voir D1 : « Amendé le 5 novembre : la permission POST_NOTIFICATIONS est aussi demandée à l'étape « notifications » du parcours d'accueil (ADR-028). Raison : exigence du sujet demandée par le lead développeur. » L'ADR-020 reste Accepté.

## Liens
- [ADR-013](ADR-013-mesures-donnees-personnelles.md) : mesures d'audience et données personnelles ; il décrit l'écran d'accueil unique que cet ADR remplace, et son interrupteur de refus dans Paramètres s'applique au parcours (D3).
- [ADR-006](ADR-006-analytics-posthog.md) : mesures PostHog, envoi direct depuis l'application ; test A/B du paywall (D7).
- [ADR-022](ADR-022-plusieurs-comptes-appareil.md) : préférences par appareil dans le DataStore `preferences` (`onboarding_completed`, centres d'intérêt, et les nouvelles préférences de cet ADR).
- [ADR-007](ADR-007-offre-premium.md), [ADR-014](ADR-014-paiement-stripe-mode-test.md) : offre Premium et paiement ; le paywall de l'étape 10 est l'écran existant, l'achat exige un compte et le serveur seul décide (D6).
- [ADR-020](ADR-020-notifications-fcm.md) : notifications FCM ; tension sur le moment de la demande de permission (D1).
- [ADR-019](ADR-019-je-naime-pas-telechargements-playlists-aide.md) : la permission POST_NOTIFICATIONS y est déjà demandée pour les téléchargements ; accordée à l'étape 8, elle n'est pas redemandée.
- [ADR-008](ADR-008-authentification-jwt.md) : authentification ; l'étape 9 passe par l'inscription et la connexion existantes.
- [ADR-011](ADR-011-ffmpeg-chemins-medias.md) : FFmpeg, déjà outil du projet ; il sert ici à générer les sons une fois, hors de l'application.
- [ADR-001](ADR-001-android-natif.md) : application native Android ; sons, vibrations et animations par les API de la plateforme et Compose.
