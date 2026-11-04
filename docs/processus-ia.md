# Encadrement de l'IA

Ce projet a été développé avec Claude Code. Ce document décrit comment l'IA a été encadrée. Il
présente ensuite les cas où son travail a été repris, retrouvés dans les ADR et l'historique Git,
puis les cas où elle s'est arrêtée d'elle-même, rapportés depuis les conversations.

Principe général : **l'IA propose et rédige, l'humain décide et valide.**

## 1. Le `CLAUDE.md` : les règles du projet

Le [`CLAUDE.md`](../CLAUDE.md), à la racine, est lu par Claude Code au début de chaque conversation.
Il est versionné dès le premier commit (15 octobre, 9 h 24), avant tout code applicatif. Il fixe :

- **les règles de conduite** : répondre en français ; lire les ADR concernés avant de proposer du
  code, respecter les ADR acceptés et signaler toute contradiction ; ne jamais ajouter de
  fonctionnalité, de dépendance ou d'écran non demandé ; poser la question en cas de doute au lieu
  de deviner ; pas d'hybride ;
- **la stack et l'architecture** de chacun des trois projets, pour que l'IA ne choisisse pas une
  bibliothèque ou une organisation à sa place ;
- **les pièges du modèle de données** : ne jamais confondre `FOLLOW` (abonnement gratuit) et
  `PREMIUM_SUBSCRIPTION` (abonnement payant) ; l'accès aux vidéos Premium toujours vérifié côté
  serveur ;
- **le périmètre** : un socle prioritaire, une suite, et une liste explicite de ce qui est hors
  périmètre (iOS, live, playlists, notifications…) ;
- **les conventions** : langue du code et des commentaires, nommage, un commit par unité de travail,
  et l'obligation de vérifier que le projet compile avant d'annoncer qu'une tâche est terminée.

Le `CLAUDE.md` a lui-même évolué deux fois, à chaque fois pour corriger un écart constaté
(voir les cas 1 et 2 plus bas).

## 2. Les ADR, écrits avant le code

Les décisions d'architecture sont consignées dans [`docs/adr/`](adr/README.md), au format ADR
(contexte, décision, alternatives, conséquences). Le `CLAUDE.md` impose de les lire avant de
proposer du code : ils servent de garde-fou pour que l'IA ne remette pas en cause une décision
prise.

Ce que montre l'historique :

| ADR | Rédigé | Code correspondant | Accepté |
|---|---|---|---|
| ADR-001 à ADR-006 | 15 oct., 9 h 24 | ensuite | déjà acceptés au premier commit |
| ADR-007 (Premium) | 15 oct., 9 h 24 | 3 nov. | 15 oct., 11 h 44 |
| ADR-008 (JWT) | 15 oct., 11 h 44 | 11 h 24 – 11 h 38 | 12 h 02 (index), 14 h 09 (fichier) |
| ADR-009 (tokens du back-office) | 15 oct., 14 h 09 | 14 h 07 | 15 h 48 |
| ADR-010 (WebSocket) | 15 oct., 15 h 48 | 15 h 47 | 16 h 28 |
| ADR-011 (FFmpeg, chemins) | 16 oct., 14 h 42, avec le code | 14 h 42 – 14 h 43 | 14 h 48, après clarification |
| ADR-012 (paiement simulé) | 3 nov., 9 h 11, avec le code | 9 h 11 | dans le même commit |
| ADR-013 (données personnelles) | 3 nov., 9 h 11 | 10 h 48 | dans le même commit que sa rédaction |

Les décisions fondatrices (ADR-001 à ADR-007) ont bien été écrites avant le code, de même que
l'ADR-013, livré avant le code PostHog qu'il encadre. Pour les ADR-008 à ADR-012, le fichier est
committé avec le code ou quelques minutes après : l'historique ne permet pas de savoir si la
décision avait été arrêtée avant, seulement que sa trace écrite est arrivée avec la fonctionnalité.
Pour les ADR-012 et ADR-013, les conversations situent la décision au 23 octobre, soit dix jours
avant la rédaction du fichier (voir les arrêts 1 et 2). L'écart entre la décision et sa rédaction
n'est donc pas toujours un écart entre la décision et le code.

**Analyse.** La rédaction avant le code a tenu sur les sept premiers ADR, puis elle a glissé quand
le rythme s'est accéléré : le 15 octobre, l'authentification, le back-office et le temps réel ont
été livrés en un peu plus de quatre heures, et chaque ADR a suivi son code au lieu de le précéder. C'est ce
relâchement qui a multiplié les incohérences de statut entre les fichiers et l'index. Le premier
écart, sur l'ADR-007, date certes du commit initial, mais il s'est répété sur l'ADR-008 en plein
glissement. D'où la règle ajoutée au `CLAUDE.md` le jour même (cas 1). Le processus n'a pas été
parfait. Il s'est corrigé en observant ses propres défauts : un écart constaté devient une règle
écrite, et la règle tient ensuite.

## 3. Le sous-agent `adr-architecte`

Le sous-agent [`adr-architecte`](../.claude/agents/adr-architecte.md) est le seul autorisé à rédiger
les ADR. Ses règles :

- partir uniquement de ce que le lead développeur explique, sans inventer de contrainte,
  d'alternative ni de justification ;
- reformuler la décision, poser des questions si le contexte manque, et attendre les réponses ;
- consulter les ADR existants pour éviter doublons et contradictions ;
- toujours rédiger au statut **Proposé** : il ne décide jamais à la place du lead développeur ;
- numéroter à la suite, sans jamais réutiliser un numéro ;
- pendant le développement, conclure chaque vérification par « Conforme aux ADR existants »,
  « Contradiction avec ADR-XXX » ou « Décision non documentée — ADR recommandé ».

Son rôle tient en une phrase de sa définition : « L'IA documente et contrôle les décisions. Le lead
développeur reste responsable de leur validation. »

## 4. Une fonctionnalité par conversation

Chaque fonctionnalité est traitée dans sa propre conversation avec Claude Code : le contexte reste
court et centré sur une seule tâche, et la conversation se termine par un commit qui la livre.

Les conversations elles-mêmes ne sont pas versionnées. L'historique en garde la trace indirecte :
un commit par unité de travail, et, quand une fonctionnalité touche deux projets, deux commits
livrés à la même minute (backend puis Android) — chaînes et abonnements à 13 h 22, administration
à 14 h 07, temps réel à 15 h 47, catégories et premier lancement à 16 h 15 le 15 octobre.

## 5. La validation humaine

- **Seul un humain passe un ADR en « Accepté ».** C'est écrit dans le `CLAUDE.md`, dans la définition
  du sous-agent et en tête de l'index des ADR. Le commit « Accepte l'ADR-008 » (15 octobre, 12 h 02)
  ne porte d'ailleurs pas la mention de co-auteur Claude.
- **Traçabilité des commits.** Les commits produits avec l'IA se terminent par une ligne
  `Co-Authored-By: Claude`. Sept commits sur quarante-six ne la portent pas : la création du projet
  Android, le fil d'accueil, l'écran de lecture, l'onglet Shorts, l'authentification côté backend,
  l'acceptation de l'ADR-008 et l'ajout de l'offre Premium.
- **Compilation vérifiée** avant d'annoncer qu'une tâche est terminée (`gradlew.bat assembleDebug`,
  `npm run build`).

## 6. Les cas où l'IA a été reprise

### Cas 1 — Statut des ADR incohérent : une règle ajoutée au `CLAUDE.md`

Dès le premier commit, l'index annonçait l'ADR-007 « Accepté » alors que son fichier disait
« Proposé ». L'écart est corrigé à 11 h 44, puis réapparaît : l'ADR-008 est accepté dans l'index à
12 h 02, mais son fichier ne l'est qu'à 14 h 09.

**Reprise :** le 15 octobre à 16 h 15, le `CLAUDE.md` reçoit une règle explicite — passer un ADR en
Accepté signifie modifier son fichier **et** l'index, qui doivent toujours concorder. Les
acceptations suivantes touchent bien les deux fichiers dans le même commit.

### Cas 2 — ADR-011 : contradictions repérées avant l'acceptation

Proposé avec le code FFmpeg, l'ADR-011 prévoyait un réencodage des vidéos de démonstration, alors
que l'ADR-002, accepté, annonçait un MP4 « sans étape de transcodage ». La commande `seed:videos`
pouvait aussi passer pour une deuxième entrée au catalogue, contraire à l'ADR-005.

**Reprise :** l'ADR n'est pas accepté en l'état. Six minutes plus tard, le commit « Clarifie le
périmètre du transcodage et accepte l'ADR-011 » précise l'ADR-002 (« sans transcodage à
l'exécution »), ajoute à l'ADR-011 deux paragraphes expliquant pourquoi ni l'ADR-002 ni l'ADR-005
ne sont contredits, et ajuste la règle du `CLAUDE.md` sur le modèle de données (« l'URL ou le chemin
relatif des fichiers »).

### Cas 3 — ADR-010 : un écart signalé au lieu d'être passé sous silence

L'implémentation du temps réel publie les commentaires par l'API REST, alors que l'ADR-004 prévoyait
de les envoyer sur la connexion WebSocket.

**Reprise :** l'écart est documenté comme volontaire dans l'ADR-010 (décision, conséquences et liens), avec sa
conséquence (il affaiblit l'argument de l'ADR-004 contre les Server-Sent Events). L'ADR-004, accepté,
n'est pas réécrit.

### Cas 4 — ADR-007 : une décision acceptée qui ne tenait pas

L'ADR-007, accepté le 15 octobre, prévoyait de simuler le paiement avec le mode test de Google Play
Billing. Au moment de l'implémenter, ce mode s'avère impossible sans application déposée sur la Play
Console, compte développeur payant, produit et testeurs configurés. L'agent s'est arrêté pour
demander un arbitrage (voir l'arrêt 1 plus bas).

**Reprise :** au lieu de contourner la décision dans le code, un nouvel ADR, l'ADR-012, remplace la
seule partie paiement de l'ADR-007. L'ADR-007 est conservé, avec le statut « Accepté, remplacé en
partie par l'ADR-012 (paiement) », et l'index est mis à jour en même temps.

### Cas 5 — ADR-006 : des mesures qui n'étaient plus anonymes

L'ADR-006 affirmait que les événements PostHog seraient anonymes et que l'utilisateur pourrait
refuser les mesures depuis l'écran d'accueil. Deux choses ne tenaient plus : associer les
événements au compte les rend pseudonymes, et l'écran d'accueil, refait le 19 octobre, ne contient
plus de choix de refus. L'agent s'est arrêté pour demander comment trancher (voir l'arrêt 2 plus
bas).

**Reprise :** avant de brancher PostHog, l'ADR-013 remplace la section RGPD de l'ADR-006 :
identification par le seul UUID du compte, jamais l'email ni le nom, et interrupteur de refus dans
Paramètres. Il consigne aussi, comme conséquence négative, que le partage activé par défaut n'est
pas un consentement préalable et doit être justifié à l'oral. L'écart sur l'écran d'accueil, lui,
n'a été soulevé que quatre jours après sa refonte, le 23 octobre.

### Cas 6 — Un bug introduit par la lecture en arrière-plan

En Premium, l'écran de lecture ne se mettait plus en pause en passant au second plan : en ouvrant
une autre vidéo par-dessus, la précédente continuait de jouer derrière.

**Reprise :** corrigé le 3 novembre (« Corrige la lecture en double après l'ouverture d'une autre
vidéo en Premium ») : la pause n'est plus évitée que lorsque l'application elle-même passe en
arrière-plan.

### Cas 7 — Les écrans repris d'après les maquettes

Les premiers écrans Android, livrés le 15 octobre, ont été repris un par un après l'ajout de
captures de référence : écran d'accueil et écran de lecture le 16 octobre, puis onglet Vous, onglet
Shorts, page de chaîne, connexion, inscription et onboarding le 19 octobre. Les six commits
concernés commencent par « Reprend ».

Ces captures viennent de Mobbin et ne doivent pas être redistribuées : elles restent sur le poste,
exclues du dépôt, et seul leur index est versionné.

### Cas 8 — Deux sessions sur les mêmes ADR

Le 3 novembre, deux sessions travaillent en parallèle sur les ADR. La première fait un audit
d'ensemble et ses corrections sont rédigées par `adr-architecte`. La seconde rédige l'ADR-015. À
13 h 16, la seconde commite ses propres corrections des ADR-002, 005, 007, 011 et de l'index. Les
corrections de l'audit, qui portent sur les mêmes fichiers, ne sont pas encore commitées.

Une relecture du diff montre qu'aucune modification n'en a écrasé une autre, puisque chaque session
n'avait remplacé que ses propres phrases. Mais le sens s'est croisé :
- dans l'ADR-002, la phrase de l'audit « Les fichiers sont déposés dans le stockage par le seed »
  devient fausse à côté de la phrase de l'ADR-015 sur l'envoi depuis le mobile ;
- l'audit a retouché les liens de l'ADR-005, que l'autre session venait de déclarer remplacé et
  conservé à titre historique.

**Reprise :** la phrase devient « Les fichiers de démonstration… » et passe avant celle sur le
mobile. La retouche de l'ADR-005 est annulée.

**Ce que ça montre :** deux agents qui travaillent en parallèle peuvent produire des modifications
qui ne se gênent pas dans Git mais qui se contredisent sur le fond. Git ne signale aucun conflit.
Seule une relecture du résultat combiné le montre.

### Cas 9 — Amender ou remplacer un ADR accepté : une règle écrite le 4 novembre

La même question s'est posée trois fois : que faire d'un ADR déjà accepté quand il doit changer ?
D'abord pour l'ADR-007, réécrit après son acceptation ; puis pour l'ADR-015, quand l'onglet « En
direct » a remplacé sa mention « Bientôt disponible » par un écran de déblocage. `adr-architecte`
a signalé que le comportement d'un ADR « Accepté » changeait sans repasser par « Proposé ».

**Reprise :** le 4 novembre, le `CLAUDE.md` reçoit une règle explicite. Un changement de décision
sur le fond donne un nouvel ADR qui remplace l'ancien, comme pour les ADR-005, 012 et 002. Une
précision, ou un ajustement de périmètre explicitement demandé par le lead développeur, donne un
amendement sur place, avec une ligne datée en fin d'ADR et sa raison. L'ADR-015 a été amendé
ainsi, sans repasser en « Proposé ».

### Le périmètre tenu

L'index des maquettes couvre aussi des écrans hors périmètre : playlists, « à regarder plus tard »,
téléchargements, notifications, création et import. Aucun n'a été réalisé, conformément à la liste
« Hors périmètre » du `CLAUDE.md`, sauf la création : la publication de vidéos et de Shorts depuis
l'application a été décidée le 3 novembre par l'ADR-015, qui l'a retirée du hors-périmètre avant
qu'elle soit développée.

## 7. Les cas où l'IA s'est arrêtée

Ces arrêts n'apparaissent pas dans Git : ils ont eu lieu dans les conversations, qui ne sont pas
versionnées. Ils sont rapportés ici par le lead développeur. Seul leur résultat, quand il y en a un,
se lit dans les ADR.

### Arrêt 1 — La facturation Play

Le 23 octobre, la consigne donnée à l'agent était double : implémenter la facturation Google Play, ou s'arrêter si
elle supposait une application publiée. Elle le supposait. L'agent s'est arrêté et a demandé un
arbitrage. Il n'a pas choisi seul l'achat simulé par le backend.

**Résultat :** l'ADR-012, décidé par le lead développeur (cas 4).

### Arrêt 2 — Les mesures d'audience

Le 23 octobre également, avant de brancher PostHog, l'agent s'est arrêté sur deux questions qu'aucun document ne tranchait :
l'utilisateur devait-il rester anonyme ou être identifié, et où placer le refus des mesures, que
l'écran d'accueil ne proposait plus ?

**Résultat :** l'ADR-013 — identification par le seul UUID du compte, refus dans Paramètres (cas 5).

### Arrêt 3 — L'origine des vidéos dans l'ADR-002

Le 16 octobre, en travaillant sur une autre tâche, l'agent a relevé de lui-même une incohérence
dans la section « Origine des vidéos » de l'ADR-002. Cette section dit que les vidéos Pexels sont
« téléchargées puis importées dans notre stockage via le back-office ». Or la commande
`seed:videos` les écrit directement dans le stockage, sans passer par le back-office.

Le point sortait du périmètre de sa tâche. L'agent l'a signalé et a proposé une correction : « hors
données de démonstration préparées par le seed, voir ADR-011 ». Il ne l'a pas appliquée : il a
attendu l'accord avant de toucher à un ADR accepté.

**Résultat :** la correction a été validée, mais elle n'a été appliquée que le 3 novembre à 11 h 33
(commit `fc46626`), dix-huit jours plus tard. Le cas montre que l'agent s'arrête même quand il a déjà
la solution.

Le même jour, une relecture d'ensemble des ADR a montré que la phrase corrigée restait fausse : le
back-office n'envoie aucun fichier, il enregistre une vidéo par son URL ou son chemin. La section a
alors été réécrite.

### Arrêt 4 — L'émulateur

Le 19 octobre, l'émulateur Android s'est arrêté trois fois sans explication. L'agent a cessé de le
relancer et a demandé confirmation avant de continuer, plutôt que de s'acharner. Il a refait de
même le 3 novembre.

### Le point commun

**L'agent s'arrête quand une décision n'est écrite ni dans la demande ni dans un ADR.** C'est une
consigne du `CLAUDE.md` : « En cas de doute ou d'information manquante, poser la question au lieu de
deviner. » Elle a tenu. Dans les quatre cas, l'agent aurait pu avancer seul : choisir un paiement de
remplacement, fixer une politique de données personnelles, réécrire un ADR accepté, relancer
l'émulateur en boucle. Chaque fois, il a rendu la décision à l'humain.
