package dev.gtoe.agent;

/** Selected-slot HUD plus the entry point for agent-owned GUI rendering. */
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
            int selected = BlockSelection.selectedItemId();
            String label;
            if (selected < 0) {
                label = "SLOT " + (BlockSelection.selectedHotbarIndex() + 1) + " EMPTY";
            } else {
                String type = selected > 0 && ItemCatalog.isBlock(selected) ? "BLOCK " : "ITEM ";
                label = type + ItemCatalog.itemName(selected)
                        + " X" + BlockSelection.selectedItemCount();
            }
            GuiGraphics.drawTextNormal(label, 4, 4);
            GuiManager.render(screenWidth, screenHeight);
            GuiManager.renderHotbar(screenWidth, screenHeight);
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
