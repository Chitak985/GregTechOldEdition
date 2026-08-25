package dev.gtoe.agent;

import java.util.Map;
import java.util.TreeMap;

/** Process-wide player inventory, accessible to every transformed hook and GUI. */
public final class Inventory {
    private static final TreeMap<Integer, Integer> COUNTS = new TreeMap<Integer, Integer>();

    private Inventory() {
    }

    public static synchronized void add(int itemId, int amount) {
        requireValid(itemId, amount);
        Integer current = COUNTS.get(Integer.valueOf(itemId));
        int count = current == null ? 0 : current.intValue();
        COUNTS.put(Integer.valueOf(itemId), Integer.valueOf(count + amount));
    }

    public static synchronized boolean remove(int itemId, int amount) {
        requireValid(itemId, amount);
        Integer current = COUNTS.get(Integer.valueOf(itemId));
        if (current == null || current.intValue() < amount) {
            return false;
        }

        int remaining = current.intValue() - amount;
        if (remaining == 0) {
            COUNTS.remove(Integer.valueOf(itemId));
        } else {
            COUNTS.put(Integer.valueOf(itemId), Integer.valueOf(remaining));
        }
        return true;
    }

    public static synchronized boolean contains(int itemId, int amount) {
        if (itemId < 0 || amount <= 0) {
            return false;
        }
        Integer current = COUNTS.get(Integer.valueOf(itemId));
        return current != null && current.intValue() >= amount;
    }

    public static synchronized int count(int itemId) {
        Integer count = COUNTS.get(Integer.valueOf(itemId));
        return count == null ? 0 : count.intValue();
    }

    /** Returns occupied item IDs in stable numeric order for inventory screens. */
    public static synchronized int[] itemIds() {
        int[] itemIds = new int[COUNTS.size()];
        int index = 0;
        for (Map.Entry<Integer, Integer> entry : COUNTS.entrySet()) {
            if (entry.getValue().intValue() > 0) {
                itemIds[index++] = entry.getKey().intValue();
            }
        }
        if (index == itemIds.length) {
            return itemIds;
        }
        int[] compact = new int[index];
        System.arraycopy(itemIds, 0, compact, 0, index);
        return compact;
    }

    static synchronized void clearForTests() {
        COUNTS.clear();
    }

    private static void requireValid(int itemId, int amount) {
        if (itemId < 0) {
            throw new IllegalArgumentException("Item IDs cannot be negative: " + itemId);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Inventory amount must be positive: " + amount);
        }
    }
}
