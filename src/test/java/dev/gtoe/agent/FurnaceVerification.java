package dev.gtoe.agent;

import java.util.Random;

/** End-to-end machine, fuel and GUI-input regressions without a display or game source. */
public final class FurnaceVerification {
    private FurnaceVerification() {
    }

    public static void run() throws Exception {
        verifyDefaultsAndRotations();
        verifyRoundingAndDecay();
        verifyFailuresAndByproducts();
        verifyRandomRecipes();
        verifyGuiTransfers();
        System.out.println("Furnace verification passed: recipes, rotations, round-robin fuel, outputs, GUI transfers and invalidation");
    }

    static BlockTickVerification.TestLevel createFurnace(int rotation) {
        BlockTickVerification.TestLevel world = new BlockTickVerification.TestLevel();
        String[][] slices = {{"-2-", "2!2", "252", "111"}, {"262", "141", "535", "111"}, {"-2-", "212", "252", "111"}};
        int[] ids = {0, 21, 3, 25, 26, 24, 23};
        for (int slice = 0; slice < 3; slice++) {
            for (int row = 0; row < 4; row++) {
                for (int column = 0; column < 3; column++) {
                    char symbol = slices[slice][row].charAt(column);
                    if (symbol == '-') { continue; }
                    int dx = column - 1, dz = slice, rx = dx, rz = dz;
                    switch (rotation) {
                        case 1: rx = -dz; rz = dx; break;
                        case 2: rx = -dx; rz = -dz; break;
                        case 3: rx = dz; rz = -dx; break;
                        default: break;
                    }
                    world.setTile(5 + rx, 6 - row, 5 + rz, symbol == '!' ? 22 : ids[symbol - '0']);
                }
            }
        }
        world.gtoe$blockTicker = BlockTicker.initialize(world);
        ticks(world, 10);
        return world;
    }

    static MachineMultiblock main(BlockTickVerification.TestLevel world) {
        return (MachineMultiblock) world.gtoe$blockTicker.blockAt(5, 5, 5);
    }

    static FurnaceFuelHatch hatch(BlockTickVerification.TestLevel world, int index) {
        int[] p = main(world).fuelHatchPositions()[index];
        return (FurnaceFuelHatch) world.gtoe$blockTicker.blockAt(p[0], p[1], p[2]);
    }

    static void ticks(Object world, int count) {
        for (int i = 0; i < count * 3; i++) { BlockTicker.onGameTick(world); }
    }

    private static void verifyDefaultsAndRotations() {
        for (int rotation = 0; rotation < 4; rotation++) {
            BlockTickVerification.TestLevel world = createFurnace(rotation);
            MachineMultiblock main = main(world);
            require(main.isFormed(), "Furnace must form in all four rotations");
            main.input.add(21, 1);
            hatch(world, 0).input.add(9, 1);
            ticks(world, 1);
            require(main.isProcessing() && main.storedFuel() == 10 && main.input.count() == 0,
                    "Starting a recipe must reserve its input and consume logs for ten fuel");
            for (int i = 0; i < 4; i++) {
                require(hatch(world, i).controller() == main && hatch(world, i).flamePhase() == 8,
                        "All four hatches must display shared controller heat");
            }
            ticks(world, 99);
            require(main.output.count() == 0 && main.storedFuel() == 1,
                    "Stone must not finish before 100 processing ticks; no idle decay while processing");
            ticks(world, 1);
            require(main.output.itemID() == 29 && main.output.count() == 1 && main.storedFuel() == 0,
                    "Default stone recipe must produce hardened stone in 100 ticks for ten fuel");
        }
    }

    private static void verifyRoundingAndDecay() {
        FurnaceRecipes.registerFurnaceRecipe(900, 9, 10, 901);
        BlockTickVerification.TestLevel world = createFurnace(0);
        MachineMultiblock main = main(world);
        main.input.add(900, 1);
        hatch(world, 0).input.add(9, 1);
        ticks(world, 1);
        for (int tick = 1; tick <= 10; tick++) {
            ticks(world, 1);
            require(main.storedFuel() == 10 - Math.min(tick, 9),
                    "Nine fuel should deduct 1,1,1,1,1,1,1,1,1,0");
        }
        require(main.flamePhase() == 1 && main.output.itemID() == 901,
                "The final positive reserve must retain the lowest lit flame phase");
        ticks(world, 19);
        require(main.storedFuel() == 1, "Idle fuel must not decay before 20 ticks");
        ticks(world, 1);
        require(main.storedFuel() == 0 && main.flamePhase() == 0, "Zero heat must extinguish the flame");
        main.input.add(900, 2);
        hatch(world, 0).input.add(9, 1);
        ticks(world, 1);
        require(main.isProcessing(), "A second recipe should start");
        world.setTile(5, 3, 5, 0);
        require(!main.isFormed() && !main.isProcessing() && main.storedFuel() == 0 && main.input.count() == 1,
                "Breaking the structure must void the reserved recipe and all heat, preserving unprocessed input");
        for (int i = 0; i < 4; i++) {
            require(hatch(world, i).flamePhase() == 0, "Invalidated furnace flames must clear");
        }
    }

    private static void verifyFailuresAndByproducts() {
        FurnaceRecipes.registerFurnaceRecipe(910, 6, 10, 911);
        FurnaceRecipes.registerFurnaceFuel(912, 1, 913, 1.0);
        BlockTickVerification.TestLevel world = createFurnace(0);
        MachineMultiblock main = main(world);
        main.input.add(910, 1);
        ticks(world, 1);
        require("Insufficient fuel!".equals(main.status()), "Missing fuel must show the correct message");
        for (int i = 0; i < 4; i++) { hatch(world, i).input.add(912, 2); }
        ticks(world, 39);
        require(!main.isProcessing() && !main.status().isEmpty(), "Insufficient-fuel retry must wait 40 ticks");
        hatch(world, 0).output.add(913, 64);
        ticks(world, 1);
        require("No fuel output space!".equals(main.status()) && main.input.count() == 1,
                "A blocked fuel byproduct must block the whole attempt");
        for (int i = 0; i < 4; i++) { require(hatch(world, i).input.count() == 2, "Failed plans must not partially burn fuel"); }
        hatch(world, 0).output.takeForDrag();
        hatch(world, 0).output.finishDrag(null);
        ticks(world, 40);
        require(main.isProcessing() && main.storedFuel() == 6, "Free output space must allow retry");
        int[] expected = {2, 2, 1, 1};
        for (int i = 0; i < 4; i++) {
            require(hatch(world, i).output.count() == expected[i]
                            && hatch(world, i).input.count() == 2 - expected[i],
                    "Fuel must consume one per hatch per circuit in front-right-back-left order");
        }
        ticks(world, 10);
        require(main.output.itemID() == 911, "Fuel-backed recipe must complete");

        world = createFurnace(0);
        main = main(world);
        main.input.add(3, 1);
        main.output.add(21, 64);
        hatch(world, 0).input.add(9, 1);
        ticks(world, 5);
        require(!main.isProcessing() && main.input.count() == 1 && hatch(world, 0).input.count() == 1,
                "A full main output must prevent startup without consuming items or fuel");
        main.output.takeForDrag(); main.output.finishDrag(null);
        main.input.takeForDrag(); main.input.finishDrag(null);
        main.input.add(9999, 1);
        ticks(world, 5);
        require(!main.isProcessing() && hatch(world, 0).input.count() == 1,
                "Unregistered inputs must do nothing");
        require(FurnaceRecipes.fuel(9999) == null && !FurnaceRecipes.fuel(9).hasByproduct(),
                "Unregistered fuels do nothing and logs have no byproduct");
    }

    private static void verifyRandomRecipes() {
        FurnaceRecipes.registerFurnaceRecipe(920, 1, 10, 921);
        FurnaceRecipes.registerFurnaceRecipe(920, 1, 10, 922);
        Random random = new Random(1234);
        int first = 0;
        for (int i = 0; i < 10000; i++) {
            if (FurnaceRecipes.choose(920, random).resultID == 921) { first++; }
        }
        require(first > 4700 && first < 5300, "Alternative recipes should have equal selection probability");
        boolean rejected = false;
        try { FurnaceRecipes.registerFurnaceFuel(999, 1, -1, 1.1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Fuel probabilities outside 0.0-1.0 must be rejected");
    }

    private static void verifyGuiTransfers() {
        GuiManager.resetForTests();
        Inventory.clearForTests();
        BlockTickVerification.TestLevel world = createFurnace(0);
        MachineMultiblock main = main(world);
        Inventory.addToSlot(0, 3, 70);
        require(BlockGuiRegistry.openForBlock(world, 5, 5, 5, 22), "Main hatch must open a position-bound GUI");
        drag(410, 442, 440, 345);
        require(main.input.count() == 64 && Inventory.countAt(0) == 6,
                "GUI input must cap machine stacks and return the remainder to the original player slot");
        Inventory.addToSlot(1, 21, 4);
        drag(432, 442, 575, 345);
        require(main.output.count() == 0 && Inventory.countAt(1) == 4,
                "GUI output must reject insertion");
        main.output.add(21, 2);
        drag(575, 345, 454, 442);
        require(main.output.count() == 0 && Inventory.itemIdAt(2) == 21 && Inventory.countAt(2) == 2,
                "GUI output must allow extraction into a chosen inventory slot");
        GuiManager.close();
        int[] front = main.fuelHatchPositions()[0];
        require(BlockGuiRegistry.openForBlock(world, front[0], front[1], front[2], 24), "Fuel hatch GUI must open");
        Inventory.addToSlot(3, 9, 2);
        drag(476, 442, 507, 336);
        require(hatch(world, 0).input.count() == 2, "Fuel GUI's central input should accept fuel stacks");
        Inventory.addToSlot(4, 106, 1);
        drag(498, 442, 507, 376);
        require(hatch(world, 0).output.count() == 0 && Inventory.countAt(4) == 1,
                "Fuel byproduct output must reject insertion");
        GuiManager.close(); GuiManager.resetForTests(); Inventory.clearForTests();
    }

    private static void drag(int fromX, int fromY, int toX, int toY) {
        GuiManager.handleMouseEvent(fromX, 767 - fromY, 0, true, 0, 1024, 768);
        GuiManager.handleMouseEvent(toX, 767 - toY, 0, false, 0, 1024, 768);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
