# Brussels Collection Demo

Projet Android propre et minimal pour démonstration :
- Kotlin
- Jetpack Compose
- Material 3
- build GitHub Actions
- APK debug en artifact

## Build dans GitHub
Le workflow `.github/workflows/android-build.yml` :
- installe Java 17
- installe le SDK Android
- installe Gradle 8.7
- lance `gradle assembleDebug`
- publie l'APK en artifact si succès
- publie `gradle-build.log` sinon

## Structure
- `app/` : module Android
- `settings.gradle.kts` : configuration du projet
- `build.gradle.kts` : plugins racine

## Notes
Ce projet n'a volontairement pas de wrapper Gradle, pour rester léger.
Le workflow GitHub Actions utilise `gradle/actions/setup-gradle`.
