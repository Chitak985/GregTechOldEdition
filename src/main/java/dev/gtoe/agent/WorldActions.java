package dev.gtoe.agent;

import java.lang.reflect.Method;

/** Inventory-aware break/place/interact operations called from transformed RubyDung bytecode. */
public final class WorldActions {
    private static volatile Access access;
    private static volatile boolean reflectionErrorReported;

    private WorldActions() {
    }

    public static void breakBlockOrInteract(Object level, int x, int y, int z) {
        if (level == null || GuiManager.blocksWorldAction()) {
            return;
        }

        try {
            int blockId = blockIdAt(level, x, y, z);
            if (blockId <= 0 || !ItemCatalog.isBlock(blockId)) {
                return;
            }

            // Planks demonstrate the reusable block GUI. Shift preserves access to breaking it.
            if (!GuiManager.isShiftDown() && BlockGuiRegistry.openForBlock(blockId)) {
                return;
            }

            setTile(level, x, y, z, 0);
            if (blockIdAt(level, x, y, z) == 0) {
                Inventory.add(blockId, 1);
                System.out.println("[gtoe] Collected " + ItemCatalog.itemName(blockId));
            }
        } catch (Throwable error) {
            reportReflectionError(error);
        }
    }

    public static void placeSelectedBlock(Object level, int x, int y, int z, int blockId) {
        if (level == null || GuiManager.blocksWorldAction() || blockId <= 0
                || !ItemCatalog.isBlock(blockId)) {
            return;
        }

        boolean reserved = false;
        try {
            if (blockIdAt(level, x, y, z) != 0 || !Inventory.remove(blockId, 1)) {
                return;
            }
            reserved = true;

            setTile(level, x, y, z, blockId);
            if (blockIdAt(level, x, y, z) == blockId) {
                reserved = false;
            } else {
                Inventory.add(blockId, 1);
                reserved = false;
            }
        } catch (Throwable error) {
            if (reserved) {
                Inventory.add(blockId, 1);
            }
            reportReflectionError(error);
        }
    }

    private static int blockIdAt(Object level, int x, int y, int z) throws Exception {
        Access current = accessFor(level);
        Object result = current.getBlockId.invoke(level,
                Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
        return ((Integer) result).intValue();
    }

    private static void setTile(Object level, int x, int y, int z, int blockId) throws Exception {
        Access current = accessFor(level);
        current.setTile.invoke(level,
                Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z), Integer.valueOf(blockId));
    }

    private static Access accessFor(Object level) throws Exception {
        Access current = access;
        if (current != null && current.levelClass == level.getClass()) {
            return current;
        }

        synchronized (WorldActions.class) {
            current = access;
            if (current == null || current.levelClass != level.getClass()) {
                Method getBlockId = level.getClass().getMethod(
                        GtoeTransformer.BLOCK_ID_METHOD,
                        Integer.TYPE, Integer.TYPE, Integer.TYPE);
                Method setTile = level.getClass().getMethod(
                        "setTile", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE);
                current = new Access(level.getClass(), getBlockId, setTile);
                access = current;
            }
            return current;
        }
    }

    private static void reportReflectionError(Throwable error) {
        if (!reflectionErrorReported) {
            reflectionErrorReported = true;
            System.err.println("[gtoe] Inventory-aware world action failed");
            error.printStackTrace(System.err);
        }
    }

    static synchronized void resetForTests() {
        access = null;
        reflectionErrorReported = false;
    }

    private static final class Access {
        private final Class<?> levelClass;
        private final Method getBlockId;
        private final Method setTile;

        private Access(Class<?> levelClass, Method getBlockId, Method setTile) {
            this.levelClass = levelClass;
            this.getBlockId = getBlockId;
            this.setTile = setTile;
        }
    }
}
