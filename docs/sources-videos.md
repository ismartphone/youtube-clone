# Sources des vidéos

Le catalogue de démonstration contient deux sortes de vidéos, hébergées chez nous ([ADR-002](adr/ADR-002-hebergement-videos.md)).

| Source | Licence | Attribution | Fichiers |
|---|---|---|---|
| Pexels | Licence Pexels, usage gratuit | Non exigée | Retéléchargés par `npm run seed:videos` |
| YouTube, sept vidéos d'auteurs | Creative Commons Attribution (CC BY) | **Obligatoire** | Fournis à la main, aucun script ne peut les retélécharger |

## Vidéos sous licence Creative Commons

Ces sept vidéos sont publiées sur YouTube par leurs auteurs sous la licence **Creative Commons Attribution (CC BY)**. Cette licence autorise la réutilisation, y compris la modification, à une condition : citer l'auteur et la source.

Cette page tient lieu d'attribution. C'est une obligation de la licence, pas une politesse. Toute vidéo ajoutée sous cette licence doit y figurer avec son titre, sa chaîne et son lien.

| Fichier | Titre | Auteur (chaîne) | Lien de la chaîne | Licence |
|---|---|---|---|---|
| `cc-01.mp4` | I Buy EVERY Nintendo Switch 2 Game! | BeatEmUps | https://www.youtube.com/@BeatEmUps | CC BY |
| `cc-02.mp4` | This Dinosaur Horror Game Is So Good! \| Terrible Lizards - FULL GAME | TheGamingBeaver | https://www.youtube.com/@AGamingBeaver | CC BY |
| `cc-03.mp4` | "11 SPECTATORS!" Barret gaming (ROS TAGALOG) | Jazon Gaming | https://www.youtube.com/@JazonGaming | CC BY |
| `cc-04.mp4` | I Made My Sheep Too Real #gamedev #indiedev | GoofiDev | https://www.youtube.com/@GoofiDev | CC BY |
| `cc-05.mp4` | Voici l'endroit le plus profond de la planète (et ce qu'on y a trouvé) | Edward wkl | https://www.youtube.com/@edwardwkl | CC BY |
| `cc-06.mp4` | HAMXTON- Reviens ft @DILO_Dégaine-la-magie & @thereal_mnl (Clip officiel) | Hamxton | https://www.youtube.com/channel/UC0KiP4me3XkJmuCFxmZio0w | CC BY |
| `cc-07.mp4` | Learned from the best | TheRealGYGII | https://www.youtube.com/@TheRealGYGII | CC BY |

Les vidéos sont reprises telles quelles : nous ne les modifions pas. Le titre affiché dans l'application est celui de l'auteur. Chaque vidéo est rattachée à une chaîne au nom de son auteur.

### Ce que l'application crée pour ces chaînes

- **Une chaîne par auteur**, avec son nom réel et un identifiant dérivé de ce nom (par exemple `@beatemups`, `@jazongaming`).
- **Un compte propriétaire technique** (`<identifiant>@creators.example.com`), avec un mot de passe aléatoire jamais communiqué. Il ne sert pas à se connecter.
- **Un avatar généré** : les initiales du nom en blanc, sur un fond dont la couleur est déduite du nom. La même chaîne a donc toujours la même couleur. Le fichier est rangé dans `backend/storage/media/avatars/`.
- **Une miniature** extraite de chaque vidéo à 10 % de sa durée, par le service FFmpeg du backend.

La liste se trouve dans `backend/prisma/cc-videos.ts`. Le seed et la commande d'import la lisent.

## Pourquoi ces fichiers ne se retéléchargent pas

Les vidéos Pexels se reconstituent après un clone : `npm run seed:videos` les télécharge depuis l'API Pexels, les vérifie et les réencode.

Les sept vidéos Creative Commons ne peuvent pas être retéléchargées ainsi. YouTube ne fournit pas les fichiers vidéo, et les récupérer par un script contournerait ses conditions d'utilisation. Les fichiers `cc-01.mp4` à `cc-07.mp4` ont donc été déposés à la main dans `backend/storage/media/videos/`.

Ce dossier n'est pas versionné (`storage/` est ignoré par Git). Les fichiers n'existent que sur la machine où ils ont été déposés.

### Sur un clone neuf

- **Si vous lancez le seed** (`npx prisma db seed`) : il crée les sept chaînes et les sept vidéos en base, quel que soit l'état du stockage. Sans les fichiers, ces entrées pointent vers des fichiers absents. Elles apparaissent dans le fil, mais la lecture échoue.
- **Si vous lancez `npm run seed:cc-videos`** : la commande vérifie chaque fichier avec ffprobe. Un fichier absent est signalé et la vidéo n'est pas créée. Les entrées déjà créées par le seed restent en base, sans fichier.

### Comment s'en sortir

Deux possibilités :

1. **Fournir les fichiers à la main.** Copiez `cc-01.mp4` à `cc-07.mp4` dans `backend/storage/media/videos/`, puis lancez `npm run seed:cc-videos`. La commande génère les avatars et remplace les miniatures provisoires du seed par une vraie image de chaque vidéo.
2. **Se passer de ces vidéos.** Ne lancez pas `npm run seed:cc-videos`. Si le seed a déjà créé les entrées, supprimez ces vidéos depuis le back-office, ou ajustez le seed pour ne pas les créer.

## Commande d'import

```powershell
cd backend
npm run seed:cc-videos
```

La commande ajoute les sept vidéos à la base existante, **sans rien effacer**. Contrairement au seed, elle conserve vos données. Pour chaque vidéo, elle :

1. vérifie le fichier avec ffprobe : flux vidéo lisible, durée attendue, orientation conforme au format (un Short est vertical). Un fichier qui ne passe pas est signalé et passé ;
2. crée la chaîne de l'auteur si elle n'existe pas, avec son compte technique et son avatar ;
3. crée la vidéo avec son titre et sa catégorie (Gaming, Documentaire ou Musique, qui doivent exister en base), sans description. Sa miniature est générée par le service FFmpeg du backend.

La commande est **idempotente**. Une chaîne ou une vidéo déjà présente n'est pas recréée : la relancer ne crée aucun doublon. Une vidéo déjà présente avec une miniature provisoire, posée par le seed, reçoit sa vraie miniature.

Elle demande FFmpeg et ffprobe (`FFMPEG_PATH`, `FFPROBE_PATH`), et une police pour les initiales des avatars. Une police courante du système est cherchée (Arial sous Windows, DejaVu sous Linux). À défaut, indiquez un fichier `.ttf` dans `AVATAR_FONT`.
