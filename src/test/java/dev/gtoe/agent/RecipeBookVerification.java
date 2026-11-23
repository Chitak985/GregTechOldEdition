package dev.gtoe.agent;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/** Exercises recipe discovery, filtering and real mouse/keyboard crafting flows without OpenGL. */
final class RecipeBookVerification {
    static void run() throws Exception {
        try {
            verifySearchAndFiltering();
            verifyFillingAndCrafting();
            verifyFullInventory();
            verifyGridLimitsAndScrolling();
            verifyNarrowWindow();
            System.out.println("Recipe book passed: search, craftable filter, grid limits, scrolling, "
                    + "autofill, previews, full inventory and input ownership");
        } finally {
            GuiManager.resetForWorld();
            Inventory.clearForTests();
        }
    }

    private static void verifySearchAndFiltering() throws Exception {
        start(false);
        openBook(1024, 768);
        GuiManager.Layout crafting = GuiManager.craftingLayout(1024, 768);
        RecipeBook.Layout book = new RecipeBook.Layout(crafting);
        require(book.x >= 4 && book.x + book.width < crafting.panelX
                && crafting.panelX + crafting.panelWidth <= 1024, "Book and grid must fit side by side");
        click(book.filterX() + 4, book.y + 40, 1024, 768);
        require(RecipeBook.recipes(2, grid(false)).isEmpty(), "An empty inventory has no craftable recipes");
        Inventory.addToSlot(73, 9, 1);
        require(RecipeBook.recipes(2, grid(false)).size() == 1
                && RecipeBook.recipes(2, grid(false)).get(0).outputItemId == 10,
                "Craftable recipes must update when inventory contents change");
        click(book.x + 14, book.y + 16, 1024, 768);
        int selection = BlockSelection.selectedHotbarIndex();
        GuiManager.handleKeyEvent(18, true, 'e');
        GuiManager.handleKeyEvent(2, true, '1');
        require(GuiManager.isOpen() && RecipeBook.isSearchFocused()
                && BlockSelection.selectedHotbarIndex() == selection,
                "Typing E or numbers in search must not close crafting or select the hotbar");
        require(RecipeBook.recipes(2, grid(false)).isEmpty(), "Unmatched searches must return no results");
        search("PlAnKs", 1024, 768);
        require(RecipeBook.recipes(2, grid(false)).size() == 1, "Search must match result names without case sensitivity");
        GuiManager.handleKeyEvent(14, true, '\b');
        require(RecipeBook.recipes(2, grid(false)).size() == 1, "Backspace must edit the search");
        search("wood", 1024, 768);
        require(RecipeBook.recipes(2, grid(false)).isEmpty(), "Search is by output name, not ingredient name");
        search("", 1024, 768);
        GuiManager.handleKeyEvent(28, true, '\r');
        require(!RecipeBook.isSearchFocused() && GuiManager.isOpen(), "Enter should finish editing without closing crafting");
        click(book.x + book.width - 18, book.y + 42, 1024, 768);
        require(!RecipeBook.isOpen() && GuiManager.isOpen(), "X closes only the book");
        require(GuiManager.blocksWorldAction(), "Book clicks must never reach world interaction");
    }

    private static void verifyFillingAndCrafting() throws Exception {
        start(false);
        Inventory.addToSlot(5, 9, 2);
        openBook(1024, 768);
        choose(10, 1024, 768);
        require(grid(false)[0] == 9 && Inventory.count(9) == 1,
                "Selecting a recipe must move exactly one set of owned ingredients into the grid");
        output();
        choose(10, 1024, 768);
        output();
        require(Inventory.count(9) == 0 && Inventory.count(10) == 4, "Two actual crafts must consume two wood and give four planks");
        choose(10, 1024, 768);
        require(Arrays.equals(grid(false), new int[] {-1, -1, -1, -1}) && RecipeBook.preview()[0] == 9,
                "An unavailable recipe must only preview ingredients");
        output();
        require(Inventory.count(10) == 4, "Preview ingredients cannot be crafted into free items");

        Inventory.clearForTests();
        Inventory.addToSlot(2, 10, 1);
        Inventory.addToSlot(75, 10, 1);
        choose(100, 1024, 768);
        require(Arrays.equals(grid(false), new int[] {10, -1, 10, -1}) && Inventory.count(10) == 0,
                "Autofill must combine split stacks and preserve a shaped recipe's arrangement");
        choose(100, 1024, 768);
        require(Inventory.count(10) == 0 && RecipeBook.canCraft(entry(100, 2), grid(false)),
                "Ingredients already in the grid must count once and be reusable for filling");
        output();
        require(Inventory.count(100) == 2, "Filled recipes must use the existing output crafting path");

        Inventory.clearForTests();
        Inventory.addToSlot(Inventory.HOTBAR_START + 8, 20, 1);
        require(!RecipeBook.canCraft(entry(106, 2), grid(false)), "One gravel cannot satisfy three ingredients");
        Inventory.addToSlot(17, 20, 1);
        require(!RecipeBook.canCraft(entry(106, 2), grid(false)), "Two gravel cannot satisfy three ingredients");
        grid(false)[3] = 20;
        require(RecipeBook.canCraft(entry(106, 2), grid(false)), "Inventory, hotbar and current grid must all contribute");
        choose(106, 1024, 768);
        output();
        require(Inventory.count(106) == 1 && Inventory.count(20) == 0, "Shapeless filling must consume each ingredient exactly once");

        Inventory.addToSlot(0, 9, 11);
        GuiManager.Layout layout = GuiManager.craftingLayout(1024, 768);
        RecipeBook.Layout book = new RecipeBook.Layout(layout);
        mouse(layout.inventoryX + 5, layout.inventoryY + 5, 0, true, 0, 1024, 768);
        mouse(book.x + 20, book.listY + 10, 0, false, 0, 1024, 768);
        require(Inventory.itemIdAt(0) == 9 && Inventory.countAt(0) == 11,
                "Releasing a dragged stack over the book must return it to its original slot");
    }

    private static void verifyFullInventory() throws Exception {
        start(false);
        for (int slot = 0; slot < Inventory.TOTAL_SLOT_COUNT; slot++) { Inventory.addToSlot(slot, 1000 + slot, 2); }
        Inventory.takeStack(20);
        Inventory.addToSlot(20, 9, 2);
        grid(false)[0] = 2;
        Inventory.Stack[] before = Inventory.snapshot();
        require(!GuiManager.fillRecipe(entry(10, 2).grid()), "A recipe must fail safely if old grid items have no return space");
        assertInventory(before);
        require(grid(false)[0] == 2, "A failed fill must leave the existing crafting grid unchanged");
        Inventory.removeFromSlot(20, 9, 1);
        require(GuiManager.fillRecipe(entry(10, 2).grid()) && Inventory.itemIdAt(20) == 2
                && Inventory.countAt(20) == 1 && grid(false)[0] == 9,
                "Consuming the last ingredient in a stack can free a slot for returning old grid contents");
    }

    private static void verifyGridLimitsAndScrolling() throws Exception {
        CraftingRecipes.registerShaped("A A" + "   " + "   ", 101, 1, CraftingRecipes.ingredient('A', 801));
        CraftingRecipes.registerShapeless(102, 1, 802, 802, 802, 802, 802);
        require(!has(22, 2) && !has(101, 2) && !has(102, 2) && has(28, 2),
                "Inventory book must exclude wide, tall and more-than-four-ingredient recipes");
        require(has(22, 3) && has(101, 3) && has(102, 3), "Table book must include all 3x3 recipes");
        for (int width : new int[] {2, 3}) {
            for (CraftingRecipes.BookRecipe entry : CraftingRecipes.bookRecipes(width)) {
                require(Arrays.equals(CraftingRecipes.recipeFor(entry.grid()), new int[] {entry.outputItemId, entry.outputAmount}),
                        "Every displayed arrangement must match the actual recipe engine");
            }
        }
        start(true);
        openBook(1024, 768);
        RecipeBook.Layout book = new RecipeBook.Layout(GuiManager.craftingLayout(1024, 768));
        List<CraftingRecipes.BookRecipe> recipes = RecipeBook.recipes(3, grid(true));
        Field scroll = GuiManager.class.getDeclaredField("scrollRow");
        scroll.setAccessible(true);
        for (int i = 0; i < recipes.size() + 3; i++) {
            mouse(book.x + 20, book.listY + 10, -1, false, -120, 1024, 768);
        }
        click(book.x + 20, book.listY + (book.rows - 1) * RecipeBook.Layout.ROW_HEIGHT + 10, 1024, 768);
        require(Arrays.equals(RecipeBook.preview(), recipes.get(recipes.size() - 1).grid()), "Scrolling must reach the final recipe");
        require(scroll.getInt(null) == 0, "Scrolling the book must not scroll inventory slots");
        search("planks", 1024, 768);
        click(book.x + 20, book.listY + 10, 1024, 768);
        require(RecipeBook.preview()[0] == 9, "Changing search after scrolling must reset to the first matching result");
        click(book.x + 14, book.y + 16, 1024, 768);
        GuiManager.handleKeyEvent(1, true, (char) 0);
        require(RecipeBook.isOpen() && !RecipeBook.isSearchFocused(), "Escape must first leave the search field");
        require(!GuiManager.isGameplayKeyDown(1), "An open GUI must suppress the original game's Escape-to-exit poll");
        GuiManager.handleKeyEvent(1, true, (char) 0);
        require(!RecipeBook.isOpen() && GuiManager.isOpen(), "Escape must then close the book");
    }

    private static void verifyNarrowWindow() throws Exception {
        start(false);
        Inventory.addToSlot(0, 9, 1);
        openBook(320, 480);
        GuiManager.Layout layout = GuiManager.craftingLayout(320, 480);
        RecipeBook.Layout book = new RecipeBook.Layout(layout);
        require(book.overlay && book.x >= 0 && book.x + book.width <= 320, "The compact book must stay inside a narrow window");
        click(book.x + 40, book.listY + 10, 320, 480);
        require(!RecipeBook.isOpen() && grid(false)[0] == 9 && Inventory.count(9) == 0,
                "Compact recipe selection must fill once, then reveal the crafting grid");
    }

    private static void start(boolean table) {
        GuiManager.resetForWorld();
        Inventory.clearForTests();
        if (table) { GuiManager.openCraftingTable(); } else { GuiManager.handleKeyEvent(18, true); }
    }

    private static void openBook(int width, int height) {
        RecipeBook.Layout book = new RecipeBook.Layout(GuiManager.craftingLayout(width, height));
        click(book.buttonX + 10, book.buttonY + 10, width, height);
        require(RecipeBook.isOpen(), "The book icon must open the recipe list");
    }

    private static void search(String text, int width, int height) {
        RecipeBook.Layout book = new RecipeBook.Layout(GuiManager.craftingLayout(width, height));
        click(book.x + 14, book.y + 16, width, height);
        for (int i = 0; i < 48; i++) { GuiManager.handleKeyEvent(14, true, '\b'); }
        for (char c : text.toCharArray()) { GuiManager.handleKeyEvent(0, true, c); }
    }

    private static void choose(int id, int width, int height) throws Exception {
        search(ItemCatalog.itemName(id), width, height);
        RecipeBook.Layout book = new RecipeBook.Layout(GuiManager.craftingLayout(width, height));
        click(book.x + 16, book.listY + 10, width, height);
    }

    private static void output() {
        GuiManager.Layout layout = GuiManager.craftingLayout(1024, 768);
        click(layout.outputX + 10, layout.outputY + 10, 1024, 768);
    }

    private static int[] grid(boolean table) throws Exception {
        if (table) { return CraftingTableGui.grid(); }
        Field field = GuiManager.class.getDeclaredField("PLAYER_CRAFTING_GRID");
        field.setAccessible(true);
        return (int[]) field.get(null);
    }

    private static CraftingRecipes.BookRecipe entry(int id, int width) {
        for (CraftingRecipes.BookRecipe recipe : CraftingRecipes.bookRecipes(width)) { if (recipe.outputItemId == id) { return recipe; } }
        throw new AssertionError("Missing recipe " + id);
    }
    private static boolean has(int id, int width) { for (CraftingRecipes.BookRecipe recipe : CraftingRecipes.bookRecipes(width)) { if (recipe.outputItemId == id) { return true; } } return false; }
    private static void click(int x, int y, int w, int h) { mouse(x, y, 0, true, 0, w, h); mouse(x, y, 0, false, 0, w, h); }
    private static void mouse(int x, int y, int button, boolean pressed, int wheel, int w, int h) { GuiManager.handleMouseEvent(x, h - 1 - y, button, pressed, wheel, w, h); }
    private static void assertInventory(Inventory.Stack[] expected) {
        for (int slot = 0; slot < expected.length; slot++) {
            Inventory.Stack stack = expected[slot];
            require(Inventory.itemIdAt(slot) == (stack == null ? -1 : stack.itemId)
                    && Inventory.countAt(slot) == (stack == null ? 0 : stack.count), "Failed fill changed inventory slot " + slot);
        }
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
