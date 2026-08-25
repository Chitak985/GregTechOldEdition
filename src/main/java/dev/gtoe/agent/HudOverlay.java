package dev.gtoe.agent;

/** Selected-block HUD plus the entry point for agent-owned GUI rendering. */
public final class HudOverlay {
    private static volatile boolean disabled;

    private HudOverlay() {
    }

    public static void render(int screenWidth, int screenHeight) {
        if (disabled || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        boolean drawingStarted = false;
        try {
            GuiGraphics.begin(screenWidth, screenHeight);
            drawingStarted = true;
            int selected = BlockSelection.selectedBlockId();
            GuiGraphics.drawTextNormal(
                    "BLOCK " + ItemCatalog.blockName(selected)
                            + " X" + Inventory.count(selected),
                    4, 4);
            GuiManager.render(screenWidth, screenHeight);
        } catch (Throwable error) {
            disabled = true;
            System.err.println("[gtoe] Disabling HUD and GUI rendering after an error");
            error.printStackTrace(System.err);
        } finally {
            if (drawingStarted) {
                try {
                    GuiGraphics.end();
                } catch (Throwable ignored) {
                    disabled = true;
                }
            }
        }
    }
}
