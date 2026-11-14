package io.github.beretta299.sharejoy.game

/** A block in castle-local coordinates: x runs from the back wall (0) toward the gorge, y is up. */
data class BlockSpec(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val material: Material, val role: Role, val flag: Boolean = false,
)

/** Blueprints for the starting castle and everything upgrades can add. */
object Castle {
    const val KEEPER_X = 4.5f
    const val KEEPER_Y = 3.2f
    const val KEEPER_HALF_W = 0.4f
    const val KEEPER_HALF_H = 0.7f

    val base: List<BlockSpec> = buildList {
        // Ground storey: three stone pillars.
        for (x in listOf(0.5f, 4.5f, 8.5f)) add(BlockSpec(x, 1f, 1f, 2f, Material.STONE, Role.GROUND_PILLAR))
        // First floor.
        add(BlockSpec(2.25f, 2.25f, 4.4f, 0.5f, Material.WOOD, Role.FLOOR_BEAM))
        add(BlockSpec(6.75f, 2.25f, 4.4f, 0.5f, Material.WOOD, Role.FLOOR_BEAM))
        // Upper storey: the Keeper's hall.
        for (x in listOf(0.3f, 3.0f, 6.0f, 8.7f)) add(BlockSpec(x, 3.5f, 0.6f, 2f, Material.WOOD, Role.UPPER_PILLAR))
        add(BlockSpec(2.25f, 4.75f, 4.4f, 0.5f, Material.WOOD, Role.ROOF_BEAM))
        add(BlockSpec(6.75f, 4.75f, 4.4f, 0.5f, Material.WOOD, Role.ROOF_BEAM))
        // Battlements, with a taller tower at each end.
        for (x in listOf(0.5f, 3.0f, 6.0f, 8.5f)) add(BlockSpec(x, 5.5f, 1f, 1f, Material.STONE, Role.MERLON))
        add(BlockSpec(0.5f, 6.5f, 1f, 1f, Material.STONE, Role.MERLON, flag = true))
        add(BlockSpec(8.5f, 6.5f, 1f, 1f, Material.STONE, Role.MERLON))
        // Curtain wall facing the enemy.
        for (y in listOf(0.75f, 2.25f, 3.75f)) add(BlockSpec(10.3f, y, 1.3f, 1.5f, Material.STONE, Role.FRONT_WALL))
    }

    /** Blocks added by each level of the Cover track. */
    val cover: List<List<BlockSpec>> = listOf(
        listOf(1.75f, 4.5f, 7.25f).map { BlockSpec(it, 5.5f, 1f, 1f, Material.STONE, Role.COVER) },
        listOf(2.75f, 6.25f).map { BlockSpec(it, 6.2f, 3f, 0.4f, Material.WOOD, Role.COVER) },
        listOf(0.75f, 2.25f, 3.75f).map { BlockSpec(13.2f, it, 1.3f, 1.5f, Material.STONE, Role.COVER) },
    )

    /** Blocks added by the last level of the Walls track. */
    val buttresses: List<BlockSpec> = listOf(
        BlockSpec(-0.75f, 0.5f, 1.4f, 1f, Material.STONE, Role.BUTTRESS),
        BlockSpec(11.7f, 0.5f, 1.4f, 1f, Material.STONE, Role.BUTTRESS),
    )
}
