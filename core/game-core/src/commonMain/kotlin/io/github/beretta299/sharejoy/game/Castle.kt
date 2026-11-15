package io.github.beretta299.sharejoy.game

/** A block in castle-local coordinates: x runs from the back wall (0) toward the gorge, y is up. */
data class BlockSpec(
    val x: Float, val y: Float, val width: Float, val height: Float,
    val material: Material, val role: Role, val hasFlag: Boolean = false,
)

/**
 * Blueprints for the starting tower and everything upgrades can add.
 *
 * The tower is tall and narrow because each player's half of the battlefield is one portrait phone screen.
 */
object Castle {
    const val KEEPER_X = 2.5f
    const val KEEPER_Y = 5.7f
    const val KEEPER_HALF_WIDTH = 0.4f
    const val KEEPER_HALF_HEIGHT = 0.7f

    private const val CURTAIN_WALL_X = 6.2f

    val base: List<BlockSpec> = buildList {
        // Ground storey: three stone pillars.
        for (x in listOf(0.5f, 2.5f, 4.5f)) add(BlockSpec(x, 1f, 1f, 2f, Material.STONE, Role.GROUND_PILLAR))
        add(BlockSpec(2.5f, 2.25f, 5f, 0.5f, Material.WOOD, Role.FLOOR_BEAM))
        // Middle storey.
        for (x in listOf(0.4f, 4.6f)) add(BlockSpec(x, 3.5f, 0.8f, 2f, Material.WOOD, Role.UPPER_PILLAR))
        add(BlockSpec(2.5f, 4.75f, 5f, 0.5f, Material.WOOD, Role.FLOOR_BEAM))
        // Top storey: the Keeper's hall.
        for (x in listOf(0.4f, 4.6f)) add(BlockSpec(x, 6f, 0.8f, 2f, Material.STONE, Role.UPPER_PILLAR))
        add(BlockSpec(2.5f, 7.25f, 5f, 0.5f, Material.WOOD, Role.ROOF_BEAM))
        // Battlements, with a turret at the back carrying the flag.
        for (x in listOf(0.5f, 2.5f, 4.5f)) add(BlockSpec(x, 8f, 1f, 1f, Material.STONE, Role.MERLON))
        add(BlockSpec(0.5f, 9f, 1f, 1f, Material.STONE, Role.MERLON, hasFlag = true))
        // Curtain wall facing the enemy.
        for (y in listOf(0.75f, 2.25f, 3.75f)) add(BlockSpec(CURTAIN_WALL_X, y, 1.3f, 1.5f, Material.STONE, Role.FRONT_WALL))
    }

    /** Blocks added by each level of the Cover track. */
    val cover: List<List<BlockSpec>> = listOf(
        listOf(1.5f, 3.5f).map { BlockSpec(it, 8f, 1f, 1f, Material.STONE, Role.COVER) },
        listOf(BlockSpec(2.9f, 8.7f, 3.6f, 0.4f, Material.WOOD, Role.COVER)),
        listOf(5.25f, 6.75f).map { BlockSpec(CURTAIN_WALL_X, it, 1.3f, 1.5f, Material.STONE, Role.FRONT_WALL) },
    )

    /** Block added by the last level of the Walls track, bracing the foot of the curtain wall. */
    val buttresses: List<BlockSpec> = listOf(
        BlockSpec(7.5f, 0.5f, 1.2f, 1f, Material.STONE, Role.BUTTRESS),
    )
}
