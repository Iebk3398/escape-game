# Escape Game IA – app multi-équipes

10 équipes, 5 salles (4 épreuves + 1 repos), timer synchronisé, scan QR, validation par les maîtres, indices, classement en direct, finale.
Stack : Spring Boot 3 (Java 17) + Angular 22 (signals) + PostgreSQL. Temps réel par SSE + rechargement toutes les 4 s.

## Démo locale (2 équipes)

Prérequis : Java 17, Maven, Node 22.

    ./run-demo.sh                       # Mac / Linux / Git Bash

Windows (PowerShell) :

    cd frontend; npm ci; npx ng build; cd ..
    rmdir backend\src\main\resources\static -Recurse -Force -ErrorAction SilentlyContinue
    mkdir backend\src\main\resources\static
    copy frontend\dist\frontend\browser\* backend\src\main\resources\static\ -Recurse
    cd backend; $env:ESCAPE_DEMO="true"; mvn spring-boot:run

Puis ouvrir http://localhost:8080 (base en mémoire : tout repart de zéro à chaque lancement).

En mode démo : codes capitaines `DEMO1` et `DEMO2`, PIN des salles `1111` … `5555`, PIN maître final `9999`, mot de passe admin `admin`.
Les deux équipes font ensemble salle 1 → 2 → 3 → 4 → repos, puis la finale. Durées courtes : manche 60 s, validation 30 s, transition 20 s, finale 5 min.

Pour la démo avec un téléphone : ouvrir `http://<IP-du-laptop>:8080/#/captain` sur le téléphone (même wifi), et `http://localhost:8080/#/master` sur le laptop (la webcam scanne le QR du téléphone).
Plusieurs onglets (navigation privée) : admin `#/admin`, capitaine 1 et 2 `#/captain`, maître `#/master`, écran `#/screen/1`, classement `#/ranking`, finale `#/final`.
Code final de la démo : `21479`. Bouton « Reset » de l'admin pour recommencer.

## Production (Render payant + Neon gratuit)

1. Neon : créer une base PostgreSQL. Récupérer l'hôte, la base, l'utilisateur, le mot de passe.
2. Render : « New + > Blueprint » sur ce dépôt (`render.yaml`, plan Starter). Variables à renseigner :
   `ADMIN_PASSWORD`, `JDBC_URL` (`jdbc:postgresql://HOTE/BASE?sslmode=require`), `DB_USER`, `DB_PASSWORD`.
3. Ouvrir `https://<app>.onrender.com/#/admin`, se connecter : les codes capitaines et les PIN sont affichés. Les distribuer.

Une seule instance du serveur doit tourner (l'état est en mémoire et sauvegardé en base à chaque changement ; un redémarrage ne perd rien).

## Utilisation le jour J

| Qui | Adresse | Accès |
|---|---|---|
| Admin | `/#/admin` | mot de passe |
| Capitaine | `/#/captain` | code d'équipe |
| Maître de salle | `/#/master` | PIN de la salle |
| Projecteur salle N | `/#/screen/N` | aucun |
| Classement | `/#/ranking` | aucun |
| Maître final | `/#/final` | PIN final |

Déroulé : l'admin lance le jeu. Transition (2 min) → manche (10 min, démarre toute seule) → validation (1 min) → … → finale.
Les capitaines montrent leur QR, le maître scanne (ou marque présent à la main), puis valide le résultat à la fin de la manche.
Si un maître oublie : l'admin voit les scores non validés en rouge et peut les saisir.
Pause / « terminer la phase » / reset : page admin.

## Réglages

- Durées : variables `ESCAPE_ROUND_SEC` (600), `ESCAPE_VALIDATION_SEC` (60), `ESCAPE_TRANSITION_SEC` (120), `ESCAPE_FINAL_TRANSIT_SEC` (120), `ESCAPE_FINAL_SEC` (1200).
- Contenu (noms de salles, symboles, chiffres, indices bonus, énigme, code final, nombre d'essais) : `backend/src/main/resources/content.json`. Les valeurs actuelles sont des EXEMPLES à remplacer.
- Points : victoire 3 / défaite 1 / égalité 2 chacun / repos 0. Bonus finale : 10, 7, 5, 4, 3, 2, 1… (classe `Scoring`).
- Indices : chaque équipe reçoit le chiffre de chaque salle. Le gagnant d'un duel reçoit en plus l'indice bonus (pas en cas d'égalité).

## Rotation

Équipes 1–5 (groupe A) et 6–10 (groupe B). Manche r : l'équipe A numéro i va en salle (i + r) mod 5, l'équipe B numéro j en salle (j + 2r) mod 5.
Chaque équipe passe une fois dans chaque salle (repos compris) et affronte 5 adversaires différents.

## Tests

La logique (rotation, timer, points, classement) a un auto-test sans dépendance :

    javac -d /tmp/out backend/src/main/java/be/ilyteq/escape/core/*.java backend/src/test/java/be/ilyteq/escape/core/CoreSelfTest.java
    java -cp /tmp/out be.ilyteq.escape.core.CoreSelfTest
