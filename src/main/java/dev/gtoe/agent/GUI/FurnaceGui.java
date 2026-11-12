package dev.gtoe.agent;

/** Position-bound hatch GUI and drag/drop controller, independent of crafting-grid state. */
final class FurnaceGui {
    private static AdvancedBlock hatch;
    private static Inventory.Stack held;
    private static MachineSlot sourceMachine;
    private static int sourcePlayer = -1;
    private static int scrollRow;
    private static int mouseX, mouseY;

    private FurnaceGui() {
    }

    static void open(AdvancedBlock block) {
        close();
        hatch = block;
        scrollRow = 0;
    }

    static boolean valid() {
        return hatch != null && BlockTicker.forLevel(hatch.level).blockAt(hatch.x, hatch.y, hatch.z) == hatch;
    }

    static void close() {
        returnHeld();
        hatch = null;
    }

    static void snapshotPlayerDrag(Inventory.Stack[] inventory) {
        if (held != null && sourcePlayer >= 0) {
            inventory[sourcePlayer] = new Inventory.Stack(held.itemId, held.count);
        }
        // A drag from a machine is already included in MachineSlot's save reservation.
    }

    private static MachineSlot input() {
        return hatch instanceof MachineMultiblock ? ((MachineMultiblock) hatch).input
                : ((FurnaceFuelHatch) hatch).input;
    }

    private static MachineSlot output() {
        return hatch instanceof MachineMultiblock ? ((MachineMultiblock) hatch).output
                : ((FurnaceFuelHatch) hatch).output;
    }

    static void handleMouse(int x, int y, int button, boolean pressed, int wheel, GuiManager.Layout layout) {
        mouseX = x;
        mouseY = y;
        if (wheel != 0) {
            scrollRow = Math.max(0, Math.min(Inventory.MAIN_ROWS - 3, scrollRow + (wheel < 0 ? 1 : -1)));
        }
        if (button != 0) { return; }
        MachineSlot target = slotAt(x, y, layout);
        int playerSlot = layout.inventorySlotAt(x, y, scrollRow);
        if (pressed && held == null) {
            if (target != null) {
                held = target.takeForDrag();
                if (held != null) { sourceMachine = target; }
            } else if (playerSlot >= 0) {
                held = Inventory.takeStack(playerSlot);
                if (held != null) { sourcePlayer = playerSlot; }
            }
        } else if (!pressed && held != null) {
            if (target == sourceMachine && sourceMachine != null) {
                returnHeld();
            } else if (target == input()) {
                int amount = target.itemID() < 0 || target.itemID() == held.itemId
                        ? Math.min(held.count, MachineSlot.CAPACITY - target.count()) : 0;
                if (amount > 0 && target.add(held.itemId, amount)) {
                    int remainder = held.count - amount;
                    held = remainder == 0 ? null : new Inventory.Stack(held.itemId, remainder);
                }
                returnHeld();
            } else if (playerSlot >= 0) {
                // Machine extractions only merge into compatible slots; player-to-player drags can swap.
                int targetId = Inventory.itemIdAt(playerSlot);
                if (sourceMachine != null && targetId >= 0 && targetId != held.itemId) {
                    returnHeld();
                    return;
                }
                Inventory.Stack displaced = Inventory.placeStack(playerSlot, held);
                held = displaced;
                returnHeld();
            } else {
                // Output slots reject insertion, including a different output slot.
                returnHeld();
            }
        }
    }

    private static void returnHeld() {
        if (sourceMachine != null) {
            sourceMachine.finishDrag(held);
        } else if (held != null && sourcePlayer >= 0) {
            Inventory.Stack displaced = Inventory.placeStack(sourcePlayer, held);
            if (displaced != null) { throw new IllegalStateException("Player drag source was unexpectedly occupied"); }
        }
        held = null;
        sourceMachine = null;
        sourcePlayer = -1;
    }

    private static MachineSlot slotAt(int x, int y, GuiManager.Layout layout) {
        int[] first = slotPosition(layout, false);
        int[] second = slotPosition(layout, true);
        if (contains(x, y, first[0], first[1], 30, 30)) { return input(); }
        if (contains(x, y, second[0], second[1], 30, 30)) { return output(); }
        return null;
    }

    private static boolean contains(int x, int y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    static int[] slotPosition(GuiManager.Layout layout, boolean result) {
        if (hatch instanceof MachineMultiblock) {
            return new int[] {layout.panelX + (result ? 202 : 68), layout.panelY + 80};
        }
        return new int[] {layout.panelX + 135, layout.panelY + (result ? 112 : 72)};
    }

    static void render(GuiManager.Layout layout) {
        boolean main = hatch instanceof MachineMultiblock;
        GuiGraphics.drawTextNormal(main ? "FURNACE" : "FUEL HATCH", layout.panelX + 16, layout.panelY + 14);
        if (main) {
            MachineMultiblock machine = (MachineMultiblock) hatch;
            int labelX = layout.panelX + 108;
            GuiGraphics.drawTextSmall("Formed: ", labelX, layout.panelY + 51);
            GuiGraphics.drawTextShadowed(machine.isFormed() ? "Yes" : "No",
                    labelX + 48, layout.panelY + 51, 1,
                    machine.isFormed() ? 0.25f : 1.0f, machine.isFormed() ? 1.0f : 0.25f, 0.25f);
            drawArrow(layout.panelX + 121, layout.panelY + 87, machine.progress());
            String message = machine.status();
            GuiGraphics.drawTextShadowed(message,
                    layout.panelX + (layout.panelWidth - GuiGraphics.textWidth(message, 1)) / 2,
                    layout.panelY + 130, 1, 1.0f, 0.35f, 0.25f);
            GuiGraphics.drawTextSmall("INPUT", layout.panelX + 68, layout.panelY + 69);
            GuiGraphics.drawTextSmall("OUTPUT", layout.panelX + 199, layout.panelY + 69);
            GuiGraphics.drawTextSmall("FUEL " + machine.storedFuel(), layout.panelX + 16, layout.panelY + 151);
        } else {
            FurnaceFuelHatch fuel = (FurnaceFuelHatch) hatch;
            drawFlame(layout.panelX + 141, layout.panelY + 40, fuel.flamePhase());
            MachineMultiblock controller = fuel.controller();
            GuiGraphics.drawTextSmall(controller != null && controller.isProcessing() ? "PROCESSING" : "IDLE",
                    layout.panelX + 16, layout.panelY + 151);
            GuiGraphics.drawTextSmall("OUTPUT", layout.panelX + 171, layout.panelY + 124);
        }
        drawSlot(input(), slotPosition(layout, false));
        drawSlot(output(), slotPosition(layout, true));
        GuiManager.renderInventory(layout, scrollRow);
        if (held != null) {
            GuiGraphics.drawSlot(mouseX - 11, mouseY - 11, 22, true);
            GuiGraphics.drawItemIcon(held.itemId, held.count, mouseX - 11, mouseY - 11, 22);
        }
    }

    private static void drawSlot(MachineSlot slot, int[] position) {
        GuiGraphics.drawSlot(position[0], position[1], 30, false);
        if (slot.count() > 0) {
            GuiGraphics.drawItemIcon(slot.itemID(), slot.count(), position[0], position[1], 30);
        }
    }

    private static void drawArrow(int x, int y, double progress) {
        int fillWidth = (int) Math.round(58 * Math.max(0.0, Math.min(1.0, progress)));
        for (int row = 0; row < 9; row++) {
            int end = 58 - Math.abs(row - 4) * 2;
            int start = row >= 2 && row <= 6 ? 0 : 40;
            GuiGraphics.fillRect(x + start, y + row * 2, end - start, 2, 0.16f, 0.16f, 0.18f, 1);
            if (fillWidth > start) {
                GuiGraphics.fillRect(x + start, y + row * 2, Math.min(end, fillWidth) - start,
                        2, 0.95f, 0.70f, 0.17f, 1);
            }
        }
    }

    private static void drawFlame(int x, int y, int phase) {
        String[] pixels = {"   X   ", "   XX  ", "  XXX  ", " XXXXXX", "XXXXXXX", "XXXOXXX", "XXOOOXX", " XOOOX ", "  XXX  "};
        float brightness = phase == 0 ? 0.0f : 0.2f + phase * 0.1f;
        for (int row = 0; row < pixels.length; row++) {
            for (int column = 0; column < pixels[row].length(); column++) {
                char pixel = pixels[row].charAt(column);
                if (pixel != ' ') {
                    GuiGraphics.fillRect(x + column * 3, y + row * 3, 3, 3,
                            phase == 0 ? 0.18f : brightness,
                            phase == 0 ? 0.18f : brightness * (pixel == 'O' ? 0.85f : 0.38f),
                            phase == 0 ? 0.19f : 0.05f, 1);
                }
            }
        }
    }
}
