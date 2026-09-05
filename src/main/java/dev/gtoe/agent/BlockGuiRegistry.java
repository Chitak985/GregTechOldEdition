package dev.gtoe.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

// Remove the "simple" text and button code

/** Registry that lets block IDs opt into reusable simple GUIs without touching game classes. */
public final class BlockGuiRegistry {
    // All the GUI definitions as block ID: definition
    private static final Map<Integer, Definition> DEFINITIONS =
            new HashMap<Integer, Definition>();

    static {
        // Add block GUIs here
        // First number is the block ID
        register(10, "Test", "Test button");
    }

    private BlockGuiRegistry() {
    }

    public static synchronized void register(int blockId, String title, String buttonText) {
        if (!ItemCatalog.isBlock(blockId)) {
            throw new IllegalArgumentException("BlockGuiRegistry/register: Cannot register GUI for block ID "+String.valueOf(blockId)+" as it is not considered a block!");
        }
        DEFINITIONS.put(Integer.valueOf(blockId), new Definition(title, buttonText));
    }

    // Called if a block is right clicked to open a GUI
    public static synchronized boolean openForBlock(int blockId) {
        Definition definition = DEFINITIONS.get(Integer.valueOf(blockId));  // Get the block's GUI definition
        if (definition == null) {  // Skip if it has no GUI
            return false;
        }
        GuiManager.openSimple(definition.title, definition.buttonText);  // Open a simple GUI
        return true;
    }

    // Stores the GUI definition data
    // Everything is public to make it modifyable if needed
    public static class Definition {
        public String title;
        public String buttonText;
        public List<GUIButton> buttons;
        public List<GUIText> texts;
    
        public Definition(String title,
                          String buttonText,
                          List<GUIButton> buttons,
                          List<GUIText> texts) {
            this.title = title;
            this.buttonText = buttonText;
            this.buttons = buttons != null ? buttons : new ArrayList<GUIButton>();
            this.texts = texts != null ? texts : new ArrayList<GUIText>();
        }
    
        // Both lists default to empty
        public Definition(String title, String buttonText) {
            this(title, buttonText, null, null);
        }
    
        public synchronized void render() {
            for (GUIButton tmp : buttons) {
                tmp.render();
            }
            for (GUIText tmp : texts) {
                tmp.render();
            }
        }
    }

    // Stores GUI button data
    // callback is checked elsewhere to do a hard-coded action
    // Everything is public to make it modifyable if needed
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
        public GUIButton(String text, String callback, int posX, int posY) {
            this(text, callback, posX, posY, 140, 28);
        }

        public synchronized void render() {
            GuiGraphics.drawButton(posX, posY, width, height, text);
        }
    }

    // Stores GUI text data
    // Everything is public to make it modifyable if needed
    public static class GUIText {
        public String text;
        public int posX;
        public int posY;
        public float scale;
        public float colR;
        public float colG;
        public float colB;

        public GUIText(String text,      // Text
                          int posX,      // X position in pixels
                          int posY,      // Y position in pixels
                          float scale,   // Text scale
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
        public GUIText(String text, int posX, int posY) {
            this(text, posX, posY, 2f, 0.93f, 0.93f, 0.93f);
        }
        public GUIText(String text, int posX, int posY, float scale) {
            this(text, posX, posY, scale, 0.93f, 0.93f, 0.93f);
        }

        public synchronized void render() {
            GuiGraphics.drawTextShadowed(text, posX, posY, scale, colR, colG, colB);
        }
    }
}
