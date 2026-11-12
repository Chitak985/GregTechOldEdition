package dev.gtoe.agent;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/** Real installed classes, real Level.save hook and separate JVMs; never uses a Prism instance's saves. */
public final class PlayerPersistenceVerification {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            Path directory = Files.createTempDirectory(Paths.get(".").toAbsolutePath(), "player-save-test-");
            try {
                runPhase(directory, "write");
                runPhase(directory, "read");
                System.out.println("Player persistence passed: separate JVM restart, exact slots, position, facing, "
                        + "crafting, cursor items, empty inventory, world identity and damaged saves");
            } finally {
                for (String file : new String[] {"level.dat", PlayerPersistence.FILE_NAME, FurnacePersistence.FILE_NAME}) {
                    Files.deleteIfExists(directory.resolve(file));
                }
                Files.delete(directory);
            }
            return;
        }
        if ("write".equals(args[0])) { write(); }
        else if ("read".equals(args[0])) { read(); }
        else { throw new IllegalArgumentException("Unknown test phase"); }
    }

    private static void runPhase(Path directory, String phase) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString());
        command.add("-Xverify:all");
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:")) { command.add(argument); }
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(PlayerPersistenceVerification.class.getName());
        command.add(phase);
        Process child = new ProcessBuilder(command).directory(directory.toFile()).inheritIO().start();
        if (!child.waitFor(45, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new AssertionError("Restart verification timed out");
        }
        require(child.exitValue() == 0, "Restart phase failed: " + phase);
    }

    private static void write() throws Exception {
        require(!Files.exists(Paths.get(PlayerPersistence.FILE_NAME)), "Test folder must have no player save");
        byte[] blocks = new byte[12 * 12 * 12];
        blocks[(5 * 12 + 5) * 12 + 5] = 22;
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(
                Paths.get("level.dat"), StandardOpenOption.CREATE_NEW))) { output.write(blocks); }
        Object level = level();
        Object player = player(level);
        PlayerPersistence.initialize(level, player);
        require(Inventory.count(21) == 32 && Inventory.count(22) == 16,
                "A world without player data must grant the current starter items");
        Inventory.clearForTests();
        Inventory.addToSlot(3, 3, 7);
        Inventory.addToSlot(42, 3, 5);
        Inventory.addToSlot(Inventory.HOTBAR_START, 21, 16);
        Inventory.addToSlot(Inventory.HOTBAR_START + 8, 100, 4);
        BlockSelection.selectHotbarSlot(8);
        Method setPos = player.getClass().getDeclaredMethod("setPos", float.class, float.class, float.class);
        setPos.setAccessible(true);
        setPos.invoke(player, 2.5f, 7.9f, 6.25f);
        setPlayer(player, "y", 8.62f);
        setPlayer(player, "yRot", 123.5f);
        setPlayer(player, "xRot", -25.0f);
        GuiManager.handleKeyEvent(18, true);
        grid()[0] = 9;
        CraftingTableGui.grid()[8] = 10;
        Inventory.Stack held = Inventory.takeStack(42);
        setGui("draggedItemId", held.itemId);
        setGui("draggedAmount", held.count);
        setGui("draggedSourceInventorySlot", 42);
        save(level);
        require(Files.isRegularFile(Paths.get(PlayerPersistence.FILE_NAME)), "Level.save must write player data");
        require(Inventory.countAt(42) == 0 && grid()[0] == 9,
                "Saving must not mutate live cursor items or crafting grids");
    }

    private static void read() throws Exception {
        Object level = level();
        Object player = player(level);
        PlayerPersistence.initialize(level, player);
        assertSavedPlayer(player);
        save(level);
        PlayerPersistence.initialize(level, player);
        assertSavedPlayer(player);

        // An empty inventory is saved deliberately; never replenish starter items on restart.
        GuiManager.resetForWorld();
        Inventory.clearForTests();
        save(level);
        PlayerPersistence.initialize(level, player);
        require(empty(), "An intentionally empty inventory must remain empty after reload");

        // Full inventories must not discard ingredients when a GUI closes or a world restarts.
        for (int slot = 0; slot < Inventory.TOTAL_SLOT_COUNT; slot++) { Inventory.addToSlot(slot, 1000 + slot, 1); }
        GuiManager.openCraftingTable();
        CraftingTableGui.grid()[4] = 9;
        GuiManager.close();
        save(level);
        PlayerPersistence.initialize(level, player);
        require(CraftingTableGui.grid()[4] == 9 && GuiManager.isCraftingTableOpen(),
                "A full inventory must keep crafting ingredients accessible after restart");
        for (int slot = 0; slot < Inventory.TOTAL_SLOT_COUNT; slot++) {
            require(Inventory.itemIdAt(slot) == 1000 + slot && Inventory.countAt(slot) == 1,
                    "Full inventories must preserve all slots");
        }
        GuiManager.resetForWorld();
        Inventory.clearForTests();

        setPlayer(player, "x", -2.5f);
        setPlayer(player, "y", -5.0f);
        save(level);
        PlayerPersistence.initialize(level, player);
        require(player.getClass().getField("x").getFloat(player) == -2.5f
                        && player.getClass().getField("y").getFloat(player) == -5.0f,
                "Legitimate positions beyond the world boundary must also survive restart");

        // A player item being dragged in a furnace screen belongs in the player's save.
        MachineMultiblock machine = (MachineMultiblock) BlockTicker.forLevel(level).blockAt(5, 5, 5);
        Inventory.addToSlot(57, 9, 23);
        GuiManager.openFurnace(machine);
        Inventory.Stack held = Inventory.takeStack(57);
        setFurnace("held", held);
        setFurnace("sourcePlayer", 57);
        save(level);
        PlayerPersistence.initialize(level, player);
        require(Inventory.itemIdAt(57) == 9 && Inventory.countAt(57) == 23,
                "Player stacks dragged inside a furnace must survive restarting");

        // A dragged machine stack is saved by FurnacePersistence and must not become a player stack too.
        Inventory.clearForTests();
        machine.output.add(21, 2);
        GuiManager.openFurnace(machine);
        setFurnace("held", machine.output.takeForDrag());
        setFurnace("sourceMachine", machine.output);
        save(level);
        Object restoredLevel = level();
        PlayerPersistence.initialize(restoredLevel, player(restoredLevel));
        require(empty(), "Dragging from a machine must not duplicate its saved contents into the player inventory");
        require(((MachineMultiblock) BlockTicker.forLevel(restoredLevel).blockAt(5, 5, 5)).output.count() == 2,
                "The machine's existing save must preserve dragged machine output");

        byte[] good = Files.readAllBytes(Paths.get(PlayerPersistence.FILE_NAME));
        byte[] damaged = good.clone();
        damaged[damaged.length - 1] ^= 1;
        Files.write(Paths.get(PlayerPersistence.FILE_NAME), damaged);
        PlayerPersistence.initialize(restoredLevel, player(restoredLevel));
        require(Inventory.count(21) == 32 && Inventory.count(22) == 16,
                "A damaged save must be rejected without partially restoring inventory");
        require(Arrays.equals(damaged, Files.readAllBytes(Paths.get(PlayerPersistence.FILE_NAME))),
                "Loading a damaged save must leave the original file available");

        Files.write(Paths.get(PlayerPersistence.FILE_NAME), good);
        WorldActions.setTile(restoredLevel, 0, 0, 0, 3);
        PlayerPersistence.initialize(restoredLevel, player(restoredLevel));
        require(Inventory.count(21) == 32, "Player data from another world snapshot must be ignored");
        PlayerPersistence.save(restoredLevel);
        require(Arrays.equals(good, Files.readAllBytes(Paths.get(PlayerPersistence.FILE_NAME))),
                "An unsuccessful world save must not replace player data");
    }

    private static void assertSavedPlayer(Object player) throws Exception {
        for (int slot = 0; slot < Inventory.TOTAL_SLOT_COUNT; slot++) {
            int id = slot == 3 || slot == 42 ? 3 : slot == Inventory.HOTBAR_START ? 21
                    : slot == Inventory.HOTBAR_START + 8 ? 100 : -1;
            int count = slot == 3 ? 7 : slot == 42 ? 5 : slot == Inventory.HOTBAR_START ? 16
                    : slot == Inventory.HOTBAR_START + 8 ? 4 : 0;
            require(Inventory.itemIdAt(slot) == id && Inventory.countAt(slot) == count,
                    "Exact inventory contents and empty slots must survive a fresh process: " + slot);
        }
        require(BlockSelection.selectedHotbarIndex() == 8, "Selected hotbar slot must survive restart");
        float[] values = {2.5f, 8.62f, 6.25f, 123.5f, -25.0f};
        String[] fields = {"x", "y", "z", "yRot", "xRot"};
        for (int i = 0; i < fields.length; i++) {
            require(Math.abs(player.getClass().getField(fields[i]).getFloat(player) - values[i]) < 0.0001f,
                    "Player position/facing was not restored: " + fields[i]);
            if (i < 3) {
                require(player.getClass().getField(fields[i] + "o").getFloat(player) == values[i],
                        "Camera interpolation must start at the restored position");
                require(player.getClass().getField(fields[i] + "d").getFloat(player) == 0,
                        "Old movement velocity must not carry into a restored player");
            }
        }
        Object bounds = player.getClass().getField("bb").get(player);
        require(Math.abs(bounds.getClass().getField("y0").getFloat(bounds) - 7.0f) < 0.0001f,
                "Restored collision bounds must match eye height so the player does not clip into the ground");
        require(grid()[0] == 9 && CraftingTableGui.grid()[8] == 10 && GuiManager.isCraftingTableOpen(),
                "Crafting ingredients must survive and remain accessible");
    }

    private static Object level() throws Exception {
        return Class.forName("com.mojang.rubydung.level.Level").getConstructor(
                int.class, int.class, int.class).newInstance(12, 12, 12);
    }

    private static Object player(Object level) throws Exception {
        return Class.forName("com.mojang.rubydung.Player").getConstructor(level.getClass()).newInstance(level);
    }

    private static void save(Object level) throws Exception { level.getClass().getMethod("save").invoke(level); }
    private static boolean empty() { for (Inventory.Stack stack : Inventory.snapshot()) { if (stack != null) { return false; } } return true; }
    private static int[] grid() throws Exception { Field f = GuiManager.class.getDeclaredField("PLAYER_CRAFTING_GRID"); f.setAccessible(true); return (int[]) f.get(null); }
    private static void setGui(String name, int value) throws Exception { Field f = GuiManager.class.getDeclaredField(name); f.setAccessible(true); f.setInt(null, value); }
    private static void setFurnace(String name, Object value) throws Exception { Field f = FurnaceGui.class.getDeclaredField(name); f.setAccessible(true); f.set(null, value); }
    private static void setPlayer(Object player, String name, float value) throws Exception { player.getClass().getField(name).setFloat(player, value); }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
