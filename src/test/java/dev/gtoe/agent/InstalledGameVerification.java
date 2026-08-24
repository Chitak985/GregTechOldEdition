package dev.gtoe.agent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Structural verification against the installed, unmodified rd-132211 client JAR. */
public final class InstalledGameVerification {
    private InstalledGameVerification() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the installed rd-132211 client JAR path");
        }

        File gameJar = new File(arguments[0]);
        require(gameJar.isFile(), "Game JAR does not exist: " + gameJar);

        GtoeTransformer transformer = new GtoeTransformer();
        verifyRubyDung(transform(transformer, gameJar, GtoeTransformer.RUBY_DUNG));
        verifyLevel(transform(transformer, gameJar, GtoeTransformer.LEVEL));
        verifyTile(transform(transformer, gameJar, GtoeTransformer.TILE));
        verifyChunk(transform(transformer, gameJar, GtoeTransformer.CHUNK));
        verifyTextureResource();

        System.out.println("Installed-game verification passed for " + gameJar.getAbsolutePath());
    }

    private static byte[] transform(
            GtoeTransformer transformer, File gameJar, String internalName) throws Exception {
        byte[] original = readEntry(gameJar, internalName + ".class");
        byte[] transformed = transformer.transform(null, internalName, null, null, original);
        require(transformed != null, "Transformer returned no bytes for " + internalName);
        return transformed;
    }

    private static void verifyRubyDung(byte[] bytecode) {
        final boolean[] messageFound = {false};
        final boolean[] keyboardSelectionFound = {false};
        final boolean[] selectedPlacementFound = {false};
        final boolean[] hudFound = {false};
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (GtoeTransformer.INIT_MESSAGE.equals(value)) {
                            messageFound[0] = true;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String methodName, String methodDescriptor,
                            boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "dev/gtoe/agent/BlockSelection".equals(owner)
                                && "handleKeyEvent".equals(methodName)) {
                            keyboardSelectionFound[0] = true;
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                                && "dev/gtoe/agent/BlockSelection".equals(owner)
                                && "selectedBlockId".equals(methodName)) {
                            selectedPlacementFound[0] = true;
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                                && "dev/gtoe/agent/HudOverlay".equals(owner)
                                && "render".equals(methodName)
                                && "(II)V".equals(methodDescriptor)) {
                            hudFound[0] = true;
                        }
                    }
                };
            }
        }, 0);
        require(messageFound[0], "RubyDung.init()V does not contain the injected message");
        require(keyboardSelectionFound[0], "RubyDung does not forward number-key events");
        require(selectedPlacementFound[0], "RubyDung placement does not use the selected block ID");
        require(hudFound[0], "RubyDung does not render the top-left block overlay");
    }

    private static void verifyLevel(byte[] bytecode) {
        final boolean[] getterFound = {false};
        final boolean[] applyFound = {false};
        final boolean[] nonzeroCheckFound = {false};

        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                if (GtoeTransformer.BLOCK_ID_METHOD.equals(name) && "(III)I".equals(descriptor)) {
                    getterFound[0] = true;
                }

                if ("<init>".equals(name) && "(III)V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                                int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC
                                    && "dev/gtoe/agent/TerrainLayers".equals(owner)
                                    && "apply".equals(name)) {
                                applyFound[0] = true;
                            }
                        }
                    };
                }

                if ("isTile".equals(name) && "(III)Z".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                            if (opcode == Opcodes.IFEQ) {
                                nonzeroCheckFound[0] = true;
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        require(getterFound[0], "Level block-ID getter was not added");
        require(applyFound[0], "Level constructor does not apply Y layers");
        require(nonzeroCheckFound[0], "Level.isTile does not accept every nonzero block ID");
    }

    private static void verifyTile(byte[] bytecode) {
        final boolean[] fieldFound = {false};
        final boolean[] initializationFound = {false};
        final boolean[] horizontalAtlasFound = {false};
        final boolean[] verticalAtlasFound = {false};

        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                if (GtoeTransformer.TILE_ARRAY_FIELD.equals(name)
                        && ("[L" + GtoeTransformer.TILE + ";").equals(descriptor)) {
                    fieldFound[0] = true;
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                if ("<clinit>".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(
                                int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.PUTSTATIC
                                    && GtoeTransformer.TILE.equals(owner)
                                    && GtoeTransformer.TILE_ARRAY_FIELD.equals(name)) {
                                initializationFound[0] = true;
                            }
                        }
                    };
                }
                if ("render".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.IREM) {
                                horizontalAtlasFound[0] = true;
                            } else if (opcode == Opcodes.IDIV) {
                                verticalAtlasFound[0] = true;
                            }
                        }
                    };
                }
                return null;
            }
        }, 0);

        require(fieldFound[0], "Tile array field was not added");
        require(initializationFound[0], "Tile array field was not initialized");
        require(horizontalAtlasFound[0], "Tile.render does not wrap texture columns");
        require(verticalAtlasFound[0], "Tile.render does not select texture rows");
    }

    private static void verifyChunk(byte[] bytecode) {
        final boolean[] textureFound = {false};
        final int[] tileArrayReads = {0};
        final int[] oldTileReads = {0};
        final int[] blockIdCalls = {0};

        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (GtoeTransformer.AGENT_TERRAIN_RESOURCE.equals(value)) {
                            textureFound[0] = true;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode, String owner, String name, String descriptor) {
                        if (opcode == Opcodes.GETSTATIC
                                && GtoeTransformer.TILE.equals(owner)
                                && GtoeTransformer.TILE_ARRAY_FIELD.equals(name)) {
                            tileArrayReads[0]++;
                        }
                        if (opcode == Opcodes.GETSTATIC
                                && GtoeTransformer.TILE.equals(owner)
                                && ("rock".equals(name) || "grass".equals(name))) {
                            oldTileReads[0]++;
                        }
                    }

                    @Override
                    public void visitMethodInsn(
                            int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEVIRTUAL
                                && GtoeTransformer.LEVEL.equals(owner)
                                && GtoeTransformer.BLOCK_ID_METHOD.equals(name)) {
                            blockIdCalls[0]++;
                        }
                    }
                };
            }
        }, 0);

        require(textureFound[0], "Chunk does not load the agent-owned terrain texture");
        require(tileArrayReads[0] == 2, "Expected two ID-based tile array reads");
        require(oldTileReads[0] == 0, "Old rock/grass render selection remains");
        require(blockIdCalls[0] == 2, "Expected two block-ID getter calls");
    }

    private static void verifyTextureResource() throws IOException {
        InputStream input = GtoeAgent.class.getResourceAsStream(
                GtoeTransformer.AGENT_TERRAIN_RESOURCE);
        require(input != null, "Generated terrain texture is absent from the agent classpath");
        try {
            BufferedImage image = ImageIO.read(input);
            require(image != null, "Generated terrain texture is not a readable image");
            require(image.getWidth() == 256 && image.getHeight() == 256,
                    "Generated terrain texture must be 256x256");
            for (int blockId = 1; blockId <= TerrainLayers.MAX_BLOCK_ID; blockId++) {
                int slot = blockId - 1;
                int centerX = (slot % 16) * 16 + 8;
                int centerY = (slot / 16) * 16 + 8;
                require((image.getRGB(centerX, centerY) & 0x00FFFFFF) != 0x00FF00FF,
                        "Texture slot " + blockId + " is still the magenta placeholder");
            }
        } finally {
            input.close();
        }
    }

    private static byte[] readEntry(File jar, String entryName) throws IOException {
        ZipFile zip = new ZipFile(jar);
        try {
            ZipEntry entry = zip.getEntry(entryName);
            require(entry != null, "Missing class in game JAR: " + entryName);
            InputStream input = zip.getInputStream(entry);
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            } finally {
                input.close();
            }
        } finally {
            zip.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
