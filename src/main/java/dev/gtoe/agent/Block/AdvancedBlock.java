package dev.gtoe.agent;

/** Runtime state for one placed block. Override Tick() for custom behavior. */
public class AdvancedBlock {
    public final Object level;
    public final int x;
    public final int y;
    public final int z;
    public final int blockId;

    public AdvancedBlock(Object level, int x, int y, int z, int blockId) {
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
    }

    /** Called once per block tick (20 times per simulation second). */
    public void Tick() {
    }
}
