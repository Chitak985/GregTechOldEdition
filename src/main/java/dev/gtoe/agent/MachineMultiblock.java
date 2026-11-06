package dev.gtoe.agent;

import java.util.Arrays;

/** Class for various multiblock operations. **/
public class MachineMultiblock {
    private MachineMultiblock() {
    }

    /** Get the multiblock rules for a block ID. **/
    public MultiblockStructure GetMultiblockRulesForBlock(int blockId) {
        switch (blockId) {
            case 0:
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
                                Arrays.asList("1","2","3","4","5","6"),
                                Arrays.asList(21, 3, 25, 26, 24, 23)
                        ));
            default: return null;
        }
    }
}
