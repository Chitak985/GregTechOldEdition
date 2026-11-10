package dev.gtoe.agent;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

/** Run only in an isolated working directory; verifies actual sidecar files and resume behavior. */
public final class FurnacePersistenceVerification {
    public static void main(String[] arguments) throws Exception {
        Path worldFile = Paths.get("level.dat");
        Path dataFile = Paths.get(FurnacePersistence.FILE_NAME);
        if (Files.exists(worldFile) || Files.exists(dataFile)) {
            throw new IllegalStateException("Persistence test requires an empty working directory");
        }
        try {
            FurnaceRecipes.registerFurnaceRecipe(900, 9, 10, 901);
            BlockTickVerification.TestLevel world = FurnaceVerification.createFurnace(0);
            MachineMultiblock main = FurnaceVerification.main(world);
            main.input.add(900, 4);
            main.output.add(901, 2);
            FurnaceVerification.hatch(world, 0).input.add(9, 2);
            FurnaceVerification.hatch(world, 0).output.add(106, 3);
            FurnaceVerification.ticks(world, 1);
            FurnaceVerification.ticks(world, 3);
            require(main.progressTicks() == 3 && main.storedFuel() == 7, "Fixture must be midway through a recipe");
            byte[] blocks = blocks(world);
            try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(worldFile, StandardOpenOption.CREATE_NEW))) {
                out.write(blocks);
            }
            Inventory.Stack cursor = main.output.takeForDrag();
            FurnacePersistence.save(world);
            main.output.finishDrag(cursor);
            require(Files.exists(dataFile), "Saving must create a furnace sidecar");
            byte[] savedData = Files.readAllBytes(dataFile);

            BlockTickVerification.TestLevel restored = restoreWorld(blocks);
            MachineMultiblock loaded = FurnaceVerification.main(restored);
            require(loaded.input.count() == 3 && loaded.output.count() == 2 && loaded.isProcessing()
                            && loaded.progressTicks() == 3 && loaded.storedFuel() == 7,
                    "Restart must preserve input, dragged output, chosen recipe, progress and fuel");
            // Restored hatches attach when the saved machine revalidates its structure.
            FurnaceVerification.ticks(restored, 16);
            require(!loaded.isProcessing() && loaded.output.count() == 3 && loaded.storedFuel() == 1,
                    "A saved active recipe must finish exactly once with its remaining fuel deductions");
            require(FurnaceVerification.hatch(restored, 0).input.count() == 1
                            && FurnaceVerification.hatch(restored, 0).output.count() == 3,
                    "Fuel slots and byproducts must survive restarting");

            byte[] differentWorld = blocks.clone();
            differentWorld[0] = 3;
            BlockTickVerification.TestLevel reset = restoreWorld(differentWorld);
            require(FurnaceVerification.main(reset).input.count() == 0,
                    "Stale data must not attach to a different world snapshot");
            Files.write(dataFile, Arrays.copyOf(savedData, savedData.length - 1));
            BlockTickVerification.TestLevel corrupt = restoreWorld(blocks);
            require(FurnaceVerification.main(corrupt).input.count() == 0
                            && FurnaceVerification.main(corrupt).storedFuel() == 0,
                    "A truncated sidecar must be rejected without partially restoring machines");
            System.out.println("Furnace persistence passed: slots, active recipes, fuel, drag reservations, fingerprint and corruption handling");
        } finally {
            Files.deleteIfExists(dataFile);
            Files.deleteIfExists(worldFile);
        }
    }

    private static byte[] blocks(BlockTickVerification.TestLevel world) throws Exception {
        Field field = world.getClass().getDeclaredField("blocks");
        field.setAccessible(true);
        return (byte[]) field.get(world);
    }

    private static BlockTickVerification.TestLevel restoreWorld(byte[] data) throws Exception {
        BlockTickVerification.TestLevel world = new BlockTickVerification.TestLevel();
        System.arraycopy(data, 0, blocks(world), 0, data.length);
        world.gtoe$blockTicker = BlockTicker.initialize(world);
        return world;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
