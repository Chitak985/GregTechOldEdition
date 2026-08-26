package dev.gtoe.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Bytecode-level smoke test. The fixture is generated here and contains no
 * Minecraft or decompiled source.
 */
public final class TransformerVerification {
    private TransformerVerification() {
    }

    public static void main(String[] arguments) throws Exception {
        byte[] fixture = createFixture();
        GtoeTransformer transformer = new GtoeTransformer();

        byte[] ignored = transformer.transform(
                null, "example/NotRubyDung", null, null, fixture);
        require(ignored == null, "The transformer must ignore every non-target class");

        byte[] transformed = transformer.transform(
                null, GtoeTransformer.RUBY_DUNG, null, null, fixture);
        require(transformed != null, "The target class was not transformed");

        List<String> instructions = readInitialInstructions(transformed);
        List<String> expected = Arrays.asList(
                "FIELD 178 java/lang/System.out Ljava/io/PrintStream;",
                "LDC " + GtoeTransformer.INIT_MESSAGE,
                "METHOD 182 java/io/PrintStream.println (Ljava/lang/String;)V",
                "INSN 3");
        require(instructions.equals(expected),
                "Unexpected instructions at the start of init()V: " + instructions);

        TerrainLayers.printConfiguration();
        require(TerrainLayers.blockIdForY(0) == 5, "Y=0 should use block ID 5");
        require(TerrainLayers.blockIdForY(19) == 4, "Y=19 should use block ID 4");
        require(TerrainLayers.blockIdForY(20) == 3, "Y=20 should use block ID 3");
        require(TerrainLayers.blockIdForY(40) == 1, "Y=40 should use block ID 1");
        verifyTreeGeneration();

        BlockSelection.handleKeyEvent(5, true, false);
        require(BlockSelection.selectedBlockId() == 4, "Number 4 should select block ID 4");
        BlockSelection.handleKeyEvent(9, false, false);
        require(BlockSelection.selectedBlockId() == 4, "Key release must not change selection");
        BlockSelection.handleKeyEvent(11, true, false);
        require(BlockSelection.selectedBlockId() == 10, "Number 0 should select block ID 10");
        BlockSelection.handleKeyEvent(2, true, true);
        require(BlockSelection.selectedBlockId() == 11, "Shift+1 should select block ID 11");
        BlockSelection.handleKeyEvent(10, true, true);
        require(BlockSelection.selectedBlockId() == 19, "Shift+9 should select block ID 19");
        require("Machine".equals(BlockSelection.selectedBlockName()),
                "Block ID 19 should be named Machine");

        verifyInventoryAndRecipes();

        System.out.println("Transformer verification passed: trees, inventory, recipes, input, and world actions are correct");
    }

    private static void verifyTreeGeneration() {
        int width = 100;
        int height = 1;
        int depth = 16;
        byte[] blocks = flatWorld(width, height, depth, 0);
        int trees = TreeGenerator.generate(
                blocks, width, height, depth, new CyclingChanceRandom());
        require(trees == 5, "Exactly 5 of 100 eligible surface blocks should pass a 5% roll");

        int woodBlocks = 0;
        for (byte block : blocks) {
            if ((block & 255) == 9) {
                woodBlocks++;
            }
        }
        require(woodBlocks == trees * 4,
                "Minimum-height tree rolls should create four wood blocks per trunk");
        for (int x = 0; x < width; x++) {
            require((blocks[x] & 255) == 1, "Tree generation must not replace surface blocks");
        }

        byte[] maximumTree = flatWorld(1, 1, 16, 0);
        int maximumTrees = TreeGenerator.generate(
                maximumTree, 1, 1, 16, new MaximumHeightRandom());
        require(maximumTrees == 1, "Forced chance roll should create one tree");
        for (int y = 1; y <= 10; y++) {
            require((maximumTree[y] & 255) == 9,
                    "Maximum-height tree should contain wood at Y=" + y);
        }
        require((maximumTree[11] & 255) == 0,
                "Tree height must never exceed ten blocks");
    }

    private static byte[] flatWorld(int width, int height, int depth, int surfaceY) {
        byte[] blocks = new byte[width * height * depth];
        for (int y = 0; y <= surfaceY; y++) {
            for (int z = 0; z < height; z++) {
                for (int x = 0; x < width; x++) {
                    blocks[(y * height + z) * width + x] = 1;
                }
            }
        }
        return blocks;
    }

    private static final class CyclingChanceRandom extends Random {
        private static final long serialVersionUID = 1L;
        private int chance;

        @Override
        public int nextInt(int bound) {
            if (bound == 100) {
                return chance++ % 100;
            }
            return 0;
        }
    }

    private static final class MaximumHeightRandom extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        public int nextInt(int bound) {
            return bound == 100 ? 0 : bound - 1;
        }
    }

    private static void verifyInventoryAndRecipes() {
        Inventory.clearForTests();
        GuiManager.resetForTests();
        WorldActions.resetForTests();

        FakeLevel level = new FakeLevel();
        level.setTile(1, 1, 1, 2);
        WorldActions.breakBlockOrInteract(level, 1, 1, 1);
        require(level.gtoe$getBlockId(1, 1, 1) == 0, "Breaking should remove the block");
        require(Inventory.count(2) == 1, "Breaking dirt should add one dirt block");

        WorldActions.placeSelectedBlock(level, 2, 1, 1, 2);
        require(level.gtoe$getBlockId(2, 1, 1) == 2, "Owned dirt should be placeable");
        require(Inventory.count(2) == 0, "Successful placement should consume one dirt block");

        WorldActions.placeSelectedBlock(level, 3, 1, 1, 2);
        require(level.gtoe$getBlockId(3, 1, 1) == 0,
                "Placement without the selected block must be rejected");

        require(Arrays.equals(
                        GuiManager.recipeFor(new int[] {9, -1, -1, -1}),
                        new int[] {10, 2}),
                "One wood anywhere should craft two planks");
        require(Arrays.equals(
                        GuiManager.recipeFor(new int[] {-1, 10, -1, 10}),
                        new int[] {100, 2}),
                "Two vertical planks should craft two sticks");
        require(Arrays.equals(
                        GuiManager.recipeFor(new int[] {10, 10, -1, -1}),
                        new int[] {-1, 0}),
                "Horizontal planks must not match the stick recipe");
        require("Stick".equals(ItemCatalog.itemName(100)), "Item ID 100 should be Stick");

        // Exercise the real drag/release/output-click flow at the 1024x768 game size.
        Inventory.add(9, 1);
        GuiManager.handleKeyEvent(18, true);
        drag(410, 442, 440, 320); // Inventory wood -> upper-left crafting slot.
        click(530, 332); // Output: two planks.
        require(Inventory.count(9) == 0,
                "Crafting planks should consume one wood");
        require(Inventory.count(10) == 2,
                "Crafting wood should produce two planks");

        drag(410, 442, 440, 320); // First plank -> upper-left.
        drag(410, 442, 440, 352); // Second plank -> lower-left.
        click(530, 332); // Output: two sticks.
        require(Inventory.count(10) == 0,
                "Crafting sticks should consume two vertical planks");
        require(Inventory.count(100) == 2,
                "Two vertical planks should produce two sticks");
        GuiManager.handleKeyEvent(18, true);

        int selectionBeforeGui = BlockSelection.selectedBlockId();
        BlockSelection.handleKeyEvent(18, true, false);
        require(GuiManager.isOpen(), "E should open crafting");
        BlockSelection.handleKeyEvent(2, true, false);
        require(BlockSelection.selectedBlockId() == selectionBeforeGui,
                "Number keys must not change selection while a GUI is open");
        BlockSelection.handleKeyEvent(18, true, false);
        require(!GuiManager.isOpen(), "E should close crafting");

        require(BlockGuiRegistry.openForBlock(10),
                "Planks should be registered with the reusable block GUI system");
        // Screen-space button point (450,340), converted back to LWJGL's bottom-left Y.
        GuiManager.handleMouseEvent(450, 427, 0, true, 0, 1024, 768);
        require(!GuiManager.isOpen(), "The reusable simple-GUI button should close its window");
        require(GuiManager.blocksWorldAction(),
                "A GUI close click must not fall through to world placement");

        Inventory.clearForTests();
        GuiManager.resetForTests();
        WorldActions.resetForTests();
    }

    private static void drag(int fromX, int fromY, int toX, int toY) {
        mouse(fromX, fromY, true);
        mouse(toX, toY, false);
    }

    private static void click(int x, int y) {
        mouse(x, y, true);
        mouse(x, y, false);
    }

    private static void mouse(int screenX, int screenY, boolean pressed) {
        GuiManager.handleMouseEvent(
                screenX, 768 - 1 - screenY, 0, pressed, 0, 1024, 768);
    }

    private static byte[] createFixture() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_6,
                Opcodes.ACC_PUBLIC,
                GtoeTransformer.RUBY_DUNG,
                null,
                "java/lang/Object",
                null);
        writer.visitField(Opcodes.ACC_PRIVATE, "width", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "height", "I", null, null).visitEnd();

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "init",
                "()V",
                null,
                null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();

        MethodVisitor render = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "render",
                "(F)V",
                null,
                null);
        render.visitCode();

        render.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/lwjgl/input/Mouse",
                "getDX",
                "()I",
                false);
        render.visitInsn(Opcodes.POP);
        render.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/lwjgl/input/Mouse",
                "getDY",
                "()I",
                false);
        render.visitInsn(Opcodes.POP);

        // The real mouse loop reads the event button twice.
        render.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/lwjgl/input/Mouse",
                "getEventButton",
                "()I",
                false);
        render.visitInsn(Opcodes.POP);
        render.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/lwjgl/input/Mouse",
                "getEventButton",
                "()I",
                false);
        render.visitInsn(Opcodes.POP);

        // Removal call: setTile(0, 0, 0, 0).
        render.visitInsn(Opcodes.ACONST_NULL);
        render.visitInsn(Opcodes.ICONST_0);
        render.visitInsn(Opcodes.ICONST_0);
        render.visitInsn(Opcodes.ICONST_0);
        render.visitInsn(Opcodes.ICONST_0);
        render.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                GtoeTransformer.LEVEL,
                "setTile",
                "(IIII)V",
                false);

        // Placement call: the hardcoded ID 1 is replaced by the current selection.
        render.visitInsn(Opcodes.ACONST_NULL);
        render.visitInsn(Opcodes.ICONST_0);
        render.visitInsn(Opcodes.ICONST_0);
        render.visitInsn(Opcodes.ICONST_0);
        render.visitInsn(Opcodes.ICONST_1);
        render.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                GtoeTransformer.LEVEL,
                "setTile",
                "(IIII)V",
                false);

        render.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/lwjgl/input/Keyboard",
                "getEventKey",
                "()I",
                false);
        render.visitInsn(Opcodes.POP);
        render.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "org/lwjgl/opengl/Display",
                "update",
                "()V",
                false);
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(5, 2);
        render.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    /** Reflection-compatible stand-in; it is test code, not a copied game class. */
    public static final class FakeLevel {
        private final int[][][] blocks = new int[4][4][4];

        public int gtoe$getBlockId(int x, int y, int z) {
            return blocks[x][y][z];
        }

        public void setTile(int x, int y, int z, int blockId) {
            blocks[x][y][z] = blockId;
        }
    }

    private static List<String> readInitialInstructions(byte[] bytecode) {
        final List<String> instructions = new ArrayList<String>();
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                if (!"init".equals(name) || !"()V".equals(descriptor)) {
                    return null;
                }

                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(
                            int opcode, String owner, String name, String descriptor) {
                        add("FIELD " + opcode + " " + owner + "." + name + " " + descriptor);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        add("LDC " + value);
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String name,
                            String descriptor,
                            boolean isInterface) {
                        add("METHOD " + opcode + " " + owner + "." + name + " " + descriptor);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        add("INSN " + opcode);
                    }

                    private void add(String instruction) {
                        if (instructions.size() < 4) {
                            instructions.add(instruction);
                        }
                    }
                };
            }
        }, 0);
        return instructions;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
