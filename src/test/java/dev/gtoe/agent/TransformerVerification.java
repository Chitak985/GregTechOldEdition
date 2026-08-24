package dev.gtoe.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        System.out.println("Transformer verification passed: layers and number-key selection are correct");
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
