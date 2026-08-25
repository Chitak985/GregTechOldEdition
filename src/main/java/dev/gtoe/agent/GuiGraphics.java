package dev.gtoe.agent;

import org.lwjgl.opengl.GL11;

/** Shared immediate-mode drawing helpers used by the HUD and reusable GUI screens. */
final class GuiGraphics {
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;

    private GuiGraphics() {
    }

    static void begin(int screenWidth, int screenHeight) {
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
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    static void end() {
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();
    }

    static void fillRect(int x, int y, int width, int height,
            float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
    }

    static void drawPanel(int x, int y, int width, int height) {
        fillRect(x - 2, y - 2, width + 4, height + 4, 0.08f, 0.08f, 0.08f, 0.92f);
        fillRect(x, y, width, height, 0.34f, 0.34f, 0.37f, 0.96f);
        fillRect(x + 2, y + 2, width - 4, 2, 0.60f, 0.60f, 0.64f, 1.0f);
    }

    static void drawButton(int x, int y, int width, int height, String text) {
        fillRect(x, y, width, height, 0.14f, 0.14f, 0.16f, 1.0f);
        fillRect(x + 2, y + 2, width - 4, height - 4, 0.48f, 0.48f, 0.52f, 1.0f);
        int textX = x + Math.max(4, (width - textWidth(text, 1)) / 2);
        int textY = y + Math.max(3, (height - 7) / 2);
        drawTextShadowed(text, textX, textY, 1, 0.96f, 0.96f, 0.96f);
    }

    static void drawSlot(int x, int y, int size, boolean highlighted) {
        fillRect(x, y, size, size, 0.08f, 0.08f, 0.09f, 1.0f);
        if (highlighted) {
            fillRect(x + 2, y + 2, size - 4, size - 4, 0.70f, 0.64f, 0.30f, 1.0f);
        } else {
            fillRect(x + 2, y + 2, size - 4, size - 4, 0.24f, 0.24f, 0.27f, 1.0f);
        }
    }

    static void drawItemIcon(int itemId, int count, int x, int y, int size) {
        int inset = Math.max(3, size / 6);
        float[] color = itemColor(itemId);
        fillRect(x + inset, y + inset, size - inset * 2, size - inset * 2,
                color[0] * 0.55f, color[1] * 0.55f, color[2] * 0.55f, 1.0f);

        if (itemId == ItemCatalog.STICK_ITEM_ID) {
            int center = x + size / 2;
            fillRect(center - 2, y + inset, 4, size - inset * 2,
                    0.64f, 0.40f, 0.18f, 1.0f);
        } else {
            fillRect(x + inset + 2, y + inset + 2,
                    Math.max(2, size - inset * 2 - 6), Math.max(2, size / 4),
                    color[0], color[1], color[2], 1.0f);
        }

        drawTextShadowed(String.valueOf(itemId), x + 2, y + 2, 1, 0.96f, 0.96f, 0.96f);
        if (count > 1) {
            String countText = String.valueOf(count);
            drawTextShadowed(countText,
                    x + size - textWidth(countText, 1) - 2,
                    y + size - 9, 1, 1.0f, 1.0f, 1.0f);
        }
    }

    static void drawTextNormal(String text, int startX, int startY) {
        drawTextShadowed(text, startX, startY, 2, 0.93f, 0.93f, 0.93f);
    }

    static void drawTextSmall(String text, int startX, int startY) {
        drawTextShadowed(text, startX, startY, 1, 0.93f, 0.93f, 0.93f);
    }

    static void drawTextShadowed(
            String text, int startX, int startY, int scale, float red, float green, float blue) {
        String upper = text.toUpperCase();
        drawString(upper, startX + 1, startY + 1, scale, 0.08f, 0.08f, 0.08f);
        drawString(upper, startX, startY, scale, red, green, blue);
    }

    static int textWidth(String text, int scale) {
        return text.length() * 6 * scale;
    }

    private static void drawString(
            String text, int startX, int startY, int scale, float red, float green, float blue) {
        GL11.glColor3f(red, green, blue);
        GL11.glBegin(GL11.GL_QUADS);
        int cursorX = startX;

        for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            int[] rows = glyph(text.charAt(characterIndex));
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

    private static float[] itemColor(int itemId) {
        int[] colors = {
            0x777777, 0x5A9F42, 0x79553A, 0x81858A, 0x3F4852,
            0x202124, 0xD8C58B, 0xA76243, 0xE4EDF1, 0x76552E,
            0xB88952, 0x9B6338, 0x747B82, 0xBBC2C7, 0x91A8AC,
            0xB86A35, 0xB99A7A, 0x202226, 0xC8D1D5, 0x69737B
        };
        int rgb = itemId >= 0 && itemId < colors.length ? colors[itemId] : 0xA06030;
        return new float[] {
            ((rgb >> 16) & 255) / 255.0f,
            ((rgb >> 8) & 255) / 255.0f,
            (rgb & 255) / 255.0f
        };
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
