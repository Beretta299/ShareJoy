# ShareJoy: Castle Duel

A two-player, turn-based castle siege built with **Kotlin Multiplatform** and **Compose Multiplatform**.
Each player has a medieval castle, a Keeper hiding inside it, and a slingshot. Take turns: spend build
points on upgrades, drag the slingshot back, and let real physics bring the other castle down. Knock the
enemy Keeper to the ground to win the round; first to two rounds wins the match.

The finished game is played on **two phones standing upright side by side**, like the two halves of a
tablet (`||`). Each phone shows its owner's half of the battlefield and the two screens join into one
picture, with the gorge running down the seam.

This is the **playable prototype**. Both players share one phone: the screen shows one half, exactly what
that player's phone will show later, and the camera slides across to whoever is up. On a wide screen (a
tablet, or the desktop window stretched wide) the app previews the paired game: two phone screens side by
side, each with its own HUD. At rest each phone shows its owner's tower. There is open ground between the
two towers that neither phone shows at rest: when a shot is fired the two screens lock into one window that
travels with it across that ground, then each camera glides back home.
Two Android phones can also pair for real: see *Pairing two phones* below.

## Run it

| Target | Command |
|---|---|
| Desktop (fastest way to try it) | `./gradlew :composeApp:run` |
| Android debug APK | `./gradlew :composeApp:assembleDebug` then install `composeApp/build/outputs/apk/debug/composeApp-debug.apk` |
| iOS | Targets are declared and the entry point is `MainViewController()`. The Xcode host project is not added yet. |
| Tests | `./gradlew :core:game-core:jvmTest :composeApp:desktopTest` |

You need JDK 17+ and, for Android, SDK platform 36.

## How to play

* **Aim:** press on the slingshot with the pulsing ring, drag back, release. Longer pull, faster shot.
* **Peek:** drag anywhere else to slide the camera across to the enemy tower.
* **Build:** before you fire, spend points on the cards along the bottom.
  You get 3 points a turn, plus 1 for each enemy block your last shot destroyed (up to 3).
* **Split shot:** tap while it is in the air to break it into three.
* From turn 12 it is **sudden death**: all damage is doubled.

| Track | Level 1 | Level 2 | Level 3 |
|---|---|---|---|
| Power | +11% launch speed | +22% | +33% |
| Aim | Longer trajectory guide | Long guide | Full arc |
| Ammo | Split shot | Iron ball | Powder keg |
| Walls | Stone pillars | Iron ground pillars | Iron curtain wall and buttress |
| Cover | Parapet | Second roof | Higher curtain wall |

Levels cost 2, 4 and 6 points. **Repair** costs 2 and mends your 4 most damaged blocks.

## Architecture

MVVM on top of a pure domain module. Dependencies point one way only: `ui` knows `presentation`,
`presentation` knows the domain, and the domain knows nothing about either.

```
core/game-core                     DOMAIN. Pure Kotlin, no UI, no platform code.
  physics/                         Rigid-body solver written for this game.
  game/                            Rules: Game (the match), Castle blueprints, Balance (every tunable number).

composeApp                         The app: Android, desktop (JVM) and iOS entry points.
  presentation/match/              PRESENTATION. No composables.
    MatchViewModel                 Steps the match, takes input, publishes state. The only door into the domain.
    MatchUiState                   Immutable state for the HUD, exposed as a StateFlow.
    MatchAction                    Everything the HUD can ask for (buy, repair, select ammo, next round, restart).
    MatchScene                     Read-only view of the live world for the canvas, read once per frame.
    camera/CameraDirector          Where each screen looks: one shared phone, or two paired phones.
    aim/SlingshotAim               Finger drag to pull vector to shot.
    effects/                       Rubble, shake and slow motion driven by game events.
  ui/                              UI. Stateless composables: state in, events out.
    match/MatchScreen              MatchRoute binds the ViewModel; MatchScreen lays out one or two phones.
    match/WorldCanvas              Draws the world and turns touches into world coordinates.
    match/hud/                     Top bar, build bar, round-over panel, shared HUD pieces.
    match/render/                  One painter per thing on screen: scenery, terrain, castle, Keeper, shots.
    theme/
  util/                            Small helpers with no home of their own.
```

How a frame flows: `MatchRoute` calls `MatchViewModel.onFrame`, the ViewModel steps `Game`, hands its events
to the effects and cameras, and publishes a new `MatchUiState`. The HUD recomposes only when that state
changes. The canvas redraws every frame from `MatchScene`, because a world that moves sixty times a second is
not worth copying into immutable state.

The data layer so far is `data/link`: the `PeerLink` interface, its wire protocol, and the Android implementation.

Things worth a look:

* `physics/`: `World` steps the simulation, `CollisionDetector` finds contacts (SAT + clipping for boxes), `ContactSolver` applies sequential impulses with warm starting; still bodies sleep. No native
  dependencies, so it runs on every target, and it is deterministic.
* `render/Textures.kt`: stone, wood and iron textures are painted in code at start-up, so the prototype
  ships without image assets.

## Tests

* `core/game-core`: rules, stability of the standing castle, determinism, and `BalanceSimTest`, which plays
  bot-vs-bot rounds headlessly and prints pacing numbers used for tuning.
* `composeApp` (`commonTest`): `MatchViewModelTest`, `CameraDirectorTest` (including that two paired screens
  form one continuous picture while a shot crosses) and `SlingshotAimTest`.
* `CaptureTool` (desktop test): plays a scripted match off-screen and saves screenshots or a video:
  `./gradlew :composeApp:desktopTest --tests '*CaptureTool*' -Dcapture=true`
  (add `-Dcapture.width=1460` for the two-phone view, `-Dcapture.video=true` for video frames)

## Not in the prototype yet

iOS pairing (MultipeerConnectivity), matching the picture scale across different screen sizes, a bot opponent, sound and haptics, turn timers, wind, commissioned art,
the backend, analytics, ads and purchases, and Roborazzi screenshot tests. They are all in the plan.

## Pairing two phones

From the lobby one phone taps **Host a game** and the other **Join a game**. They find each other with Google
Nearby Connections (Bluetooth and Wi-Fi, no internet). The host plays the left castle, the guest the right, and
each phone shows its own half, so standing side by side they form one picture.

* **Lockstep, not streaming.** Phones never send positions. Each runs the same simulation and they exchange only
  `GameCommand`s (buy, repair, select ammo, fire, split). `LockstepSync` schedules every command a few ticks
  ahead and lets a phone simulate a tick only after the other has confirmed its commands for it.
* **Bit-identical physics.** The simulation uses `DeterministicMath` for sine and cosine, so results do not
  depend on the device. Twice a second the phones compare a checksum of the game and report if they drift.
* **Swappable transport.** `PeerLink` is a plain byte channel. `NearbyPeerLink` (Android) implements it; tests use
  an in-memory pair with artificial latency (`PairedMatchTest`).
