package dev.gtoe.agent;

/* Multiblock structures:
Shared:
" " = Air
"?" = Any block
"-" = Any block including air
"!" = Controller Block
----- Furnace -----
-2-
212
252
111

262
141
535
111

-2-
2!2
252
111

! = Furnace Main Hatch (22)
1 = Hardened Stone (21)
2 = Stone (3)
3 = Furnace Fuel Chamber (25)
4 = Furnace Main Chamber (26)
5 = Furnace Fuel Hatch (24)
6 = Furnace Exhaust (23)
-----
 */

import java.util.HashMap;
import java.util.List;

/** Instance of a multiblock structure that defines what blocks are used in the structure and where. **/
public class MultiblockStructure {
    public int controllerBlockId;
    public List<List<String>> structure;
    public HashMap<String, Integer> blockMappings;
    public int controllerStructureSlice;
    public int controllerStructureRow;
    public int controllerStructureRowIndex;

    public MultiblockStructure(int controllerBlockId,
                               List<List<String>> structure,
                               HashMap<String, Integer> blockMappings) {
        this.controllerBlockId = controllerBlockId;
        this.structure = structure;
        this.blockMappings = blockMappings;

        // Reverse the slices so they start from the controller and go backwards
        Collections.reverse(structure);

        // Find the controller position inside the structure
        boolean controllerFound = false;
        for (List<string> slice : structure) {
            for (List<string> row : slice) {
                for (char block : row.toCharArray()) {
                    if (block == '!') {
                        controllerFound = true;
                        break;
                    }
                    controllerStructureRowIndex += 1;
                }
                if (controllerFound) break;
                controllerStructureRow += 1;
            }
            if (controllerFound) break;
            controllerStructureSlice += 1;
        }
        if (!controllerFound)
            System.err.println("[gtoe] Controller not found in multiblock structure!");
    }

    /** Check if the multiblock can form. **/
    public boolean CheckStructure(Object level,
                                  int x, int y, int z) {  // Controller position
        if (level == null) {
            throw new IllegalArgumentException("Level access is required to check the structure!");
        }

        for (List<string> slice : structure) {
            for (List<string> row : slice) {
                for (char block : row.toCharArray()) {
                    int blockID;
                    if (block == '!') {
                        blockID = controllerBlockId;
                    } else if (block == ' ') {
                        blockID = 0;
                    } else if (block == '?') {
                        blockID = -1;  // Not equal 0
                    } else if (block == '-') {
                        continue;
                    } else {
                        blockID = blockMappings.get(block);
                        if (blockID == null) {
                            System.err.println("[gtoe] Block "+block+" not found in block mappings for multiblock structure, skipping block.");
                            continue;
                        }
                    }

                    // APPLY OFFSET!!!!!
                    if (blockID == -1 && WorldActions.blockIdAt(level, x, y, z) == 0) {
                        return false;
                    } else if (WorldActions.blockIdAt(level, x, y, z) != blockID) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }

    /** Create the block mapping HashMap for a multiblock structure. **/
    public static HashMap<String, Integer> CreateBlockMapping(List<String> symbols, List<Integer> blocks) {
        if(symbols.size() != blocks.size()) {
            System.err.println("[gtoe] Array length mismatch while creating multiblock block mapping!" +
                               "Returning no mapping instead. The arrays were: " +
                               "[" + String.join(", ", symbols) + "], [" + String.join(", ", symbols) + "]");
            return new HashMap<>();
        }
        HashMap<String, Integer> blockMapping = new HashMap<>();
        for (int i = 0; i < symbols.size(); i++) {
            blockMapping.put(symbols.get(i), blocks.get(i));
        }
        return blockMapping;
    }
}
