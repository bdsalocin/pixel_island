# Architecture du Projet Pixel Island (Google Pixel 8 Pro)

Ce document décrit l'organisation architecturale du projet selon les principes de la Clean Architecture.

## 1. Découpage en couches

### Couche Domaine (domain)
Totalement indépendante du système Android et facilement testable unitairement :
* **Modèles** :
  * `IslandMode` : Représente les différents états de la pilule (repos, musique en lecture, déverrouillage facial, alertes).
  * `MediaTrack` : Données de la piste audio (titre, artiste, pochette, état de lecture, durée).
  * `BiometricStatus` : États du déverrouillage facial (attente, scan, succès, échec).
  * `CutoutGeometry` : Coordonnées géométriques et orientation de la découpe matérielle.
* **Référentiels (Interfaces)** :
  * `MediaRepository`
  * `BiometricRepository`
  * `DisplayCutoutRepository`
* **Cas d'usage (UseCases)** :
  * `ObserveActiveMediaUseCase`
  * `ObserveUnlockStateUseCase`
  * `CalculateCutoutAnchorUseCase`
  * `TogglePlaybackUseCase`

### Couche Données (data)
Gère l'interaction avec le système d'exploitation Android :
* **Implémentations des référentiels** :
  * `MediaRepositoryImpl` : Exploite `MediaSessionManager` pour intercepter les sessions Spotify et YouTube Music.
  * `BiometricRepositoryImpl` : Écoute les signaux système `ACTION_SCREEN_ON` et `ACTION_USER_PRESENT`.
  * `DisplayCutoutRepositoryImpl` : Fournit les dimensions physiques du poinçon Pixel 8 Pro et gère la rotation.
* **Services d'arrière plan** :
  * `IslandOverlayService` : Service d'avant plan qui injecte la fenêtre flottante via `WindowManager` avec l'option `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`.
  * `MusicNotificationListenerService` : Service officiel autorisant la lecture des métadonnées multimédia.

### Couche Présentation (presentation)
Gère l'interface graphique avec Jetpack Compose :
* `IslandViewModel` : Centralise les flux des cas d'usage et expose un état immuable `IslandUiState`.
* `DynamicPillView` : Composant visuel principal qui anime la largeur, la hauteur et l'arrondi avec des transitions basées sur des ressorts (`SpringSpec`).
* `EqualizerBars` : Barres d'ondes sonores qui oscillent de façon asynchrone lors de la lecture audio.
* `FaceUnlockScanner` : Anneau et cadenas animés autour du capteur photo lors du déverrouillage.
* `MainActivity` : Écran d'accueil pour guider l'utilisateur dans l'activation des permissions nécessaires.

## 2. Spécificités matérielles du Google Pixel 8 Pro

* **Noir OLED absolu** : L'utilisation de la couleur `#000000` permet d'éteindre les pixels de la dalle. L'écran et la vitre sombre du poinçon fusionnent totalement.
* **Verrouillage en mode paysage** : Grâce au recalcul dynamique de la gravité de la fenêtre (`Gravity.START` à 90° et `Gravity.END` à 270°), la capsule reste accrochée au capteur photo physique même lorsque l'interface pivote.
