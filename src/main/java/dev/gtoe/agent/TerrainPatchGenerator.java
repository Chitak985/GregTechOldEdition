package dev.gtoe.agent;

import java.util.Random;

/** Generates irregular patches of surface blocks. */
public final class TerrainPatchGenerator {
    public static final int SAND_BLOCK_ID = 6;
    public static final int GRAVEL_BLOCK_ID = 20;

    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 5;

    private TerrainPatchGenerator() {
    }

    public static int generate(
            byte[] blocks,
            int width,
            int height,
            int depth,
            Random random) {

        int patches = 0;

        // Roughly one patch per this many blocks of world surface.
        int patchCount = Math.max(1, width * height / 250);

        for (int i = 0; i < patchCount; i++) {
            int centerX = random.nextInt(width);
            int centerZ = random.nextInt(height);

            int radius = MIN_RADIUS
                    + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);

            int blockId = random.nextBoolean()
                    ? SAND_BLOCK_ID
                    : GRAVEL_BLOCK_ID;

            generatePatch(
                    blocks,
                    width,
                    height,
                    depth,
                    centerX,
                    centerZ,
                    radius,
                    blockId,
                    random
            );

            patches++;
        }

        return patches;
    }

    private static void generatePatch(
            byte[] blocks,
            int width,
            int height,
            int depth,
            int centerX,
            int centerZ,
            int radius,
            int blockId,
            Random random) {

        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {

                if (x < 0 || x >= width || z < 0 || z >= height) {
                    continue;
                }

                int dx = x - centerX;
                int dz = z - centerZ;

                double distance = Math.sqrt(dx * dx + dz * dz);

                // Randomness makes the edge less perfectly circular.
                if (distance > radius + random.nextDouble() - 0.5) {
                    continue;
                }

                int surfaceY = findSurface(
                        blocks,
                        width,
                        height,
                        depth,
                        x,
                        z
                );

                if (surfaceY < 0) {
                    continue;
                }

                int surfaceIndex =
                        index(width, height, x, surfaceY, z);

                // Only replace normal surface terrain.
                int oldBlock = blocks[surfaceIndex] & 255;

                if (oldBlock != 1 && oldBlock != 2) {
                    continue;
                }

                blocks[surfaceIndex] = (byte) blockId;
            }
        }
    }

    private static int findSurface(
            byte[] blocks,
            int width,
            int height,
            int depth,
            int x,
            int z) {

        for (int y = depth - 1; y >= 0; y--) {
            if ((blocks[index(width, height, x, y, z)] & 255) != 0) {
                return y;
            }
        }

        return -1;
    }

    private static int index(
            int width,
            int height,
            int x,
            int y,
            int z) {

        return (y * height + z) * width + x;
    }
}