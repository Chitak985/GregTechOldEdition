package dev.gtoe.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.input.Keyboard;

/** Searchable recipe sidebar shared by the player's and table's crafting screens. */
final class RecipeBook {
    private static boolean open;
    private static boolean focused;
    private static boolean repeatBeforeSearch;
    private static boolean craftableOnly;
    private static String search = "";
    private static int firstRow;
    private static CraftingRecipes.BookRecipe selected;
    private static String notice;

    private RecipeBook() { }

    static boolean isOpen() { return open; }
    static boolean isSearchFocused() { return open && focused; }
    static int[] preview() { return selected == null ? null : selected.grid(); }
    static String notice() { return notice; }
    static void onGridChanged() { notice = null; }

    static void close() {
        hide();
        selected = null;
        notice = null;
    }

    private static void hide() {
        open = false;
        focus(false);
    }

    static void reset() {
        close();
        search = "";
        craftableOnly = false;
        firstRow = 0;
    }

    static boolean handleKey(int key, boolean pressed, char character) {
        if (!open) { return false; }
        if (focused) {
            if (!pressed) { return true; }
            if (key == 1 || key == 28 || key == 156) { focus(false); }
            else if (key == 14 && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                firstRow = 0;
            } else if (character >= 32 && character < 127 && search.length() < 48) {
                search += character;
                firstRow = 0;
            }
            return true; // Typing E or a number must not close crafting or select a hotbar slot.
        }
        if (pressed && key == 1) { hide(); return true; }
        return false;
    }

    /** Counts repeated ingredients separately, including items already in the active grid. */
    static boolean canCraft(CraftingRecipes.BookRecipe recipe, int[] currentGrid) {
        return canCraft(recipe, availableItems(currentGrid));
    }

    private static Map<Integer, Integer> availableItems(int[] currentGrid) {
        Map<Integer, Integer> available = new HashMap<>();
        for (Inventory.Stack stack : Inventory.snapshot()) {
            if (stack != null) { addAvailable(available, stack.itemId, Math.min(9, stack.count)); }
        }
        for (int id : currentGrid) { if (id >= 0) { addAvailable(available, id, 1); } }
        return available;
    }

    private static boolean canCraft(CraftingRecipes.BookRecipe recipe, Map<Integer, Integer> available) {
        Map<Integer, Integer> required = new HashMap<>();
        for (int id : recipe.grid()) {
            if (id < 0) { continue; }
            int count = required.getOrDefault(id, 0) + 1;
            if (count > available.getOrDefault(id, 0)) { return false; }
            required.put(id, count);
        }
        return true;
    }

    private static void addAvailable(Map<Integer, Integer> available, int id, int count) {
        available.put(id, Math.min(9, available.getOrDefault(id, 0) + count));
    }

    static List<CraftingRecipes.BookRecipe> recipes(int width, int[] currentGrid) {
        return recipes(width, availableItems(currentGrid));
    }

    private static List<CraftingRecipes.BookRecipe> recipes(int width, Map<Integer, Integer> available) {
        List<CraftingRecipes.BookRecipe> result = new ArrayList<>();
        String query = search.trim().toLowerCase(Locale.ROOT);
        for (CraftingRecipes.BookRecipe recipe : CraftingRecipes.bookRecipes(width)) {
            if (ItemCatalog.itemName(recipe.outputItemId).toLowerCase(Locale.ROOT).contains(query)
                    && (!craftableOnly || canCraft(recipe, available))) {
                result.add(recipe);
            }
        }
        return result;
    }

    /** Returns true for events owned by the book, so they cannot reach the grid behind it. */
    static boolean handleMouse(int x, int y, int button, boolean pressed, int wheel,
            GuiManager.Layout crafting, int[] grid, boolean dragging) {
        Layout book = new Layout(crafting);
        if ((!open || !book.overlay) && button == 0 && pressed && book.inBookButton(x, y)) {
            if (!dragging) {
                if (open) { hide(); } else { open = true; firstRow = 0; }
            }
            return true;
        }
        if (!open) { return false; }
        if (!book.contains(x, y)) {
            if (button == 0 && pressed) { focus(false); }
            return false;
        }
        List<CraftingRecipes.BookRecipe> entries = recipes(crafting.gridColumns, grid);
        clampScroll(entries.size(), book.rows);
        if (wheel != 0) {
            firstRow += wheel < 0 ? 1 : -1;
            clampScroll(entries.size(), book.rows);
        }
        if (button != 0 || !pressed || dragging) { return true; }
        if (book.inClose(x, y)) { hide(); return true; }
        if (book.inSearch(x, y)) { focus(true); return true; }
        focus(false);
        if (book.inFilter(x, y)) {
            craftableOnly = !craftableOnly;
            firstRow = 0;
            return true;
        }
        int row = book.rowAt(x, y);
        if (row >= 0 && firstRow + row < entries.size()) {
            selected = entries.get(firstRow + row);
            notice = null;
            if (canCraft(selected, grid)) {
                if (!GuiManager.fillRecipe(selected.grid())) { notice = "NO SPACE TO RETURN GRID ITEMS"; }
            } else { notice = "MISSING INGREDIENTS - PREVIEW"; }
            if (book.overlay) { hide(); }
        }
        return true;
    }

    private static void clampScroll(int count, int rows) {
        firstRow = Math.max(0, Math.min(firstRow, Math.max(0, count - rows)));
    }

    private static void focus(boolean value) {
        if (focused == value) { return; }
        focused = value;
        try {
            if (value) { repeatBeforeSearch = Keyboard.areRepeatEventsEnabled(); }
            Keyboard.enableRepeatEvents(value || repeatBeforeSearch);
        } catch (Throwable ignored) { }
    }

    static void render(GuiManager.Layout crafting, int[] grid, int mouseX, int mouseY) {
        Layout book = new Layout(crafting);
        drawBookButton(book, mouseX, mouseY);
        if (!open) { return; }
        GuiGraphics.drawPanel(book.x, book.y, book.width, book.height);
        GuiGraphics.fillRect(book.x + 8, book.y + 8, book.width - 16, 20, 0.10f, 0.10f, 0.12f, 1);
        String visibleSearch = search;
        int capacity = (book.width - 30) / 6;
        if (visibleSearch.length() > capacity) { visibleSearch = visibleSearch.substring(visibleSearch.length() - capacity); }
        GuiGraphics.drawTextShadowed(search.isEmpty() ? "SEARCH RECIPES" : visibleSearch,
                book.x + 14, book.y + 14, 1, focused ? 1 : 0.76f, focused ? 1 : 0.76f, focused ? 1 : 0.80f);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            GuiGraphics.fillRect(book.x + 14 + visibleSearch.length() * 6, book.y + 13, 1, 9, 1, 1, 1, 1);
        }
        GuiGraphics.drawTextSmall("RECIPES", book.x + 10, book.y + 40);
        GuiGraphics.drawButton(book.filterX(), book.y + 34, 102, 20,
                craftableOnly ? "CRAFTABLE: ON" : "CRAFTABLE: OFF");
        GuiGraphics.drawButton(book.x + book.width - 28, book.y + 34, 20, 20, "X");

        Map<Integer, Integer> availableItems = availableItems(grid);
        List<CraftingRecipes.BookRecipe> entries = recipes(crafting.gridColumns, availableItems);
        clampScroll(entries.size(), book.rows);
        for (int row = 0; row < book.rows && firstRow + row < entries.size(); row++) {
            CraftingRecipes.BookRecipe recipe = entries.get(firstRow + row);
            int left = book.x + 8, top = book.listY + row * Layout.ROW_HEIGHT;
            boolean available = canCraft(recipe, availableItems);
            boolean hover = book.rowAt(mouseX, mouseY) == row;
            GuiGraphics.fillRect(left, top, book.width - 16, Layout.ROW_HEIGHT - 2,
                    hover ? 0.37f : 0.20f, hover ? 0.38f : 0.21f, hover ? 0.42f : 0.24f, 1);
            GuiGraphics.drawSlot(left + 3, top + 7, 28, available);
            GuiGraphics.drawItemIcon(recipe.outputItemId, recipe.outputAmount, left + 3, top + 7, 28);
            String name = ItemCatalog.itemName(recipe.outputItemId);
            int maxName = (book.width - 58) / 6;
            if (name.length() > maxName) { name = name.substring(0, maxName - 1) + "."; }
            GuiGraphics.drawTextSmall(name, left + 36, top + 5);
            GuiGraphics.drawTextShadowed(available ? "AVAILABLE" : "NEED ITEMS", left + 36, top + 19,
                    1, available ? 0.55f : 0.95f, available ? 0.95f : 0.65f, 0.55f);
            GuiGraphics.drawTextSmall(recipe.shapeless ? "SHAPELESS" : "SHAPED", left + 36, top + 31);
            int[] pattern = recipe.grid();
            for (int i = 0; i < pattern.length; i++) {
                int cellX = book.x + book.width - 36 + i % crafting.gridColumns * 8;
                int cellY = top + 17 + i / crafting.gridColumns * 8;
                GuiGraphics.fillRect(cellX, cellY, 7, 7, 0.10f, 0.10f, 0.12f, 1);
                if (pattern[i] >= 0) { GuiGraphics.drawItemIcon(pattern[i], 1, cellX, cellY, 7); }
            }
        }
        if (entries.isEmpty()) { GuiGraphics.drawTextSmall("NO MATCHING RECIPES", book.x + 12, book.listY + 12); }
        String footer = entries.isEmpty() ? "0 RECIPES" : (firstRow + 1) + "-"
                + Math.min(firstRow + book.rows, entries.size()) + " / " + entries.size() + "   SCROLL";
        GuiGraphics.drawTextSmall(footer, book.x + 10, book.y + book.height - 12);
    }

    private static void drawBookButton(Layout book, int mouseX, int mouseY) {
        boolean hover = book.inBookButton(mouseX, mouseY);
        GuiGraphics.drawSlot(book.buttonX, book.buttonY, 24, open || hover);
        // Pixel book cover, open pages and spine, drawn without another texture dependency.
        GuiGraphics.fillRect(book.buttonX + 4, book.buttonY + 6, 16, 13, 0.36f, 0.18f, 0.09f, 1);
        GuiGraphics.fillRect(book.buttonX + 5, book.buttonY + 4, 6, 12, 0.91f, 0.83f, 0.61f, 1);
        GuiGraphics.fillRect(book.buttonX + 13, book.buttonY + 4, 6, 12, 0.96f, 0.90f, 0.70f, 1);
        GuiGraphics.fillRect(book.buttonX + 11, book.buttonY + 5, 2, 13, 0.55f, 0.38f, 0.19f, 1);
    }

    static final class Layout {
        static final int WIDTH = 240;
        static final int GAP = 8;
        static final int ROW_HEIGHT = 44;
        final int x, y, width, height, listY, rows, buttonX, buttonY;
        final boolean overlay;

        Layout(GuiManager.Layout crafting) {
            overlay = crafting.panelX < WIDTH + GAP + 4;
            x = overlay ? crafting.panelX : crafting.panelX - WIDTH - GAP;
            y = crafting.panelY;
            width = WIDTH;
            height = crafting.panelHeight;
            listY = y + 60;
            rows = (height - 78) / ROW_HEIGHT;
            buttonX = crafting.gridX - 32;
            buttonY = crafting.gridY;
        }

        int filterX() { return x + width - 134; }
        boolean inBookButton(int px, int py) { return inside(px, py, buttonX, buttonY, 24, 24); }
        boolean contains(int px, int py) { return inside(px, py, x, y, width, height); }
        boolean inClose(int px, int py) { return inside(px, py, x + width - 28, y + 34, 20, 20); }
        boolean inFilter(int px, int py) { return inside(px, py, filterX(), y + 34, 102, 20); }
        boolean inSearch(int px, int py) { return inside(px, py, x + 8, y + 8, width - 16, 20); }
        int rowAt(int px, int py) {
            return inside(px, py, x + 8, listY, width - 16, rows * ROW_HEIGHT)
                    ? (py - listY) / ROW_HEIGHT : -1;
        }
        private static boolean inside(int px, int py, int left, int top, int w, int h) {
            return px >= left && py >= top && px < left + w && py < top + h;
        }
    }
}
