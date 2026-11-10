package dev.gtoe.agent;

/**
    The catalog of all items along with some helper functions.
    Item IDs are after 100, block IDs are 0-99.
**/
public final class ItemCatalog {
    public static final int FIRST_ITEM_ID = 100;

    private ItemCatalog() {
    }

    public static boolean isBlock(int itemId) {
        return itemId >= 0 && itemId < FIRST_ITEM_ID;
    }

    public static String itemName(int itemId) {
        if (isBlock(itemId)) {
            return blockName(itemId);
        }

        switch (itemId) {
            case 100: return "Stick";
            case 101: return "Copper Ingot";
            case 102: return "Iron Ingot";
            case 103: return "Coal";
            case 104: return "Tin Ingot";
            case 105: return "Bronze Ingot";
            case 106: return "Flint";
            case 107: return "Stone-Reinforced Stick";
            case 108: return "Tempered Flint";
            case 109: return "Tempered Stone-Reinforced Stick";
            default: return "Unknown Item";
        }
    }

    public static String blockName(int blockId) {
        switch (blockId) {
            case 0: return "Air";
            case 1: return "Grass";
            case 2: return "Dirt";
            case 3: return "Stone";
            case 4: return "Deep Stone";
            case 5: return "Bedrock";
            case 6: return "Sand";
            case 7: return "Clay";
            case 8: return "Snow";
            case 9: return "Wood";
            case 10: return "Planks";
            case 11: return "Bronze Casing";
            case 12: return "Steel Casing";
            case 13: return "Aluminium Casing";
            case 14: return "Stainless Steel Casing";
            case 15: return "Copper Ore";
            case 16: return "Iron Ore";
            case 17: return "Coal Ore";
            case 18: return "Tin Ore";
            case 19: return "Basic Machine";  // LV machine
            case 20: return "Gravel";
            case 21: return "Hardened Stone";  // 2 stone 2 flint
            case 22: return "Furnace Main Hatch";  // T1 Furnace controller
            case 23: return "Furnace Exhaust";  // T1 Furnace muffler
            case 24: return "Furnace Fuel Hatch";  // T1 Furnace fuel input
            case 25: return "Furnace Fuel Chamber";  // T1 Furnace internal block
            case 26: return "Furnace Main Chamber";  // T1 Furnace internal block
            case 27: return "Cupronickel Coil Block";
            case 28: return "Crafting Table";
            case 29: return "Tempered Stone";  // smelted stone
            case 30: return "Tempered Furnace Main Hatch";  // T2 Furnace controller
            case 31: return "Tempered Furnace Exhaust";  // T2 Furnace muffler
            case 32: return "Tempered Furnace Fuel Hatch";  // T2 Furnace fuel input
            case 33: return "Tempered Furnace Fuel Chamber";  // T2 Furnace internal block
            case 34: return "Tempered Furnace Main Chamber";  // T2 Furnace internal block
            case 35: return "Coke Oven Main Hatch";  // Coke Oven controller
            case 36: return "Coke Oven Fuel Hatch";  // Coke Oven fuel input
            case 37: return "Coke Oven Output Hatch";  // Coke Oven output
            case 38: return "Coke Oven Exhaust";  // Coke Oven muffler
            default: return "Unknown Block";
        }
    }
}
