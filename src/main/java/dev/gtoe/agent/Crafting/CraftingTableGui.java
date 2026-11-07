package dev.gtoe.agent;

import java.util.Arrays;

/** Dedicated 3x3 crafting-table GUI type, separate from generic block definitions. */
public final class CraftingTableGui {
    public static final int BLOCK_ID = 28;
    static final int GRID_WIDTH = 3;
    private static final int[] GRID = new int[GRID_WIDTH * GRID_WIDTH];

    static {
        Arrays.fill(GRID, Inventory.EMPTY_ITEM_ID);
    }

    private CraftingTableGui() {
    }

    public static void open() {
        GuiManager.openCraftingTable();
    }

    static int[] grid() {
        return GRID;
    }
}
