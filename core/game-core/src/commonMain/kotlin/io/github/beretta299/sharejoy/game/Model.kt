package io.github.beretta299.sharejoy.game

import io.github.beretta299.sharejoy.physics.Body

/** Building materials. Toughness is how much impact (in m/s of velocity change) a block absorbs before breaking. */
enum class Material(val density: Float, val toughness: Float, val friction: Float) {
    WOOD(density = 0.7f, toughness = 7f, friction = 0.7f),
    STONE(density = 2.0f, toughness = 12f, friction = 0.8f),
    IRON(density = 5.5f, toughness = 26f, friction = 0.7f),
}

enum class PieceKind { BLOCK, KEEPER, PROJECTILE }

/** What part of the castle a block is; upgrades target blocks by role. */
enum class Role { GROUND_PILLAR, UPPER_PILLAR, FLOOR_BEAM, ROOF_BEAM, MERLON, FRONT_WALL, COVER, BUTTRESS, NONE }

/** [impactMultiplier] makes a shot hit harder than its mass alone would. */
enum class Ammo(val title: String, val radius: Float, val density: Float, val impactMultiplier: Float) {
    STONE("Stone", radius = 0.36f, density = 5.5f, impactMultiplier = 2.0f),
    SPLIT("Split shot", radius = 0.36f, density = 5.5f, impactMultiplier = 2.0f),
    IRON("Iron ball", radius = 0.42f, density = 8f, impactMultiplier = 2.6f),
    KEG("Powder keg", radius = 0.42f, density = 2.2f, impactMultiplier = 1f),
}

/** A game object that owns a physics body: a castle block, a Keeper or a projectile. */
class Piece(
    val body: Body,
    val kind: PieceKind,
    var material: Material,
    /** Index of the player this piece belongs to. */
    val owner: Int,
    val role: Role = Role.NONE,
    /** Stable random number for cosmetic variation (texture offset, crack shapes). */
    val cosmeticSeed: Int = 0,
) {
    var maxHealth = 1f
    var health = 1f
    var hasFlag = false
    var ammo: Ammo = Ammo.STONE

    /** Projectiles only: seconds since launch. */
    var flightSeconds = 0f

    /** Projectiles only: true once the shot has hit something. */
    var hasHit = false

    /** Projectiles only: true for the small pieces a split shot breaks into. */
    var isFragment = false

    /** 0 when undamaged, 1 when about to break. */
    val damageFraction: Float get() = 1f - (health / maxHealth).coerceIn(0f, 1f)

    /** 0 = clean, 1 = cracked, 2 = badly broken. */
    val damageStage: Int get() = when {
        damageFraction > 0.6f -> 2
        damageFraction > 0.25f -> 1
        else -> 0
    }
}

enum class Track(val title: String, val description: String) {
    POWER("Power", "Faster, harder shots"),
    AIM("Aim", "Longer trajectory guide"),
    AMMO("Ammo", "Split shot, iron ball, powder keg"),
    WALLS("Walls", "Stone hall, iron pillars, iron wall"),
    COVER("Cover", "Parapet, roof, high wall"),
}

class PlayerState(val index: Int) {
    val name: String get() = if (index == 0) "Red" else "Blue"
    var points = 0
    var roundsWon = 0
    val levels = IntArray(Track.entries.size)
    var selectedAmmo = Ammo.STONE
    var blocksDestroyedLastShot = 0

    fun level(track: Track): Int = levels[track.ordinal]

    fun unlockedAmmo(): List<Ammo> = Ammo.entries.filter { it.ordinal <= level(Track.AMMO) }

    fun resetForRound() {
        points = 0
        levels.fill(0)
        selectedAmmo = Ammo.STONE
        blocksDestroyedLastShot = 0
    }
}

enum class Phase {
    /** The active player spends build points and aims. */
    PLAN,

    /** Newly bought blocks are dropping into place. */
    BUILDING,

    /** A shot is in the air or the castles are still collapsing. */
    RESOLVING,

    /** Short pause while the turn passes to the other player. */
    HANDOVER,
    ROUND_OVER,
    MATCH_OVER,
}

sealed interface GameEvent {
    data class Fired(val x: Float, val y: Float) : GameEvent
    data class Impact(val x: Float, val y: Float, val strength: Float, val material: Material) : GameEvent
    data class BlockDestroyed(
        val x: Float, val y: Float, val halfWidth: Float, val halfHeight: Float, val angle: Float,
        val material: Material, val velocityX: Float, val velocityY: Float,
    ) : GameEvent

    data class Explosion(val x: Float, val y: Float, val radius: Float) : GameEvent
    data class KeeperDown(val owner: Int, val x: Float, val y: Float) : GameEvent
    data class Built(val x: Float, val y: Float) : GameEvent
    data class Repaired(val x: Float, val y: Float) : GameEvent
    data object Split : GameEvent
}

/** All tunable numbers in one place. Distances are metres, speeds metres per second. */
object Balance {
    /** Metres of battlefield one phone screen shows across its width. */
    const val SCREEN_WIDTH = 13f

    /** Open ground between the two home screens. Neither phone shows it at rest; the cameras cross it with the shot. */
    const val OPEN_GROUND_WIDTH = 12f

    /** Half the width of the battlefield; the seam between the players is x = 0. */
    const val HALF_FIELD_WIDTH = SCREEN_WIDTH + OPEN_GROUND_WIDTH / 2f
    const val GORGE_HALF_WIDTH = 1.2f

    /** Distance from the seam to the back wall of each castle. */
    const val CASTLE_BACK_X = 12.5f + OPEN_GROUND_WIDTH / 2f
    const val SLINGSHOT_X = 3.4f + OPEN_GROUND_WIDTH / 2f
    const val SLINGSHOT_Y = 2.3f
    const val MAX_SLINGSHOT_PULL = 2.2f
    const val BASE_LAUNCH_SPEED = 21f
    const val LAUNCH_SPEED_PER_POWER_LEVEL = 0.11f

    const val POINTS_PER_TURN = 3
    const val MAX_DESTRUCTION_BONUS = 3
    const val MAX_SAVED_POINTS = 12
    const val REPAIR_COST = 2
    const val BLOCKS_PER_REPAIR = 4
    val UPGRADE_COSTS = intArrayOf(2, 4, 6)

    /** Impacts weaker than this (m/s of velocity change) do no damage. */
    const val IMPACT_DAMAGE_THRESHOLD = 2.0f
    const val KEEPER_HEALTH = 12f
    const val SUDDEN_DEATH_TURN = 12
    const val ROUNDS_TO_WIN = 2

    /** How many seconds of flight the trajectory guide shows, per Aim level. */
    val AIM_GUIDE_SECONDS = floatArrayOf(0.45f, 0.85f, 1.4f, 4.5f)

    const val KEG_BLAST_RADIUS = 3.4f
    const val KEG_BLAST_FORCE = 13f
}
