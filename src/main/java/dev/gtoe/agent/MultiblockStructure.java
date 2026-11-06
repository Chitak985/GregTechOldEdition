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

    public MultiblockStructure(int controllerBlockId,
                               List<List<String>> structure,
                               HashMap<String, Integer> blockMappings) {
        this.controllerBlockId = controllerBlockId;
        this.structure = structure;
        this.blockMappings = blockMappings;
    }

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
