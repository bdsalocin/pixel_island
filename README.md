# Pixel Island pour Google Pixel 8 Pro

Application d'îlot dynamique sur mesure pour Google Pixel 8 Pro, développée en Kotlin avec Jetpack Compose et une architecture propre (Clean Architecture).

## Fonctionnalités principales

1. Ancrage matériel absolu
La capsule est centrée sur le poinçon de la caméra frontale du Pixel 8 Pro. Grâce au noir OLED absolu (#000000), le capteur physique et l'affichage fusionnent totalement.

2. Maintien en mode paysage
Lors des rotations d'écran (90° et 270°), l'îlot reste physiquement verrouillé autour du capteur sans dériver vers le bord supérieur logique.

3. Extension musicale symétrique
Dès qu'une musique est lancée (Spotify, YouTube Music), la capsule s'ouvre de part et d'autre :
* Onde sonore vibrante à gauche (barres d'égaliseur animées).
* Poinçon physique au centre.
* Pochette d'album miniature à droite.

4. Déverrouillage facial (Pixel Face)
Lors du réveil de l'écran, un cercle doux entoure l'objectif avec un cadenas qui s'anime et se rétracte une fois le téléphone déverrouillé.

## Clean Architecture

* domain : Modèles purs (MediaTrack, IslandMode, CutoutGeometry) et cas d'usage indépendants de la plateforme.
* data : Implémentations des référentiels (MediaRepository, BiometricRepository, DisplayCutoutRepository) et services d'arrière plan.
* presentation : Composables Jetpack Compose, transitions élastiques (SpringSpec) et gestionnaire de fenêtre flottante.
