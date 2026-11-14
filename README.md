# ShareJoy: Castle Duel

A two-player, turn-based castle siege built with **Kotlin Multiplatform** and **Compose Multiplatform**.
Each player has a medieval castle, a Keeper hiding inside it, and a slingshot. Take turns: spend build
points on upgrades, drag the slingshot back, and let real physics bring the other castle down. Knock the
enemy Keeper to the ground to win the round; first to two rounds wins the match.

This is the **playable prototype**. Both players share one screen and the camera pans to whoever is up.
Pairing two phones side by side is the next big step (see the project plan).

## Run it

| Target | Command |
|---|---|
| Desktop (fastest way to try it) | `./gradlew :composeApp:run` |
| Android debug APK | `./gradlew :composeApp:assembleDebug` then install `composeApp/build/outputs/apk/debug/composeApp-debug.apk` |
| iOS | Targets are declared and the entry point is `MainViewController()`. The Xcode host project is not added yet. |
| Tests | `./gradlew :core:game-core:jvmTest` |

You need JDK 17+ and, for Android, SDK platform 36.

## How to play

* **Aim:** press on the slingshot with the pulsing ring, drag back, release. Longer pull, faster shot.
* **Peek:** drag anywhere else to slide the camera and look at the enemy castle.
* **Build:** before you fire, spend points on the cards along the bottom.
  You get 3 points a turn, plus 1 for each enemy block your last shot destroyed (up to 3).
* **Split shot:** tap while it is in the air to break it into three.
* From turn 12 it is **sudden death**: all damage is doubled.

| Track | Level 1 | Level 2 | Level 3 |
|---|---|---|---|
| Power | +11% launch speed | +22% | +33% |
| Aim | Longer trajectory guide | Long guide | Full arc |
| Ammo | Split shot | Iron ball | Powder keg |
| Walls | Stone upper hall | Iron ground pillars | Buttresses |
| Cover | Parapet | Second roof | Barricade |

Levels cost 2, 4 and 6 points. **Repair** costs 2 and mends your 4 most damaged blocks.

## Project layout

```
core/game-core   Pure Kotlin, no UI: the physics engine, castle blueprints, turn rules, balance numbers.
                 Deterministic, so the same inputs always give the same match. Fully unit-testable.
composeApp       Compose Multiplatform app: Canvas renderer, camera, input, HUD.
                 Entry points for Android, desktop (JVM) and iOS.
```

Things worth a look:

* `physics/World.kt`: a small rigid-body solver written for this game (SAT + clipping for boxes,
  sequential impulses with warm starting, sleeping). No native dependencies, so it runs on every target.
* `game/Game.kt`: the match state machine. `game/Model.kt` holds every tunable number in `Balance`.
* `render/Textures.kt`: stone, wood and iron textures are painted in code at start-up, so the prototype
  ships without image assets.
* `BalanceSimTest`: plays bot-vs-bot rounds headlessly and prints pacing numbers used for tuning.
* `CaptureTool` (desktop test): plays a scripted match off-screen and saves screenshots or a video:
  `./gradlew :composeApp:desktopTest --tests '*CaptureTool*' -Dcapture=true`

## Not in the prototype yet

Two-device pairing and netcode, a bot opponent, sound and haptics, turn timers, wind, commissioned art,
the backend, analytics, ads and purchases, and Roborazzi screenshot tests. They are all in the plan.
