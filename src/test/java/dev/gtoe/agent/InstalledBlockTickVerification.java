package dev.gtoe.agent;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;

/** Runs with -javaagent in an isolated working directory, without opening a display. */
public final class InstalledBlockTickVerification {
    private InstalledBlockTickVerification() {
    }

    public static void main(String[] arguments) throws Exception {
        // CREATE_NEW guarantees this test cannot overwrite an existing game save.
        Path save = Paths.get("level.dat");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(
                save, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            byte[] blocks = new byte[12 * 12 * 12];
            blocks[(5 * 12 + 5) * 12 + 5] = 22;
            output.write(blocks);
        }
        try {
            BlockRegistry.register(98, BlockTickVerification.CountingBlock::new);
            Class<?> type = Class.forName("com.mojang.rubydung.level.Level");
            Object world = type.getConstructor(int.class, int.class, int.class).newInstance(12, 12, 12);
            BlockTicker ticker = BlockTicker.forLevel(world);
            require(ticker != null && ticker.blockAt(5, 5, 5) instanceof MachineMultiblock,
                    "The transformed constructor must create saved controller instances");

            WorldActions.setTile(world, 2, 3, 4, 98);
            BlockTickVerification.CountingBlock block =
                    (BlockTickVerification.CountingBlock) ticker.blockAt(2, 3, 4);
            require(block != null, "Real Level.setTile must create block instances");
            for (int gameTick = 0; gameTick < 60; gameTick++) {
                BlockTicker.onGameTick(world);
            }
            require(block.calls == 20, "The installed world must run block Tick at 20 Hz");
            WorldActions.setTile(world, 2, 3, 4, 0);
            require(ticker.blockAt(2, 3, 4) == null, "Real setTile must remove broken block instances");

            Method getter = type.getMethod(GtoeTransformer.BLOCK_ID_METHOD,
                    int.class, int.class, int.class);
            require((Integer) getter.invoke(world, 12, 0, 0) == -1
                    && (Integer) getter.invoke(world, -1, 1, 0) == -1
                    && (Integer) getter.invoke(world, 0, 12, 0) == -1,
                    "Out-of-world structure checks must not wrap into another block-array row");
            type.getMethod("load").invoke(world);
            require(BlockTicker.forLevel(world) != ticker
                    && BlockTicker.forLevel(world).blockAt(5, 5, 5) instanceof MachineMultiblock,
                    "Real Level.load must rebuild saved controller state");
            System.out.println("Installed block ticking passed: constructor, setTile, 20 Hz, bounds and reload");
        } finally {
            BlockRegistry.register(98, AdvancedBlock::new);
            Files.delete(save);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
