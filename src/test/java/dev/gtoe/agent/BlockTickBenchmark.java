package dev.gtoe.agent;

import java.util.Arrays;
import java.util.Locale;

/** Manual benchmark: a normal-sized terrain-only world, without rendering or game saves. */
public final class BlockTickBenchmark {
    public static void main(String[] arguments) throws Exception {
        BenchmarkLevel world = new BenchmarkLevel();
        System.gc();
        long heapBefore = usedHeap();
        long start = System.nanoTime();
        world.gtoe$blockTicker = BlockTicker.initialize(world);
        double initializationMs = (System.nanoTime() - start) / 1_000_000.0;
        System.gc();
        long extraHeap = usedHeap() - heapBefore;
        int instances = 0;
        for (int y = 0; y < world.depth; y++) {
            for (int z = 0; z < world.height; z++) {
                for (int x = 0; x < world.width; x++) {
                    if (world.gtoe$blockTicker.blockAt(x, y, z) != null) {
                        instances++;
                    }
                }
            }
        }
        for (int i = 0; i < 60; i++) {
            BlockTicker.onGameTick(world);
        }
        start = System.nanoTime();
        for (int i = 0; i < 6000; i++) {
            BlockTicker.onGameTick(world);
        }
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
        System.out.printf(Locale.ROOT,
                "Terrain cells=%d, ticking instances=%d, extra heap=%.2f MiB, init=%.2f ms, mean tick=%.4f ms%n",
                world.blocks.length, instances, extraHeap / 1048576.0,
                initializationMs, elapsedMs / 2000.0);
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public static final class BenchmarkLevel {
        public final int width = 256;
        public final int height = 256;
        public final int depth = 64;
        private final byte[] blocks = new byte[width * height * depth];
        public BlockTicker gtoe$blockTicker;

        public BenchmarkLevel() {
            Arrays.fill(blocks, 0, width * height * 43, (byte) 3);
        }
    }
}
