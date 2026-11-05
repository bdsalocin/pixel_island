# Pixel Island

Une expérience d'îlot dynamique interactive et fluide, pensée sur mesure pour le Google Pixel 8 Pro.

Pixel Island transforme le poinçon de la caméra frontale en un espace vivant qui réagit en temps réel à vos applications. Contrairement aux solutions génériques, cette application tire parti de l'emplacement matériel exact du capteur photo et de la dalle OLED pour donner l'impression d'une fonctionnalité intégrée nativement dans le smartphone.

## Ce que propose Pixel Island

* **Fusion matérielle et logicielle** : Grâce au noir OLED absolu (#000000), les pixels entourant l'objectif s'éteignent totalement. La caméra frontale devient le cœur naturel de chaque animation.
* **Maintien autour du capteur en mode paysage** : Quand vous tournez votre smartphone à l'horizontale, l'îlot s'adapte immédiatement et reste verrouillé sur la caméra physique, sans glisser vers le haut de l'écran logique.
* **Lecteur audio symétrique** : Dès qu'une musique démarre sur Spotify, YouTube Music ou toute autre application, la pilule s'élargit des deux côtés. Une barre d'ondes sonores vibre à gauche et la miniature de l'album s'affiche à droite.
* **Animation de déverrouillage facial** : Au réveil de l'écran, un cercle lumineux doux enveloppe la caméra le temps de la détection, puis s'efface discrètement une fois votre visage reconnu.
* **Animations organiques** : Les transitions s'appuient sur la physique des ressorts pour offrir un effet élastique et naturel à chaque interaction.

## Technologies utilisées

* **Langage** : Kotlin
* **Interface graphique** : Jetpack Compose
* **Gestionnaire d'affichage** : WindowManager avec affichage dans la zone de découpe matérielle
* **Musique et médias** : MediaSessionManager
