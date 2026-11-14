package io.github.beretta299.sharejoy.game

import io.github.beretta299.sharejoy.physics.Body
import io.github.beretta299.sharejoy.physics.World
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The whole match: two castles, turn order, build points, upgrades and the win check.
 *
 * It is deterministic and has no UI or platform dependencies. The UI calls [update] with a fixed
 * time step and reads [pieces], [phase] and [events] to draw.
 */
class Game {
    val world = World()
    val pieces = ArrayList<Piece>()
    val players = arrayOf(PlayerState(0), PlayerState(1))
    val events = ArrayList<GameEvent>()

    var phase = Phase.PLAN
        private set
    var current = 0
        private set

    /** Shots fired this round, starting at 1 for the first turn. */
    var turn = 1
        private set
    var round = 1
        private set

    /** Winner of the last finished round, or -1 for a draw / none yet. */
    var roundWinner = -1
        private set
    val keeperDown = BooleanArray(2)

    private val keepers = arrayOfNulls<Piece>(2)
    private val destroyedThisShot = IntArray(2)
    private var resolveTime = 0f
    private var calmTime = 0f
    private var handoverTime = 0f
    private var seedCounter = 12345
    private var firstPlayerOfRound = 0

    val activePlayer: PlayerState get() = players[current]
    val suddenDeath: Boolean get() = turn >= Balance.SUDDEN_DEATH_TURN
    val projectiles: List<Piece> get() = pieces.filter { it.kind == PieceKind.PROJECTILE }

    init {
        startRound()
    }

    // ------------------------------------------------------------------ setup

    fun newMatch() {
        players.forEach { it.roundsWon = 0 }
        round = 1
        firstPlayerOfRound = 0
        startRound()
    }

    fun nextRound() {
        if (phase != Phase.ROUND_OVER) return
        round++
        firstPlayerOfRound = 1 - firstPlayerOfRound
        startRound()
    }

    private fun startRound() {
        world.clear()
        pieces.clear()
        events.clear()
        keeperDown.fill(false)
        roundWinner = -1
        turn = 1
        players.forEach { it.resetForRound() }

        for (s in intArrayOf(-1, 1)) {
            val ground = Body(world.newId(), halfW = 20f, halfH = 6f, density = 0f, friction = 0.9f)
            ground.x = s * (Balance.GORGE_HALF_WIDTH + 20f)
            ground.y = -6f
            ground.isGround = true
            world.add(ground)
        }

        for (owner in 0..1) {
            Castle.base.forEach { addBlock(it, owner, lift = 0f) }
            val body = Body(
                world.newId(), Castle.KEEPER_HALF_W, Castle.KEEPER_HALF_H,
                density = 1f, friction = 0.8f, restitution = 0f,
            )
            body.x = worldX(owner, Castle.KEEPER_X)
            body.y = Castle.KEEPER_Y
            val keeper = Piece(body, PieceKind.KEEPER, Material.WOOD, owner, seed = nextSeed())
            keeper.maxHp = Balance.KEEPER_HP
            keeper.hp = Balance.KEEPER_HP
            body.tag = keeper
            world.add(body)
            pieces.add(keeper)
            keepers[owner] = keeper
        }

        world.sleepAll()
        current = firstPlayerOfRound
        players[current].points = Balance.TURN_INCOME
        // Going second is a disadvantage, so that player starts with one extra point.
        players[1 - current].points = 1
        phase = Phase.PLAN
    }

    fun side(owner: Int): Float = if (owner == 0) -1f else 1f

    fun worldX(owner: Int, localX: Float): Float = side(owner) * (Balance.CASTLE_BACK_X - localX)

    fun slingshotX(owner: Int): Float = side(owner) * Balance.SLINGSHOT_X

    private fun nextSeed(): Int {
        seedCounter = seedCounter * 1103515245 + 12345
        return (seedCounter ushr 8) and 0xFFFF
    }

    private fun addBlock(spec: BlockSpec, owner: Int, lift: Float): Piece {
        val body = Body(
            world.newId(), spec.w / 2f, spec.h / 2f,
            density = spec.material.density, friction = spec.material.friction,
        )
        body.x = worldX(owner, spec.x)
        body.y = spec.y + lift
        val piece = Piece(body, PieceKind.BLOCK, spec.material, owner, spec.role, nextSeed())
        piece.maxHp = spec.material.toughness
        piece.hp = piece.maxHp
        piece.hasFlag = spec.flag
        body.tag = piece
        world.add(body)
        pieces.add(piece)
        return piece
    }

    // ------------------------------------------------------------------ building

    fun cost(track: Track): Int? {
        val level = activePlayer.level(track)
        return if (level >= Balance.LEVEL_COSTS.size) null else Balance.LEVEL_COSTS[level]
    }

    fun canBuy(track: Track): Boolean {
        val c = cost(track) ?: return false
        return phase == Phase.PLAN && activePlayer.points >= c
    }

    fun buy(track: Track): Boolean {
        if (!canBuy(track)) return false
        val player = activePlayer
        player.points -= cost(track)!!
        player.levels[track.ordinal]++
        val level = player.level(track)
        when (track) {
            Track.POWER, Track.AIM -> Unit
            Track.AMMO -> player.selectedAmmo = Ammo.entries[level]
            Track.MATERIALS -> when (level) {
                1 -> reinforce(player.index, Role.UPPER_PILLAR, Material.STONE)
                2 -> reinforce(player.index, Role.GROUND_PILLAR, Material.IRON)
                else -> {
                    reinforce(player.index, Role.FRONT_WALL, Material.IRON)
                    place(Castle.buttresses, player.index)
                }
            }
            Track.COVER -> place(Castle.cover[level - 1], player.index)
        }
        return true
    }

    private fun reinforce(owner: Int, role: Role, material: Material) {
        for (p in pieces) {
            if (p.owner != owner || p.role != role || p.kind != PieceKind.BLOCK) continue
            val health = p.hp / p.maxHp
            p.material = material
            p.maxHp = material.toughness
            p.hp = p.maxHp * health
            p.body.setDensity(material.density)
            p.body.friction = material.friction
            events.add(GameEvent.Built(p.body.x, p.body.y))
        }
    }

    /** Drops new blocks into the castle, sliding each one up until it is clear of what is already there. */
    private fun place(specs: List<BlockSpec>, owner: Int) {
        for (spec in specs) {
            val x = worldX(owner, spec.x)
            var lift = 0.12f
            while (lift < 9f && overlaps(x, spec.y + lift, spec.w / 2f, spec.h / 2f)) lift += 0.25f
            val piece = addBlock(spec, owner, lift)
            events.add(GameEvent.Built(piece.body.x, piece.body.y))
        }
        resolveTime = 0f
        calmTime = 0f
        phase = Phase.BUILDING
    }

    private fun overlaps(x: Float, y: Float, hw: Float, hh: Float): Boolean {
        for (b in world.bodies) {
            if (abs(b.x - x) < hw + b.aabbHalfW() - 0.02f && abs(b.y - y) < hh + b.aabbHalfH() - 0.02f) return true
        }
        return false
    }

    fun canRepair(): Boolean = phase == Phase.PLAN && activePlayer.points >= Balance.REPAIR_COST &&
        pieces.any { it.owner == current && it.kind == PieceKind.BLOCK && it.hp < it.maxHp }

    fun repair(): Boolean {
        if (!canRepair()) return false
        activePlayer.points -= Balance.REPAIR_COST
        pieces.filter { it.owner == current && it.kind == PieceKind.BLOCK && it.hp < it.maxHp }
            .sortedByDescending { it.damage }
            .take(Balance.REPAIR_BLOCKS)
            .forEach {
                it.hp = it.maxHp
                events.add(GameEvent.Repaired(it.body.x, it.body.y))
            }
        return true
    }

    fun selectAmmo(ammo: Ammo) {
        if (phase == Phase.PLAN && ammo in activePlayer.unlockedAmmo()) activePlayer.selectedAmmo = ammo
    }

    // ------------------------------------------------------------------ shooting

    fun launchSpeed(player: PlayerState = activePlayer): Float =
        Balance.BASE_SPEED * (1f + Balance.POWER_STEP * player.level(Track.POWER))

    fun aimSeconds(player: PlayerState = activePlayer): Float = Balance.AIM_SECONDS[player.level(Track.AIM)]

    /**
     * Fires from the pouch, which sits at slingshot anchor + ([pullX], [pullY]).
     * The shot flies back through the anchor; a longer pull is a faster shot.
     */
    fun fire(pullX: Float, pullY: Float): Boolean {
        if (phase != Phase.PLAN) return false
        val len = sqrt(pullX * pullX + pullY * pullY)
        if (len < 0.4f) return false
        val k = min(len, Balance.MAX_PULL) / len
        val px = pullX * k
        val py = pullY * k
        val speed = launchSpeed() / Balance.MAX_PULL
        val ammo = activePlayer.selectedAmmo

        val body = Body(world.newId(), 0f, 0f, radius = ammo.radius, density = ammo.density, friction = 0.6f, restitution = 0.15f)
        body.x = slingshotX(current) + px
        body.y = max(Balance.SLINGSHOT_Y + py, ammo.radius + 0.05f)
        body.vx = -px * speed
        body.vy = -py * speed
        body.punch = ammo.punch
        val piece = Piece(body, PieceKind.PROJECTILE, Material.STONE, current, seed = nextSeed())
        piece.ammo = ammo
        body.tag = piece
        world.add(body)
        pieces.add(piece)

        destroyedThisShot.fill(0)
        resolveTime = 0f
        calmTime = 0f
        phase = Phase.RESOLVING
        events.add(GameEvent.Fired(body.x, body.y))
        return true
    }

    /** Tap while a split shot is in the air to break it into three. */
    fun tapSplit(): Boolean {
        if (phase != Phase.RESOLVING) return false
        val shot = pieces.firstOrNull {
            it.kind == PieceKind.PROJECTILE && it.ammo == Ammo.SPLIT && !it.isFragment && !it.hasHit
        } ?: return false
        val b = shot.body
        removePiece(shot)
        for (i in -1..1) {
            val a = i * 0.13f
            val c = cos(a)
            val s = sin(a)
            val body = Body(world.newId(), 0f, 0f, radius = 0.25f, density = 6f, friction = 0.6f, restitution = 0.15f)
            body.x = b.x
            body.y = b.y + i * 0.55f
            body.vx = b.vx * c - b.vy * s
            body.vy = b.vx * s + b.vy * c
            body.punch = 1.7f
            val piece = Piece(body, PieceKind.PROJECTILE, Material.STONE, current, seed = nextSeed())
            piece.ammo = Ammo.SPLIT
            piece.isFragment = true
            piece.age = shot.age
            body.tag = piece
            world.add(body)
            pieces.add(piece)
        }
        events.add(GameEvent.Split)
        return true
    }

    // ------------------------------------------------------------------ simulation

    fun update(dt: Float) {
        when (phase) {
            Phase.PLAN, Phase.ROUND_OVER, Phase.MATCH_OVER -> Unit
            Phase.HANDOVER -> {
                handoverTime -= dt
                if (handoverTime <= 0f) phase = Phase.PLAN
            }
            Phase.BUILDING -> {
                world.step(dt)
                resolveTime += dt
                calmTime = if (isCalm()) calmTime + dt else 0f
                if ((calmTime > 0.35f && resolveTime > 0.5f) || resolveTime > 2.5f) {
                    freeze()
                    phase = Phase.PLAN
                }
            }
            Phase.RESOLVING -> {
                world.step(dt)
                resolveTime += dt
                applyDamage()
                updateProjectiles(dt)
                removeLost()
                checkKeepers()
                calmTime = if (isCalm()) calmTime + dt else 0f
                if ((calmTime > 0.6f && resolveTime > 1f) || resolveTime > 12f) endTurn()
            }
        }
    }

    private fun isCalm(): Boolean {
        for (b in world.bodies) {
            if (b.isStatic || b.asleep) continue
            if (b.speedSq() > 0.07f || abs(b.omega) > 0.35f) return false
        }
        return true
    }

    private fun freeze() = world.sleepAll()

    private fun applyDamage() {
        val mult = if (suddenDeath) 2f else 1f
        var i = pieces.size - 1
        while (i >= 0) {
            val p = pieces[i]
            val impact = p.body.impact
            if (impact > 0f) {
                if (p.kind == PieceKind.PROJECTILE) {
                    if (!p.hasHit) {
                        p.hasHit = true
                        if (p.ammo == Ammo.KEG) {
                            explode(p)
                            i = min(i, pieces.size) - 1
                            continue
                        }
                    }
                } else if (impact > Balance.DAMAGE_THRESHOLD) {
                    p.hp -= (impact - Balance.DAMAGE_THRESHOLD) * mult
                    if (impact > 3.5f) events.add(GameEvent.Impact(p.body.x, p.body.y, impact, p.material))
                    if (p.kind == PieceKind.BLOCK && p.hp <= 0f) destroy(p)
                }
            }
            i--
        }
    }

    private fun destroy(p: Piece) {
        val b = p.body
        events.add(GameEvent.BlockDestroyed(b.x, b.y, b.halfW, b.halfH, b.angle, p.material, b.vx, b.vy))
        destroyedThisShot[p.owner]++
        removePiece(p)
    }

    private fun removePiece(p: Piece) {
        world.remove(p.body)
        pieces.remove(p)
    }

    private fun explode(keg: Piece) {
        val cx = keg.body.x
        val cy = keg.body.y
        removePiece(keg)
        events.add(GameEvent.Explosion(cx, cy, Balance.KEG_RADIUS))
        val mult = if (suddenDeath) 2f else 1f
        for (p in pieces.toList()) {
            val b = p.body
            val dx = b.x - cx
            val dy = b.y - cy
            val d = sqrt(dx * dx + dy * dy)
            if (d > Balance.KEG_RADIUS) continue
            val f = 1f - d / Balance.KEG_RADIUS
            val push = Balance.KEG_FORCE * f / max(1f, sqrt(b.mass))
            val nx = if (d > 0.01f) dx / d else 0f
            val ny = if (d > 0.01f) dy / d else 1f
            b.vx += nx * push
            b.vy += ny * push + push * 0.25f
            b.omega += (if (dx > 0f) -1f else 1f) * push * 0.3f
            if (p.kind != PieceKind.PROJECTILE) {
                p.hp -= Balance.KEG_FORCE * f * 0.9f * mult
                if (p.kind == PieceKind.BLOCK && p.hp <= 0f) destroy(p)
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        var i = pieces.size - 1
        while (i >= 0) {
            val p = pieces[i]
            if (p.kind == PieceKind.PROJECTILE) {
                p.age += dt
                if (p.hasHit) {
                    // Rolling resistance, so a spent shot comes to rest quickly.
                    p.body.omega *= 0.97f
                    p.body.vx *= 0.992f
                }
            }
            i--
        }
    }

    private fun removeLost() {
        var i = pieces.size - 1
        while (i >= 0) {
            val p = pieces[i]
            val b = p.body
            if (b.y < -14f || abs(b.x) > Balance.HALF_WORLD + 6f) {
                if (p.kind == PieceKind.BLOCK) destroyedThisShot[p.owner]++
                if (p.kind != PieceKind.KEEPER) removePiece(p)
            }
            i--
        }
    }

    private fun checkKeepers() {
        for (owner in 0..1) {
            if (keeperDown[owner]) continue
            val k = keepers[owner] ?: continue
            val b = k.body
            if (k.hp <= 0f || b.touchingGround || b.y < -1f || abs(b.angle) > 1.25f) {
                keeperDown[owner] = true
                events.add(GameEvent.KeeperDown(owner, b.x, b.y))
            }
        }
    }

    private fun endTurn() {
        pieces.filter { it.kind == PieceKind.PROJECTILE }.forEach { removePiece(it) }
        freeze()

        if (keeperDown[0] || keeperDown[1]) {
            roundWinner = when {
                keeperDown[0] && keeperDown[1] -> -1
                keeperDown[0] -> 1
                else -> 0
            }
            if (roundWinner >= 0) players[roundWinner].roundsWon++
            phase = if (roundWinner >= 0 && players[roundWinner].roundsWon >= Balance.ROUNDS_TO_WIN) {
                Phase.MATCH_OVER
            } else {
                Phase.ROUND_OVER
            }
            return
        }

        val shooter = players[current]
        shooter.destroyedLastShot = destroyedThisShot[1 - current]
        current = 1 - current
        turn++

        val next = players[current]
        var income = Balance.TURN_INCOME + min(Balance.MAX_DESTROY_BONUS, next.destroyedLastShot)
        // Comeback: the player with the more battered castle gets one extra point.
        if (blockCount(current) < blockCount(1 - current)) income++
        next.points = min(Balance.MAX_POINTS, next.points + income)
        next.destroyedLastShot = 0

        handoverTime = 1.3f
        phase = Phase.HANDOVER
    }

    fun blockCount(owner: Int): Int = pieces.count { it.owner == owner && it.kind == PieceKind.BLOCK }

    fun keeper(owner: Int): Piece? = keepers[owner]
}
