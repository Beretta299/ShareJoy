package io.github.beretta299.sharejoy.game

import io.github.beretta299.sharejoy.physics.Body

/** Building materials. Toughness is how much impact (in m/s of delta-v) a block absorbs before breaking. */
enum class Material(val density: Float, val toughness: Float, val friction: Float) {
    WOOD(0.7f, 7f, 0.7f),
    STONE(2.0f, 12f, 0.8f),
    IRON(5.5f, 26f, 0.7f),
}

enum class PieceKind { BLOCK, KEEPER, PROJECTILE }

/** What part of the castle a block is; upgrades target blocks by role. */
enum class Role { GROUND_PILLAR, UPPER_PILLAR, FLOOR_BEAM, ROOF_BEAM, MERLON, FRONT_WALL, COVER, BUTTRESS, NONE }

enum class Ammo(val title: String, val radius: Float, val density: Float, val punch: Float) {
    STONE("Stone", 0.36f, 5.5f, 2.7f),
    SPLIT("Split shot", 0.36f, 5.5f, 2.7f),
    IRON("Iron ball", 0.42f, 8f, 3.5f),
    KEG("Powder keg", 0.42f, 2.2f, 1f),
}

/** A game object that owns a physics body. */
class Piece(
    val body: Body,
    val kind: PieceKind,
    var material: Material,
    val owner: Int,
    val role: Role = Role.NONE,
    /** Stable random seed for cosmetic variation (texture offset, crack shapes). */
    val seed: Int = 0,
) {
    var maxHp = 1f
    var hp = 1f
    var hasFlag = false
    var ammo: Ammo = Ammo.STONE

    /** For projectiles: seconds since launch, whether it already hit something. */
    var age = 0f
    var hasHit = false
    var isFragment = false

    val damage: Float get() = 1f - (hp / maxHp).coerceIn(0f, 1f)

    /** 0 = clean, 1 = cracked, 2 = badly broken. */
    val damageStage: Int get() = when {
        damage > 0.6f -> 2
        damage > 0.25f -> 1
        else -> 0
    }
}

enum class Track(val title: String, val blurb: String) {
    POWER("Power", "Faster, harder shots"),
    AIM("Aim", "Longer trajectory guide"),
    AMMO("Ammo", "Split shot, iron ball, powder keg"),
    MATERIALS("Walls", "Stone hall, iron pillars, iron wall"),
    COVER("Cover", "Parapet, roof, high wall"),
}

class PlayerState(val index: Int) {
    val name: String get() = if (index == 0) "Red" else "Blue"
    var points = 0
    var roundsWon = 0
    val levels = IntArray(Track.entries.size)
    var selectedAmmo = Ammo.STONE
    var destroyedLastShot = 0

    fun level(track: Track): Int = levels[track.ordinal]

    fun unlockedAmmo(): List<Ammo> {
        val lvl = level(Track.AMMO)
        return Ammo.entries.filter { it.ordinal <= lvl }
    }

    fun resetForRound() {
        points = 0
        levels.fill(0)
        selectedAmmo = Ammo.STONE
        destroyedLastShot = 0
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
        val x: Float, val y: Float, val halfW: Float, val halfH: Float, val angle: Float,
        val material: Material, val vx: Float, val vy: Float,
    ) : GameEvent

    data class Explosion(val x: Float, val y: Float, val radius: Float) : GameEvent
    data class KeeperDown(val owner: Int, val x: Float, val y: Float) : GameEvent
    data class Built(val x: Float, val y: Float) : GameEvent
    data class Repaired(val x: Float, val y: Float) : GameEvent
    data object Split : GameEvent
}

/** All tunable numbers in one place. */
object Balance {
    /** Half the width of the battlefield. Each phone shows one half; the seam between them is x = 0. */
    const val HALF_WORLD = 13f
    const val GORGE_HALF_WIDTH = 1.2f
    const val CASTLE_BACK_X = 12.5f
    const val SLINGSHOT_X = 3.4f
    const val SLINGSHOT_Y = 2.3f
    const val MAX_PULL = 2.2f
    const val BASE_SPEED = 15f
    const val POWER_STEP = 0.11f

    const val TURN_INCOME = 3
    const val MAX_DESTROY_BONUS = 3
    const val MAX_POINTS = 12
    const val REPAIR_COST = 2
    const val REPAIR_BLOCKS = 4
    val LEVEL_COSTS = intArrayOf(2, 4, 6)

    const val DAMAGE_THRESHOLD = 2.0f
    const val KEEPER_HP = 12f
    const val SUDDEN_DEATH_TURN = 12
    const val ROUNDS_TO_WIN = 2

    val AIM_SECONDS = floatArrayOf(0.45f, 0.85f, 1.4f, 4.5f)

    const val KEG_RADIUS = 3.4f
    const val KEG_FORCE = 13f
}
