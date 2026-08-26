package dev.gtoe.agent;

import java.util.Arrays;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Global input, state, crafting, and rendering coordinator for agent-owned GUIs. */
public final class GuiManager {
    private static final int SCREEN_NONE = 0;
    private static final int SCREEN_CRAFTING = 1;
    private static final int SCREEN_SIMPLE = 2;
    private static final int KEY_E = 18;
    private static final int LEFT_MOUSE_BUTTON = 0;

    private static final int[] CRAFTING_GRID = {-1, -1, -1, -1};

    private static int screen;
    private static int draggedItemId = -1;
    private static int scrollRow;
    private static int mouseX;
    private static int mouseY;
    private static boolean currentMouseEventOwnedByGui;
    private static String simpleTitle = "";
    private static String simpleButtonText = "";

    private GuiManager() {
    }

    public static synchronized boolean isOpen() {
        return screen != SCREEN_NONE;
    }

    /** True when world break/place code must ignore the current mouse event. */
    public static synchronized boolean blocksWorldAction() {
        return screen != SCREEN_NONE || currentMouseEventOwnedByGui;
    }

    /** Returns true when the event belongs to a GUI and block selection should ignore it. */
    public static synchronized boolean handleKeyEvent(int keyCode, boolean pressed) {
        if (!pressed) {
            return screen != SCREEN_NONE;
        }

        if (keyCode == KEY_E) {
            if (screen == SCREEN_CRAFTING) {
                closeInternal();
            } else {
                returnCraftingItems();
                screen = SCREEN_CRAFTING;
                scrollRow = 0;
                simpleTitle = "";
                simpleButtonText = "";
                setMouseGrabbed(false);
            }
            return true;
        }

        return screen != SCREEN_NONE;
    }

    public static synchronized void openSimple(String title, String buttonText) {
        returnCraftingItems();
        simpleTitle = title == null ? "" : title;
        simpleButtonText = buttonText == null ? "" : buttonText;
        screen = SCREEN_SIMPLE;
        setMouseGrabbed(false);
    }

    public static synchronized void close() {
        closeInternal();
    }

    /** Receives one LWJGL mouse event before the original world interaction code sees it. */
    public static synchronized void handleMouseEvent(
            int rawX,
            int rawY,
            int button,
            boolean pressed,
            int wheel,
            int screenWidth,
            int screenHeight) {
        mouseX = rawX;
        mouseY = screenHeight - 1 - rawY;
        currentMouseEventOwnedByGui = screen != SCREEN_NONE;

        if (screen == SCREEN_NONE) {
            return;
        }

        if (screen == SCREEN_CRAFTING && wheel != 0) {
            int itemRows = (Inventory.itemIds().length + 9) / 10;
            int maxScroll = Math.max(0, itemRows - 3);
            if (wheel < 0) {
                scrollRow = Math.min(maxScroll, scrollRow + 1);
            } else {
                scrollRow = Math.max(0, scrollRow - 1);
            }
        }

        if (button != LEFT_MOUSE_BUTTON) {
            return;
        }

        Layout layout = new Layout(screenWidth, screenHeight);
        if (screen == SCREEN_SIMPLE) {
            if (pressed && layout.isInSimpleButton(mouseX, mouseY)) {
                closeInternal();
            }
            return;
        }

        if (pressed) {
            if (layout.isInOutput(mouseX, mouseY)) {
                craftCurrentRecipe();
                return;
            }

            int gridIndex = layout.gridIndexAt(mouseX, mouseY);
            if (gridIndex >= 0 && draggedItemId < 0 && CRAFTING_GRID[gridIndex] >= 0) {
                draggedItemId = CRAFTING_GRID[gridIndex];
                CRAFTING_GRID[gridIndex] = -1;
                return;
            }

            int visibleIndex = layout.inventoryIndexAt(mouseX, mouseY);
            if (visibleIndex >= 0 && draggedItemId < 0) {
                int[] itemIds = Inventory.itemIds();
                int inventoryIndex = scrollRow * 10 + visibleIndex;
                if (inventoryIndex < itemIds.length) {
                    int itemId = itemIds[inventoryIndex];
                    if (Inventory.remove(itemId, 1)) {
                        draggedItemId = itemId;
                    }
                }
            }
            return;
        }

        if (draggedItemId >= 0) {
            int gridIndex = layout.gridIndexAt(mouseX, mouseY);
            if (gridIndex >= 0 && CRAFTING_GRID[gridIndex] < 0) {
                CRAFTING_GRID[gridIndex] = draggedItemId;
            } else {
                Inventory.add(draggedItemId, 1);
            }
            draggedItemId = -1;
        }
    }

    /** Zeroes camera deltas while a GUI owns the mouse. */
    public static synchronized int filterMouseDelta(int delta) {
        return screen == SCREEN_NONE ? delta : 0;
    }

    /** Replacement for Player keyboard polling so movement pauses while a GUI is open. */
    public static synchronized boolean isGameplayKeyDown(int keyCode) {
        return screen == SCREEN_NONE && Keyboard.isKeyDown(keyCode);
    }

    public static synchronized void render(int screenWidth, int screenHeight) {
        if (screen == SCREEN_NONE) {
            return;
        }

        Layout layout = new Layout(screenWidth, screenHeight);
        GuiGraphics.drawPanel(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight);
        if (screen == SCREEN_SIMPLE) {
            GuiGraphics.drawTextNormal(simpleTitle, layout.panelX + 16, layout.panelY + 18);
            GuiGraphics.drawButton(
                    layout.simpleButtonX, layout.simpleButtonY,
                    layout.simpleButtonWidth, layout.simpleButtonHeight,
                    simpleButtonText);
            return;
        }

        renderCrafting(layout);
    }

    static synchronized boolean isShiftDown() {
        try {
            return Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Returns {output ID, amount}, or {-1, 0} when the supplied 2x2 grid has no recipe. */
    static int[] recipeFor(int[] grid) {
        if (grid == null || grid.length != 4) {
            return new int[] {-1, 0};
        }

        int occupied = 0;
        int wood = 0;
        for (int itemId : grid) {
            if (itemId >= 0) {
                occupied++;
            }
            if (itemId == 9) {
                wood++;
            }
        }
        if (occupied == 1 && wood == 1) {
            return new int[] {10, 2};
        }

        boolean leftColumn = grid[0] == 10
                && grid[2] == 10
                && grid[1] < 0 && grid[3] < 0;
        boolean rightColumn = grid[1] == 10
                && grid[3] == 10
                && grid[0] < 0 && grid[2] < 0;
        if (leftColumn || rightColumn) {
            return new int[] {100, 2};
        }

        return new int[] {-1, 0};
    }

    static synchronized void resetForTests() {
        returnCraftingItems();
        screen = SCREEN_NONE;
        scrollRow = 0;
        currentMouseEventOwnedByGui = false;
        simpleTitle = "";
        simpleButtonText = "";
    }

    private static void renderCrafting(Layout layout) {
        GuiGraphics.drawTextNormal("CRAFTING", layout.panelX + 16, layout.panelY + 14);
        GuiGraphics.drawTextSmall("DRAG ITEMS TO THE 2X2 GRID", layout.panelX + 16, layout.panelY + 34);

        for (int index = 0; index < 4; index++) {
            int x = layout.gridX + (index % 2) * layout.gridPitch;
            int y = layout.gridY + (index / 2) * layout.gridPitch;
            GuiGraphics.drawSlot(x, y, layout.craftSlotSize, false);
            if (CRAFTING_GRID[index] >= 0) {
                GuiGraphics.drawItemIcon(CRAFTING_GRID[index], 1, x, y, layout.craftSlotSize);
            }
        }

        int[] recipe = recipeFor(CRAFTING_GRID);
        GuiGraphics.drawTextSmall("OUTPUT", layout.outputX - 3, layout.outputY - 11);
        GuiGraphics.drawSlot(layout.outputX, layout.outputY, layout.craftSlotSize, recipe[0] >= 0);
        if (recipe[0] >= 0) {
            GuiGraphics.drawItemIcon(
                    recipe[0], recipe[1], layout.outputX, layout.outputY, layout.craftSlotSize);
        }

        int[] itemIds = Inventory.itemIds();
        int totalRows = Math.max(1, (itemIds.length + 9) / 10);
        int maxScroll = Math.max(0, totalRows - 3);
        if (scrollRow > maxScroll) {
            scrollRow = maxScroll;
        }
        GuiGraphics.drawTextSmall("INVENTORY", layout.inventoryX, layout.inventoryY - 13);
        GuiGraphics.drawTextSmall(
                "ROW " + (scrollRow + 1) + " OF " + Math.max(1, maxScroll + 1),
                layout.inventoryX + 132, layout.inventoryY - 13);

        for (int visibleIndex = 0; visibleIndex < 30; visibleIndex++) {
            int x = layout.inventoryX + (visibleIndex % 10) * layout.inventoryPitch;
            int y = layout.inventoryY + (visibleIndex / 10) * layout.inventoryPitch;
            GuiGraphics.drawSlot(x, y, layout.inventorySlotSize, false);
            int inventoryIndex = scrollRow * 10 + visibleIndex;
            if (inventoryIndex < itemIds.length) {
                int itemId = itemIds[inventoryIndex];
                GuiGraphics.drawItemIcon(
                        itemId, Inventory.count(itemId), x, y, layout.inventorySlotSize);
            }
        }

        if (draggedItemId >= 0) {
            GuiGraphics.drawSlot(mouseX - 10, mouseY - 10, 22, true);
            GuiGraphics.drawItemIcon(draggedItemId, 1, mouseX - 10, mouseY - 10, 22);
        }
    }

    private static void craftCurrentRecipe() {
        int[] recipe = recipeFor(CRAFTING_GRID);
        if (recipe[0] < 0) {
            return;
        }
        Arrays.fill(CRAFTING_GRID, -1);
        Inventory.add(recipe[0], recipe[1]);
        System.out.println("[gtoe] Crafted " + recipe[1] + " "
                + ItemCatalog.itemName(recipe[0]));
    }

    private static void closeInternal() {
        returnCraftingItems();
        screen = SCREEN_NONE;
        simpleTitle = "";
        simpleButtonText = "";
        setMouseGrabbed(true);
    }

    private static void returnCraftingItems() {
        if (draggedItemId >= 0) {
            Inventory.add(draggedItemId, 1);
            draggedItemId = -1;
        }
        for (int index = 0; index < CRAFTING_GRID.length; index++) {
            if (CRAFTING_GRID[index] >= 0) {
                Inventory.add(CRAFTING_GRID[index], 1);
                CRAFTING_GRID[index] = -1;
            }
        }
    }

    private static void setMouseGrabbed(boolean grabbed) {
        try {
            Mouse.setGrabbed(grabbed);
        } catch (Throwable error) {
            // Tests and structural verification do not initialize LWJGL.
        }
    }

    private static boolean contains(int x, int y, int width, int height, int pointX, int pointY) {
        return pointX >= x && pointY >= y && pointX < x + width && pointY < y + height;
    }

    private static final class Layout {
        private final int panelWidth = 300;
        private final int panelHeight = 260;
        private final int panelX;
        private final int panelY;
        private final int craftSlotSize = 30;
        private final int gridPitch = 32;
        private final int gridX;
        private final int gridY;
        private final int outputX;
        private final int outputY;
        private final int inventorySlotSize = 22;
        private final int inventoryPitch = 22;
        private final int inventoryX;
        private final int inventoryY;
        private final int simpleButtonWidth = 140;
        private final int simpleButtonHeight = 28;
        private final int simpleButtonX;
        private final int simpleButtonY;

        private Layout(int screenWidth, int screenHeight) {
            panelX = Math.max(4, (screenWidth - panelWidth) / 2);
            panelY = Math.max(20, (screenHeight - panelHeight) / 2);
            gridX = panelX + 70;
            gridY = panelY + 58;
            outputX = gridX + 94;
            outputY = gridY + 16;
            inventoryX = panelX + 40;
            inventoryY = panelY + 180;
            simpleButtonX = panelX + (panelWidth - simpleButtonWidth) / 2;
            simpleButtonY = panelY + 82;
        }

        private int gridIndexAt(int x, int y) {
            for (int index = 0; index < 4; index++) {
                int slotX = gridX + (index % 2) * gridPitch;
                int slotY = gridY + (index / 2) * gridPitch;
                if (contains(slotX, slotY, craftSlotSize, craftSlotSize, x, y)) {
                    return index;
                }
            }
            return -1;
        }

        private int inventoryIndexAt(int x, int y) {
            if (!contains(inventoryX, inventoryY, inventoryPitch * 10,
                    inventoryPitch * 3, x, y)) {
                return -1;
            }
            int column = (x - inventoryX) / inventoryPitch;
            int row = (y - inventoryY) / inventoryPitch;
            return row * 10 + column;
        }

        private boolean isInOutput(int x, int y) {
            return contains(outputX, outputY, craftSlotSize, craftSlotSize, x, y);
        }

        private boolean isInSimpleButton(int x, int y) {
            return contains(simpleButtonX, simpleButtonY,
                    simpleButtonWidth, simpleButtonHeight, x, y);
        }
    }
}
