package dev.gtoe.agent;

/** Recipe matching shared by the player's 2x2 grid and the crafting table's 3x3 grid. */
final class CraftingRecipes {
    private CraftingRecipes() {
    }

    /** Returns {output ID, amount}, or {-1, 0} when no recipe matches. */
    static int[] recipeFor(int[] grid) {
        int width = gridWidth(grid);
        if (width == 0) {
            return noRecipe();
        }

        int occupied = countOccupied(grid);
        if (occupied == 1 && countItem(grid, 9) == 1) {
            return new int[] {10, 2};
        }
        if (occupied == 2 && countItem(grid, 10) == 2
                && containsAdjacentVerticalPair(grid, width, 10)) {
            return new int[] {100, 2};
        }
        if (occupied == 3 && countItem(grid, 20) == 3) {
            return new int[] {106, 1};
        }
        if (occupied == 4 && containsCraftingTablePattern(grid, width)) {
            return new int[] {CraftingTableGui.BLOCK_ID, 1};
        }

        return noRecipe();
    }

    private static int gridWidth(int[] grid) {
        if (grid == null) {
            return 0;
        }
        if (grid.length == 4) {
            return 2;
        }
        if (grid.length == 9) {
            return 3;
        }
        return 0;
    }

    private static int countOccupied(int[] grid) {
        int count = 0;
        for (int itemId : grid) {
            if (itemId >= 0) {
                count++;
            }
        }
        return count;
    }

    private static int countItem(int[] grid, int wantedItemId) {
        int count = 0;
        for (int itemId : grid) {
            if (itemId == wantedItemId) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsAdjacentVerticalPair(int[] grid, int width, int itemId) {
        for (int row = 0; row < width - 1; row++) {
            for (int column = 0; column < width; column++) {
                int top = row * width + column;
                if (grid[top] == itemId && grid[top + width] == itemId) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsCraftingTablePattern(int[] grid, int width) {
        for (int row = 0; row < width - 1; row++) {
            for (int column = 0; column < width - 1; column++) {
                int topLeft = row * width + column;
                if (grid[topLeft] == 106
                        && grid[topLeft + 1] == 106
                        && grid[topLeft + width] == 10
                        && grid[topLeft + width + 1] == 10) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int[] noRecipe() {
        return new int[] {-1, 0};
    }
}
