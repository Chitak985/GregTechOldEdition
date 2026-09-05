package dev.gtoe.agent;

/** Shared item/block identity rules. Block IDs occupy 0-99; ordinary items start at 100. */
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
            case 22: return "Furnace Main Hatch";
            case 23: return "Furnace Exhaust";
            case 24: return "Furnace Fuel Hatch";
            case 25: return "Furnace Fuel Chamber";
            case 26: return "Furnace Main Chamber";
            case 27: return "Cupronickel Coil Block";
            default: return "Unknown Block";
        }
    }
}
