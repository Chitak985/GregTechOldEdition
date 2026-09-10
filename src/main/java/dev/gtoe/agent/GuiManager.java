package dev.gtoe.agent;

import java.util.Arrays;
import java.util.Objects;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import static dev.gtoe.agent.BlockGuiRegistry.Definition;
import static dev.gtoe.agent.BlockGuiRegistry.GUIButton;

/** Global input, fixed-slot inventory, crafting, and rendering coordinator. */
public final class GuiManager {
    private static final int SCREEN_NONE = 0;
    private static final int SCREEN_CRAFTING = 1;
    private static final int SCREEN_DEFINITION = 2;
    private static final int SCREEN_CRAFTING_TABLE = 3;
    private static final int KEY_E = 18;
    private static final int LEFT_MOUSE_BUTTON = 0;

    private static final int[] PLAYER_CRAFTING_GRID = {-1, -1, -1, -1};

    private static int screen;
    private static int draggedItemId = Inventory.EMPTY_ITEM_ID;
    private static int draggedAmount;
    private static int draggedSourceInventorySlot = -1;
    private static int draggedSourceCraftSlot = -1;
    private static int scrollRow;
    private static int mouseX;
    private static int mouseY;
    private static Definition screen_definition;
    private static boolean currentMouseEventOwnedByGui;

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
            if (screen != SCREEN_NONE) {
                closeInternal();
            } else {
                returnCraftingItems();
                screen = SCREEN_CRAFTING;
                scrollRow = 0;
                setMouseGrabbed(false);
            }
            return true;
        }

        return screen != SCREEN_NONE;
    }

    public static synchronized void openDefinition(Definition definition) {
        returnCraftingItems();
        screen = SCREEN_DEFINITION;
        screen_definition = definition;
        setMouseGrabbed(false);
    }

    /** Opens the crafting table's dedicated 3x3 screen. */
    public static synchronized void openCraftingTable() {
        returnCraftingItems();
        screen = SCREEN_CRAFTING_TABLE;
        screen_definition = null;
        scrollRow = 0;
        setMouseGrabbed(false);
    }

    static synchronized boolean isCraftingTableOpen() {
        return screen == SCREEN_CRAFTING_TABLE;
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

        if (isCraftingScreen() && wheel != 0) {
            int maxScroll = Math.max(0, Inventory.MAIN_ROWS - 3);
            if (wheel < 0) {
                scrollRow = Math.min(maxScroll, scrollRow + 1);
            } else {
                scrollRow = Math.max(0, scrollRow - 1);
            }
        }

        if (button != LEFT_MOUSE_BUTTON) {
            return;
        }

        Layout layout = new Layout(screenWidth, screenHeight, activeGridWidth());
        if (screen == SCREEN_DEFINITION) {
            if (pressed) {
                for (GUIButton buttonDefinition : screen_definition.buttons) {
                    if (layout.isInButton(mouseX, mouseY, buttonDefinition)
                            && Objects.equals(buttonDefinition.callback, "CLOSE")) {
                        closeInternal();
                        break;
                    }
                }
            }
            return;
        }

        if (pressed) {
            beginCraftingDrag(layout);
        } else {
            finishCraftingDrag(layout);
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

        Layout layout = new Layout(screenWidth, screenHeight, activeGridWidth());
        GuiGraphics.drawPanel(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight);
        if (screen == SCREEN_DEFINITION) {
            screen_definition.render(layout);
            return;
        }

        renderCrafting(layout, activeCraftingGrid());
    }

    /** Draws the selected nine-slot hotbar even when no GUI screen is open. */
    public static synchronized void renderHotbar(int screenWidth, int screenHeight) {
        Layout layout = new Layout(screenWidth, screenHeight);
        GuiGraphics.fillRect(
                layout.hotbarX - 4,
                layout.hotbarY - 12,
                layout.hotbarWidth + 8,
                layout.hotbarSlotSize + 16,
                0.08f, 0.08f, 0.09f, 0.88f);

        for (int index = 0; index < Inventory.HOTBAR_SLOT_COUNT; index++) {
            int x = layout.hotbarX + index * layout.hotbarPitch;
            boolean selected = index == BlockSelection.selectedHotbarIndex();
            GuiGraphics.drawTextSmall(String.valueOf(index + 1), x + 10, layout.hotbarY - 9);
            GuiGraphics.drawSlot(x, layout.hotbarY, layout.hotbarSlotSize, selected);
            int slot = Inventory.HOTBAR_START + index;
            int itemId = Inventory.itemIdAt(slot);
            if (itemId >= 0) {
                GuiGraphics.drawItemIcon(
                        itemId, Inventory.countAt(slot), x, layout.hotbarY, layout.hotbarSlotSize);
            }
        }
    }

    static synchronized boolean isShiftDown() {
        try {
            return Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Returns {output ID, amount} for a 2x2 or 3x3 grid, or {-1, 0}. */
    static int[] recipeFor(int[] grid) {
        return CraftingRecipes.recipeFor(grid);
    }

    static synchronized void resetForTests() {
        returnCraftingItems();
        screen = SCREEN_NONE;
        scrollRow = 0;
        currentMouseEventOwnedByGui = false;
        screen_definition = null;
    }

    private static void beginCraftingDrag(Layout layout) {
        int[] craftingGrid = activeCraftingGrid();
        if (layout.isInOutput(mouseX, mouseY)) {
            craftCurrentRecipe();
            return;
        }
        if (draggedItemId >= 0) {
            return;
        }

        int gridIndex = layout.gridIndexAt(mouseX, mouseY);
        if (gridIndex >= 0 && craftingGrid[gridIndex] >= 0) {
            draggedItemId = craftingGrid[gridIndex];
            draggedAmount = 1;
            draggedSourceCraftSlot = gridIndex;
            craftingGrid[gridIndex] = Inventory.EMPTY_ITEM_ID;
            return;
        }

        int inventorySlot = layout.inventorySlotAt(mouseX, mouseY, scrollRow);
        if (inventorySlot >= 0) {
            Inventory.Stack stack = Inventory.takeStack(inventorySlot);
            if (stack != null) {
                draggedItemId = stack.itemId;
                draggedAmount = stack.count;
                draggedSourceInventorySlot = inventorySlot;
            }
        }
    }

    private static void finishCraftingDrag(Layout layout) {
        if (draggedItemId < 0) {
            return;
        }

        int gridIndex = layout.gridIndexAt(mouseX, mouseY);
        if (gridIndex >= 0) {
            dropOnCraftingSlot(gridIndex);
            return;
        }

        int inventorySlot = layout.inventorySlotAt(mouseX, mouseY, scrollRow);
        if (inventorySlot >= 0) {
            dropOnInventorySlot(inventorySlot);
            return;
        }

        returnDraggedToSource();
    }

    private static void dropOnCraftingSlot(int gridIndex) {
        int[] craftingGrid = activeCraftingGrid();
        if (draggedSourceCraftSlot >= 0) {
            int displaced = craftingGrid[gridIndex];
            craftingGrid[gridIndex] = draggedItemId;
            if (gridIndex != draggedSourceCraftSlot) {
                craftingGrid[draggedSourceCraftSlot] = displaced;
            }
            clearDragged();
            return;
        }

        if (craftingGrid[gridIndex] < 0) {
            craftingGrid[gridIndex] = draggedItemId;
            draggedAmount--;
            if (draggedAmount > 0) {
                Inventory.placeStack(
                        draggedSourceInventorySlot,
                        new Inventory.Stack(draggedItemId, draggedAmount));
            }
            clearDragged();
            return;
        }

        returnDraggedToSource();
    }

    private static void dropOnInventorySlot(int inventorySlot) {
        if (draggedSourceCraftSlot >= 0) {
            int targetItemId = Inventory.itemIdAt(inventorySlot);
            if (targetItemId >= 0 && targetItemId != draggedItemId) {
                returnDraggedToSource();
                return;
            }
            Inventory.placeStack(inventorySlot, new Inventory.Stack(draggedItemId, draggedAmount));
            clearDragged();
            return;
        }

        Inventory.Stack displaced = Inventory.placeStack(
                inventorySlot, new Inventory.Stack(draggedItemId, draggedAmount));
        if (displaced != null && inventorySlot != draggedSourceInventorySlot) {
            Inventory.placeStack(draggedSourceInventorySlot, displaced);
        }
        clearDragged();
    }

    private static void renderCrafting(Layout layout, int[] craftingGrid) {
        String title = screen == SCREEN_CRAFTING_TABLE ? "CRAFTING TABLE" : "CRAFTING";
        GuiGraphics.drawTextNormal(title, layout.panelX + 16, layout.panelY + 14);
        GuiGraphics.drawTextSmall(
                "DRAG ITEMS TO THE " + layout.gridColumns + "X" + layout.gridColumns + " GRID",
                layout.panelX + 16, layout.panelY + 34);

        for (int index = 0; index < craftingGrid.length; index++) {
            int x = layout.gridX + (index % layout.gridColumns) * layout.gridPitch;
            int y = layout.gridY + (index / layout.gridColumns) * layout.gridPitch;
            GuiGraphics.drawSlot(x, y, layout.craftSlotSize, false);
            if (craftingGrid[index] >= 0) {
                GuiGraphics.drawItemIcon(craftingGrid[index], 1, x, y, layout.craftSlotSize);
            }
        }

        int[] recipe = recipeFor(craftingGrid);
        GuiGraphics.drawTextSmall("OUTPUT", layout.outputX - 3, layout.outputY - 11);
        GuiGraphics.drawSlot(layout.outputX, layout.outputY, layout.craftSlotSize, recipe[0] >= 0);
        if (recipe[0] >= 0) {
            GuiGraphics.drawItemIcon(
                    recipe[0], recipe[1], layout.outputX, layout.outputY, layout.craftSlotSize);
        }

        int maxScroll = Math.max(0, Inventory.MAIN_ROWS - 3);
        if (scrollRow > maxScroll) {
            scrollRow = maxScroll;
        }
        GuiGraphics.drawTextSmall("INVENTORY", layout.inventoryX, layout.inventoryY - 13);
        GuiGraphics.drawTextSmall(
                "ROW " + (scrollRow + 1) + " OF " + (maxScroll + 1),
                layout.inventoryX + 132, layout.inventoryY - 13);

        for (int visibleIndex = 0; visibleIndex < 30; visibleIndex++) {
            int x = layout.inventoryX + (visibleIndex % Inventory.MAIN_COLUMNS)
                    * layout.inventoryPitch;
            int y = layout.inventoryY + (visibleIndex / Inventory.MAIN_COLUMNS)
                    * layout.inventoryPitch;
            GuiGraphics.drawSlot(x, y, layout.inventorySlotSize, false);
            int inventorySlot = scrollRow * Inventory.MAIN_COLUMNS + visibleIndex;
            int itemId = Inventory.itemIdAt(inventorySlot);
            if (itemId >= 0) {
                GuiGraphics.drawItemIcon(
                        itemId, Inventory.countAt(inventorySlot), x, y, layout.inventorySlotSize);
            }
        }

        if (draggedItemId >= 0) {
            GuiGraphics.drawSlot(mouseX - 11, mouseY - 11, 22, true);
            GuiGraphics.drawItemIcon(draggedItemId, draggedAmount, mouseX - 11, mouseY - 11, 22);
        }
    }

    private static void craftCurrentRecipe() {
        int[] craftingGrid = activeCraftingGrid();
        int[] recipe = recipeFor(craftingGrid);
        if (recipe[0] < 0 || !Inventory.add(recipe[0], recipe[1])) {
            return;
        }
        Arrays.fill(craftingGrid, Inventory.EMPTY_ITEM_ID);
        System.out.println("[gtoe] Crafted " + recipe[1] + " "
                + ItemCatalog.itemName(recipe[0]));
    }

    private static void closeInternal() {
        returnCraftingItems();
        screen = SCREEN_NONE;
        screen_definition = null;
        setMouseGrabbed(true);
    }

    private static void returnCraftingItems() {
        if (draggedItemId >= 0) {
            returnDraggedToSource();
        }
        returnGridItems(PLAYER_CRAFTING_GRID);
        returnGridItems(CraftingTableGui.grid());
    }

    private static void returnGridItems(int[] craftingGrid) {
        for (int index = 0; index < craftingGrid.length; index++) {
            if (craftingGrid[index] >= 0) {
                Inventory.add(craftingGrid[index], 1);
                craftingGrid[index] = Inventory.EMPTY_ITEM_ID;
            }
        }
    }

    private static void returnDraggedToSource() {
        if (draggedSourceInventorySlot >= 0) {
            Inventory.Stack displaced = Inventory.placeStack(
                    draggedSourceInventorySlot,
                    new Inventory.Stack(draggedItemId, draggedAmount));
            if (displaced != null) {
                Inventory.add(displaced.itemId, displaced.count);
            }
        } else if (draggedSourceCraftSlot >= 0
                && activeCraftingGrid()[draggedSourceCraftSlot] < 0) {
            activeCraftingGrid()[draggedSourceCraftSlot] = draggedItemId;
        } else {
            Inventory.add(draggedItemId, draggedAmount);
        }
        clearDragged();
    }

    private static void clearDragged() {
        draggedItemId = Inventory.EMPTY_ITEM_ID;
        draggedAmount = 0;
        draggedSourceInventorySlot = -1;
        draggedSourceCraftSlot = -1;
    }

    private static boolean isCraftingScreen() {
        return screen == SCREEN_CRAFTING || screen == SCREEN_CRAFTING_TABLE;
    }

    private static int activeGridWidth() {
        return screen == SCREEN_CRAFTING_TABLE ? CraftingTableGui.GRID_WIDTH : 2;
    }

    private static int[] activeCraftingGrid() {
        return screen == SCREEN_CRAFTING_TABLE
                ? CraftingTableGui.grid()
                : PLAYER_CRAFTING_GRID;
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

    public static final class Layout {
        public final int panelWidth = 300;
        public final int panelHeight = 260;
        public final int panelX;
        public final int panelY;
        final int craftSlotSize = 30;
        final int gridPitch = 32;
        final int gridColumns;
        final int gridX;
        final int gridY;
        final int outputX;
        final int outputY;
        final int inventorySlotSize = 22;
        final int inventoryPitch = 22;
        final int inventoryX;
        final int inventoryY;
        final int hotbarSlotSize = 26;
        final int hotbarPitch = 28;
        final int hotbarWidth = Inventory.HOTBAR_SLOT_COUNT * hotbarPitch - 2;
        final int hotbarX;
        final int hotbarY;

        Layout(int screenWidth, int screenHeight) {
            this(screenWidth, screenHeight, 2);
        }

        Layout(int screenWidth, int screenHeight, int gridColumns) {
            this.gridColumns = gridColumns;
            panelX = Math.max(4, (screenWidth - panelWidth) / 2);
            panelY = Math.max(20, (screenHeight - panelHeight) / 2);
            gridX = panelX + (gridColumns == 3 ? 54 : 70);
            gridY = panelY + 58;
            outputX = gridX + gridColumns * gridPitch + 30;
            outputY = gridY + (gridColumns - 1) * gridPitch / 2;
            inventoryX = panelX + 40;
            inventoryY = panelY + 180;
            hotbarX = Math.max(4, (screenWidth - hotbarWidth) / 2);
            hotbarY = Math.max(4, screenHeight - hotbarSlotSize - 8);
        }

        int gridIndexAt(int x, int y) {
            for (int index = 0; index < gridColumns * gridColumns; index++) {
                int slotX = gridX + (index % gridColumns) * gridPitch;
                int slotY = gridY + (index / gridColumns) * gridPitch;
                if (contains(slotX, slotY, craftSlotSize, craftSlotSize, x, y)) {
                    return index;
                }
            }
            return -1;
        }

        int inventorySlotAt(int x, int y, int currentScrollRow) {
            int hotbarSlot = hotbarSlotAt(x, y);
            if (hotbarSlot >= 0) {
                return hotbarSlot;
            }
            if (!contains(inventoryX, inventoryY,
                    inventoryPitch * Inventory.MAIN_COLUMNS,
                    inventoryPitch * 3, x, y)) {
                return -1;
            }
            int column = (x - inventoryX) / inventoryPitch;
            int row = (y - inventoryY) / inventoryPitch;
            return currentScrollRow * Inventory.MAIN_COLUMNS
                    + row * Inventory.MAIN_COLUMNS + column;
        }

        int hotbarSlotAt(int x, int y) {
            if (!contains(hotbarX, hotbarY, hotbarWidth,
                    hotbarSlotSize, x, y)) {
                return -1;
            }
            int index = (x - hotbarX) / hotbarPitch;
            int withinSlot = (x - hotbarX) % hotbarPitch;
            if (index >= Inventory.HOTBAR_SLOT_COUNT || withinSlot >= hotbarSlotSize) {
                return -1;
            }
            return Inventory.HOTBAR_START + index;
        }

        boolean isInOutput(int x, int y) {
            return contains(outputX, outputY, craftSlotSize, craftSlotSize, x, y);
        }

        boolean isInButton(int x, int y, GUIButton button) {
            return contains(panelX + button.posX, panelY + button.posY,
                    button.width, button.height, x, y);
        }
    }
}
