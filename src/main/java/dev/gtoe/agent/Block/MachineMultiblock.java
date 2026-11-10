package dev.gtoe.agent;

import java.util.Arrays;
import java.util.Random;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Class for various multiblock operations. **/
public class MachineMultiblock extends AdvancedBlock {
    public static final int STRUCTURE_CHECK_INTERVAL = 10;
    private final MultiblockStructure rules;
    private int ticksUntilStructureCheck = STRUCTURE_CHECK_INTERVAL;
    private boolean formed;
    private int matchingRotation = -1;
    public final MachineSlot input = new MachineSlot();
    public final MachineSlot output = new MachineSlot();
    private final FurnaceFuelHatch[] hatches = new FurnaceFuelHatch[4];
    private final Random random;
    private FurnaceRecipes.Recipe activeRecipe;
    private int storedFuel;
    private int flameMaximum;
    private int progressTicks;
    private int spentFuel;
    private int idleTicks;
    private int retryTicks;
    private String message = "";

    public MachineMultiblock(Object level, int x, int y, int z, int blockId) {
        this(level, x, y, z, blockId, new Random());
    }

    MachineMultiblock(Object level, int x, int y, int z, int blockId, Random random) {
        super(level, x, y, z, blockId);
        rules = GetMultiblockRulesForBlock(blockId);
        this.random = random;
    }

    @Override
    public void Tick() {
        if (--ticksUntilStructureCheck == 0) {
            ticksUntilStructureCheck = STRUCTURE_CHECK_INTERVAL;
            int rotation = rules == null ? -1 : rules.FindMatchingRotation(level, x, y, z);
            if (rotation < 0) {
                invalidateStructure();
            } else {
                if (formed && matchingRotation != rotation) { invalidateStructure(); }
                matchingRotation = rotation;
                formed = true;
                if (!attachHatches()) { invalidateStructure(); }
            }
        }
        if (!formed) { return; }
        if (activeRecipe != null) {
            advanceRecipe();
            return;
        }
        if (retryTicks > 0) { retryTicks--; }
        if (retryTicks == 0) { tryStartRecipe(); }
        if (activeRecipe == null && ++idleTicks >= 20) {
            idleTicks = 0;
            if (storedFuel > 0) { storedFuel--; }
        }
    }

    private boolean attachHatches() {
        BlockTicker ticker = BlockTicker.forLevel(level);
        int[][] positions = fuelHatchPositions();
        for (int index = 0; index < hatches.length; index++) {
            int[] p = positions[index];
            AdvancedBlock candidate = ticker.blockAt(p[0], p[1], p[2]);
            if (!(candidate instanceof FurnaceFuelHatch)) { return false; }
            FurnaceFuelHatch hatch = (FurnaceFuelHatch) candidate;
            if (!hatch.attach(this)) { return false; }
            if (hatches[index] != null && hatches[index] != hatch) { hatches[index].detach(this); }
            hatches[index] = hatch;
        }
        return true;
    }

    /** Front, right, back, left, rotated around the controller with the matched structure. */
    public int[][] fuelHatchPositions() {
        int[][] offsets = {{0, 0}, {1, 1}, {0, 2}, {-1, 1}};
        int[][] result = new int[4][3];
        for (int index = 0; index < offsets.length; index++) {
            int dx = offsets[index][0], dz = offsets[index][1];
            int rx = dx, rz = dz;
            switch (matchingRotation) {
                case 1: rx = -dz; rz = dx; break;
                case 2: rx = -dx; rz = -dz; break;
                case 3: rx = dz; rz = -dx; break;
                default: break;
            }
            result[index] = new int[] {x + rx, y - 1, z + rz};
        }
        return result;
    }

    private void tryStartRecipe() {
        message = "";
        FurnaceRecipes.Recipe recipe = FurnaceRecipes.choose(input.itemID(), random);
        if (recipe == null || input.count() == 0 || !output.canAdd(recipe.resultID, 1)) { return; }

        FurnaceRecipes.Fuel[] fuels = new FurnaceRecipes.Fuel[4];
        long available = storedFuel;
        for (int index = 0; index < 4; index++) {
            fuels[index] = FurnaceRecipes.fuel(hatches[index].input.itemID());
            if (fuels[index] != null) {
                available += (long) fuels[index].fuelValue * hatches[index].input.count();
            }
        }
        if (available < recipe.fuelRequirement) { fail("Insufficient fuel!"); return; }

        int[] consumption = new int[4];
        long plannedFuel = storedFuel;
        // Plan one item per hatch per circuit. Do not partially consume on a failed preflight.
        while (plannedFuel < recipe.fuelRequirement) {
            boolean consumed = false;
            for (int index = 0; index < 4 && plannedFuel < recipe.fuelRequirement; index++) {
                FurnaceRecipes.Fuel fuel = fuels[index];
                if (fuel != null && consumption[index] < hatches[index].input.count()) {
                    consumption[index]++;
                    plannedFuel += fuel.fuelValue;
                    consumed = true;
                }
            }
            if (!consumed || plannedFuel > Integer.MAX_VALUE) { fail("Insufficient fuel!"); return; }
        }
        for (int index = 0; index < 4; index++) {
            FurnaceRecipes.Fuel fuel = fuels[index];
            if (consumption[index] > 0 && fuel.hasByproduct()
                    && !hatches[index].output.canAdd(fuel.resultID, consumption[index])) {
                fail("No fuel output space!");
                return;
            }
        }

        input.remove(1); // Reserve input now; invalidating the structure intentionally voids it.
        for (int index = 0; index < 4; index++) {
            FurnaceRecipes.Fuel fuel = fuels[index];
            for (int item = 0; item < consumption[index]; item++) {
                hatches[index].input.remove(1);
                storedFuel += fuel.fuelValue;
                if (fuel.hasByproduct() && random.nextDouble() < fuel.outputChance) {
                    hatches[index].output.add(fuel.resultID, 1);
                }
            }
        }
        activeRecipe = recipe;
        flameMaximum = storedFuel;
        progressTicks = 0;
        spentFuel = 0;
        idleTicks = 0;
    }

    private void advanceRecipe() {
        if (progressTicks < activeRecipe.time) { progressTicks++; }
        int stages = (int) ((long) progressTicks * 10 / activeRecipe.time);
        int required = activeRecipe.fuelRequirement;
        int due = (required / 10) * stages + Math.min(required % 10, stages);
        storedFuel -= due - spentFuel;
        spentFuel = due;
        // Extraction can temporarily reserve an output slot during dragging; wait without losing output.
        if (progressTicks == activeRecipe.time && output.add(activeRecipe.resultID, 1)) {
            activeRecipe = null;
            progressTicks = 0;
            spentFuel = 0;
        }
    }

    private void fail(String reason) {
        message = reason;
        retryTicks = 40;
    }

    public void invalidateStructure() {
        formed = false;
        matchingRotation = -1;
        activeRecipe = null;
        storedFuel = 0;
        flameMaximum = 0;
        progressTicks = 0;
        spentFuel = 0;
        idleTicks = 0;
        retryTicks = 0;
        message = "";
        for (int index = 0; index < 4; index++) {
            if (hatches[index] != null) { hatches[index].detach(this); hatches[index] = null; }
        }
    }

    /** Recheck on nearby edits as well, so a removed component cannot finish a pending recipe. */
    void onWorldChanged(int changedX, int changedY, int changedZ) {
        if (formed && Math.abs(changedX - x) <= 3 && Math.abs(changedY - y) <= 3
                && Math.abs(changedZ - z) <= 3 && !rules.CheckStructure(level, x, y, z)) {
            invalidateStructure();
        }
    }

    @Override
    public void onRemoved() { invalidateStructure(); }

    public int storedFuel() { return storedFuel; }
    public boolean isProcessing() { return activeRecipe != null; }
    public int progressTicks() { return progressTicks; }
    public double progress() { return activeRecipe == null ? 0.0 : (double) progressTicks / activeRecipe.time; }
    public String status() { return retryTicks > 0 ? message : ""; }

    /** Zero = unlit; phases 1-8 keep any positive integer reserve visibly lit. */
    public int flamePhase() {
        if (storedFuel <= 0) { return 0; }
        return (int) Math.max(1, Math.min(8, ((long) storedFuel * 8 + Math.max(1, flameMaximum) - 1)
                / Math.max(1, flameMaximum)));
    }

    void writeState(DataOutput out) throws IOException {
        out.writeInt(storedFuel);
        out.writeInt(flameMaximum);
        out.writeInt(progressTicks);
        out.writeInt(spentFuel);
        out.writeInt(idleTicks);
        out.writeInt(retryTicks);
        out.writeUTF(message);
        out.writeBoolean(activeRecipe != null);
        if (activeRecipe != null) {
            out.writeInt(activeRecipe.inputID);
            out.writeInt(activeRecipe.fuelRequirement);
            out.writeInt(activeRecipe.time);
            out.writeInt(activeRecipe.resultID);
        }
    }

    void readState(DataInput in) throws IOException {
        storedFuel = in.readInt();
        flameMaximum = in.readInt();
        progressTicks = in.readInt();
        spentFuel = in.readInt();
        idleTicks = in.readInt();
        retryTicks = in.readInt();
        message = in.readUTF();
        activeRecipe = in.readBoolean() ? new FurnaceRecipes.Recipe(
                in.readInt(), in.readInt(), in.readInt(), in.readInt()) : null;
        if (storedFuel < 0 || flameMaximum < storedFuel || progressTicks < 0 || spentFuel < 0
                || idleTicks < 0 || idleTicks >= 20 || retryTicks < 0 || retryTicks > 40
                || (activeRecipe == null && (progressTicks != 0 || spentFuel != 0))) {
            throw new IOException("Invalid furnace state");
        }
        if (activeRecipe != null) {
            int requirement = activeRecipe.fuelRequirement;
            if (activeRecipe.inputID < 0 || activeRecipe.resultID < 0 || requirement < 0
                    || activeRecipe.time <= 0 || progressTicks > activeRecipe.time) {
                throw new IOException("Invalid saved furnace recipe");
            }
            int stages = (int) ((long) progressTicks * 10 / activeRecipe.time);
            int due = (requirement / 10) * stages + Math.min(requirement % 10, stages);
            if (spentFuel != due || storedFuel < requirement - spentFuel) {
                throw new IOException("Inconsistent saved furnace fuel");
            }
        }
    }

    void restoreStateFrom(MachineMultiblock saved) {
        storedFuel = saved.storedFuel;
        flameMaximum = saved.flameMaximum;
        progressTicks = saved.progressTicks;
        spentFuel = saved.spentFuel;
        idleTicks = saved.idleTicks;
        retryTicks = saved.retryTicks;
        message = saved.message;
        activeRecipe = saved.activeRecipe;
    }

    public boolean isFormed() {
        return formed;
    }

    /** Quarter turns clockwise from the pattern's default orientation, or -1 if unformed. */
    public int matchingRotation() {
        return matchingRotation;
    }

    /** Get the multiblock rules for a block ID. **/
    public static MultiblockStructure GetMultiblockRulesForBlock(int blockId) {
        switch (blockId) {
            case 22:
                return new MultiblockStructure(
                        22,
                        Arrays.asList(
                            Arrays.asList("-2-",
                                          "212",
                                          "252",
                                          "111"),
                            Arrays.asList("262",
                                          "141",
                                          "535",
                                          "111"),
                            Arrays.asList("-2-",
                                          "2!2",
                                          "252",
                                          "111")
                        ),
                        MultiblockStructure.CreateBlockMapping(
                                Arrays.asList('1','2','3','4','5','6'),
                                Arrays.asList(21, 3, 25, 26, 24, 23)
                        ));
            default: return null;
        }
    }
}
