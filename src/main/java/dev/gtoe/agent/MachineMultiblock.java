package dev.gtoe.agent;

import java.util.Arrays;

/** Class for various multiblock operations. **/
public class MachineMultiblock extends AdvancedBlock {
    public static final int STRUCTURE_CHECK_INTERVAL = 10;
    private final MultiblockStructure rules;
    private int ticksUntilStructureCheck = STRUCTURE_CHECK_INTERVAL;
    private boolean formed;
    private int matchingRotation = -1;

    public MachineMultiblock(Object level, int x, int y, int z, int blockId) {
        super(level, x, y, z, blockId);
        rules = GetMultiblockRulesForBlock(blockId);
    }

    @Override
    public void Tick() {
        if (--ticksUntilStructureCheck == 0) {
            ticksUntilStructureCheck = STRUCTURE_CHECK_INTERVAL;
            matchingRotation = rules == null ? -1 : rules.FindMatchingRotation(level, x, y, z);
            formed = matchingRotation >= 0;
        }
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
