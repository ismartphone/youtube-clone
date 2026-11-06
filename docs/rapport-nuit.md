# Rapport de nuit — 5 au 6 novembre 2026

Tout est poussé sur `main` : rien n'est resté en local. Dernière vérification faite à la fin :
`assembleDebug` passe, les 168 tests du backend passent, l'arbre git est propre.

## 1. Ce qui a été fait et poussé

**Onboarding en 10 étapes (ADR-028)** — `2fefe58`, `1a10220`, `087385f`, `7561c01` ; réglage « Format préféré » dans
Paramètres : `f183d67`. Parcours complet testé sur émulateur : **63 s** en tapant au plus vite.

**Live shopping (ADR-029)**
- Étape 1, démarrer et regarder (chat, compteur, battement 30 s) : `653f155`, `55c4571`, `c478b9c`, `88ff358`, `cfb64a2`, `b1727e0`, `9bd5f54`.
- Étape 2, réactions : `4ecb377`, `d10238f`.
- Étape 3, produit épinglé, achat Stripe test, annonce dans le chat, pages Produits et Lives du back-office : `42d1672`, `498dfc2`, `402b110`, `5feb14c`, `ec2291b`.
- Messagerie retestée après la modification de la passerelle : message envoyé et reçu en temps réel, vidéo partagée reçue, commentaires en direct reçus.
- Back-office vu dans un navigateur : ajout d'un produit avec image, modification, suppression, « Terminer » sur un live, achat affiché.

**Accessibilité TalkBack (ADR-030)** — `45f5075`, `4ce04e2`, `fb759ba`, `25e0dfe`, `d5021ae` (libellés, regroupement, rôles) ;
`624f510` (contrastes) ; `e989cdc` (annonces d'état) ; `d325c59` (cibles 48 dp) ; `5189074` (grande police) ;
`8273f44`, `e6d737d`, `b395242`, `ac04294` (reste des écrans).

**Documentation :** ADR-028, 029 et 030 acceptés (`5cbbc4c`, `0bba792`, `6222284`), ADR-015, 020 et 029 amendés, CLAUDE.md à jour (`5e4a19b`).

## 2. Ce qui n'a pas été fait, et pourquoi

- **Modération du live (étape 4, bonus) : sautée.** L'accessibilité n'est pas entièrement testée (voir §5), donc la condition posée n'était pas remplie. `LIVE_RESTRICTION` n'existe pas.
- **Sons et vibrations de l'onboarding :** jamais entendus ni sentis, l'émulateur n'a pas d'audio. À vérifier sur le téléphone.
- **Non testés :** permission de notifications refusée (étape 8), inscription et connexion réelles dans le parcours, un achat Premium, le mode silencieux, le repli de paiement simulé du live.
- **Accessibilité non terminée :** voir §5 (ordre au balayage, annonces entendues, barre de progression).

## 3. Défauts connus visibles, et comment les éviter en démonstration

| Défaut | Éviter |
|---|---|
| Image verte et rose en haut du fil (décodeur de l'émulateur, bandeau sponsorisé) | Faire défiler le fil. Le téléphone ne devrait pas l'avoir. |
| Fil vide (cadres gris) : l'émulateur ne joint pas `10.0.2.2:3000` | `adb reverse tcp:3000 tcp:3000` et réglage « Adresse du serveur » (voir §4). |
| Page Lives du back-office : 5 anciens lives de test, dont « Seconde d�mo » (titre corrompu) | Les supprimer ou les renommer avant de présenter. |
| Messagerie : anciens messages de test (« Second test », « Test messagerie après le battement ») entre Studio Libre et Camille | Ne pas ouvrir cette conversation, ou en créer une autre. |
| Mini-lecteur vide après « Régler l'adresse du serveur » depuis une erreur de chargement | Ne pas y toucher. |
| Texte coupé à 200 % de police : nom de l'annonceur d'avant-lecture, « Visiter le … » du bandeau | Démontrer à 100 % ou 130 %. |
| Bouton rouge de l'onboarding à 4,00:1 (seuil 4,5:1) | Volontairement non touché (consigne). |
| Réactions du live : plafond de 12 émojis non vu, chevauchement en rafale, refus du serveur ignoré sans message | Acceptés. |
| Machine saturée en mémoire : l'émulateur affichait « System UI isn't responding » | Un seul émulateur, 1536 Mo, fermer les autres applications. |

## 4. Démarrer, dans l'ordre

```
# 1. Docker Desktop, puis, à la racine du dépôt :
docker compose up -d

# 2. Backend (port 3000). Un `npm run build` réécrit dist : arrêter l'ancien backend avant.
cd backend
npm run build
npm run start:prod

# 3. Back-office (port 3001). API_URL est lue à la build.
cd backoffice
npm run build
npm start

# 4. Émulateur (un seul) puis redirection du port
emulator -avd Pixel_8 -memory 1536
adb reverse tcp:3000 tcp:3000

# 5. Application (PowerShell). Avec adb reverse, compiler avec l'adresse locale :
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd android
.\gradlew.bat installDebug -PapiBaseUrl=http://127.0.0.1:3000/
```

Sur téléphone réel : version debug, Paramètres → « Adresse du serveur » → `http://<IP du PC>:3000/` → Tester la connexion → Enregistrer.
Comptes de démonstration : voir `backend/prisma`. Studio Libre a 53 abonnés et 5 produits.
Si le backend du port 3000 tournait déjà avant le build, **le redémarrer** : `dist` a été réécrit pendant la nuit.

## 5. Ce que TalkBack dit vraiment (sortie vocale relevée sur le Pixel_8)

Limite à lire d'abord : les gestes de balayage envoyés par `adb` ne sont pas reconnus par TalkBack. La navigation s'est faite
au clavier (TAB), qui ne parcourt que les éléments interactifs. **L'ordre complet au balayage n'a pas été rejoué.**

- **Accueil, carte vidéo (un seul arrêt)** : « Voici l'endroit le plus profond de la planète (et ce qu'on y a trouvé), chaîne Edward wkl, 8 vues, il y a 1 semaine, durée 13 minutes et 14 secondes ». Avant : « Unlabelled ».
- **Accueil, carte du live** : « En direct : Démo accessibilité, chaîne Studio Libre, 0 spectateurs. In list. 15 items ».
- **Shorts** : « J'aime, 1. Button », « Je n'aime pas. Button », « Commentaires, 0. Button », « Partager. Button ». Avant : « 1,2 k, bouton ».
- **Live, zone vidéo** : « Direct : Démo accessibilité, chaîne Studio Libre » (lu dans l'arbre d'accessibilité, pas à la voix).
- **Live, chat** : « Camille Spectatrice : Un seul message de test » ; une rafale de 4 messages en 4,4 s : « 2 nouveaux messages ».
- **Page de chaîne** : « I Spent $5000 on Nintendo Switch 2 Games So You Don't Have To, chaîne BeatEmUps, 10 vues, il y a 3 jours, durée 27 minutes et 38 secondes », puis « Non abonné. S'abonner. Button ».
- **Barre d'onglets** : « Accueil. Tab. 1 of 5. In list. 5 items » jusqu'à « Vous. Tab. 5 of 5 ».
- **Paramètres** : « Selected. Plutôt des vidéos. Radio button. 1 of 2 », « On. Lecture automatique. … Switch ».
- **Messagerie** : « Studio Libre. Vous : Second test. il y a 3 heures. Button ».

**Pas entendu :** annonce du nombre de spectateurs, de l'abonnement et de la pause (couverts seulement par des tests unitaires) ;
barre de progression du lecteur au geste haut/bas ; libellé « Show player controls » (resté en anglais, Media3) ; l'écran de lecture
sous la vidéo (lu par l'arbre, pas à la voix). Écrans compilés mais non ouverts : playlists, téléchargements, feuilles de création, aide, commentaires.

**Chiffres :** contrastes blanc sur rouge 4,00 → 4,53 ; rouge sur gris 3,74 → 4,57. Zéro cible sous 48 dp mesurée sur accueil, lecteur, live et Shorts.
`contentDescription = null` : 55, tous justifiés par un commentaire sauf 3 (onboarding et paywall, exclus par la consigne).

## 6. Lectures raisonnables prises pendant la nuit

- Le rouge d'erreur du thème passe à `#FF4D4D` pour toute l'application ; les bulles de messagerie envoyées et la pastille de non-lus ont aussi changé de rouge (couleur seulement).
- L'onglet d'arrivée suit le format préféré à chaque ouverture ; le choix est modifiable dans Paramètres.
- La boîte de dialogue de choix du produit à épingler remplace la feuille prévue (écart mineur, noté dans l'ADR-029).
- Quand le lecteur est déployé, toute la navigation en dessous est masquée à TalkBack.
- Les commentaires du lecteur ne sont pas regroupés en une phrase : leurs boutons J'aime et Répondre doivent rester atteignables.
