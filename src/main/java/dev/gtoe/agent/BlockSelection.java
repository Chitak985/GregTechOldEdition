package dev.gtoe.agent;

/** Number-key block selection kept outside the game classes. */
public final class BlockSelection {
    private static volatile int selectedBlockId = 1;

    private BlockSelection() {
    }

    public static int selectedBlockId() {
        return selectedBlockId;
    }

    /** Receives LWJGL 2 keyboard events from the transformed RubyDung render loop. */
    public static void handleKeyEvent(int keyCode, boolean pressed, boolean shifted) {
        if (!pressed) {
            return;
        }

        int blockId = blockIdForKey(keyCode, shifted);
        if (blockId == 0) {
            return;
        }

        selectedBlockId = blockId;
        System.out.println("[gtoe] Selected block " + blockId + ": " + blockName(blockId));
    }

    public static String selectedBlockName() {
        return blockName(selectedBlockId);
    }

    public static String blockName(int blockId) {
        switch (blockId) {
            case 1:
                return "Grass";
            case 2:
                return "Dirt";
            case 3:
                return "Stone";
            case 4:
                return "Deep Stone";
            case 5:
                return "Bedrock";
            case 6:
                return "Sand";
            case 7:
                return "Clay";
            case 8:
                return "Snow";
            case 9:
                return "Wood";
            case 10:
                return "Planks";
            case 11:
                return "Bronze Casing";
            case 12:
                return "Steel Casing";
            case 13:
                return "Aluminium Casing";
            case 14:
                return "Stainless Steel Casing";
            case 15:
                return "Copper Ore";
            case 16:
                return "Iron Ore";
            case 17:
                return "Coal Ore";
            case 18:
                return "Tin Ore";
            case 19:
                return "Machine";
            default:
                return "Unknown";
        }
    }

    private static int blockIdForKey(int keyCode, boolean shifted) {
        // LWJGL 2 KEY_1 through KEY_9, followed by KEY_0.
        if (keyCode >= 2 && keyCode <= 10) {
            int digit = keyCode - 1;
            return shifted ? digit + 10 : digit;
        }
        if (keyCode == 11) {
            return 10;
        }

        // Numpad support: Shift+1-9 also selects 11-19.
        int digit;
        switch (keyCode) {
            case 79:
                digit = 1;
                break;
            case 80:
                digit = 2;
                break;
            case 81:
                digit = 3;
                break;
            case 75:
                digit = 4;
                break;
            case 76:
                digit = 5;
                break;
            case 77:
                digit = 6;
                break;
            case 71:
                digit = 7;
                break;
            case 72:
                digit = 8;
                break;
            case 73:
                digit = 9;
                break;
            case 82:
                return 10;
            default:
                return 0;
        }
        return shifted ? digit + 10 : digit;
    }
}
