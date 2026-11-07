package dev.gtoe.agent;

/** Picking regression tests: all placement faces, occlusion, range, camera and bounded work. */
public final class BlockPickingVerification {
    private BlockPickingVerification() {
    }

    public static void run() {
        int[][] directions = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};
        for (int face = 0; face < directions.length; face++) {
            int[] direction = directions[face];
            TestLevel world = new TestLevel();
            int x = 5 + direction[0] * 2;
            int y = 5 + direction[1] * 2;
            int z = 5 + direction[2] * 2;
            world.setTile(x, y, z, 22);
            BlockPicker.Hit hit = BlockPicker.trace(world, 5.5, 5.5, 5.5,
                    direction[0], direction[1], direction[2], 4.0);
            require(hit != null && hit.x == x && hit.y == y && hit.z == z && hit.face == face,
                    "Picking must retain the game's placement face number " + face);
        }

        TestLevel world = new TestLevel();
        world.setTile(0, 0, -2, 9);
        world.setTile(0, 0, -3, 3);
        BlockPicker.Hit hit = BlockPicker.trace(world, 0.5, 0.5, 0.5, 0, 0, -1, 4);
        require(hit != null && hit.z == -2, "The nearest solid block must occlude farther blocks");
        require(BlockPicker.trace(world, 0.5, 0.5, 0.5, 0, 0, -1, 1.0) == null,
                "Blocks beyond reach must not be selectable");

        world = new TestLevel();
        world.setTile(1, 0, 0, 3);
        world.setTile(1, 1, 0, 10);
        hit = BlockPicker.trace(world, 0.5, 0.5, 0.5, 1, 1, 0, 3);
        require(hit != null && hit.x == 1 && hit.y == 1,
                "Rays crossing an exact corner must not select a side cell they only touch");

        world = new TestLevel();
        world.setTile(0, 0, 0, 3);
        world.setTile(-2, 0, 0, 9);
        hit = BlockPicker.trace(world, 0, 0.5, 0.5, -1, 0, 0, 4);
        require(hit != null && hit.x == -2,
                "A ray starting on a grid plane must not hit the block behind it");

        world = new TestLevel();
        require(BlockPicker.trace(world, 0.5, 20.5, 0.5, 1, 0, 1, 8) == null,
                "Air above the terrain must clear selection");
        require(world.reads <= 18, "Picking work must stay bounded by ray length, not nearby solid volume");

        TestPlayer player = new TestPlayer();
        world = new TestLevel();
        world.setTile(5, 5, 3, 21);
        hit = BlockPicker.pick(world, player, 1.0f);
        require(hit != null && hit.z == 3 && hit.face == 3
                && Math.abs(hit.distance - 1.8) < 0.00001,
                "Picking should use the rendered camera's 0.3-unit backward translation");
        world.setTile(5, 5, 3, 0);
        world.setTile(5, 5, 1, 21);
        require(BlockPicker.pick(world, player, 1.0f) == null,
                "Picking should preserve the original nearby candidate region");

        player.x = 6.5f;
        world.setTile(6, 5, 3, 21);
        hit = BlockPicker.pick(world, player, 0.5f);
        require(hit != null && hit.x == 6, "Selection must follow the interpolated rendered position");
        player.x = player.xo;
        player.yRot = 90;
        world.setTile(7, 5, 5, 3);
        hit = BlockPicker.pick(world, player, 1.0f);
        require(hit != null && hit.x == 7 && hit.face == 4, "Yaw must match the actual view transform");
        player.yRot = 0;
        player.xRot = 90;
        world.setTile(5, 3, 5, 3);
        hit = BlockPicker.pick(world, player, 1.0f);
        require(hit != null && hit.y == 3 && hit.face == 1, "Positive pitch must look downward");
        System.out.println("CPU picking verification passed: faces, occlusion, range, camera and bounded ray traversal");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static final class TestLevel {
        private final int[] blocks = new int[64 * 64 * 64];
        int reads;

        public int gtoe$getBlockId(int x, int y, int z) {
            reads++;
            if (x < -32 || y < -32 || z < -32 || x >= 32 || y >= 32 || z >= 32) {
                return -1;
            }
            return blocks[((y + 32) * 64 + z + 32) * 64 + x + 32];
        }

        public void setTile(int x, int y, int z, int id) {
            blocks[((y + 32) * 64 + z + 32) * 64 + x + 32] = id;
        }
    }

    public static final class TestPlayer {
        public float xo = 5.5f, yo = 5.5f, zo = 5.5f;
        public float x = xo, y = yo, z = zo;
        public float xRot, yRot;
        public TestBox bb = new TestBox();
    }

    public static final class TestBox {
        public float x0 = 5.2f, y0 = 3.88f, z0 = 5.2f;
        public float x1 = 5.8f, y1 = 5.68f, z1 = 5.8f;
    }
}
