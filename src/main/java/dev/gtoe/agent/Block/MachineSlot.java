package dev.gtoe.agent;

/** One hatch slot. Machine stacks hold up to 64 items; output insertion is GUI-restricted. */
public final class MachineSlot {
    public static final int CAPACITY = 64;
    private int itemID = -1;
    private int amount;
    private boolean dragged;
    private Inventory.Stack dragReservation;

    public int itemID() { return itemID; }
    public int count() { return amount; }

    public boolean canAdd(int id, int count) {
        return !dragged && id >= 0 && count >= 0 && (amount == 0 || itemID == id)
                && count <= CAPACITY - amount;
    }

    public boolean add(int id, int count) {
        if (count <= 0 || !canAdd(id, count)) {
            return false;
        }
        itemID = id;
        amount += count;
        return true;
    }

    public boolean remove(int count) {
        if (dragged || count <= 0 || count > amount) {
            return false;
        }
        amount -= count;
        if (amount == 0) { itemID = -1; }
        return true;
    }

    public Inventory.Stack takeForDrag() {
        if (amount == 0 || dragged) { return null; }
        Inventory.Stack stack = new Inventory.Stack(itemID, amount);
        itemID = -1;
        amount = 0;
        dragged = true; // Protect the space until a drag is committed or returned.
        dragReservation = stack;
        return stack;
    }

    public void finishDrag(Inventory.Stack remainder) {
        dragged = false;
        dragReservation = null;
        if (remainder != null && !add(remainder.itemId, remainder.count)) {
            throw new IllegalStateException("Cannot restore a reserved hatch slot");
        }
    }

    Inventory.Stack snapshot() {
        return dragged ? dragReservation : amount == 0 ? null : new Inventory.Stack(itemID, amount);
    }

    void restore(Inventory.Stack stack) {
        itemID = stack == null ? -1 : stack.itemId;
        amount = stack == null ? 0 : stack.count;
        dragged = false;
        dragReservation = null;
    }
}
