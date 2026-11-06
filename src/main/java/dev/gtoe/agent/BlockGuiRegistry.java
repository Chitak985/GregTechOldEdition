package dev.gtoe.agent;

import java.util.*;
import static dev.gtoe.agent.GuiManager.Layout;

public final class BlockGuiRegistry {
    /** All loaded GUI definitions as block ID: definition **/
    private static final Map<Integer, Definition> DEFINITIONS =
            new HashMap<>();
    /** Block IDs that have the special crafting table GUI. **/
    private static final Set<Integer> CRAFTING_TABLES = new HashSet<>();

    static {
        // Add block GUIs here
        // First number is the block ID
        register(10,
                Collections.singletonList(
                        new GUIText("Hello", 16, 16)
                ),
                Collections.singletonList(
                        new GUIButton("Button 1",
                                "callback1",
                                140,
                                82,
                                140,
                                28)
                ));
        registerCraftingTable(CraftingTableGui.BLOCK_ID);
    }

    private BlockGuiRegistry() {
    }

    /** Register a GUI definition for a block ID with provided text and buttons. **/
    public static synchronized void register(int blockId,
                                             List<GUIText> texts,
                                             List<GUIButton> buttons) {
        if (!ItemCatalog.isBlock(blockId)) {
            throw new IllegalArgumentException("BlockGuiRegistry/register: Cannot register GUI for block ID "+blockId+" as it is not a block!");
        }
        DEFINITIONS.put(blockId, new Definition(texts, buttons));
    }

    /** Registers the special crafting table GUI type for the provided block ID. **/
    public static synchronized void registerCraftingTable(int blockId) {
        if (!ItemCatalog.isBlock(blockId)) {
            throw new IllegalArgumentException(
                    "BlockGuiRegistry/registerCraftingTable: Invalid block ID " + blockId);
        }
        CRAFTING_TABLES.add(blockId);
    }

    /** Open a GUI definition for the provided block ID. **/
    public static synchronized boolean openForBlock(int blockId) {
        // Handle crafting tables (opens a special GUI)
        if (CRAFTING_TABLES.contains(blockId)) {
            CraftingTableGui.open();
            return true;
        }
        
        // Get the block's GUI definition
        Definition definition = DEFINITIONS.get(blockId);
        
        // Skip if it has no GUI
        if (definition == null) {
            return false;
        }

        // Open GUI
        GuiManager.openDefinition(definition);
        return true;
    }

    /**
        Stores the GUI definition data.
        Most parameters are public to make them modifiable if needed.
    **/
    public static class Definition {
        public List<GUIButton> buttons;
        public List<GUIText> texts;
    
        public Definition(List<GUIText> texts,
                          List<GUIButton> buttons) {
            this.texts = texts != null ? texts : new ArrayList<>();
            this.buttons = buttons != null ? buttons : new ArrayList<>();
        }
    
        /** Render the GUI's buttons and text. **/
        public synchronized void render(Layout layout) {
            for (GUIButton tmp : buttons) {
                tmp.render(layout);
            }
            for (GUIText tmp : texts) {
                tmp.render(layout);
            }
        }
    }

    /**
        Stores the GUI button data.
        Callback is checked elsewhere to do a hard-coded action.
        Most parameters are public to make them modifiable if needed.
    **/
    public static class GUIButton {
        public String text;
        public String callback;
        public int posX;
        public int posY;
        public int width;
        public int height;

        public GUIButton(String text, String callback, int posX, int posY, int width, int height) {
            this.text = text;
            this.callback = callback;
            this.posX = posX;
            this.posY = posY;
            this.width = width;
            this.height = height;
        }
        /** GUIButton but width and height are defaulted. **/
        public GUIButton(String text, String callback, int posX, int posY) {
            this(text, callback, posX, posY, 140, 28);
        }

        /** Render the button using stored data. **/
        public synchronized void render(Layout layout) {
            GuiGraphics.drawButton(
                    layout.panelX + posX,
                    layout.panelY + posY,
                    width,
                    height,
                    text);
        }
    }

    /**
        Stores the GUI text data.
        Most parameters are public to make them modifiable if needed.
    **/
    public static class GUIText {
        public String text;
        public int posX;
        public int posY;
        public int scale;
        public float colR;
        public float colG;
        public float colB;

        public GUIText(String text,      // Text
                          int posX,      // X position in pixels
                          int posY,      // Y position in pixels
                          int scale,     // Text scale
                          float colR,    // Color (Red in RGB)
                          float colG,    // Color (Green in RGB)
                          float colB) {  // Color (Blue in RGB)
            this.text = text;
            this.posX = posX;
            this.posY = posY;
            this.scale = scale;
            this.colR = colR;
            this.colG = colG;
            this.colB = colB;
        }
        /** GUIText but color is defaulted. **/
        public GUIText(String text, int posX, int posY, int scale) {
            this(text, posX, posY, scale, 0.93f, 0.93f, 0.93f);
        }
        /** GUIText but scale and color are defaulted. **/
        public GUIText(String text, int posX, int posY) {
            this(text, posX, posY, 2);
        }

        /** Render the text using stored data. **/
        public synchronized void render(Layout layout) {
            GuiGraphics.drawTextShadowed(text, layout.panelX + posX, layout.panelY + posY, scale, colR, colG, colB);
        }
    }
}
