package dev.gtoe.agent;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Machine data is saved beside level.dat; game JARs and the level.dat format are unchanged. */
public final class FurnacePersistence {
    public static final String FILE_NAME = "level.dat.gtoe-furnaces";
    private static final int MAGIC = 0x47544631;
    private static final int VERSION = 1;

    private FurnacePersistence() {
    }

    /** Injected after Level.save(). Only commit data if the game's save actually succeeded. */
    public static void save(Object level) {
        BlockTicker ticker = BlockTicker.forLevel(level);
        if (ticker == null) { return; }
        Path temporary = null;
        try {
            byte[] worldHash = digest(ticker.blockData());
            MessageDigest diskDigest = sha256();
            try (InputStream input = new GZIPInputStream(Files.newInputStream(Paths.get("level.dat")))) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) >= 0) { diskDigest.update(buffer, 0, length); }
            }
            if (!Arrays.equals(worldHash, diskDigest.digest())) {
                throw new IOException("The world save does not match the current world");
            }
            Path destination = Paths.get(FILE_NAME).toAbsolutePath();
            temporary = Files.createTempFile(destination.getParent(), "gtoe-furnaces-", ".tmp");
            List<AdvancedBlock> machines = new ArrayList<>();
            for (AdvancedBlock block : ticker.blocksSnapshot()) {
                if (block instanceof MachineMultiblock || block instanceof FurnaceFuelHatch) { machines.add(block); }
            }
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.write(worldHash);
                output.writeInt(machines.size());
                for (AdvancedBlock block : machines) {
                    output.writeInt(block.x); output.writeInt(block.y); output.writeInt(block.z);
                    output.writeInt(block.blockId);
                    writeSlot(output, input(block));
                    writeSlot(output, output(block));
                    if (block instanceof MachineMultiblock) { ((MachineMultiblock) block).writeState(output); }
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            System.err.println("[gtoe] Could not save furnace data: " + error.getMessage());
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    static void load(BlockTicker ticker) {
        Path file = Paths.get(FILE_NAME);
        if (!Files.isRegularFile(file)) { return; }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) { throw new IOException("Unknown furnace save version"); }
            byte[] hash = new byte[32];
            input.readFully(hash);
            if (!Arrays.equals(hash, digest(ticker.blockData()))) {
                System.out.println("[gtoe] Ignoring furnace data belonging to a different world snapshot");
                return;
            }
            int count = input.readInt();
            if (count < 0 || count > ticker.blockData().length) { throw new IOException("Invalid furnace count"); }
            List<AdvancedBlock> staged = new ArrayList<>();
            Set<String> coordinates = new HashSet<>();
            for (int index = 0; index < count; index++) {
                int x = input.readInt(), y = input.readInt(), z = input.readInt(), id = input.readInt();
                if (!coordinates.add(x + "," + y + "," + z) || (id != 22 && id != 24)) {
                    throw new IOException("Duplicate or invalid saved hatch");
                }
                AdvancedBlock existing = ticker.blockAt(x, y, z);
                if (existing == null || existing.blockId != id) { throw new IOException("Saved hatch does not exist"); }
                AdvancedBlock block = id == 22 ? new MachineMultiblock(existing.level, x, y, z, id)
                        : new FurnaceFuelHatch(existing.level, x, y, z, id);
                input(block).restore(readSlot(input));
                output(block).restore(readSlot(input));
                if (block instanceof MachineMultiblock) { ((MachineMultiblock) block).readState(input); }
                staged.add(block);
            }
            if (input.read() != -1) { throw new IOException("Unexpected trailing furnace save data"); }
            // Apply only after the entire file has passed validation.
            for (AdvancedBlock saved : staged) {
                AdvancedBlock existing = ticker.blockAt(saved.x, saved.y, saved.z);
                input(existing).restore(input(saved).snapshot());
                output(existing).restore(output(saved).snapshot());
                if (existing instanceof MachineMultiblock) {
                    ((MachineMultiblock) existing).restoreStateFrom((MachineMultiblock) saved);
                }
            }
        } catch (IOException error) {
            System.err.println("[gtoe] Ignoring unreadable furnace data: " + error.getMessage());
        }
    }

    private static MachineSlot input(AdvancedBlock block) {
        return block instanceof MachineMultiblock ? ((MachineMultiblock) block).input : ((FurnaceFuelHatch) block).input;
    }

    private static MachineSlot output(AdvancedBlock block) {
        return block instanceof MachineMultiblock ? ((MachineMultiblock) block).output : ((FurnaceFuelHatch) block).output;
    }

    private static void writeSlot(DataOutput out, MachineSlot slot) throws IOException {
        Inventory.Stack stack = slot.snapshot();
        out.writeInt(stack == null ? -1 : stack.itemId);
        out.writeInt(stack == null ? 0 : stack.count);
    }

    private static Inventory.Stack readSlot(DataInput in) throws IOException {
        int id = in.readInt(), count = in.readInt();
        if (id == -1 && count == 0) { return null; }
        if (id < 0 || count <= 0 || count > MachineSlot.CAPACITY) { throw new IOException("Invalid saved hatch slot"); }
        return new Inventory.Stack(id, count);
    }

    private static byte[] digest(byte[] blocks) { return sha256().digest(blocks); }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
