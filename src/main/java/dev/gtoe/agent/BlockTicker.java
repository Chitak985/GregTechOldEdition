package dev.gtoe.agent;

import java.lang.reflect.Field;

/** World-owned block instances and ticking, driven by RubyDung's 60 Hz simulation. */
public final class BlockTicker {
    public static final int TICKS_PER_SECOND = 20;
    private static final int GAME_TICKS_PER_BLOCK_TICK = 3;
    private static final String STATE_FIELD = "gtoe$blockTicker";
    private final Object level;
    private final byte[] blockIds;
    private final AdvancedBlock[] blocks;
    private final int width;
    private final int height;
    private final int depth;
    private int gameTicks;
    private long ticks;
    private boolean tickErrorReported;

    private BlockTicker(Object level) throws ReflectiveOperationException {
        this.level = level;
        Class<?> type = level.getClass();
        width = type.getField("width").getInt(level);
        height = type.getField("height").getInt(level); // Horizontal Z size in rd-132211.
        depth = type.getField("depth").getInt(level); // Vertical Y size.
        Field field = type.getDeclaredField("blocks");
        field.setAccessible(true);
        blockIds = (byte[]) field.get(level);
        if (width <= 0 || height <= 0 || depth <= 0
                || (long) width * height * depth != blockIds.length) {
            throw new IllegalArgumentException("Unexpected world dimensions or block array");
        }
        blocks = new AdvancedBlock[blockIds.length];
        for (int y = 0; y < depth; y++) {
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    updateBlock(x, y, z);
                }
            }
        }
    }

    /** Called after terrain conversion, including when a typed saved world is opened. */
    public static BlockTicker initialize(Object level) {
        try {
            return new BlockTicker(level);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot initialize world block ticking", error);
        }
    }

    /** Runs on the game thread; three 60 Hz game ticks produce one 20 Hz block tick. */
    public static void onGameTick(Object level) {
        BlockTicker ticker = forLevel(level);
        if (ticker != null && ++ticker.gameTicks == GAME_TICKS_PER_BLOCK_TICK) {
            ticker.gameTicks = 0;
            ticker.tickBlocks();
        }
    }

    /** Called by Level.setTile, so edits from machine code are tracked too. */
    public static void onBlockChanged(Object level, int x, int y, int z) {
        BlockTicker ticker = forLevel(level);
        if (ticker != null && ticker.inBounds(x, y, z)) {
            ticker.updateBlock(x, y, z);
        }
    }

    /** A live reload rebuilds instances; the constructor's initial load waits for terrain. */
    public static void onLevelLoaded(Object level) {
        if (forLevel(level) != null) {
            try {
                level.getClass().getField(STATE_FIELD).set(level, initialize(level));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot reload block state", error);
            }
        }
    }

    public static BlockTicker forLevel(Object level) {
        if (level == null) {
            return null;
        }
        try {
            return (BlockTicker) level.getClass().getField(STATE_FIELD).get(level);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Level is missing the block ticking hook", error);
        }
    }

    /** Returns the persistent instance at a position; air/outside the world returns null. */
    public AdvancedBlock blockAt(int x, int y, int z) {
        return inBounds(x, y, z) ? blocks[index(x, y, z)] : null;
    }

    public long tickCount() {
        return ticks;
    }

    private void updateBlock(int x, int y, int z) {
        int index = index(x, y, z);
        int id = blockIds[index] & 255;
        AdvancedBlock existing = blocks[index];
        if (existing == null || existing.blockId != id) {
            blocks[index] = BlockRegistry.create(level, x, y, z, id);
        }
    }

    private void tickBlocks() {
        ticks++;
        // Traverse persistent instances without reflection or reading the world's block IDs.
        // Changes made by Tick are visible to subsequent blocks in this traversal.
        for (int index = 0; index < blocks.length; index++) {
            AdvancedBlock block = blocks[index];
            if (block == null) {
                continue;
            }
            try {
                block.Tick();
            } catch (RuntimeException error) {
                if (!tickErrorReported) {
                    tickErrorReported = true;
                    System.err.println("[gtoe] Block Tick failed at "
                            + block.x + "," + block.y + "," + block.z);
                    error.printStackTrace(System.err);
                }
            }
        }
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < width && y < depth && z < height;
    }

    private int index(int x, int y, int z) {
        return (y * height + z) * width + x;
    }
}
