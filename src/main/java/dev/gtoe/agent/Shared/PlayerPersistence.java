package dev.gtoe.agent;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/** Player data saved beside level.dat, independently of Minecraft's original file format. */
public final class PlayerPersistence {
    public static final String FILE_NAME = "level.dat.gtoe-player";
    private static final int MAGIC = 0x47545031;
    private static final int VERSION = 1;
    private static Object boundLevel;
    private static Object boundPlayer;
    private static final ClassValue<Access> ACCESS = new ClassValue<Access>() {
        @Override protected Access computeValue(Class<?> type) { return new Access(type); }
    };

    private PlayerPersistence() {
    }

    /** Called after RubyDung creates its player, instead of granting starter items every launch. */
    public static synchronized void initialize(Object level, Object player) {
        if (level == null || player == null) { throw new IllegalArgumentException("World and player are required"); }
        GuiManager.resetForWorld();
        Inventory.restoreContents(new Inventory.Stack[Inventory.TOTAL_SLOT_COUNT]);
        BlockSelection.selectHotbarSlot(0);
        boundLevel = level;
        boundPlayer = player;
        try {
            Saved saved = read(level);
            if (saved != null) {
                if (saved.position != null) { ACCESS.get(player.getClass()).restore(player, saved.position); }
                Inventory.restoreContents(saved.inventory);
                BlockSelection.selectHotbarSlot(saved.selected);
                GuiManager.restoreCrafting(saved.grids);
                return;
            }
        } catch (IOException | ReflectiveOperationException error) {
            System.err.println("[gtoe] Could not restore player data: " + error);
        }
        Inventory.addStarterItems();
    }

    /** Called after Level.save(), covering both the save key and normal game shutdown. */
    public static synchronized void save(Object level) {
        if (level != boundLevel || boundPlayer == null) { return; }
        Path temporary = null;
        try {
            byte[] hash = worldHash(level);
            MessageDigest disk = sha256();
            try (InputStream input = new GZIPInputStream(Files.newInputStream(Paths.get("level.dat")))) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) { disk.update(buffer, 0, count); }
            }
            if (!Arrays.equals(hash, disk.digest())) { throw new IOException("World save did not complete"); }
            Inventory.Stack[] inventory = Inventory.snapshot();
            int[][] grids = GuiManager.craftingSnapshot(inventory);
            float[] position = ACCESS.get(boundPlayer.getClass()).capture(boundPlayer);
            boolean hasPosition = validPosition(position);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                for (String dimension : new String[] {"width", "height", "depth"}) {
                    output.writeInt(level.getClass().getField(dimension).getInt(level));
                }
                output.writeBoolean(hasPosition);
                if (hasPosition) { for (float value : position) { output.writeFloat(value); } }
                output.writeInt(BlockSelection.selectedHotbarIndex());
                output.writeInt(inventory.length);
                for (Inventory.Stack stack : inventory) {
                    output.writeInt(stack == null ? -1 : stack.itemId);
                    output.writeInt(stack == null ? 0 : stack.count);
                }
                for (int[] grid : grids) { for (int id : grid) { output.writeInt(id); } }
            }
            byte[] payload = bytes.toByteArray();
            Path destination = Paths.get(FILE_NAME).toAbsolutePath();
            temporary = Files.createTempFile(destination.getParent(), "gtoe-player-", ".tmp");
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.write(hash);
                output.writeInt(payload.length);
                output.write(payload);
                output.write(sha256().digest(payload));
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | ReflectiveOperationException error) {
            System.err.println("[gtoe] Could not save player data: " + error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private static Saved read(Object level) throws IOException, ReflectiveOperationException {
        Path file = Paths.get(FILE_NAME);
        if (!Files.isRegularFile(file)) { return null; }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) { throw new IOException("Unknown player save version"); }
            byte[] hash = new byte[32];
            input.readFully(hash);
            if (!Arrays.equals(hash, worldHash(level))) {
                System.out.println("[gtoe] Ignoring player data belonging to a different world snapshot");
                return null;
            }
            int length = input.readInt();
            if (length <= 0 || length > 8192) { throw new IOException("Invalid player save size"); }
            byte[] payload = new byte[length];
            input.readFully(payload);
            byte[] checksum = new byte[32];
            input.readFully(checksum);
            if (!Arrays.equals(checksum, sha256().digest(payload)) || input.read() != -1) {
                throw new IOException("Damaged player save checksum");
            }
            try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(payload))) {
                for (String dimension : new String[] {"width", "height", "depth"}) {
                    if (data.readInt() != level.getClass().getField(dimension).getInt(level)) {
                        throw new IOException("World dimensions differ from player save");
                    }
                }
                Saved saved = new Saved();
                if (data.readBoolean()) {
                    saved.position = new float[5];
                    for (int index = 0; index < 5; index++) { saved.position[index] = data.readFloat(); }
                    if (!validPosition(saved.position)) { throw new IOException("Invalid player position"); }
                }
                saved.selected = data.readInt();
                if (saved.selected < 0 || saved.selected >= Inventory.HOTBAR_SLOT_COUNT
                        || data.readInt() != Inventory.TOTAL_SLOT_COUNT) { throw new IOException("Invalid inventory layout"); }
                for (int slot = 0; slot < saved.inventory.length; slot++) {
                    int id = data.readInt(), count = data.readInt();
                    if (id == -1 && count == 0) { continue; }
                    if (id < 0 || count <= 0) { throw new IOException("Invalid inventory stack"); }
                    saved.inventory[slot] = new Inventory.Stack(id, count);
                }
                for (int[] grid : saved.grids) {
                    for (int index = 0; index < grid.length; index++) {
                        grid[index] = data.readInt();
                        if (grid[index] < -1) { throw new IOException("Invalid saved crafting ingredient"); }
                    }
                }
                if (data.read() != -1) { throw new IOException("Unexpected player save data"); }
                return saved;
            }
        }
    }

    private static boolean validPosition(float[] p) {
        for (float value : p) { if (!Float.isFinite(value)) { return false; } }
        // Walking beyond the map edge or falling below it is a valid player position too.
        return p[4] >= -90 && p[4] <= 90;
    }

    private static byte[] worldHash(Object level) {
        BlockTicker ticker = BlockTicker.forLevel(level);
        if (ticker == null) { throw new IllegalStateException("Player saving requires an initialized world"); }
        return sha256().digest(ticker.blockData());
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static final class Saved {
        float[] position;
        int selected;
        final Inventory.Stack[] inventory = new Inventory.Stack[Inventory.TOTAL_SLOT_COUNT];
        final int[][] grids = {new int[4], new int[9]};
    }

    private static final class Access {
        final Field[] position = new Field[5];
        final Field[] previous = new Field[3];
        final Field[] velocity = new Field[3];
        final Method setPos;
        final Field onGround;

        Access(Class<?> type) {
            try {
                String[] names = {"x", "y", "z", "yRot", "xRot"};
                for (int i = 0; i < names.length; i++) { position[i] = type.getField(names[i]); }
                for (int i = 0; i < 3; i++) {
                    previous[i] = type.getField(names[i] + "o");
                    velocity[i] = type.getField(names[i] + "d");
                }
                onGround = type.getField("onGround");
                setPos = type.getDeclaredMethod("setPos", float.class, float.class, float.class);
                setPos.setAccessible(true);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unexpected player layout", error); }
        }

        float[] capture(Object player) throws IllegalAccessException {
            float[] values = new float[5];
            for (int i = 0; i < values.length; i++) { values[i] = position[i].getFloat(player); }
            return values;
        }

        void restore(Object player, float[] values) throws ReflectiveOperationException {
            // Player.y is eye height (bb.y0 + 1.62), whereas setPos uses the body's center (bb.y0 + 0.9).
            setPos.invoke(player, values[0], values[1] - 0.72f, values[2]);
            for (int i = 0; i < values.length; i++) { position[i].setFloat(player, values[i]); }
            for (int i = 0; i < 3; i++) {
                previous[i].setFloat(player, values[i]);
                velocity[i].setFloat(player, 0.0f);
            }
            onGround.setBoolean(player, false);
        }
    }
}
