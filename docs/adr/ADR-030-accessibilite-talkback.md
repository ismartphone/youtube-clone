# ADR-030 — Accessibilité pour les personnes aveugles : TalkBack sur l'application Android (Challenge 2)

## Statut
Accepté (5 novembre 2026, par le lead développeur)

## Contexte
Le sujet ajoute l'exigence « Challenge 2 — l'accessibilité pour les personnes aveugles ». Le 5 novembre 2026, le lead développeur demande de la documenter. Le rendu du projet est le 6 novembre 2026, soit demain : le temps est la contrainte principale.

Cet ADR ne remplace aucun ADR existant et ne change pas le périmètre fonctionnel : **aucune nouvelle fonctionnalité visuelle**. Il ajoute des libellés, du regroupement, des annonces et des corrections de taille et de contraste à l'existant. Il touche l'application Android seulement (le back-office et le backend ne sont pas concernés).

**Note sur la liste d'exigences.** La liste du lead est arrivée coupée après la 8e puce, puis a été renvoyée complète avec une 9e exigence (regroupement des éléments). Aucune exigence manquante n'est connue à ce jour. Si le lead en ajoute, un amendement suffira.

### Exigences du lead
1. Chaque élément interactif a un libellé lisible par TalkBack : boutons, icônes, miniatures, onglets, cartes vidéo, barre de lecture, champs. Aucune icône sans description.
2. Les images décoratives sont explicitement ignorées par TalkBack.
3. Les éléments cliquables font au moins 48 dp de côté.
4. Contrastes de texte de 4,5:1 au minimum, 3:1 pour les gros titres.
5. L'ordre de parcours au balayage suit l'ordre visuel, du haut vers le bas.
6. Les changements d'état sont annoncés (j'aime, abonnement, lecture en pause, nouveau message dans le chat, nombre de spectateurs), sans annonce répétée en boucle.
7. Les écrans tiennent avec la plus grande taille de police du système, sans texte coupé.
8. Le lecteur vidéo est utilisable sans voir : lecture, pause, avance, sous-titres, plein écran, tous atteignables au balayage.
9. Les groupes qui se lisent comme un tout sont regroupés : une carte vidéo s'annonce en une seule fois (titre, chaîne, durée, vues), pas en cinq arrêts. Cela vaut pour les cartes vidéo, les lignes de liste, les cartes du live, etc.

### État des lieux (relevé du code, 5 novembre)
Relevé fait par recherche dans `android/app/src/main/java/com/youtubeclone/app/` (255 fichiers Kotlin, dont 105 dans `presentation/`). **Ce sont des comptages de texte dans le code, pas ce que TalkBack dit réellement** : aucun test avec TalkBack n'a encore été fait. Les chiffres marqués « approx. » sont des ordres de grandeur.

**Ce qui existe déjà**
- `contentDescription` renseigné, en ressources (`strings.xml`) : environ 37 occurrences (28 par `stringResource`, 9 par variable). Les boutons du lecteur (lecture/pause du mini-lecteur, sous-titres, retour, envoi) en ont un.
- Rôles : environ 41 usages de `Role.*` (Button, RadioButton, Switch, Checkbox, Tab). Les boutons « J'aime » et « Je n'aime pas » de l'écran de lecture ont `Role.Switch` et un `stateDescription` (`WatchScreen.kt`, 3 usages de `stateDescription` en tout).
- Regroupement implicite : `Modifier.clickable` fusionne les enfants en un seul nœud par défaut, donc une carte vidéo cliquable est déjà lue en un bloc (titre, chaîne, vues, date, durée, chaque texte lu à la suite dans le même arrêt). Rien n'est explicite, et les éléments cliquables imbriqués (avatar, nom de la chaîne) restent des arrêts séparés.
- Mouvement : le réglage système « réduire les animations » est déjà lu une fois (`LocalReduceMotion`, `Motion.kt`, ADR-028) et partagé. Cet ADR le réutilise ; il n'y touche pas.
- Les boîtes `IconButton` et la barre d'onglets Material 3 appliquent la cible minimale de 48 dp d'elles-mêmes.
- Dépendances de test : `ui-test-junit4` et `ui-test-manifest` (Compose) sont déjà déclarées dans `app/build.gradle.kts`. **Il n'existe aucun dossier `androidTest`** : aucun test d'accessibilité aujourd'hui.

**Ce qui manque (zéro occurrence dans tout le code)**
`liveRegion`, `heading()`, `traversalIndex` / `isTraversalGroup`, `mergeDescendants` explicite, `customActions`, `invisibleToUser`, `announceForAccessibility`, gestion particulière de `fontScale`. Les titres d'écran et de section ne sont donc pas annoncés comme titres, et rien n'est annoncé de façon active.

**Exigence 1 et 2, libellés et images décoratives**
- 68 `Icon(`, 22 `IconButton(`, 26 `AsyncImage(` et environ 22 `Image(`.
- **55 `contentDescription = null`.** Un `null` rend l'élément invisible pour TalkBack : c'est correct pour une image décorative, c'est un défaut pour une icône qui porte le sens. Les 55 n'ont pas encore été classés un par un. Échantillon de 7 lu : 6 décoratifs légitimes (miniatures sous un titre dans un élément cliquable, image de fond de l'écran « live terminé », aperçu de playlist), **1 défaut confirmé** (voir ci-dessous).
- **Défaut confirmé, colonne d'actions des Shorts** (`ShortsScreen.kt`, ligne 442) : l'icône a `null` et le seul texte est le compteur (« 1,2 k »). TalkBack lit donc « 1,2 k, bouton » sans dire « J'aime ». À corriger, et probablement ailleurs dans la même colonne.
- Aucun `contentDescription` n'est explicitement « ignoré » : l'exigence 2 est satisfaite par le comportement par défaut de `null`, pas par un choix écrit. La convention proposée (D2) la rend explicite.
- Les compteurs et durées sont du texte formaté (`formatDuration`, `formatViewCount`) : lus tels quels (« 3:12 », « 1,2 k vues »), probablement mal prononcés. À entendre au test réel.
- Éléments sans libellé évident : le clic sur toute la surface d'un Short (`onTap`, sans rôle ni libellé), la vue vidéo du live (aucune description).

**Exigence 3, cibles de 48 dp**
- 51 `.clickable` et 14 `toggleable` / `selectable`. Environ 27 des 51 `.clickable` n'ont pas de `role` (surtout les cartes : accueil, chaîne, live, abonnements, playlists).
- Tailles trop petites **confirmées par le code** : avatar de chaîne de la carte vidéo cliquable, 36 dp (`VideoCard.kt`) ; bouton de son du bandeau sponsorisé, 40 dp (`SponsoredAdCard.kt`) ; boutons de réaction du live, 44 dp (`LiveReactions.kt`) ; trois `IconButton` du commentaire forcés à `size(36.dp)` (`CommentsSection.kt`, lignes 308, 327, 347), dont la cible réelle est probablement 36 dp aussi puisque la taille imposée de l'extérieur borne la cible minimale (à confirmer).
- À mesurer (taille non lisible dans le code) : colonne d'actions des Shorts (icône de 32 dp avec 4 dp de marge, plus un texte), lignes de « J'aime » / « Je n'aime pas » de l'écran de lecture (icône de 20 dp dans une ligne dont la hauteur vient du parent), puces de catégorie de l'accueil (`ChipHeight`).

**Exigence 4, contrastes** (calculés avec la formule WCAG sur les couleurs réelles de `Color.kt` et `Theme.kt`) :

| Combinaison | Ratio | Seuil 4,5:1 (texte) | Seuil 3:1 (gros texte, icône) |
|---|---|---|---|
| Texte principal `#F1F1F1` sur fond `#0F0F0F` | 16,97 | oui | oui |
| Texte principal sur `surfaceVariant` `#272727` | 13,23 | oui | oui |
| Texte secondaire `#AAAAAA` sur fond | 8,25 | oui | oui |
| Texte secondaire sur `surfaceVariant` | 6,43 | oui | oui |
| Rouge `#FF0000` (primary, erreur) sur fond | 4,79 | oui | oui |
| **Rouge sur `surfaceVariant`** | **3,74** | **non** | oui |
| **Blanc sur rouge `#FF0000` (`onPrimary`, pastille « EN DIRECT », boutons)** | **4,00** | **non** | oui |
| Texte secondaire de la carte publicitaire `#606060` sur `#F2F2F2` | 5,62 | oui | oui |
| Blanc sur l'accent de l'onboarding (rouge à 85 %) | 5,22 | oui | oui |
| Jaune Premium `#FFC107` sur fond | 11,76 | oui | oui |
| `onSurfaceVariant` à 50 % d'opacité sur fond (`WatchScreen.kt`, ligne 1056) | 2,61 | non | non |

Lecture : le thème est globalement très contrasté. Deux échecs sont structurels : le **blanc sur rouge (4,00)** touche tout texte de petite taille sur fond rouge (pastille « EN DIRECT » en `labelSmall`, boutons rouges) ; le **rouge en texte sur gris foncé (3,74)**. Le gris à 50 % (2,61) est à qualifier : s'il ne sert qu'à décorer (poignée), il n'est pas concerné. **Non calculable sans image :** le texte blanc posé sur les miniatures et les dégradés (Shorts, bannière de chaîne, durée sur miniature) ; le code pose des voiles noirs de 60 à 80 %, qui donnent en pratique un contraste élevé, à vérifier à l'œil et au scanner. Le thème est toujours sombre (aucun `isSystemInDarkTheme`).

**Exigence 5, ordre de parcours**
Aucun `traversalIndex` ni `isTraversalGroup`. TalkBack suit donc l'ordre de composition. Il est probablement conforme à l'ordre visuel là où les écrans sont des `Column` / `LazyColumn`. Les zones à risque sont celles où un composant est posé en `Box` par-dessus un autre : le lecteur de l'écran de lecture (barre Media3 et boutons maison en surimpression), l'écran du live (vidéo, pastilles, chat, carte du produit épinglé), les Shorts (calque d'actions sur la vidéo), le mini-lecteur posé à la racine au-dessus de la navigation (ADR-024). **À constater au test réel, pas à supposer.**

**Exigence 6, annonces d'état**
- Aucun `liveRegion`. Aujourd'hui TalkBack annonce seul le changement d'état d'un élément **qui a le focus** (c'est le cas du `stateDescription` de « J'aime »). Rien n'est annoncé pour un changement hors du focus : nouveau message du chat du live et de la messagerie, nombre de spectateurs, compteur de réactions.
- Le compteur de spectateurs (`ViewerCountPill`) est un `Text` mis à jour à chaque événement WebSocket (`LiveViewModel.kt`, `viewerCount`) ; la liste du chat du live est une `LazyColumn` calée en bas. Les marquer naïvement en `liveRegion` produirait une annonce à chaque événement, donc la boucle que le lead veut éviter.
- Les erreurs et notices du live passent par un `Toast` (que TalkBack annonce déjà).
- État du bouton S'abonner (`FollowButton`) et de la lecture/pause : à écouter ; la lecture/pause du mini-lecteur a deux libellés distincts (lecture / pause) qui changent avec l'état.

**Exigence 7, grande police**
- Le thème définit trois styles de texte en `sp` (`Type.kt`). Les polices en `sp` suivent le réglage du système. 12 tailles `sp` sont écrites en dur dans les écrans (titre d'accueil 20 sp, texte de la page de lecture 18 sp, émojis de réaction 24 et 28 sp, étiquette d'onglet 10 sp).
- Risques de coupure : `maxLines` utilisé 57 fois et `TextOverflow.Ellipsis` 48 fois (titres tronqués volontairement, ce qui ne se rattrape pas en grande police) ; hauteurs fixes (puces `ChipHeight`, boutons de réaction en 44 dp, ligne « J'aime » à hauteur du parent, étiquettes de la barre d'onglets en 10 sp). **Rien n'a encore été mesuré à 200 %.**

**Exigence 8, lecteur**
- **Existe** : lecture/pause (barre Media3 `PlayerView`, plus un bouton lecture/pause du mini-lecteur), **sous-titres** (bouton dans la surimpression, affiché seulement si la vidéo en a, ADR-017 ; réglage aussi dans le menu du lecteur), **plein écran** (bouton Media3 en bas à droite ; la rotation sert de raccourci ; Retour en sort), barre de progression (Media3).
- **À vérifier au test réel** : les libellés que Media3 fournit lui-même (lecture, barre de progression, plein écran, avance et retour s'ils sont affichés). On ne sait pas encore si la barre de progression se règle au balayage vertical de TalkBack et si les boutons d'avance / retour de 10 s existent. **Je ne les suppose pas** (question D6).
- **Le live n'a pas de contrôles** par choix (`useController = false`, direct ni mis en pause ni avancé, ADR-029) : lecture, pause et avance ne s'y appliquent pas. Question D7.
- **Gestes sans équivalent connu** : le glissement vers le bas qui réduit le lecteur en mini-lecteur et le glissement latéral qui le ferme (ADR-024). Le bouton Retour et la flèche de retour existent ; la croix du mini-lecteur aussi. À confirmer au test.

**Exigence 9, regroupement**
Voir « Ce qui existe déjà » : `clickable` fusionne déjà ses enfants. Les cartes vidéo sont donc probablement lues en un bloc, mais sans phrase maîtrisée (ordre des textes dans le code, durée lue à part de son contexte, badge Premium et miniature floutée sans texte). La carte du live (`LiveCard.kt`) est un `Column` cliquable avec pastille et nombre de spectateurs : probablement un bloc aussi, même remarque. Les lignes de la messagerie, de l'historique, des playlists, des téléchargements et des résultats de recherche sont des éléments cliquables : à entendre. Les éléments non cliquables qui se lisent comme un tout (ligne de commentaire avec « J'aime », « Répondre », compteur ; ligne du chat du live) ne sont pas fusionnés.

## Décision
Rendre l'application utilisable avec TalkBack, par des corrections d'accessibilité sur l'écran existant, **sans nouvelle dépendance, sans nouvel écran, sans changement visuel** autre que les deux ajustements de couleur éventuels de la décision D1.

### Principes
- **Libellés** : une chaîne par libellé dans `strings.xml` (français, jamais « YouTube », « cette application » ni « bientôt », règle du projet). Un libellé dit l'action ou l'état (« J'aime », « Mettre en pause »), jamais la forme (« icône »).
- **Images** : décoratif = `contentDescription = null` **posé volontairement**, marqué d'un commentaire court quand ce n'est pas évident. Informatif = libellé. Une miniature dans un élément cliquable qui porte déjà le titre reste décorative.
- **Regroupement** : toute carte ou ligne qui se lit comme un tout est un seul nœud (`semantics(mergeDescendants = true)` ou `clickable` qui fusionne déjà), avec un libellé composé en une phrase : titre, chaîne, durée, vues, date, mention Premium. Les actions secondaires d'une carte (ouvrir la chaîne) sont exposées en action personnalisée (`customActions`) plutôt qu'en arrêt séparé, **ou** laissées en arrêt séparé : voir D5.
- **Titres** : `heading()` sur les titres d'écran et de section.
- **Rôles** : `Role.Button`, `Switch`, `Tab`, `RadioButton` partout où un élément cliquable n'en a pas.
- **Cibles** : 48 dp minimum, obtenu par `minimumInteractiveComponentSize()` ou une zone de toucher élargie, sans agrandir le visuel.
- **Ordre** : l'ordre de composition est corrigé par `isTraversalGroup` / `traversalIndex` seulement là où le test réel montre un écart.
- **Grande police** : lever les hauteurs fixes qui coupent le texte ; préférer un défilement à une troncature quand le texte porte le sens.

### Stratégie d'annonce (exigence 6)
- **Élément qui a le focus** : `stateDescription` (et `Role.Switch` / `Role.Tab`) pour « J'aime », « Je n'aime pas », abonnement, lecture / pause, sous-titres. TalkBack annonce seul le nouvel état ; pas de `liveRegion` ici, sinon double annonce.
- **Changement sans focus** : une région vivante **polie** (`LiveRegionMode.Polite`, qui n'interrompt pas la parole en cours), sur un nœud dédié et invisible à l'écran, dont le texte est mis à jour par un ViewModel.
- **Compteur de spectateurs** : **pas** de `liveRegion` sur la pastille. Le texte visible reste lisible au balayage, avec la valeur à jour. Annonce regroupée sur le nœud dédié, au plus **toutes les N secondes** et seulement si la valeur a changé de façon notable (valeurs de N et du seuil : D3).
- **Chat du live** : pas de `liveRegion` sur la liste. Le nœud dédié annonce un **résumé regroupé** (« 3 nouveaux messages ») au plus toutes les N secondes, et le dernier message seul quand le rythme est lent. Les messages écrits par l'utilisateur lui-même ne sont pas annoncés. L'annonce d'achat du live (message serveur) est toujours annoncée, car rare et porteuse de sens.
- **Messagerie (ADR-025)** : un message reçu dans la conversation ouverte est annoncé en polie, un à la fois ; en cas de rafale, même regroupement que le live.
- **Interdits** : annonce à chaque tick du lecteur, à chaque événement WebSocket brut, annonce d'un état qu'on vient soi-même de provoquer si le focus le dit déjà.

### Plan priorisé, une étape = un commit
L'ordre suit celui du test réel (accueil, lecteur, live d'abord) puis la valeur pour le temps restant. Le plan compte six étapes de code, plus l'étape 0 de constat.

| Étape | Contenu | Exigences | Si le temps manque |
|---|---|---|---|
| **0** | Protocole TalkBack et relevé réel, écran par écran : accueil, lecteur, live d'abord. Aucun code. Le relevé fixe la liste des défauts réels. | toutes | ne saute jamais |
| **1** | Libellés, décoratifs explicites, regroupement et rôles sur l'accueil, le lecteur (surimpression, menu, mini-lecteur) et le live. Tri des 55 `contentDescription = null` sur ces trois écrans. Colonne d'actions des Shorts. Cartes vidéo et cartes du live en une seule phrase. | 1, 2, 8, 9 | ne saute jamais (cœur) |
| **2** | Contrastes, uniquement là où la couleur porte du texte (D1 validé). | 4 | saute après les étapes 3 à 5 |
| **3** | Annonces d'état : `stateDescription` (abonnement, lecture, sous-titres), région vivante polie et regroupée pour le chat et les spectateurs du live, puis messagerie. | 6 | saute en 4e |
| **4** | Cibles de 48 dp : avatar de la carte vidéo, bouton de son du bandeau, réactions du live, `IconButton` du commentaire, colonne des Shorts, puces de l'accueil. | 3 | saute en 3e |
| **5** | Grande police : lever les hauteurs fixes qui coupent (puces, réactions, étiquettes d'onglets) ; test à 200 % sur l'accueil, le lecteur, le live. | 7 | saute en 2e |
| **6** | Reste des écrans : ordre de parcours corrigé là où le test l'a montré, titres `heading()`, rôles manquants, tri des `contentDescription = null` restants, libellés des écrans hors accueil / lecteur / live (Vous, paramètres, paywall, recherche, chaîne, messagerie, création). | 1, 5, 9 | saute en 1er, en partant des écrans les moins visités |

Les tests Compose de sémantique (voir plus bas) se glissent dans les étapes 1 et 3, en petit nombre.
**Ordre du plan validé : étape 0, étape 1, contrastes, annonces d'état, cibles 48 dp, grande police, puis le reste des écrans.** **Ordre de sacrifice si le temps manque : le reste des écrans (6), puis la grande police (5), puis les cibles (4), puis les annonces (3).** L'étape 0 et le cœur de l'étape 1 ne sautent jamais. Chaque étape est livrable seule, avec un `assembleDebug` qui passe avant le commit (règle du projet).

### Vérification
**Test réel avec TalkBack : exigence du lead, à appliquer telle quelle.** Le lead veut un test réel avec TalkBack activé sur l'émulateur, écran par écran, en commençant par l'accueil, le lecteur et le live, et un rapport de ce que TalkBack **dit vraiment**, pas de ce que le code prévoit. **Les tests Compose de sémantique ne remplacent pas ce test.**

Protocole :
1. **Émulateur** : préférer une image avec Google Play (le service « Android Accessibility Suite », qui contient TalkBack, y est le plus souvent préinstallé ou installable depuis le Play Store). Sur une image sans Play, TalkBack n'est pas fourni : l'installer depuis son APK officiel (`adb install`) est possible mais à vérifier. **Je ne sais pas laquelle des deux situations est celle de l'émulateur du projet : à constater.**
2. **Activation** : Paramètres, Accessibilité, TalkBack, ou en ligne de commande `adb shell settings put secure enabled_accessibility_services <service TalkBack>` suivi de `adb shell settings put secure accessibility_enabled 1`. Faire le didacticiel de TalkBack une fois.
3. **Voir ce que TalkBack dit** sans dépendre du son de l'émulateur : activer dans les paramètres de TalkBack, Options pour les développeurs, l'affichage du texte prononcé (« Afficher la sortie vocale »), et noter ce texte ; le son reste le contrôle de référence si l'hôte le restitue.
4. **Gestes** : balayage droite / gauche (élément suivant / précédent), double toucher (activer), balayage vers le haut puis vers le bas (actions du menu de lecture), exploration au toucher. Sur l'émulateur, les touches du clavier de l'hôte peuvent remplacer les gestes si besoin.
5. **Grande police** : `adb shell settings put system font_scale 2.0` (200 %), relancer l'écran, refaire le relevé de l'écran.
6. **Ordre des écrans** : **accueil, lecteur, live** en premier ; puis Shorts, recherche, chaîne, messagerie, Vous, paramètres, création, paywall, onboarding.
7. **Grille de relevé, une ligne par élément focalisable d'un écran** :

| Écran | N° d'arrêt | Ce que TalkBack dit (mot à mot) | Rôle annoncé | Attendu | Écart (libellé absent, ordre, double annonce, cible trop petite, texte coupé à 200 %) | Gravité (bloquant / gênant / mineur) | Étape qui le corrige |
|---|---|---|---|---|---|---|---|

Une ligne en plus par **changement d'état** testé (j'aime, abonnement, pause, nouveau message du live, spectateurs) : « annoncé : oui / non, une fois / en boucle ». Le rapport est écrit par la personne qui a fait le test, d'après ce qu'elle a entendu.

**Compléments, sans dépendance nouvelle :**
- **Tests Compose de sémantique** (`createComposeRule`, `onNodeWithContentDescription`, `assertHasClickAction`, `assertIsOn`, taille minimale par `getBoundsInRoot`) : filet de non-régression, en petit nombre, sur quelques composants (carte vidéo, boutons du lecteur, bouton de réaction). Ils exigent de créer le dossier `androidTest` (n'existe pas) et un appareil ; les bibliothèques sont déjà déclarées.
- **Accessibility Scanner** de Google : application du Play Store, hors dépôt, donc sans dépendance, **si** l'émulateur a le Play Store (D8). Il détecte cibles trop petites et contrastes ; il ne dit pas ce que TalkBack lit.
- Contrôle d'un script de recherche de texte sur le code (comptages ci-dessus) en fin de travail pour suivre les `contentDescription = null` restants.

### Décisions validées
Validées par le lead développeur le 5 novembre 2026.

- **D1 contrastes : Retenu avec la décision du lead.** On corrige uniquement là où la couleur porte du texte. Pastille « EN DIRECT » : assombrir le rouge de **fond** juste assez pour atteindre 4,5:1 avec du blanc. Rouge sur gris foncé : l'éclaircir jusqu'à 4,5:1 pour ce seul usage (texte). Le rouge de la marque n'est pas touché là où il ne porte pas de texte.
- **D2 : Retenu** comme recommandé (`contentDescription = null` volontaire, commentaire si pas évident).
- **D3 : Retenu** comme recommandé (valeurs de départ, réglées au test réel).
- **D4 : Retenu** comme recommandé (résumé « N nouveaux messages », liste lisible au balayage).
- **D5 : Retenu** comme recommandé (`customActions` pour les actions secondaires d'une carte).
- **D6 : décision du lead.** Aucun bouton d'avance de 10 s, aucune fonctionnalité nouvelle. La barre de progression doit être réglable avec TalkBack par les gestes haut/bas ; si elle ne répond pas, lui ajouter les actions d'incrément et de décrément (`semantics` `setProgress` / actions), rien d'autre.
- **D7 : décision du lead.** Le live reste sans contrôles de lecture ; sa zone vidéo s'annonce seulement comme un direct (chaîne et titre) et non comme un lecteur manipulable.
- **D8 : décision du lead.** Pas d'Accessibility Scanner.
- **D9 : Retenu** comme recommandé (rien ajouté, constat au test réel).
- **D10 : Retenu** comme recommandé (200 % sur l'accueil, le lecteur et le live).
- **D11 : Retenu** comme recommandé (3 à 5 tests Compose).
- **Les 55 `contentDescription = null` : décision du lead.** Tous sont triés sur l'accueil, le lecteur et le live (chacun est soit décoratif assumé, soit un défaut corrigé) ; les autres écrans en dernier. La colonne d'actions des Shorts (« 1,2 k, bouton ») est un vrai défaut, corrigé à l'étape 1.
- **Ordre du plan et ordre de sacrifice : décision du lead**, voir « Plan priorisé » ci-dessus, qui remplace l'ordre initial.
- **TalkBack : décision du lead.** Activation : Paramètres, Accessibilité, TalkBack. S'il est absent de l'image, installer « Android Accessibility Suite » depuis le Play Store. Sans Play Store sur l'émulateur, le dire dans le rapport et tester ce qui peut l'être avec des tests de sémantique Compose, en disant clairement que le test réel n'a pas eu lieu.

### Propositions initiales (historique, remplacées par les décisions validées ci-dessus)
Les numéros d'étapes cités dans ces propositions suivent l'ancien plan.

- **D1 : le blanc sur rouge (4,00:1) et le rouge sur gris (3,74:1) échouent au seuil de 4,5:1.** Options : (a) assombrir le rouge **uniquement** des fonds qui portent du texte, la marque ailleurs inchangée ; (b) passer le texte concerné en gras de 14 sp ou plus, qui compte comme gros texte (seuil de 3:1) ; (c) ne rien changer et l'assumer. **Recommandation : (b) là où c'est déjà du gras de taille suffisante, sinon (a)** ; la valeur exacte du rouge assombri est à calculer à l'étape 5. **À trancher.**
- **D2 : convention des images.** « `contentDescription = null` posé volontairement, avec commentaire quand ce n'est pas évident » plutôt qu'un modificateur dédié. **Recommandation : oui**, c'est plus léger et c'est le comportement de Compose.
- **D3 : fréquence et seuils des annonces du live.** Proposition de départ : le compteur de spectateurs annoncé au plus une fois toutes les 30 secondes et seulement si la valeur a changé ; le chat résumé au plus une fois toutes les 5 secondes en rafale. **Ces chiffres sont des points de départ, pas des mesures** ; ils se règlent au test réel. **À trancher** (ou à laisser au test).
- **D4 : le chat du live dans la lecture TalkBack.** Annoncer le résumé « N nouveaux messages » et laisser la liste lisible au balayage, plutôt qu'annoncer chaque message. **Recommandation : oui.** Variante : ne rien annoncer et laisser l'utilisateur parcourir la liste. **À trancher.**
- **D5 : actions secondaires d'une carte (ouvrir la chaîne).** Soit `customActions` (un arrêt par carte, l'action s'ouvre au menu d'actions de TalkBack), soit arrêt séparé. **Recommandation : `customActions`**, conforme à l'exigence 9 (un seul arrêt par carte) ; l'arrêt séparé est plus découvrable mais ramène plusieurs arrêts par carte. **À trancher.**
- **D6 : « avance » du lecteur.** Je n'ai pas trouvé de bouton d'avance ou de retour de 10 secondes dans le code (la barre Media3 est utilisée telle quelle). **La fonctionnalité existe-t-elle ou doit-elle l'être ?** Je ne l'ajoute pas sans demande (règle du projet). **Recommandation :** s'appuyer sur la barre de progression de Media3 (réglable par TalkBack) et constater au test réel si elle suffit ; n'ajouter des boutons qu'à ta demande explicite. **Question ouverte.**
- **D7 : le live sans contrôle de lecture.** Le direct n'a ni pause ni barre (ADR-029). L'exigence 8 (lecture, pause, avance) ne s'y applique donc pas. **Recommandation : l'assumer**, et décrire la vue vidéo (« Direct en cours ») ; ne pas ajouter de pause. **À confirmer.**
- **D8 : Accessibility Scanner.** Utilisable seulement si l'émulateur a le Play Store ; sinon on s'en passe. **Recommandation : optionnel**, le test TalkBack réel est la référence.
- **D9 : gestes du mini-lecteur (glisser pour réduire ou fermer).** Équivalents déjà présents : Retour, flèche de retour, croix. **Recommandation :** ne rien ajouter, constater au test réel ; ajouter des `customActions` seulement si un écart apparaît.
- **D10 : taille de police maximale à supporter.** **Recommandation :** 200 %, le maximum du système, sur l'accueil, le lecteur et le live ; les autres écrans au mieux (défilement accepté, troncature des titres acceptée si le titre complet est accessible ailleurs). **À trancher.**
- **D11 : périmètre des tests Compose.** **Recommandation :** 3 à 5 tests sur la carte vidéo, les boutons du lecteur et la réaction, ajoutés pendant les étapes 1 et 2, sans nouvelle dépendance. Sautent avant les étapes 5 et 4. **À trancher.**

## Alternatives
- **Tout corriger en une fois, sans test réel d'abord** : avantage, aucune perte de temps au départ. Inconvénient : on corrigerait ce que le code laisse supposer, pas ce que TalkBack dit ; les défauts réels (ordre, double annonce) échappent. Écarté : c'est l'exigence du lead.
- **Se limiter aux tests Compose de sémantique** : avantage, automatisable, relançable. Inconvénient : ils vérifient la présence d'un libellé, pas la qualité de ce qui est lu ni l'ordre ni la prononciation. Écarté comme seule méthode (le lead a exclu qu'ils remplacent le test réel) ; gardés en filet.
- **Ajouter une bibliothèque d'accessibilité ou de tests (Espresso accessibility, Accessibility Test Framework)** : avantage, contrôles automatiques de cibles et de contrastes. Inconvénient : nouvelle dépendance non demandée. Écarté (règle du projet).
- **`liveRegion` sur le compteur de spectateurs et la liste du chat** : avantage, une seule ligne de code. Inconvénient : annonce à chaque événement, donc répétée en boucle. Écarté au profit d'une annonce regroupée.
- **Un mode « accessible » séparé** : avantage, aucune régression pour les voyants. Inconvénient : un second parcours à maintenir et à tester, contraire à « pas de nouveau écran ». Écarté.
- **Changer la palette pour des contrastes plus larges** : avantage, tous les seuils tenus. Inconvénient : touche l'identité visuelle, non demandée. Écarté au profit d'ajustements ciblés (D1).

## Conséquences
### Positives
- Un parcours complet utilisable sans voir : accueil, lecture, live, messagerie.
- Les écarts sont mesurés par un test réel, pas supposés.
- Aucune dépendance, aucun écran ni aucune fonctionnalité ajoutés.
- Le plan par étapes permet de livrer partiellement : l'accueil, le lecteur et le live sont traités en premier.
- La stratégie d'annonce évite la répétition en boucle sur les deux flux les plus bavards (spectateurs, chat).
- Les libellés et regroupements sont aussi utiles aux outils de test d'interface.

### Négatives
- Le test réel avec TalkBack coûte du temps (installation éventuelle de TalkBack, relevé écran par écran) dans un délai d'un jour.
- Tous les écrans ne seront probablement pas traités avant le rendu ; l'ordre de sacrifice laisse des écrans moins visités en l'état.
- Les annonces regroupées ont des seuils choisis à l'estime (D3) ; ils pourront être mal réglés au premier essai.
- Le blanc sur rouge et le rouge sur gris peuvent rester sous le seuil si le lead préserve la couleur de marque (D1).
- Le regroupement en un seul nœud retire des arrêts séparés (avatar, nom de la chaîne) : l'accès à la chaîne passe par une action personnalisée moins découvrable (D5).
- Les 55 `contentDescription = null` ne sont pas tous triés : certains défauts ne seront connus qu'au relevé.
- Aucun test automatique n'existe aujourd'hui : le premier dossier `androidTest` est à créer, avec un appareil requis pour les lancer.

## Liens
- [ADR-001](ADR-001-android-natif.md) : application native Android, Jetpack Compose.
- [ADR-017](ADR-017-hls-multi-qualites-sous-titres.md) : sous-titres du lecteur.
- [ADR-024](ADR-024-mini-lecteur.md) : lecteur unique et mini-lecteur (glissements sans équivalent connu).
- [ADR-025](ADR-025-messagerie-utilisateurs.md) : messagerie, nouveau message à annoncer.
- [ADR-028](ADR-028-onboarding-parcours-etapes.md) : onboarding, réglage « réduire les animations » déjà lu et réutilisé.
- [ADR-029](ADR-029-live-shopping.md) : live, chat, compteur de spectateurs, réactions, produit épinglé ; l'étape 4 de modération y est sautée pour laisser le temps à ce challenge.
