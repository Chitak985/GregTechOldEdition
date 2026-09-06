package dev.gtoe.agent;

import java.util.*;

import static dev.gtoe.agent.GuiManager.Layout;

// Remove the "simple" text and button code

/** Registry that lets block IDs opt into reusable simple GUIs without touching game classes. */
public final class BlockGuiRegistry {
    // All the GUI definitions as block ID: definition
    private static final Map<Integer, Definition> DEFINITIONS =
            new HashMap<>();

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
    }

    private BlockGuiRegistry() {
    }

    public static synchronized void register(int blockId,
                                             List<GUIText> texts,
                                             List<GUIButton> buttons) {
        if (!ItemCatalog.isBlock(blockId)) {
            throw new IllegalArgumentException("BlockGuiRegistry/register: Cannot register GUI for block ID "+blockId+" as it is not considered a block!");
        }
        DEFINITIONS.put(blockId, new Definition(texts, buttons));
    }

    // Called if a block is right-clicked to open a GUI
    public static synchronized boolean openForBlock(int blockId) {
        Definition definition = DEFINITIONS.get(blockId);  // Get the block's GUI definition
        if (definition == null) {  // Skip if it has no GUI
            return false;
        }
        GuiManager.openDefinition(definition);
        return true;
    }

    // Stores the GUI definition data
    // Everything is public to make it modifiable if needed
    public static class Definition {
        public List<GUIButton> buttons;
        public List<GUIText> texts;
    
        public Definition(List<GUIText> texts,
                          List<GUIButton> buttons) {
            this.texts = texts != null ? texts : new ArrayList<>();
            this.buttons = buttons != null ? buttons : new ArrayList<>();
        }
    
        public synchronized void render(Layout layout) {
            for (GUIButton tmp : buttons) {
                tmp.render(layout);
            }
            for (GUIText tmp : texts) {
                tmp.render(layout);
            }
        }
    }

    // Stores GUI button data
    // callback is checked elsewhere to do a hard-coded action
    // Everything is public to make it modifiable if needed
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
        // Default width and height
        public GUIButton(String text, String callback, int posX, int posY) {
            this(text, callback, posX, posY, 140, 28);
        }

        public synchronized void render(Layout layout) {
            GuiGraphics.drawButton(
                    posX,
                    posY,
                    layout.panelX + width,
                    layout.panelY + height,
                    text);
        }
    }

    // Stores GUI text data
    // Everything is public to make it modifiable if needed
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
                          int scale,   // Text scale
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
        // Default scale and color
        public GUIText(String text, int posX, int posY) {
            this(text, posX, posY, 2);
        }
        // Default color
        public GUIText(String text, int posX, int posY, int scale) {
            this(text, posX, posY, scale, 0.93f, 0.93f, 0.93f);
        }

        public synchronized void render(Layout layout) {
            GuiGraphics.drawTextShadowed(text, layout.panelX + posX, layout.panelY + posY, scale, colR, colG, colB);
        }
    }
}
