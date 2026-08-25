package dev.gtoe.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class GtoeTransformer implements ClassFileTransformer {
    static final String RUBY_DUNG = "com/mojang/rubydung/RubyDung";
    static final String LEVEL = "com/mojang/rubydung/level/Level";
    static final String TILE = "com/mojang/rubydung/level/Tile";
    static final String CHUNK = "com/mojang/rubydung/level/Chunk";
    static final String PLAYER = "com/mojang/rubydung/Player";

    static final String INIT_MESSAGE = "[gtoe] RubyDung.init() entered";
    static final String TILE_ARRAY_FIELD = "gtoe$tiles";
    static final String BLOCK_ID_METHOD = "gtoe$getBlockId";
    static final String AGENT_TERRAIN_RESOURCE = "/gtoe-terrain.png";

    private static final String TERRAIN_LAYERS = "dev/gtoe/agent/TerrainLayers";
    private static final String BLOCK_SELECTION = "dev/gtoe/agent/BlockSelection";
    private static final String HUD_OVERLAY = "dev/gtoe/agent/HudOverlay";
    private static final String GUI_MANAGER = "dev/gtoe/agent/GuiManager";
    private static final String WORLD_ACTIONS = "dev/gtoe/agent/WorldActions";

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            if (RUBY_DUNG.equals(className)) {
                return transformRubyDung(classfileBuffer);
            }
            if (LEVEL.equals(className)) {
                return transformLevel(classfileBuffer);
            }
            if (TILE.equals(className)) {
                return transformTile(classfileBuffer);
            }
            if (CHUNK.equals(className)) {
                return transformChunk(classfileBuffer);
            }
            if (PLAYER.equals(className)) {
                return transformPlayer(classfileBuffer);
            }
            return null;
        } catch (Throwable error) {
            System.err.println("[gtoe] Transformation failed for " + className
                    + "; class left unchanged");
            error.printStackTrace(System.err);
            return null;
        }
    }

    private static byte[] transformRubyDung(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] injected = {false};
        final boolean[] keyboardPatched = {false};
        final boolean[] breakPatched = {false};
        final boolean[] placementPatched = {false};
        final boolean[] hudPatched = {false};
        final boolean[] mouseEventPatched = {false};
        final int[] setTileCalls = {0};
        final int[] mouseButtonCalls = {0};
        final int[] mouseDeltaCalls = {0};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(
                        access, name, descriptor, signature, exceptions);
                if (!"init".equals(name) || !"()V".equals(descriptor)) {
                    if (!"render".equals(name) || !"(F)V".equals(descriptor)) {
                        return delegate;
                    }

                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String methodName,
                                String methodDescriptor,
                                boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC
                                    && "org/lwjgl/input/Mouse".equals(owner)
                                    && ("getDX".equals(methodName) || "getDY".equals(methodName))
                                    && "()I".equals(methodDescriptor)) {
                                super.visitMethodInsn(
                                        opcode, owner, methodName, methodDescriptor, isInterface);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        GUI_MANAGER,
                                        "filterMouseDelta",
                                        "(I)I",
                                        false);
                                mouseDeltaCalls[0]++;
                                return;
                            }

                            if (opcode == Opcodes.INVOKESTATIC
                                    && "org/lwjgl/input/Mouse".equals(owner)
                                    && "getEventButton".equals(methodName)
                                    && "()I".equals(methodDescriptor)) {
                                mouseButtonCalls[0]++;
                                if (mouseButtonCalls[0] == 1) {
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/lwjgl/input/Mouse",
                                            "getEventX",
                                            "()I",
                                            false);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/lwjgl/input/Mouse",
                                            "getEventY",
                                            "()I",
                                            false);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/lwjgl/input/Mouse",
                                            "getEventButton",
                                            "()I",
                                            false);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/lwjgl/input/Mouse",
                                            "getEventButtonState",
                                            "()Z",
                                            false);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            "org/lwjgl/input/Mouse",
                                            "getEventDWheel",
                                            "()I",
                                            false);
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitFieldInsn(Opcodes.GETFIELD, RUBY_DUNG, "width", "I");
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitFieldInsn(Opcodes.GETFIELD, RUBY_DUNG, "height", "I");
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            GUI_MANAGER,
                                            "handleMouseEvent",
                                            "(IIIZIII)V",
                                            false);
                                    mouseEventPatched[0] = true;
                                }
                            }

                            if (opcode == Opcodes.INVOKESTATIC
                                    && "org/lwjgl/input/Keyboard".equals(owner)
                                    && "getEventKey".equals(methodName)
                                    && "()I".equals(methodDescriptor)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/lwjgl/input/Keyboard",
                                        "getEventKey",
                                        "()I",
                                        false);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/lwjgl/input/Keyboard",
                                        "getEventKeyState",
                                        "()Z",
                                        false);
                                pushInt(this, 42);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/lwjgl/input/Keyboard",
                                        "isKeyDown",
                                        "(I)Z",
                                        false);
                                pushInt(this, 54);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "org/lwjgl/input/Keyboard",
                                        "isKeyDown",
                                        "(I)Z",
                                        false);
                                super.visitInsn(Opcodes.IOR);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        BLOCK_SELECTION,
                                        "handleKeyEvent",
                                        "(IZZ)V",
                                        false);
                                keyboardPatched[0] = true;
                            }

                            if (opcode == Opcodes.INVOKEVIRTUAL
                                    && LEVEL.equals(owner)
                                    && "setTile".equals(methodName)
                                    && "(IIII)V".equals(methodDescriptor)) {
                                setTileCalls[0]++;
                                if (setTileCalls[0] == 1) {
                                    super.visitInsn(Opcodes.POP);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            WORLD_ACTIONS,
                                            "breakBlockOrInteract",
                                            "(Ljava/lang/Object;III)V",
                                            false);
                                    breakPatched[0] = true;
                                    return;
                                }
                                if (setTileCalls[0] == 2) {
                                    super.visitInsn(Opcodes.POP);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            BLOCK_SELECTION,
                                            "selectedBlockId",
                                            "()I",
                                            false);
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESTATIC,
                                            WORLD_ACTIONS,
                                            "placeSelectedBlock",
                                            "(Ljava/lang/Object;IIII)V",
                                            false);
                                    placementPatched[0] = true;
                                    return;
                                }
                            }

                            if (opcode == Opcodes.INVOKESTATIC
                                    && "org/lwjgl/opengl/Display".equals(owner)
                                    && "update".equals(methodName)
                                    && "()V".equals(methodDescriptor)) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, RUBY_DUNG, "width", "I");
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, RUBY_DUNG, "height", "I");
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        HUD_OVERLAY,
                                        "render",
                                        "(II)V",
                                        false);
                                hudPatched[0] = true;
                            }

                            super.visitMethodInsn(
                                    opcode, owner, methodName, methodDescriptor, isInterface);
                        }
                    };
                }

                injected[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitFieldInsn(
                                Opcodes.GETSTATIC,
                                "java/lang/System",
                                "out",
                                "Ljava/io/PrintStream;");
                        super.visitLdcInsn(INIT_MESSAGE);
                        super.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL,
                                "java/io/PrintStream",
                                "println",
                                "(Ljava/lang/String;)V",
                                false);
                    }
                };
            }
        }, 0);

        require(injected[0], "RubyDung.init()V was not found");
        require(keyboardPatched[0], "RubyDung.render(F)V keyboard event loop was not recognized");
        require(breakPatched[0] && placementPatched[0] && setTileCalls[0] == 2,
                "RubyDung.render(F)V break/place calls were not recognized");
        require(mouseEventPatched[0] && mouseButtonCalls[0] == 2,
                "RubyDung.render(F)V mouse event loop was not recognized");
        require(mouseDeltaCalls[0] == 2,
                "RubyDung.render(F)V mouse camera deltas were not recognized");
        require(hudPatched[0], "RubyDung.render(F)V display update was not recognized");
        System.out.println("[gtoe] Added inventory-aware actions, GUI input, block selection, and HUD to RubyDung");
        return writer.toByteArray();
    }

    private static byte[] transformPlayer(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final int[] keyboardPolls = {0};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(
                        access, name, descriptor, signature, exceptions);
                if (!"tick".equals(name) || !"()V".equals(descriptor)) {
                    return delegate;
                }

                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String methodName,
                            String methodDescriptor,
                            boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                                && "org/lwjgl/input/Keyboard".equals(owner)
                                && "isKeyDown".equals(methodName)
                                && "(I)Z".equals(methodDescriptor)) {
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    GUI_MANAGER,
                                    "isGameplayKeyDown",
                                    "(I)Z",
                                    false);
                            keyboardPolls[0]++;
                            return;
                        }
                        super.visitMethodInsn(
                                opcode, owner, methodName, methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);

        require(keyboardPolls[0] > 0, "Player.tick()V keyboard polling was not recognized");
        System.out.println("[gtoe] Paused player keyboard movement while a GUI is open");
        return writer.toByteArray();
    }

    private static byte[] transformLevel(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] constructorPatched = {false};
        final boolean[] isTilePatched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(
                        access, name, descriptor, signature, exceptions);

                if ("<init>".equals(name) && "(III)V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        TERRAIN_LAYERS,
                                        "apply",
                                        "(Ljava/lang/Object;)V",
                                        false);
                                constructorPatched[0] = true;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }

                if ("isTile".equals(name) && "(III)Z".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        private boolean afterBlockLoad;
                        private boolean suppressedOne;

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.BALOAD) {
                                afterBlockLoad = true;
                                super.visitInsn(opcode);
                            } else if (afterBlockLoad && opcode == Opcodes.ICONST_1) {
                                afterBlockLoad = false;
                                suppressedOne = true;
                            } else {
                                afterBlockLoad = false;
                                super.visitInsn(opcode);
                            }
                        }

                        @Override
                        public void visitJumpInsn(int opcode, org.objectweb.asm.Label label) {
                            if (suppressedOne && opcode == Opcodes.IF_ICMPNE) {
                                super.visitJumpInsn(Opcodes.IFEQ, label);
                                isTilePatched[0] = true;
                            } else {
                                super.visitJumpInsn(opcode, label);
                            }
                            suppressedOne = false;
                        }
                    };
                }

                return delegate;
            }

            @Override
            public void visitEnd() {
                MethodVisitor getter = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                        BLOCK_ID_METHOD,
                        "(III)I",
                        null,
                        null);
                getter.visitCode();
                getter.visitVarInsn(Opcodes.ALOAD, 0);
                getter.visitFieldInsn(Opcodes.GETFIELD, LEVEL, "blocks", "[B");
                getter.visitVarInsn(Opcodes.ILOAD, 2);
                getter.visitVarInsn(Opcodes.ALOAD, 0);
                getter.visitFieldInsn(Opcodes.GETFIELD, LEVEL, "height", "I");
                getter.visitInsn(Opcodes.IMUL);
                getter.visitVarInsn(Opcodes.ILOAD, 3);
                getter.visitInsn(Opcodes.IADD);
                getter.visitVarInsn(Opcodes.ALOAD, 0);
                getter.visitFieldInsn(Opcodes.GETFIELD, LEVEL, "width", "I");
                getter.visitInsn(Opcodes.IMUL);
                getter.visitVarInsn(Opcodes.ILOAD, 1);
                getter.visitInsn(Opcodes.IADD);
                getter.visitInsn(Opcodes.BALOAD);
                getter.visitIntInsn(Opcodes.SIPUSH, 255);
                getter.visitInsn(Opcodes.IAND);
                getter.visitInsn(Opcodes.IRETURN);
                getter.visitMaxs(0, 0);
                getter.visitEnd();
                super.visitEnd();
            }
        }, 0);

        require(constructorPatched[0], "Level(int,int,int) constructor was not patched");
        require(isTilePatched[0], "Level.isTile(III)Z block comparison was not recognized");
        System.out.println("[gtoe] Enabled fixed initial Y layers and nonzero block-ID solidity in Level");
        return writer.toByteArray();
    }

    private static byte[] transformTile(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final boolean[] initialized = {false};
        final boolean[] textureCoordinatesPatched = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(
                    int version,
                    int access,
                    String name,
                    String signature,
                    String superName,
                    String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                FieldVisitor field = super.visitField(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        TILE_ARRAY_FIELD,
                        "[L" + TILE + ";",
                        null,
                        null);
                field.visitEnd();
            }

            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(
                        access, name, descriptor, signature, exceptions);
                if ("<clinit>".equals(name) && "()V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                pushInt(this, TerrainLayers.MAX_BLOCK_ID + 1);
                                super.visitTypeInsn(Opcodes.ANEWARRAY, TILE);

                                for (int blockId = 0; blockId <= TerrainLayers.MAX_BLOCK_ID; blockId++) {
                                    super.visitInsn(Opcodes.DUP);
                                    pushInt(this, blockId);
                                    super.visitTypeInsn(Opcodes.NEW, TILE);
                                    super.visitInsn(Opcodes.DUP);
                                    pushInt(this, Math.max(0, blockId - 1));
                                    super.visitMethodInsn(
                                            Opcodes.INVOKESPECIAL,
                                            TILE,
                                            "<init>",
                                            "(I)V",
                                            false);
                                    super.visitInsn(Opcodes.AASTORE);
                                }

                                super.visitFieldInsn(
                                        Opcodes.PUTSTATIC,
                                        TILE,
                                        TILE_ARRAY_FIELD,
                                        "[L" + TILE + ";");
                                initialized[0] = true;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }

                if ("render".equals(name)
                        && ("(Lcom/mojang/rubydung/level/Tesselator;L" + LEVEL
                        + ";IIII)V").equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitVarInsn(int opcode, int variable) {
                            super.visitVarInsn(opcode, variable);
                            if (!textureCoordinatesPatched[0]
                                    && opcode == Opcodes.FSTORE
                                    && variable == 10) {
                                // u0/u1 use tex % 16; v0/v1 use tex / 16.
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, TILE, "tex", "I");
                                pushInt(this, 16);
                                super.visitInsn(Opcodes.IREM);
                                super.visitInsn(Opcodes.I2F);
                                super.visitLdcInsn(16.0f);
                                super.visitInsn(Opcodes.FDIV);
                                super.visitVarInsn(Opcodes.FSTORE, 7);
                                super.visitVarInsn(Opcodes.FLOAD, 7);
                                super.visitLdcInsn(0.0624375f);
                                super.visitInsn(Opcodes.FADD);
                                super.visitVarInsn(Opcodes.FSTORE, 8);

                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, TILE, "tex", "I");
                                pushInt(this, 16);
                                super.visitInsn(Opcodes.IDIV);
                                super.visitInsn(Opcodes.I2F);
                                super.visitLdcInsn(16.0f);
                                super.visitInsn(Opcodes.FDIV);
                                super.visitVarInsn(Opcodes.FSTORE, 9);
                                super.visitVarInsn(Opcodes.FLOAD, 9);
                                super.visitLdcInsn(0.0624375f);
                                super.visitInsn(Opcodes.FADD);
                                super.visitVarInsn(Opcodes.FSTORE, 10);
                                textureCoordinatesPatched[0] = true;
                            }
                        }
                    };
                }

                return delegate;
            }
        }, 0);

        require(initialized[0], "Tile.<clinit>()V was not patched");
        require(textureCoordinatesPatched[0], "Tile.render texture coordinates were not recognized");
        System.out.println("[gtoe] Added 19 ID-addressable, multi-row block textures to Tile");
        return writer.toByteArray();
    }

    private static byte[] transformChunk(byte[] classfileBuffer) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final int[] tileSelections = {0};
        final boolean[] textureReplaced = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(
                        access, name, descriptor, signature, exceptions);

                if ("<clinit>".equals(name) && "()V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if ("/terrain.png".equals(value)) {
                                super.visitLdcInsn(AGENT_TERRAIN_RESOURCE);
                                textureReplaced[0] = true;
                            } else {
                                super.visitLdcInsn(value);
                            }
                        }
                    };
                }

                if ("rebuild".equals(name) && "(I)V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitFieldInsn(
                                int opcode, String owner, String fieldName, String fieldDescriptor) {
                            if (opcode == Opcodes.GETSTATIC
                                    && TILE.equals(owner)
                                    && ("rock".equals(fieldName) || "grass".equals(fieldName))
                                    && ("L" + TILE + ";").equals(fieldDescriptor)) {
                                super.visitFieldInsn(
                                        Opcodes.GETSTATIC,
                                        TILE,
                                        TILE_ARRAY_FIELD,
                                        "[L" + TILE + ";");
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(
                                        Opcodes.GETFIELD,
                                        CHUNK,
                                        "level",
                                        "L" + LEVEL + ";");
                                super.visitVarInsn(Opcodes.ILOAD, 3);
                                super.visitVarInsn(Opcodes.ILOAD, 4);
                                super.visitVarInsn(Opcodes.ILOAD, 5);
                                super.visitMethodInsn(
                                        Opcodes.INVOKEVIRTUAL,
                                        LEVEL,
                                        BLOCK_ID_METHOD,
                                        "(III)I",
                                        false);
                                super.visitInsn(Opcodes.AALOAD);
                                tileSelections[0]++;
                            } else {
                                super.visitFieldInsn(opcode, owner, fieldName, fieldDescriptor);
                            }
                        }
                    };
                }

                return delegate;
            }
        }, 0);

        require(textureReplaced[0], "Chunk terrain resource was not recognized");
        require(tileSelections[0] == 2,
                "Expected two Tile.rock/Tile.grass render selections, found " + tileSelections[0]);
        System.out.println("[gtoe] Enabled ID-based block rendering in Chunk");
        return writer.toByteArray();
    }

    private static void pushInt(MethodVisitor visitor, int value) {
        if (value >= -1 && value <= 5) {
            visitor.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            visitor.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            visitor.visitLdcInsn(value);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
