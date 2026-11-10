package dev.gtoe.agent;

import java.util.*;

/**
    Instance of a multiblock structure that defines what blocks are used in the structure and where.
    Symbol mappings present in every structure mapping:
    " " = Mandatory air block
    "?" = Any block except air
    "-" = Any block including air (skip block)
    "!" = Controller block (must be a signle one per structure)
**/
public class MultiblockStructure {
    /** ID of the controller block of the structure **/
    public int controllerBlockId;
    /** The structure as a 2D string list **/
    public List<List<String>> structure;
    /** The block mappings as char: int **/
    public HashMap<Character, Integer> blockMappings;
    /** The slice the controller block is on **/
    public int controllerStructureSlice;
    /** The row the controller block is on **/
    public int controllerStructureRow;
    /** The row index the controller block is on **/
    public int controllerStructureRowIndex;

    /** Create a multiblock structure instance with the controller block, 2D structure data, and block mappings. **/
    public MultiblockStructure(int controllerBlockId,
                               List<List<String>> structure,
                               HashMap<Character, Integer> blockMappings) {
        if (controllerBlockId <= 0 || !ItemCatalog.isBlock(controllerBlockId)
                || structure == null || structure.isEmpty() || blockMappings == null) {
            throw new IllegalArgumentException("A structure needs a controller, slices and mappings");
        }
        this.controllerBlockId = controllerBlockId;
        this.blockMappings = new HashMap<>(blockMappings);
        this.structure = new ArrayList<>();
        // Copy before reversing: callers may reuse their definition or pass immutable lists.
        for (List<String> slice : structure) {
            if (slice == null || slice.isEmpty()) {
                throw new IllegalArgumentException("Structure slices cannot be empty");
            }
            this.structure.add(new ArrayList<>(slice));
        }
        Collections.reverse(this.structure);

        int rows = this.structure.get(0).size();
        String firstRow = this.structure.get(0).get(0);
        int columns = firstRow == null ? 0 : firstRow.length();
        if (columns == 0) {
            throw new IllegalArgumentException("Structure rows cannot be empty");
        }
        int controllers = 0;
        for (int slice = 0; slice < this.structure.size(); slice++) {
            if (this.structure.get(slice).size() != rows) {
                throw new IllegalArgumentException("All slices must have the same height");
            }
            for (int row = 0; row < rows; row++) {
                String symbols = this.structure.get(slice).get(row);
                if (symbols == null || symbols.length() != columns) {
                    throw new IllegalArgumentException("All structure rows must have the same width");
                }
                for (int column = 0; column < columns; column++) {
                    char symbol = symbols.charAt(column);
                    if (symbol == '!') {
                        controllers++;
                        controllerStructureSlice = slice;
                        controllerStructureRow = row;
                        controllerStructureRowIndex = column;
                    } else if (symbol != ' ' && symbol != '?' && symbol != '-') {
                        Integer id = this.blockMappings.get(symbol);
                        if (id == null || !ItemCatalog.isBlock(id)) {
                            throw new IllegalArgumentException(
                                    "No valid block mapping for structure symbol " + symbol);
                        }
                    }
                }
            }
        }
        if (controllers != 1) {
            throw new IllegalArgumentException("A structure must contain exactly one controller");
        }
    }

    /**
        Try all four horizontal rotations for the structure.
        This tries every horizontal dircetion (North, East, South, West).
        If it fails, it will return -1.
    **/
    public int FindMatchingRotation(Object level, int x, int y, int z) {
        if (level == null) {
            throw new IllegalArgumentException("Level access is required to check the structure!");
        }
        for (int rotation = 0; rotation < 4; rotation++) {
            if (CheckStructure(level, x, y, z, rotation)) {
                return rotation;
            }
        }
        return -1;
    }

    /** Check if the multiblock can form, tries every rotation. **/
    public boolean CheckStructure(Object level,
                                  int x, int y, int z) {  // Controller position
        return FindMatchingRotation(level, x, y, z) >= 0;
    }
    /** Check if the multiblock can form with a specific rotation **/
    public boolean CheckStructure(Object level,
                                  int x, int y, int z,  // Controller position
                                  int rotation) {  // Horizontal rotation integer
        for (int slice = 0; slice < structure.size(); slice++) {
            List<String> rows = structure.get(slice);
            for (int row = 0; row < rows.size(); row++) {
                String symbols = rows.get(row);
                for (int column = 0; column < symbols.length(); column++) {
                    char symbol = symbols.charAt(column);
                    if (symbol == '-') {
                        continue;
                    }
                    int dx = column - controllerStructureRowIndex;
                    int dy = controllerStructureRow - row; // Text rows run from top to bottom.
                    int dz = slice - controllerStructureSlice;
                    int rotatedX;
                    int rotatedZ;
                    switch (rotation) {
                        case 1: rotatedX = -dz; rotatedZ = dx; break;
                        case 2: rotatedX = -dx; rotatedZ = -dz; break;
                        case 3: rotatedX = dz; rotatedZ = -dx; break;
                        default: rotatedX = dx; rotatedZ = dz; break;
                    }
                    try {
                        // A read outside the world or a failed read cannot form a machine.
                        int actual = WorldActions.blockIdAt(level, x + rotatedX, y + dy, z + rotatedZ);
                        if (symbol == '?') {
                            if (actual <= 0) {
                                return false;
                            }
                            continue;
                        }
                        int expected = symbol == '!' ? controllerBlockId
                                : symbol == ' ' ? 0 : blockMappings.get(symbol);
                        if (actual != expected) {
                            return false;
                        }
                    } catch (Exception error) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Create the block mapping HashMap for a multiblock structure. **/
    public static HashMap<Character, Integer> CreateBlockMapping(List<Character> symbols, List<Integer> blocks) {
        if(symbols.size() != blocks.size()) {
            System.err.println("[gtoe] Array length mismatch while creating multiblock block mapping!" +
                               "Returning no mapping instead. The arrays were: " +
                               symbols + ", " + blocks);
            return new HashMap<>();
        }
        HashMap<Character, Integer> blockMapping = new HashMap<>();
        for (int i = 0; i < symbols.size(); i++)
            blockMapping.put(symbols.get(i), blocks.get(i));

        return blockMapping;
    }
}
