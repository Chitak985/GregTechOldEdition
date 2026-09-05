package dev.gtoe.agent;

import java.util.Random;

/** Adds simple vertical wood trunks to exposed terrain columns. */
public final class TreeGenerator {
    public static final int TREE_CHANCE_PERCENT = 5;
    public static final int MIN_TREE_HEIGHT = 4;
    public static final int MAX_TREE_HEIGHT = 10;
    public static final int WOOD_BLOCK_ID = 9;

    private TreeGenerator() {
    }

    /**
     * Gives each exposed surface column a 5% tree roll. Returns the number of
     * generated trees. The block layout matches rd-132211 Level's y/z/x order.
     */
    public static int generate(
            byte[] blocks,
            int width,
            int height,
            int depth,
            Random random) {
        if (blocks == null || random == null) {
            throw new IllegalArgumentException("Blocks and random source are required");
        }
        long expectedLength = (long) width * height * depth;
        if (width <= 0 || height <= 0 || depth <= 0 || expectedLength != blocks.length) {
            throw new IllegalArgumentException("Invalid level dimensions for tree generation");
        }

        int trees = 0;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int surfaceY = findSurface(blocks, width, height, depth, x, z);
                int availableHeight = depth - surfaceY - 1;
                if (random.nextInt(100) >= TREE_CHANCE_PERCENT) {
                    continue;  // Chance check
                }
                if ((blocks[index(width, height, x, surfaceY, z)] & 255) != 1) {
                    continue;  // Grass check
                }
                if (surfaceY < 0 || availableHeight < MIN_TREE_HEIGHT) {
                    continue;  // Height check
                }

                int maximumHeight = Math.min(MAX_TREE_HEIGHT, availableHeight);
                int treeHeight = MIN_TREE_HEIGHT
                        + random.nextInt(maximumHeight - MIN_TREE_HEIGHT + 1);
                for (int offset = 1; offset <= treeHeight; offset++) {
                    blocks[index(width, height, x, surfaceY + offset, z)] =
                            (byte) WOOD_BLOCK_ID;
                }
                trees++;
            }
        }
        return trees;
    }

    private static int findSurface(
            byte[] blocks, int width, int height, int depth, int x, int z) {
        for (int y = depth - 1; y >= 0; y--) {
            if ((blocks[index(width, height, x, y, z)] & 255) != 0) {
                return y;
            }
        }
        return -1;
    }

    private static int index(int width, int height, int x, int y, int z) {
        return (y * height + z) * width + x;
    }
}
