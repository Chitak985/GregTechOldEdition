package dev.gtoe.agent;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

/** Optional offscreen OpenGL QA. Uses installed LWJGL natives; never opens the game or touches saves. */
public final class FurnaceGuiRenderVerification {
    public static void main(String[] arguments) throws Exception {
        Path destination = Paths.get(arguments[0]);
        Files.createDirectories(destination);
        Pbuffer buffer = new Pbuffer(1024, 768, new PixelFormat(), null, null);
        try {
            buffer.makeCurrent();
            GL11.glViewport(0, 0, 1024, 768);
            BlockTickVerification.TestLevel world = FurnaceVerification.createFurnace(0);
            MachineMultiblock main = FurnaceVerification.main(world);
            main.input.add(3, 2);
            main.output.add(21, 2);
            FurnaceVerification.hatch(world, 0).input.add(9, 2);
            FurnaceVerification.ticks(world, 46);
            GuiManager.openFurnace(main);
            render(destination.resolve("main-processing.png"));
            GuiManager.openFurnace(FurnaceVerification.hatch(world, 0));
            render(destination.resolve("fuel-lit.png"));
            world.setTile(5, 3, 5, 0);
            render(destination.resolve("fuel-unlit.png"));
            GuiManager.openFurnace(main);
            render(destination.resolve("main-unformed.png"));
            GuiManager.close();
            world = FurnaceVerification.createFurnace(0);
            main = FurnaceVerification.main(world);
            main.input.add(3, 1);
            FurnaceVerification.ticks(world, 1);
            GuiManager.openFurnace(main);
            render(destination.resolve("main-insufficient-fuel.png"));
            System.out.println("Offscreen furnace GUI rendering passed on " + GL11.glGetString(GL11.GL_RENDERER));
        } finally {
            GuiManager.close();
            buffer.destroy();
        }
    }

    private static void render(Path file) throws Exception {
        GL11.glClearColor(0.08f, 0.11f, 0.14f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        HudOverlay.render(1024, 768);
        ByteBuffer pixels = BufferUtils.createByteBuffer(1024 * 768 * 4);
        GL11.glReadPixels(0, 0, 1024, 768, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        if (GL11.glGetError() != GL11.GL_NO_ERROR) { throw new AssertionError("Furnace GUI raised an OpenGL error"); }
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 768; y++) {
            for (int x = 0; x < 1024; x++) {
                int offset = ((767 - y) * 1024 + x) * 4;
                int color = 0xFF000000 | ((pixels.get(offset) & 255) << 16)
                        | ((pixels.get(offset + 1) & 255) << 8) | (pixels.get(offset + 2) & 255);
                image.setRGB(x, y, color);
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}
