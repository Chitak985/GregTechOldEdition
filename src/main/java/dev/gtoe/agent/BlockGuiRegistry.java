package dev.gtoe.agent;

import java.util.HashMap;
import java.util.Map;

/** Registry that lets block IDs opt into reusable simple GUIs without touching game classes. */
public final class BlockGuiRegistry {
    private static final Map<Integer, Definition> DEFINITIONS =
            new HashMap<Integer, Definition>();

    static {
        register(ItemCatalog.PLANKS_BLOCK_ID, "Test", "Test button");
    }

    private BlockGuiRegistry() {
    }

    public static synchronized void register(int blockId, String title, String buttonText) {
        if (!ItemCatalog.isBlock(blockId)) {
            throw new IllegalArgumentException("GUI block ID must be between 0 and 99");
        }
        DEFINITIONS.put(Integer.valueOf(blockId), new Definition(title, buttonText));
    }

    public static synchronized boolean openForBlock(int blockId) {
        Definition definition = DEFINITIONS.get(Integer.valueOf(blockId));
        if (definition == null) {
            return false;
        }
        GuiManager.openSimple(definition.title, definition.buttonText);
        return true;
    }

    private static final class Definition {
        private final String title;
        private final String buttonText;

        private Definition(String title, String buttonText) {
            this.title = title;
            this.buttonText = buttonText;
        }
    }
}
