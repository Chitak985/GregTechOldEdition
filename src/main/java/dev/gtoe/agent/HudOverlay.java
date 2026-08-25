package dev.gtoe.agent;

import org.lwjgl.opengl.GL11;

// Should be temporary as well until inventory is moved somewhere else
import java.util.ArrayList;
import java.util.List;

/** Minimal fixed-function pixel-text overlay for the selected block. */
public final class HudOverlay {
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    private static volatile boolean disabled;

    private HudOverlay() {
    }

    // TEMPORARY LOCATION FOR FUNCTION
    // This needs to be global and easily accessible
    public static String itemName(int itemId) {
        // Items 0-99 are blocks, so they can use the block name function
        if (itemId <= 99) {
            return blockName(itemId);
        }
        // Everything else are items, not blocks
        switch (itemId) {
            case 100:
                return "Stick";
            case 101:
                return "Copper Ingot";
            case 102:
                return "Iron Ingot";
            case 103:
                return "Coal";
            case 104:
                return "Tin Ingot";
            case 105:
                return "Bronze Ingot";
            default:
                return "Unknown Item";
        }
    }

    public static void render(int screenWidth, int screenHeight) {
        if (disabled || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        try {
            // Setup display
            GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, screenWidth, screenHeight, 0.0, -1.0, 1.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_FOG);

            // Draw block indicator
            drawTextNormal(
                "BLOCK " + BlockSelection.selectedBlockName().toUpperCase(), 
            4, 4);

            // TEMPORARY LOGIC
            List<int> inventory = new ArrayList<int>();  // This needs to be stored globally somewhere
            inventory.add(2);  // How to add an item, in this case dirt

            // Draw inventory
            int tmpY = 20;
            for (String item : inventory) {
                drawTextNormal(
                    item,
                4, tmpY)
                tmpY += 16
            }

            // Finish setting up display
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        } catch (Throwable error) {
            disabled = true;
            System.err.println("[gtoe] Disabling block overlay after a rendering error");
            error.printStackTrace(System.err);
        }
    }

    private static void drawTextNormal(
            String text, int startX, int startY) {
        drawTextShadowed(text, startX, startY, 2, 0.93f, 0.93f, 0.93f);
    }

    private static void drawTextShadowed(
            String text, int startX, int startY, int scale, float red, float green, float blue) {
            drawString(text, startX+1, startY+1, scale, 0.08f, 0.08f, 0.08f);
            drawString(text, startX, startY, scale, red, green, blue);
    }

    private static void drawString(
            String text, int startX, int startY, int scale, float red, float green, float blue) {
        GL11.glColor3f(red, green, blue);
        GL11.glBegin(GL11.GL_QUADS);
        int cursorX = startX;

        for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            char character = text.charAt(characterIndex);
            int[] rows = glyph(character);
            for (int row = 0; row < 7; row++) {
                int bits = rows[row];
                for (int column = 0; column < 5; column++) {
                    if ((bits & (1 << (4 - column))) == 0) {
                        continue;
                    }
                    float x0 = cursorX + column * scale;
                    float y0 = startY + row * scale;
                    float x1 = x0 + scale;
                    float y1 = y0 + scale;
                    GL11.glVertex2f(x0, y0);
                    GL11.glVertex2f(x1, y0);
                    GL11.glVertex2f(x1, y1);
                    GL11.glVertex2f(x0, y1);
                }
            }
            cursorX += 6 * scale;
        }

        GL11.glEnd();
    }

    private static int[] glyph(char character) {
        switch (character) {
            case 'A': return rows(14, 17, 17, 31, 17, 17, 17);
            case 'B': return rows(30, 17, 17, 30, 17, 17, 30);
            case 'C': return rows(14, 17, 16, 16, 16, 17, 14);
            case 'D': return rows(30, 17, 17, 17, 17, 17, 30);
            case 'E': return rows(31, 16, 16, 30, 16, 16, 31);
            case 'F': return rows(31, 16, 16, 30, 16, 16, 16);
            case 'G': return rows(14, 17, 16, 23, 17, 17, 14);
            case 'H': return rows(17, 17, 17, 31, 17, 17, 17);
            case 'I': return rows(31, 4, 4, 4, 4, 4, 31);
            case 'J': return rows(7, 2, 2, 2, 18, 18, 12);
            case 'K': return rows(17, 18, 20, 24, 20, 18, 17);
            case 'L': return rows(16, 16, 16, 16, 16, 16, 31);
            case 'M': return rows(17, 27, 21, 21, 17, 17, 17);
            case 'N': return rows(17, 25, 21, 19, 17, 17, 17);
            case 'O': return rows(14, 17, 17, 17, 17, 17, 14);
            case 'P': return rows(30, 17, 17, 30, 16, 16, 16);
            case 'Q': return rows(14, 17, 17, 17, 21, 18, 13);
            case 'R': return rows(30, 17, 17, 30, 20, 18, 17);
            case 'S': return rows(15, 16, 16, 14, 1, 1, 30);
            case 'T': return rows(31, 4, 4, 4, 4, 4, 4);
            case 'U': return rows(17, 17, 17, 17, 17, 17, 14);
            case 'V': return rows(17, 17, 17, 17, 17, 10, 4);
            case 'W': return rows(17, 17, 17, 21, 21, 21, 10);
            case 'X': return rows(17, 17, 10, 4, 10, 17, 17);
            case 'Y': return rows(17, 17, 10, 4, 4, 4, 4);
            case 'Z': return rows(31, 1, 2, 4, 8, 16, 31);
            case '0': return rows(14, 17, 19, 21, 25, 17, 14);
            case '1': return rows(4, 12, 4, 4, 4, 4, 14);
            case '2': return rows(14, 17, 1, 2, 4, 8, 31);
            case '3': return rows(30, 1, 1, 14, 1, 1, 30);
            case '4': return rows(2, 6, 10, 18, 31, 2, 2);
            case '5': return rows(31, 16, 16, 30, 1, 1, 30);
            case '6': return rows(14, 16, 16, 30, 17, 17, 14);
            case '7': return rows(31, 1, 2, 4, 8, 8, 8);
            case '8': return rows(14, 17, 17, 14, 17, 17, 14);
            case '9': return rows(14, 17, 17, 15, 1, 1, 14);
            default: return rows(0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static int[] rows(int a, int b, int c, int d, int e, int f, int g) {
        return new int[] {a, b, c, d, e, f, g};
    }
}
