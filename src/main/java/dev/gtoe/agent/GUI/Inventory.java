package dev.gtoe.agent;

import java.util.Arrays;

/**
 * A fixed-slot inventory shared by world actions and GUI screens.
 *
 * <p>The first 90 slots form the scrollable 10-column inventory. The last
 * nine slots form the always-visible hotbar.</p>
 */
public final class Inventory {
    public static final int EMPTY_ITEM_ID = -1;
    public static final int MAIN_COLUMNS = 10;
    public static final int MAIN_ROWS = 9;
    public static final int MAIN_SLOT_COUNT = MAIN_COLUMNS * MAIN_ROWS;
    public static final int HOTBAR_SLOT_COUNT = 9;
    public static final int HOTBAR_START = MAIN_SLOT_COUNT;
    public static final int TOTAL_SLOT_COUNT = MAIN_SLOT_COUNT + HOTBAR_SLOT_COUNT;

    private static final int[] ITEM_IDS = new int[TOTAL_SLOT_COUNT];
    private static final int[] COUNTS = new int[TOTAL_SLOT_COUNT];

    static {
        Arrays.fill(ITEM_IDS, EMPTY_ITEM_ID);
    }

    private Inventory() {
    }

    public static synchronized boolean add(int itemId, int amount) {
        validateItem(itemId, amount);

        int existingSlot = findItem(itemId);
        if (existingSlot >= 0) {
            COUNTS[existingSlot] += amount;
            return true;
        }

        int emptySlot = findEmpty(HOTBAR_START, TOTAL_SLOT_COUNT);
        if (emptySlot < 0) {
            emptySlot = findEmpty(0, MAIN_SLOT_COUNT);
        }
        if (emptySlot < 0) {
            return false;
        }

        ITEM_IDS[emptySlot] = itemId;
        COUNTS[emptySlot] = amount;
        return true;
    }

    /** Add some amount of the provided item to a specific slot in the inventory. */
    public static synchronized boolean addToSlot(int slot, int itemId, int amount) {
        validateSlot(slot);
        validateItem(itemId, amount);
        if (ITEM_IDS[slot] != EMPTY_ITEM_ID && ITEM_IDS[slot] != itemId) {
            return false;
        }

        ITEM_IDS[slot] = itemId;
        COUNTS[slot] += amount;
        return true;
    }

    /** Remove some amount of the provided item from inventory. */
    public static synchronized boolean remove(int itemId, int amount) {
        validateItem(itemId, amount);
        if (!contains(itemId, amount)) {
            return false;
        }

        int remaining = amount;
        for (int slot = 0; slot < TOTAL_SLOT_COUNT && remaining > 0; slot++) {
            if (ITEM_IDS[slot] != itemId) {
                continue;
            }
            int removed = Math.min(COUNTS[slot], remaining);
            COUNTS[slot] -= removed;
            remaining -= removed;
            clearIfEmpty(slot);
        }
        return true;
    }

    /** Remove an amount of a specific item from an inventory slot. */
    public static synchronized boolean removeFromSlot(int slot, int itemId, int amount) {
        validateSlot(slot);
        validateItem(itemId, amount);
        if (ITEM_IDS[slot] != itemId || COUNTS[slot] < amount) {
            return false;
        }

        COUNTS[slot] -= amount;
        clearIfEmpty(slot);
        return true;
    }

    /** Get the item ID at the provided slot. */
    public static synchronized int itemIdAt(int slot) {
        validateSlot(slot);
        return ITEM_IDS[slot];
    }

    /** Get the item amount at the provided slot. */
    public static synchronized int countAt(int slot) {
        validateSlot(slot);
        return COUNTS[slot];
    }

    /** Check if the slot is empty. */
    public static synchronized boolean isEmpty(int slot) {
        validateSlot(slot);
        return ITEM_IDS[slot] == EMPTY_ITEM_ID;
    }

    /** Count the total amount of an item ID in the inventory. */
    public static synchronized int count(int itemId) {
        int total = 0;
        for (int slot = 0; slot < TOTAL_SLOT_COUNT; slot++)
            if (ITEM_IDS[slot] == itemId)
                total += COUNTS[slot];
        return total;
    }

    /** Check if the inventory contains that much of the provided item. */
    public static synchronized boolean contains(int itemId, int amount) {
        return amount > 0 && count(itemId) >= amount;
    }
    /** Check if the inventory contains at least one of the provided item. */
    public static synchronized boolean contains(int itemId) {
        return count(itemId) >= 1;
    }

    /** Take a full stack of an item from an inventory slot. */
    public static synchronized Stack takeStack(int slot) {
        validateSlot(slot);
        if (ITEM_IDS[slot] == EMPTY_ITEM_ID) {
            return null;
        }

        Stack stack = new Stack(ITEM_IDS[slot], COUNTS[slot]);
        ITEM_IDS[slot] = EMPTY_ITEM_ID;
        COUNTS[slot] = 0;
        return stack;
    }

    /**
     * Places a complete stack into a slot. Equal stacks merge; a different
     * existing stack is returned to the caller so it can be swapped back.
     */
    public static synchronized Stack placeStack(int slot, Stack incoming) {
        validateSlot(slot);
        if (incoming == null) {
            return null;
        }
        if (ITEM_IDS[slot] == EMPTY_ITEM_ID) {
            ITEM_IDS[slot] = incoming.itemId;
            COUNTS[slot] = incoming.count;
            return null;
        }
        if (ITEM_IDS[slot] == incoming.itemId) {
            COUNTS[slot] += incoming.count;
            return null;
        }

        Stack displaced = new Stack(ITEM_IDS[slot], COUNTS[slot]);
        ITEM_IDS[slot] = incoming.itemId;
        COUNTS[slot] = incoming.count;
        return displaced;
    }

    /** Swap two inventory slots between each other. */
    public static synchronized void swapSlots(int first, int second) {
        validateSlot(first);
        validateSlot(second);
        int itemId = ITEM_IDS[first];
        int count = COUNTS[first];
        ITEM_IDS[first] = ITEM_IDS[second];
        COUNTS[first] = COUNTS[second];
        ITEM_IDS[second] = itemId;
        COUNTS[second] = count;
    }

    static synchronized void clearForTests() {
        Arrays.fill(ITEM_IDS, EMPTY_ITEM_ID);
        Arrays.fill(COUNTS, 0);
    }

    private static int findItem(int itemId) {
        for (int slot = 0; slot < TOTAL_SLOT_COUNT; slot++) {
            if (ITEM_IDS[slot] == itemId) {
                return slot;
            }
        }
        return -1;
    }

    private static int findEmpty(int start, int end) {
        for (int slot = start; slot < end; slot++) {
            if (ITEM_IDS[slot] == EMPTY_ITEM_ID) {
                return slot;
            }
        }
        return -1;
    }

    private static void clearIfEmpty(int slot) {
        if (COUNTS[slot] == 0) {
            ITEM_IDS[slot] = EMPTY_ITEM_ID;
        }
    }

    private static void validateSlot(int slot) {
        if (slot < 0 || slot >= TOTAL_SLOT_COUNT) {
            throw new IllegalArgumentException("Inventory slot out of range: " + slot);
        }
    }

    private static void validateItem(int itemId, int amount) {
        if (itemId < 0) {
            throw new IllegalArgumentException("Item ID must not be negative: " + itemId);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
    }

    public static final class Stack {
        public final int itemId;
        public final int count;

        public Stack(int itemId, int count) {
            validateItem(itemId, count);
            this.itemId = itemId;
            this.count = count;
        }
    }
}
