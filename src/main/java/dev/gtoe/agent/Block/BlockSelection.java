package dev.gtoe.agent;

/** Number-key hotbar selection kept outside the game classes. */
public final class BlockSelection {
    private static volatile int selectedHotbarIndex;

    private BlockSelection() {
    }

    public static int selectedBlockId() {
        int itemId = selectedItemId();
        return itemId > 0 && itemId < ItemCatalog.FIRST_ITEM_ID ? itemId : 0;
    }

    public static int selectedHotbarIndex() {
        return selectedHotbarIndex;
    }

    public static int selectedInventorySlot() {
        return Inventory.HOTBAR_START + selectedHotbarIndex;
    }

    public static int selectedItemId() {
        return Inventory.itemIdAt(selectedInventorySlot());
    }

    public static int selectedItemCount() {
        return Inventory.countAt(selectedInventorySlot());
    }

    /** Receives LWJGL 2 keyboard events from the transformed RubyDung render loop. */
    public static void handleKeyEvent(int keyCode, boolean pressed) {
        if (GuiManager.handleKeyEvent(keyCode, pressed)) {
            return;
        }
        if (!pressed) {
            return;
        }

        int hotbarIndex = hotbarIndexForKey(keyCode);
        if (hotbarIndex < 0) {
            return;
        }

        selectedHotbarIndex = hotbarIndex;
        int itemId = selectedItemId();
        String selection = itemId < 0
                ? "empty"
                : itemId + ": " + ItemCatalog.itemName(itemId);
        System.out.println("[gtoe] Selected hotbar slot " + (hotbarIndex + 1) + " (" + selection + ")");
    }

    public static String selectedBlockName() {
        int blockId = selectedBlockId();
        return blockId == 0 ? "Empty" : blockName(blockId);
    }

    public static String blockName(int blockId) {
        return ItemCatalog.blockName(blockId);
    }

    private static int hotbarIndexForKey(int keyCode) {
        // LWJGL 2 KEY_1 through KEY_9. Modifiers do not change the slot.
        if (keyCode >= 2 && keyCode <= 10) {
            return keyCode - 2;
        }

        // Numpad 1 through 9 selects the same nine hotbar slots.
        switch (keyCode) {
            case 79:
                return 0;
            case 80:
                return 1;
            case 81:
                return 2;
            case 75:
                return 3;
            case 76:
                return 4;
            case 77:
                return 5;
            case 71:
                return 6;
            case 72:
                return 7;
            case 73:
                return 8;
            default:
                return -1;
        }
    }
}
