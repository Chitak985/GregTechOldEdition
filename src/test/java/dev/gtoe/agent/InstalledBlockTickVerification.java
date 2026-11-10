package dev.gtoe.agent;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
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
        if (Files.exists(Paths.get(FurnacePersistence.FILE_NAME))) {
            throw new IllegalStateException("Integration test requires a folder without furnace saves");
        }
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
            verifyCpuPicking(world, type);
            MachineMultiblock machine = (MachineMultiblock) BlockTicker.forLevel(world).blockAt(5, 5, 5);
            machine.input.add(3, 5);
            type.getMethod("save").invoke(world);
            require(Files.isRegularFile(Paths.get(FurnacePersistence.FILE_NAME)),
                    "The real Level.save hook must write the furnace sidecar");
            type.getMethod("load").invoke(world);
            machine = (MachineMultiblock) BlockTicker.forLevel(world).blockAt(5, 5, 5);
            require(machine.input.itemID() == 3 && machine.input.count() == 5,
                    "The real save/load hooks must preserve hatch inventory");
            System.out.println("Installed block ticking passed: constructor, setTile, 20 Hz, bounds and reload");
        } finally {
            BlockRegistry.unregister(98);
            Files.deleteIfExists(Paths.get(FurnacePersistence.FILE_NAME));
            Files.delete(save);
        }
    }

    /** Uses the real patched private method, with no display or OpenGL context. */
    private static void verifyCpuPicking(Object world, Class<?> levelType) throws Exception {
        Class<?> gameType = Class.forName("com.mojang.rubydung.RubyDung");
        Class<?> playerType = Class.forName("com.mojang.rubydung.Player");
        Object game = gameType.getConstructor().newInstance();
        Object player = playerType.getConstructor(levelType).newInstance(world);
        Field levelField = gameType.getDeclaredField("level");
        Field playerField = gameType.getDeclaredField("player");
        Field hitField = gameType.getDeclaredField("hitResult");
        levelField.setAccessible(true);
        playerField.setAccessible(true);
        hitField.setAccessible(true);
        levelField.set(game, world);
        playerField.set(game, player);
        Method position = playerType.getDeclaredMethod("setPos", float.class, float.class, float.class);
        position.setAccessible(true);
        position.invoke(player, 2.5f, 5.5f, 6.5f);
        for (String coordinate : new String[] {"x", "y", "z"}) {
            playerType.getField(coordinate + "o").setFloat(player,
                    playerType.getField(coordinate).getFloat(player));
        }
        WorldActions.setTile(world, 2, 5, 4, 21);
        Method pick = gameType.getDeclaredMethod("pick", float.class);
        pick.setAccessible(true);
        pick.invoke(game, 1.0f);
        Object hit = hitField.get(game);
        require(hit != null && hit.getClass().getField("x").getInt(hit) == 2
                && hit.getClass().getField("z").getInt(hit) == 4
                && hit.getClass().getField("f").getInt(hit) == 3,
                "The real picker must return coordinates and the placement face without a GL context");
        WorldActions.setTile(world, 2, 5, 4, 0);
        pick.invoke(game, 1.0f);
        require(hitField.get(game) == null, "Looking into air must clear the real game's stale highlight");
        System.out.println("Installed CPU picking passed: actual RubyDung.pick, correct face and cleared miss, no OpenGL context");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
