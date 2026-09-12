package dev.gtoe.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Declarative recipe registry shared by the 2x2 inventory and 3x3 table. */
public final class CraftingRecipes {
    private static final int[] PLAYER_GRID_POSITIONS = {1, 2, 4, 5};
    private static final List<Recipe> RECIPES = new ArrayList<Recipe>();

    static {
        registerShapeless(10, 2, 9);

        registerShaped(
                "A  " +
                "A  " +
                "   ",
                100, 2,
                ingredient('A', 10));

        registerShapeless(106, 1, 20, 20, 20);

        registerShaped(
                "AA " +
                "BB " +
                "   ",
                CraftingTableGui.BLOCK_ID, 1,
                ingredient('A', 106),
                ingredient('B', 10));
    }

    private CraftingRecipes() {
    }

    /** Associates one of the allowed pattern symbols A-I with an item ID. */
    public static Ingredient ingredient(char symbol, int itemId) {
        requireAllowedSymbol(symbol);
        if (itemId < 0) {
            throw new IllegalArgumentException("Recipe item IDs cannot be negative: " + itemId);
        }
        return new Ingredient(symbol, itemId);
    }

    /**
     * Adds a shaped recipe expressed as exactly nine characters. Spaces are
     * empty cells; every other character must be A-I and have an assignment.
     */
    public static synchronized void registerShaped(
            String pattern,
            int outputItemId,
            int outputAmount,
            Ingredient... ingredients) {
        validateOutput(outputItemId, outputAmount);
        RECIPES.add(new ShapedRecipe(
                pattern, outputItemId, outputAmount, ingredients));
    }

    /** Adds a shapeless recipe. Repeated IDs represent repeated ingredients. */
    public static synchronized void registerShapeless(
            int outputItemId,
            int outputAmount,
            int... ingredientItemIds) {
        validateOutput(outputItemId, outputAmount);
        if (ingredientItemIds == null
                || ingredientItemIds.length == 0
                || ingredientItemIds.length > 9) {
            throw new IllegalArgumentException("Shapeless recipes require 1-9 ingredients");
        }
        for (int itemId : ingredientItemIds) {
            if (itemId < 0) {
                throw new IllegalArgumentException(
                        "Recipe item IDs cannot be negative: " + itemId);
            }
        }
        RECIPES.add(new ShapelessRecipe(
                outputItemId, outputAmount, ingredientItemIds));
    }

    /** Returns {output ID, amount}, or {-1, 0} when no recipe matches. */
    static synchronized int[] recipeFor(int[] grid) {
        int[] fullGrid = asThreeByThree(grid);
        if (fullGrid == null) {
            return noRecipe();
        }

        for (Recipe recipe : RECIPES) {
            if (recipe.matches(fullGrid)) {
                return new int[] {recipe.outputItemId, recipe.outputAmount};
            }
        }
        return noRecipe();
    }

    private static int[] asThreeByThree(int[] grid) {
        if (grid == null) {
            return null;
        }
        if (grid.length == 9) {
            return Arrays.copyOf(grid, grid.length);
        }
        if (grid.length != 4) {
            return null;
        }

        int[] fullGrid = new int[9];
        Arrays.fill(fullGrid, Inventory.EMPTY_ITEM_ID);
        for (int index = 0; index < grid.length; index++) {
            fullGrid[PLAYER_GRID_POSITIONS[index]] = grid[index];
        }
        return fullGrid;
    }

    private static void validateOutput(int itemId, int amount) {
        if (itemId < 0) {
            throw new IllegalArgumentException("Recipe output ID cannot be negative: " + itemId);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Recipe output amount must be positive: " + amount);
        }
    }

    private static void requireAllowedSymbol(char symbol) {
        if (symbol < 'A' || symbol > 'I') {
            throw new IllegalArgumentException(
                    "Recipe symbols must be A through I: " + symbol);
        }
    }

    private static int[] noRecipe() {
        return new int[] {-1, 0};
    }

    /** One symbol-to-item assignment used by a shaped recipe. */
    public static final class Ingredient {
        private final char symbol;
        private final int itemId;

        private Ingredient(char symbol, int itemId) {
            this.symbol = symbol;
            this.itemId = itemId;
        }
    }

    private abstract static class Recipe {
        private final int outputItemId;
        private final int outputAmount;

        private Recipe(int outputItemId, int outputAmount) {
            this.outputItemId = outputItemId;
            this.outputAmount = outputAmount;
        }

        abstract boolean matches(int[] fullGrid);
    }

    private static final class ShapedRecipe extends Recipe {
        private final int[] shape;
        private final int shapeWidth;
        private final int shapeHeight;

        private ShapedRecipe(
                String pattern,
                int outputItemId,
                int outputAmount,
                Ingredient[] ingredients) {
            super(outputItemId, outputAmount);
            if (pattern == null || pattern.length() != 9) {
                throw new IllegalArgumentException(
                        "Shaped recipe patterns must contain exactly nine characters");
            }

            int[] assignments = new int[9];
            Arrays.fill(assignments, Inventory.EMPTY_ITEM_ID);
            boolean[] assignmentPresent = new boolean[9];
            if (ingredients != null) {
                for (Ingredient ingredient : ingredients) {
                    if (ingredient == null) {
                        throw new IllegalArgumentException("Recipe assignments cannot be null");
                    }
                    int assignmentIndex = ingredient.symbol - 'A';
                    if (assignmentPresent[assignmentIndex]) {
                        throw new IllegalArgumentException(
                                "Recipe symbol assigned more than once: " + ingredient.symbol);
                    }
                    assignments[assignmentIndex] = ingredient.itemId;
                    assignmentPresent[assignmentIndex] = true;
                }
            }

            int[] expanded = new int[9];
            Arrays.fill(expanded, Inventory.EMPTY_ITEM_ID);
            int minRow = 3;
            int minColumn = 3;
            int maxRow = -1;
            int maxColumn = -1;
            boolean[] usedAssignments = new boolean[9];
            for (int index = 0; index < pattern.length(); index++) {
                char symbol = pattern.charAt(index);
                if (symbol == ' ') {
                    continue;
                }
                requireAllowedSymbol(symbol);
                int assignmentIndex = symbol - 'A';
                if (!assignmentPresent[assignmentIndex]) {
                    throw new IllegalArgumentException(
                            "No item ID assigned to recipe symbol " + symbol);
                }
                expanded[index] = assignments[assignmentIndex];
                usedAssignments[assignmentIndex] = true;
                int row = index / 3;
                int column = index % 3;
                minRow = Math.min(minRow, row);
                minColumn = Math.min(minColumn, column);
                maxRow = Math.max(maxRow, row);
                maxColumn = Math.max(maxColumn, column);
            }
            if (maxRow < 0) {
                throw new IllegalArgumentException("Shaped recipes cannot be empty");
            }
            for (int index = 0; index < assignmentPresent.length; index++) {
                if (assignmentPresent[index] && !usedAssignments[index]) {
                    throw new IllegalArgumentException(
                            "Item ID assigned to unused recipe symbol " + (char) ('A' + index));
                }
            }

            shapeWidth = maxColumn - minColumn + 1;
            shapeHeight = maxRow - minRow + 1;
            shape = new int[shapeWidth * shapeHeight];
            Arrays.fill(shape, Inventory.EMPTY_ITEM_ID);
            for (int row = minRow; row <= maxRow; row++) {
                for (int column = minColumn; column <= maxColumn; column++) {
                    shape[(row - minRow) * shapeWidth + column - minColumn]
                            = expanded[row * 3 + column];
                }
            }
        }

        @Override
        boolean matches(int[] fullGrid) {
            for (int startRow = 0; startRow <= 3 - shapeHeight; startRow++) {
                for (int startColumn = 0; startColumn <= 3 - shapeWidth; startColumn++) {
                    if (matchesAt(fullGrid, startRow, startColumn)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean matchesAt(int[] fullGrid, int startRow, int startColumn) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    int shapeRow = row - startRow;
                    int shapeColumn = column - startColumn;
                    int expected = Inventory.EMPTY_ITEM_ID;
                    if (shapeRow >= 0 && shapeRow < shapeHeight
                            && shapeColumn >= 0 && shapeColumn < shapeWidth) {
                        expected = shape[shapeRow * shapeWidth + shapeColumn];
                    }
                    if (fullGrid[row * 3 + column] != expected) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static final class ShapelessRecipe extends Recipe {
        private final int[] ingredients;

        private ShapelessRecipe(int outputItemId, int outputAmount, int[] ingredients) {
            super(outputItemId, outputAmount);
            this.ingredients = Arrays.copyOf(ingredients, ingredients.length);
        }

        @Override
        boolean matches(int[] fullGrid) {
            int occupied = 0;
            for (int itemId : fullGrid) {
                if (itemId >= 0) {
                    occupied++;
                }
            }
            if (occupied != ingredients.length) {
                return false;
            }

            boolean[] matchedGridSlots = new boolean[fullGrid.length];
            for (int ingredientItemId : ingredients) {
                boolean found = false;
                for (int slot = 0; slot < fullGrid.length; slot++) {
                    if (!matchedGridSlots[slot] && fullGrid[slot] == ingredientItemId) {
                        matchedGridSlots[slot] = true;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
    }
}
