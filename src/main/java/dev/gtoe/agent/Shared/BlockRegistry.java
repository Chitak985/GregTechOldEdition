package dev.gtoe.agent;

/** Opt block IDs into ticking here before a world loads. Ordinary terrain has no instances. */
public final class BlockRegistry {
    private static final Factory[] FACTORIES = new Factory[ItemCatalog.FIRST_ITEM_ID];

    static {
        register(22, MachineMultiblock::new);
    }

    private BlockRegistry() {
    }

    public static void register(int blockId, Factory factory) {
        if (blockId <= 0 || !ItemCatalog.isBlock(blockId) || factory == null) {
            throw new IllegalArgumentException("Expected a solid block ID and a block factory");
        }
        FACTORIES[blockId] = factory;
    }

    public static void unregister(int blockId) {
        if (blockId <= 0 || !ItemCatalog.isBlock(blockId)) {
            throw new IllegalArgumentException("Expected a solid block ID");
        }
        FACTORIES[blockId] = null;
    }

    public static boolean isTicking(int blockId) {
        return blockId > 0 && blockId < FACTORIES.length && FACTORIES[blockId] != null;
    }

    static AdvancedBlock create(Object level, int x, int y, int z, int blockId) {
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
