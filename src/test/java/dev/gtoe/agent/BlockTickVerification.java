package dev.gtoe.agent;

import java.util.Arrays;
import java.util.Collections;

/** Block timing, lifecycle and rotation tests using a small, independent world fixture. */
public final class BlockTickVerification {
    private BlockTickVerification() {
    }

    public static void run() throws Exception {
        verifyTicking();
        verifyOffsetsAndSymbols();
        verifyFurnace();
        System.out.println("Block ticking verification passed: 20 Hz, lifecycle, offsets and four rotations");
    }

    private static void verifyTicking() {
        BlockRegistry.register(98, CountingBlock::new);
        try {
            TestLevel world = new TestLevel();
            world.setTile(3, 4, 5, 98);
            world.setTile(4, 4, 5, 98);
            world.gtoe$blockTicker = BlockTicker.initialize(world);
            CountingBlock first = (CountingBlock) world.gtoe$blockTicker.blockAt(3, 4, 5);
            CountingBlock second = (CountingBlock) world.gtoe$blockTicker.blockAt(4, 4, 5);
            require(first != second && first.level == world && first.x == 3
                    && first.y == 4 && first.z == 5, "Positions need independent runtime block instances");
            gameTicks(world, 2);
            require(first.calls == 0, "Blocks must not run at the game's 60 Hz rate");
            gameTicks(world, 58);
            require(first.calls == 20 && second.calls == 20
                    && world.gtoe$blockTicker.tickCount() == 20,
                    "60 game ticks must call each block's Tick exactly 20 times");
            world.setTile(3, 4, 5, 98);
            require(world.gtoe$blockTicker.blockAt(3, 4, 5) == first,
                    "An unchanged ID should preserve machine state");
            world.setTile(3, 4, 5, 0);
            gameTicks(world, 3);
            require(first.calls == 20 && world.gtoe$blockTicker.blockAt(3, 4, 5) == null,
                    "Broken blocks must stop ticking");
            world.setTile(3, 4, 5, 98);
            CountingBlock replacement = (CountingBlock) world.gtoe$blockTicker.blockAt(3, 4, 5);
            gameTicks(world, 3);
            require(replacement != first && replacement.calls == 1,
                    "Replaced blocks must start with a fresh instance");
            world.setTile(3, 4, 5, 3);
            require(world.gtoe$blockTicker.blockAt(3, 4, 5).getClass() == AdvancedBlock.class,
                    "Ordinary blocks should fall back to AdvancedBlock");
            BlockTicker previous = world.gtoe$blockTicker;
            BlockTicker.onLevelLoaded(world);
            require(world.gtoe$blockTicker != previous && world.gtoe$blockTicker.tickCount() == 0,
                    "Reloading a world should rebuild its state and tick clock");
            TestLevel otherWorld = new TestLevel();
            otherWorld.setTile(4, 4, 5, 98);
            otherWorld.gtoe$blockTicker = BlockTicker.initialize(otherWorld);
            gameTicks(otherWorld, 60);
            require(second.calls == 22, "World tick clocks must not affect other worlds");
            require(world.gtoe$blockTicker.blockAt(-1, 4, 5) == null,
                    "Out-of-world block instances must be absent");
        } finally {
            BlockRegistry.register(98, AdvancedBlock::new);
        }
    }

    private static void verifyOffsetsAndSymbols() throws Exception {
        MultiblockStructure shape = new MultiblockStructure(22,
                Collections.singletonList(Arrays.asList("A? ", "-!B")),
                MultiblockStructure.CreateBlockMapping(Arrays.asList('A', 'B'), Arrays.asList(3, 21)));
        require(shape.controllerStructureRow == 1 && shape.controllerStructureRowIndex == 1,
                "Controller row/column must reset at each row and slice");
        for (int rotation = 0; rotation < 4; rotation++) {
            TestLevel world = new TestLevel();
            int x = 5, y = 5, z = 5;
            world.setTile(x, y, z, 22);
            int[] left = rotate(-1, 0, rotation);
            int[] right = rotate(1, 0, rotation);
            world.setTile(x + left[0], y + 1, z + left[1], 3);
            world.setTile(x, y + 1, z, 7); // ? = solid, without an ID requirement.
            world.setTile(x + right[0], y, z + right[1], 21);
            require(shape.FindMatchingRotation(world, x, y, z) == rotation,
                    "Asymmetric controller-relative offsets must rotate correctly: " + rotation);
            world.setTile(x + left[0], y, z + left[1], 8); // - accepts a solid block too.
            require(shape.CheckStructure(world, x, y, z), "Dash must accept both air and solid blocks");
            world.setTile(x + right[0], y + 1, z + right[1], 1);
            require(!shape.CheckStructure(world, x, y, z), "Space must require air");
            world.setTile(x + right[0], y + 1, z + right[1], 0);
            world.setTile(x, y + 1, z, 0);
            require(!shape.CheckStructure(world, x, y, z), "Question mark must reject air");
        }
        require(!shape.CheckStructure(new TestLevel(), 0, 0, 0),
                "Missing or out-of-world blocks must never form a structure");
        boolean rejected = false;
        try {
            new MultiblockStructure(22, Collections.singletonList(Arrays.asList("!X")),
                    new java.util.HashMap<Character, Integer>());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Undefined structure symbols must fail instead of skipping requirements");
    }

    private static void verifyFurnace() throws Exception {
        // Original definition, front/controller slice first, with rows drawn top to bottom.
        String[][] slices = {
            {"-2-", "2!2", "252", "111"},
            {"262", "141", "535", "111"},
            {"-2-", "212", "252", "111"}
        };
        int[] ids = {0, 21, 3, 25, 26, 24, 23};
        MultiblockStructure rules = MachineMultiblock.GetMultiblockRulesForBlock(22);
        require(rules != null, "Furnace rules must be associated with controller ID 22");
        for (int rotation = 0; rotation < 4; rotation++) {
            TestLevel world = new TestLevel();
            for (int slice = 0; slice < 3; slice++) {
                for (int row = 0; row < 4; row++) {
                    for (int column = 0; column < 3; column++) {
                        char symbol = slices[slice][row].charAt(column);
                        if (symbol == '-') {
                            continue;
                        }
                        int[] horizontal = rotate(column - 1, slice, rotation);
                        world.setTile(5 + horizontal[0], 6 - row, 5 + horizontal[1],
                                symbol == '!' ? 22 : ids[symbol - '0']);
                    }
                }
            }
            require(rules.CheckStructure(world, 5, 5, 5),
                    "The supplied furnace should form in direction " + rotation);
            world.gtoe$blockTicker = BlockTicker.initialize(world);
            MachineMultiblock machine = (MachineMultiblock) world.gtoe$blockTicker.blockAt(5, 5, 5);
            gameTicks(world, 27);
            require(!machine.isFormed(), "Structure should wait until the tenth block tick");
            gameTicks(world, 3);
            require(machine.isFormed() && machine.matchingRotation() == rotation,
                    "Tenth tick must form the furnace in any of the four directions");
            world.setTile(5, 3, 5, 0); // Front slice, bottom-middle hardened stone.
            gameTicks(world, 27);
            require(machine.isFormed(), "Checks should be spaced by ten ticks");
            gameTicks(world, 3);
            require(!machine.isFormed() && machine.matchingRotation() == -1,
                    "A broken component should invalidate the machine on the next check");
        }
    }

    private static int[] rotate(int dx, int dz, int rotation) {
        switch (rotation) {
            case 1: return new int[] {-dz, dx};
            case 2: return new int[] {-dx, -dz};
            case 3: return new int[] {dz, -dx};
            default: return new int[] {dx, dz};
        }
    }

    private static void gameTicks(Object world, int count) {
        for (int tick = 0; tick < count; tick++) {
            BlockTicker.onGameTick(world);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static final class CountingBlock extends AdvancedBlock {
        int calls;

        public CountingBlock(Object level, int x, int y, int z, int blockId) {
            super(level, x, y, z, blockId);
        }

        @Override
        public void Tick() {
            calls++;
        }
    }

    public static final class TestLevel {
        public final int width = 12;
        public final int height = 12;
        public final int depth = 12;
        private final byte[] blocks = new byte[width * height * depth];
        public BlockTicker gtoe$blockTicker;

        public int gtoe$getBlockId(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) {
                return -1;
            }
            return blocks[(y * height + z) * width + x] & 255;
        }

        public void setTile(int x, int y, int z, int id) {
            blocks[(y * height + z) * width + x] = (byte) id;
            BlockTicker.onBlockChanged(this, x, y, z);
        }
    }
}
