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

    /** Index of the player whose turn it is. */
    var currentPlayer = 0
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
    private val blocksDestroyedThisShot = IntArray(2)
    private var secondsSinceAction = 0f
    private var calmSeconds = 0f
    private var handoverSecondsLeft = 0f
    private var seedState = 12345
    private var firstPlayerOfRound = 0

    val activePlayer: PlayerState get() = players[currentPlayer]
    val suddenDeath: Boolean get() = turn >= Balance.SUDDEN_DEATH_TURN
    private val damageMultiplier: Float get() = if (suddenDeath) SUDDEN_DEATH_DAMAGE_MULTIPLIER else 1f

    init {
        startRound()
    }

    // ------------------------------------------------------------------ geometry

    /** -1 for the left player, +1 for the right. */
    fun sideSign(player: Int): Float = if (player == 0) -1f else 1f

    /** Converts a castle-blueprint x (0 at the back wall, growing toward the enemy) to world x. */
    fun castleToWorldX(player: Int, castleX: Float): Float = sideSign(player) * (Balance.CASTLE_BACK_X - castleX)

    fun slingshotX(player: Int): Float = sideSign(player) * Balance.SLINGSHOT_X

    fun blockCount(player: Int): Int = pieces.count { it.owner == player && it.kind == PieceKind.BLOCK }

    fun keeper(player: Int): Piece? = keepers[player]

    // ------------------------------------------------------------------ rounds

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

        addGround()
        for (player in 0..1) {
            Castle.base.forEach { addBlock(it, player, lift = 0f) }
            addKeeper(player)
        }
        world.sleepAll()

        currentPlayer = firstPlayerOfRound
        players[currentPlayer].points = Balance.POINTS_PER_TURN
        // Going second is a disadvantage, so that player starts with a point in hand.
        players[1 - currentPlayer].points = SECOND_PLAYER_START_POINTS
        phase = Phase.PLAN
    }

    /** Two static slabs of ground, one on each side of the gorge. */
    private fun addGround() {
        for (side in intArrayOf(-1, 1)) {
            val ground = Body(world.newId(), halfWidth = GROUND_HALF_WIDTH, halfHeight = GROUND_HALF_DEPTH, density = 0f, friction = 0.9f)
            ground.x = side * (Balance.GORGE_HALF_WIDTH + GROUND_HALF_WIDTH)
            ground.y = -GROUND_HALF_DEPTH
            ground.isGround = true
            world.add(ground)
        }
    }

    private fun addKeeper(player: Int) {
        val body = Body(
            world.newId(), Castle.KEEPER_HALF_WIDTH, Castle.KEEPER_HALF_HEIGHT,
            density = 1f, friction = 0.8f, restitution = 0f,
        )
        body.x = castleToWorldX(player, Castle.KEEPER_X)
        body.y = Castle.KEEPER_Y
        val keeper = Piece(body, PieceKind.KEEPER, Material.WOOD, player, cosmeticSeed = nextCosmeticSeed())
        keeper.maxHealth = Balance.KEEPER_HEALTH
        keeper.health = Balance.KEEPER_HEALTH
        addPiece(keeper)
        keepers[player] = keeper
    }

    private fun addBlock(spec: BlockSpec, player: Int, lift: Float): Piece {
        val body = Body(
            world.newId(), spec.width / 2f, spec.height / 2f,
            density = spec.material.density, friction = spec.material.friction,
        )
        body.x = castleToWorldX(player, spec.x)
        body.y = spec.y + lift
        val piece = Piece(body, PieceKind.BLOCK, spec.material, player, spec.role, nextCosmeticSeed())
        piece.maxHealth = spec.material.toughness
        piece.health = piece.maxHealth
        piece.hasFlag = spec.hasFlag
        addPiece(piece)
        return piece
    }

    private fun addPiece(piece: Piece) {
        piece.body.userData = piece
        world.add(piece.body)
        pieces.add(piece)
    }

    private fun removePiece(piece: Piece) {
        world.remove(piece.body)
        pieces.remove(piece)
    }

    /** Small deterministic random numbers for cosmetic variation. */
    private fun nextCosmeticSeed(): Int {
        seedState = seedState * 1103515245 + 12345
        return (seedState ushr 8) and 0xFFFF
    }

    // ------------------------------------------------------------------ building

    /** Price of the next level of [track], or null when it is maxed out. */
    fun upgradeCost(track: Track): Int? = Balance.UPGRADE_COSTS.getOrNull(activePlayer.level(track))

    fun canBuy(track: Track): Boolean {
        val price = upgradeCost(track) ?: return false
        return phase == Phase.PLAN && activePlayer.points >= price
    }

    fun buyUpgrade(track: Track): Boolean {
        if (!canBuy(track)) return false
        val player = activePlayer
        player.points -= upgradeCost(track)!!
        player.levels[track.ordinal]++
        val level = player.level(track)
        when (track) {
            Track.POWER, Track.AIM -> Unit
            Track.AMMO -> player.selectedAmmo = Ammo.entries[level]
            Track.WALLS -> upgradeWalls(player.index, level)
            Track.COVER -> placeBlocks(Castle.cover[level - 1], player.index)
        }
        return true
    }

    private fun upgradeWalls(player: Int, level: Int) {
        when (level) {
            1 -> rebuildInMaterial(player, Role.UPPER_PILLAR, Material.STONE)
            2 -> rebuildInMaterial(player, Role.GROUND_PILLAR, Material.IRON)
            else -> {
                rebuildInMaterial(player, Role.FRONT_WALL, Material.IRON)
                placeBlocks(Castle.buttresses, player)
            }
        }
    }

    /** Swaps every block of [role] to [material], keeping each block's share of health. */
    private fun rebuildInMaterial(player: Int, role: Role, material: Material) {
        for (piece in pieces) {
            if (piece.owner != player || piece.role != role || piece.kind != PieceKind.BLOCK) continue
            val healthShare = piece.health / piece.maxHealth
            piece.material = material
            piece.maxHealth = material.toughness
            piece.health = piece.maxHealth * healthShare
            piece.body.setDensity(material.density)
            piece.body.friction = material.friction
            events.add(GameEvent.Built(piece.body.x, piece.body.y))
        }
    }

    /** Drops new blocks into the castle, sliding each one up until it is clear of what is already there. */
    private fun placeBlocks(specs: List<BlockSpec>, player: Int) {
        for (spec in specs) {
            val x = castleToWorldX(player, spec.x)
            var lift = PLACEMENT_START_LIFT
            while (lift < PLACEMENT_MAX_LIFT && overlapsAnyBody(x, spec.y + lift, spec.width / 2f, spec.height / 2f)) {
                lift += PLACEMENT_LIFT_STEP
            }
            val piece = addBlock(spec, player, lift)
            events.add(GameEvent.Built(piece.body.x, piece.body.y))
        }
        secondsSinceAction = 0f
        calmSeconds = 0f
        phase = Phase.BUILDING
    }

    private fun overlapsAnyBody(x: Float, y: Float, halfWidth: Float, halfHeight: Float): Boolean = world.bodies.any {
        abs(it.x - x) < halfWidth + it.boundingHalfWidth() - PLACEMENT_TOLERANCE &&
            abs(it.y - y) < halfHeight + it.boundingHalfHeight() - PLACEMENT_TOLERANCE
    }

    private fun damagedBlocksOfActivePlayer(): List<Piece> =
        pieces.filter { it.owner == currentPlayer && it.kind == PieceKind.BLOCK && it.health < it.maxHealth }

    fun canRepair(): Boolean = phase == Phase.PLAN && activePlayer.points >= Balance.REPAIR_COST &&
        damagedBlocksOfActivePlayer().isNotEmpty()

    /** Fully mends the most damaged blocks of the active player. */
    fun repair(): Boolean {
        if (!canRepair()) return false
        activePlayer.points -= Balance.REPAIR_COST
        damagedBlocksOfActivePlayer()
            .sortedByDescending { it.damageFraction }
            .take(Balance.BLOCKS_PER_REPAIR)
            .forEach {
                it.health = it.maxHealth
                events.add(GameEvent.Repaired(it.body.x, it.body.y))
            }
        return true
    }

    fun selectAmmo(ammo: Ammo) {
        if (phase == Phase.PLAN && ammo in activePlayer.unlockedAmmo()) activePlayer.selectedAmmo = ammo
    }

    // ------------------------------------------------------------------ shooting

    fun launchSpeed(player: PlayerState = activePlayer): Float =
        Balance.BASE_LAUNCH_SPEED * (1f + Balance.LAUNCH_SPEED_PER_POWER_LEVEL * player.level(Track.POWER))

    /** How many seconds of flight the trajectory guide shows. */
    fun aimGuideSeconds(player: PlayerState = activePlayer): Float = Balance.AIM_GUIDE_SECONDS[player.level(Track.AIM)]

    /**
     * Fires from the pouch, which sits at the slingshot anchor + ([pullX], [pullY]).
     * The shot flies back through the anchor; a longer pull is a faster shot.
     */
    fun fire(pullX: Float, pullY: Float): Boolean {
        if (phase != Phase.PLAN) return false
        val pullLength = sqrt(pullX * pullX + pullY * pullY)
        if (pullLength < MIN_PULL_TO_FIRE) return false
        val clamp = min(pullLength, Balance.MAX_SLINGSHOT_PULL) / pullLength
        val clampedPullX = pullX * clamp
        val clampedPullY = pullY * clamp
        val speedPerMetreOfPull = launchSpeed() / Balance.MAX_SLINGSHOT_PULL
        val ammo = activePlayer.selectedAmmo

        val body = projectileBody(ammo.radius, ammo.density, ammo.impactMultiplier)
        body.x = slingshotX(currentPlayer) + clampedPullX
        body.y = max(Balance.SLINGSHOT_Y + clampedPullY, ammo.radius + 0.05f)
        body.velocityX = -clampedPullX * speedPerMetreOfPull
        body.velocityY = -clampedPullY * speedPerMetreOfPull
        val shot = Piece(body, PieceKind.PROJECTILE, Material.STONE, currentPlayer, cosmeticSeed = nextCosmeticSeed())
        shot.ammo = ammo
        addPiece(shot)

        blocksDestroyedThisShot.fill(0)
        secondsSinceAction = 0f
        calmSeconds = 0f
        phase = Phase.RESOLVING
        events.add(GameEvent.Fired(body.x, body.y))
        return true
    }

    /** Breaks a split shot in the air into three fragments that fan out slightly. */
    fun splitShot(): Boolean {
        if (phase != Phase.RESOLVING) return false
        val shot = pieces.firstOrNull {
            it.kind == PieceKind.PROJECTILE && it.ammo == Ammo.SPLIT && !it.isFragment && !it.hasHit
        } ?: return false
        val parent = shot.body
        removePiece(shot)
        for (index in -1..1) {
            val turnAngle = index * SPLIT_FAN_ANGLE
            val cosTurn = cos(turnAngle)
            val sinTurn = sin(turnAngle)
            val body = projectileBody(SPLIT_FRAGMENT_RADIUS, SPLIT_FRAGMENT_DENSITY, SPLIT_FRAGMENT_IMPACT_MULTIPLIER)
            body.x = parent.x
            body.y = parent.y + index * SPLIT_FRAGMENT_SPACING
            body.velocityX = parent.velocityX * cosTurn - parent.velocityY * sinTurn
            body.velocityY = parent.velocityX * sinTurn + parent.velocityY * cosTurn
            val fragment = Piece(body, PieceKind.PROJECTILE, Material.STONE, currentPlayer, cosmeticSeed = nextCosmeticSeed())
            fragment.ammo = Ammo.SPLIT
            fragment.isFragment = true
            fragment.flightSeconds = shot.flightSeconds
            addPiece(fragment)
        }
        events.add(GameEvent.Split)
        return true
    }

    private fun projectileBody(radius: Float, density: Float, impactMultiplier: Float): Body {
        val body = Body(world.newId(), 0f, 0f, radius = radius, density = density, friction = 0.6f, restitution = 0.15f)
        body.impactMultiplier = impactMultiplier
        return body
    }

    // ------------------------------------------------------------------ simulation

    fun update(dt: Float) {
        when (phase) {
            Phase.PLAN, Phase.ROUND_OVER, Phase.MATCH_OVER -> Unit
            Phase.HANDOVER -> updateHandover(dt)
            Phase.BUILDING -> updateBuilding(dt)
            Phase.RESOLVING -> updateResolving(dt)
        }
    }

    private fun updateHandover(dt: Float) {
        handoverSecondsLeft -= dt
        if (handoverSecondsLeft <= 0f) phase = Phase.PLAN
    }

    /** New blocks drop into place; planning resumes once they settle. */
    private fun updateBuilding(dt: Float) {
        stepWorld(dt)
        val settled = calmSeconds > BUILD_CALM_SECONDS && secondsSinceAction > BUILD_MIN_SECONDS
        if (settled || secondsSinceAction > BUILD_MAX_SECONDS) {
            world.sleepAll()
            phase = Phase.PLAN
        }
    }

    /** The shot flies and the castles take damage; the turn ends once everything is still. */
    private fun updateResolving(dt: Float) {
        stepWorld(dt)
        applyImpactDamage()
        updateProjectiles(dt)
        removeOutOfBounds()
        checkKeepers()
        val settled = calmSeconds > RESOLVE_CALM_SECONDS && secondsSinceAction > RESOLVE_MIN_SECONDS
        if (settled || secondsSinceAction > RESOLVE_MAX_SECONDS) endTurn()
    }

    private fun stepWorld(dt: Float) {
        world.step(dt)
        secondsSinceAction += dt
        calmSeconds = if (isWorldCalm()) calmSeconds + dt else 0f
    }

    private fun isWorldCalm(): Boolean = world.bodies.all {
        it.isStatic || it.isAsleep ||
            (it.speedSquared() <= CALM_SPEED_SQUARED && abs(it.angularVelocity) <= CALM_ANGULAR_SPEED)
    }

    private fun applyImpactDamage() {
        var i = pieces.size - 1
        while (i >= 0) {
            val piece = pieces[i]
            val impact = piece.body.strongestImpact
            if (impact > 0f) {
                if (piece.kind == PieceKind.PROJECTILE) {
                    if (!piece.hasHit) {
                        piece.hasHit = true
                        if (piece.ammo == Ammo.KEG) {
                            explode(piece)
                            // The blast may have removed other pieces; carry on from a valid index.
                            i = min(i, pieces.size) - 1
                            continue
                        }
                    }
                } else if (impact > Balance.IMPACT_DAMAGE_THRESHOLD) {
                    if (impact > VISIBLE_IMPACT_STRENGTH) {
                        events.add(GameEvent.Impact(piece.body.x, piece.body.y, impact, piece.material))
                    }
                    damage(piece, (impact - Balance.IMPACT_DAMAGE_THRESHOLD) * damageMultiplier)
                }
            }
            i--
        }
    }

    private fun damage(piece: Piece, amount: Float) {
        piece.health -= amount
        if (piece.kind == PieceKind.BLOCK && piece.health <= 0f) destroyBlock(piece)
    }

    private fun destroyBlock(block: Piece) {
        val body = block.body
        events.add(
            GameEvent.BlockDestroyed(
                body.x, body.y, body.halfWidth, body.halfHeight, body.angle,
                block.material, body.velocityX, body.velocityY,
            ),
        )
        blocksDestroyedThisShot[block.owner]++
        removePiece(block)
    }

    /** A powder keg throws everything nearby outward and damages it, more the closer it is. */
    private fun explode(keg: Piece) {
        val centreX = keg.body.x
        val centreY = keg.body.y
        removePiece(keg)
        events.add(GameEvent.Explosion(centreX, centreY, Balance.KEG_BLAST_RADIUS))
        for (piece in pieces.toList()) {
            val body = piece.body
            val offsetX = body.x - centreX
            val offsetY = body.y - centreY
            val distance = sqrt(offsetX * offsetX + offsetY * offsetY)
            if (distance > Balance.KEG_BLAST_RADIUS) continue
            val closeness = 1f - distance / Balance.KEG_BLAST_RADIUS
            val push = Balance.KEG_BLAST_FORCE * closeness / max(1f, sqrt(body.mass))
            val directionX = if (distance > MIN_BLAST_DISTANCE) offsetX / distance else 0f
            val directionY = if (distance > MIN_BLAST_DISTANCE) offsetY / distance else 1f
            body.velocityX += directionX * push
            body.velocityY += directionY * push + push * BLAST_UPWARD_KICK
            body.angularVelocity += (if (offsetX > 0f) -1f else 1f) * push * BLAST_SPIN
            if (piece.kind != PieceKind.PROJECTILE) {
                damage(piece, Balance.KEG_BLAST_FORCE * closeness * BLAST_DAMAGE_SCALE * damageMultiplier)
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        for (piece in pieces) {
            if (piece.kind != PieceKind.PROJECTILE) continue
            piece.flightSeconds += dt
            if (piece.hasHit) {
                // Rolling resistance, so a spent shot comes to rest quickly.
                piece.body.angularVelocity *= SPENT_SHOT_SPIN_DAMPING
                piece.body.velocityX *= SPENT_SHOT_ROLL_DAMPING
            }
        }
    }

    /** Removes anything that fell into the gorge or left the field. A block lost this way counts as destroyed. */
    private fun removeOutOfBounds() {
        for (piece in pieces.toList()) {
            val body = piece.body
            val outOfBounds = body.y < LOST_BELOW_Y || abs(body.x) > Balance.HALF_FIELD_WIDTH + LOST_BEYOND_FIELD
            if (!outOfBounds) continue
            if (piece.kind == PieceKind.BLOCK) blocksDestroyedThisShot[piece.owner]++
            if (piece.kind != PieceKind.KEEPER) removePiece(piece)
        }
    }

    /** A Keeper falls when hurt enough, knocked to the ground, into the gorge, or tipped over. */
    private fun checkKeepers() {
        for (player in 0..1) {
            if (keeperDown[player]) continue
            val keeper = keepers[player] ?: continue
            val body = keeper.body
            val fallen = keeper.health <= 0f || body.isTouchingGround || body.y < KEEPER_FALLEN_BELOW_Y ||
                abs(body.angle) > KEEPER_TIPPED_ANGLE
            if (fallen) {
                keeperDown[player] = true
                events.add(GameEvent.KeeperDown(player, body.x, body.y))
            }
        }
    }

    private fun endTurn() {
        pieces.filter { it.kind == PieceKind.PROJECTILE }.forEach { removePiece(it) }
        world.sleepAll()
        if (keeperDown[0] || keeperDown[1]) endRound() else passTurn()
    }

    private fun endRound() {
        roundWinner = when {
            keeperDown[0] && keeperDown[1] -> -1
            keeperDown[0] -> 1
            else -> 0
        }
        if (roundWinner >= 0) players[roundWinner].roundsWon++
        val matchWon = roundWinner >= 0 && players[roundWinner].roundsWon >= Balance.ROUNDS_TO_WIN
        phase = if (matchWon) Phase.MATCH_OVER else Phase.ROUND_OVER
    }

    private fun passTurn() {
        players[currentPlayer].blocksDestroyedLastShot = blocksDestroyedThisShot[1 - currentPlayer]
        currentPlayer = 1 - currentPlayer
        turn++

        val next = players[currentPlayer]
        var income = Balance.POINTS_PER_TURN + min(Balance.MAX_DESTRUCTION_BONUS, next.blocksDestroyedLastShot)
        // Comeback: the player with the more battered castle gets one extra point.
        if (blockCount(currentPlayer) < blockCount(1 - currentPlayer)) income++
        next.points = min(Balance.MAX_SAVED_POINTS, next.points + income)
        next.blocksDestroyedLastShot = 0

        handoverSecondsLeft = HANDOVER_SECONDS
        phase = Phase.HANDOVER
    }

    private companion object {
        const val SECOND_PLAYER_START_POINTS = 1
        const val SUDDEN_DEATH_DAMAGE_MULTIPLIER = 2f
        const val GROUND_HALF_WIDTH = 20f
        const val GROUND_HALF_DEPTH = 6f

        const val PLACEMENT_START_LIFT = 0.12f
        const val PLACEMENT_LIFT_STEP = 0.25f
        const val PLACEMENT_MAX_LIFT = 9f
        const val PLACEMENT_TOLERANCE = 0.02f

        /** Shorter pulls are treated as a cancelled shot. */
        const val MIN_PULL_TO_FIRE = 0.4f
        const val SPLIT_FAN_ANGLE = 0.13f
        const val SPLIT_FRAGMENT_RADIUS = 0.25f
        const val SPLIT_FRAGMENT_DENSITY = 6f
        const val SPLIT_FRAGMENT_IMPACT_MULTIPLIER = 1.7f
        const val SPLIT_FRAGMENT_SPACING = 0.55f

        const val CALM_SPEED_SQUARED = 0.07f
        const val CALM_ANGULAR_SPEED = 0.35f
        const val BUILD_CALM_SECONDS = 0.35f
        const val BUILD_MIN_SECONDS = 0.5f
        const val BUILD_MAX_SECONDS = 2.5f
        const val RESOLVE_CALM_SECONDS = 0.6f
        const val RESOLVE_MIN_SECONDS = 1f
        const val RESOLVE_MAX_SECONDS = 12f
        const val HANDOVER_SECONDS = 1.3f

        /** Impacts stronger than this emit an event, so the UI can show dust and shake. */
        const val VISIBLE_IMPACT_STRENGTH = 3.5f
        const val MIN_BLAST_DISTANCE = 0.01f
        const val BLAST_UPWARD_KICK = 0.25f
        const val BLAST_SPIN = 0.3f
        const val BLAST_DAMAGE_SCALE = 0.9f
        const val SPENT_SHOT_SPIN_DAMPING = 0.97f
        const val SPENT_SHOT_ROLL_DAMPING = 0.992f

        const val LOST_BELOW_Y = -14f
        const val LOST_BEYOND_FIELD = 6f
        const val KEEPER_FALLEN_BELOW_Y = -1f
        const val KEEPER_TIPPED_ANGLE = 1.25f
    }
}
