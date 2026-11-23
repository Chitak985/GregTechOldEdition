package dev.gtoe.agent;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Offscreen visual QA of both recipe books; never opens Minecraft or reads a real save. */
public final class RecipeBookRenderVerification {
    public static void main(String[] args) throws Exception {
        Path destination = Paths.get(args[0]);
        Files.createDirectories(destination);
        Pbuffer buffer = new Pbuffer(1024, 768, new PixelFormat(), null, null);
        try {
            buffer.makeCurrent();
            GuiManager.resetForWorld();
            Inventory.clearForTests();
            Inventory.addToSlot(Inventory.HOTBAR_START, 9, 16);
            Inventory.addToSlot(Inventory.HOTBAR_START + 1, 10, 8);
            Inventory.addToSlot(Inventory.HOTBAR_START + 2, 106, 2);
            Inventory.addToSlot(Inventory.HOTBAR_START + 3, 20, 3);
            Inventory.addToSlot(Inventory.HOTBAR_START + 4, 3, 12);
            Inventory.addToSlot(Inventory.HOTBAR_START + 5, 21, 8);
            GuiManager.handleKeyEvent(18, true);
            render(destination.resolve("inventory-book-button.png"), 1024, 768);
            open(1024, 768);
            render(destination.resolve("inventory-book.png"), 1024, 768);
            RecipeBook.Layout book = book(1024, 768);
            click(book.filterX() + 5, book.y + 42, 1024, 768);
            render(destination.resolve("inventory-craftable.png"), 1024, 768);
            search("stone", 1024, 768);
            render(destination.resolve("inventory-search.png"), 1024, 768);
            RecipeBook.reset();
            GuiManager.openCraftingTable();
            open(640, 480);
            render(destination.resolve("table-book.png"), 640, 480);
            search("furnace", 640, 480);
            book = book(640, 480);
            click(book.x + 16, book.listY + 10, 640, 480);
            render(destination.resolve("table-preview.png"), 640, 480);
            RecipeBook.reset();
            GuiManager.close();
            GuiManager.handleKeyEvent(18, true);
            open(320, 480);
            render(destination.resolve("compact-book.png"), 320, 480);
            book = book(320, 480);
            click(book.x + 16, book.listY + 10, 320, 480);
            render(destination.resolve("compact-filled.png"), 320, 480);
            System.out.println("Recipe-book rendering passed on " + GL11.glGetString(GL11.GL_RENDERER));
        } finally {
            GuiManager.close();
            buffer.destroy();
        }
    }

    private static RecipeBook.Layout book(int w, int h) { return new RecipeBook.Layout(GuiManager.craftingLayout(w, h)); }
    private static void open(int w, int h) { RecipeBook.Layout b = book(w, h); click(b.buttonX + 10, b.buttonY + 10, w, h); }
    private static void search(String value, int w, int h) {
        RecipeBook.Layout b = book(w, h);
        click(b.x + 12, b.y + 16, w, h);
        for (char c : value.toCharArray()) { GuiManager.handleKeyEvent(0, true, c); }
    }
    private static void click(int x, int y, int w, int h) {
        GuiManager.handleMouseEvent(x, h - 1 - y, 0, true, 0, w, h);
        GuiManager.handleMouseEvent(x, h - 1 - y, 0, false, 0, w, h);
    }

    private static void render(Path file, int w, int h) throws Exception {
        GL11.glViewport(0, 0, w, h);
        GL11.glClearColor(0.08f, 0.11f, 0.14f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GuiGraphics.begin(w, h);
        try { GuiManager.render(w, h); GuiManager.renderHotbar(w, h); } finally { GuiGraphics.end(); }
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        if (GL11.glGetError() != GL11.GL_NO_ERROR) { throw new AssertionError("Recipe book raised an OpenGL error"); }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                image.setRGB(x, y, 0xFF000000 | (pixels.get(i) & 255) << 16
                        | (pixels.get(i + 1) & 255) << 8 | pixels.get(i + 2) & 255);
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}
