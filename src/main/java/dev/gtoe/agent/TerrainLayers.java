package dev.gtoe.agent;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

/** Runtime layer configuration and one-time conversion of the game's block array. */
public final class TerrainLayers {
    public static final int MAX_BLOCK_ID = 27;

    private static final int[] LAYER_STARTS = {0, 8, 20, 32, 40};
    private static final int[] BLOCK_IDS = {5, 4, 3, 2, 1};

    private TerrainLayers() {
    }

    public static void printConfiguration() {
        System.out.println("[gtoe] Fixed Y layers: " + describe());
    }

    /** Called from transformed Level constructors after level.dat has been loaded. */
    public static void apply(Object level) {
        try {
            Class<?> levelClass = level.getClass();
            Field blocksField = levelClass.getDeclaredField("blocks");
            blocksField.setAccessible(true);

            byte[] blocks = (byte[]) blocksField.get(level);
            int width = levelClass.getField("width").getInt(level);
            int height = levelClass.getField("height").getInt(level);
            int depth = levelClass.getField("depth").getInt(level);

            long expectedLength = (long) width * height * depth;
            if (expectedLength != blocks.length) {
                throw new IllegalStateException(
                        "unexpected block array length " + blocks.length + " (expected " + expectedLength + ")");
            }

            for (byte block : blocks) {
                if (block != 0 && block != 1) {
                    System.out.println(
                            "[gtoe] Existing typed blocks detected; preserving saved block IDs");
                    return;
                }
            }

            int changed = 0;
            for (int y = 0; y < depth; y++) {
                byte targetId = (byte) blockIdForY(y);
                for (int z = 0; z < height; z++) {
                    int baseIndex = (y * height + z) * width;
                    for (int x = 0; x < width; x++) {
                        int index = baseIndex + x;
                        if (blocks[index] != 0 && blocks[index] != targetId) {
                            blocks[index] = targetId;
                            changed++;
                        }
                    }
                }
            }

            Random random = new Random();

            int patches = TerrainPatchGenerator.generate(
                    blocks, width, height, depth, random);
            
            int trees = TreeGenerator.generate(
                    blocks, width, height, depth, random);

            System.out.println("[gtoe] Applied Y-level block types; changed "
                + changed + " solid blocks, generated "
                + patches + " terrain patches and "
                + trees + " trees");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not access rd-132211 Level fields", error);
        }
    }

    public static int blockIdForY(int y) {
        int selected = BLOCK_IDS[0];
        for (int index = 1; index < LAYER_STARTS.length && y >= LAYER_STARTS[index]; index++) {
            selected = BLOCK_IDS[index];
        }
        return selected;
    }

    static String describe() {
        return Arrays.toString(LAYER_STARTS) + " -> block IDs " + Arrays.toString(BLOCK_IDS);
    }
}
