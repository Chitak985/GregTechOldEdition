package dev.gtoe.agent;

/** Registers special blocks like MachineMultiblock and AdvancedBlock. **/
public final class BlockRegistry {
    /** List of special block "factories" **/
    private static final Factory[] FACTORIES = new Factory[ItemCatalog.FIRST_ITEM_ID];

    static {
        // Register blocks here with a factory
        register(22, MachineMultiblock::new);
        register(24, FurnaceFuelHatch::new);
    }

    private BlockRegistry() {
    }

    /** Register a block using a factory and its ID. **/
    public static void register(int blockId, Factory factory) {
        if (blockId <= 0 || !ItemCatalog.isBlock(blockId) || factory == null) {
            throw new IllegalArgumentException("Expected a solid block ID and a block factory");
        }
        FACTORIES[blockId] = factory;
    }

    /** Unregister a block using its ID. **/
    public static void unregister(int blockId) {
        if (blockId <= 0 || !ItemCatalog.isBlock(blockId)) {
            throw new IllegalArgumentException("Expected a solid block ID");
        }
        FACTORIES[blockId] = null;
    }

    /** Check if a block can tick. **/
    public static boolean isTicking(int blockId) {
        return blockId > 0 && blockId < FACTORIES.length && FACTORIES[blockId] != null;
    }

    /** Create an AdvancedBlock with an ID at a position. **/
    public static AdvancedBlock create(Object level, int x, int y, int z, int blockId) {
        if (!isTicking(blockId)) {
            return null;
        }
        AdvancedBlock block = FACTORIES[blockId].create(level, x, y, z, blockId);
        if (block == null || block.level != level || block.x != x || block.y != y
                || block.z != z || block.blockId != blockId) {
            throw new IllegalStateException("Block factory returned invalid state for ID " + blockId);
        }
        return block;
    }

    @FunctionalInterface
    public interface Factory {
        AdvancedBlock create(Object level, int x, int y, int z, int blockId);
    }
}
