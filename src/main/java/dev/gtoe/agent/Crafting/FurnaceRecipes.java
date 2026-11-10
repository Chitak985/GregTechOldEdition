package dev.gtoe.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Add furnace recipes and fuels here. Times are ticks and probabilities are 0.0-1.0. */
public final class FurnaceRecipes {
    private static final Map<Integer, List<Recipe>> RECIPES = new HashMap<>();
    private static final Map<Integer, Fuel> FUELS = new HashMap<>();

    static {
        registerFurnaceFuel(9, 10, -1, 0.0); // Wood fuel.

        registerFurnaceRecipe(21, 10, 100, 29); // Hardened Stone -> Tempered Stone.
    }

    private FurnaceRecipes() {
    }

    public static synchronized void registerFurnaceRecipe(
            int inputID, int fuelRequirement, int time, int resultID) {
        if (inputID < 0 || resultID < 0 || fuelRequirement < 0 || time <= 0) {
            throw new IllegalArgumentException("Invalid furnace recipe: use nonnegative IDs/fuel and positive ticks");
        }
        RECIPES.computeIfAbsent(inputID, ignored -> new ArrayList<>())
                .add(new Recipe(inputID, fuelRequirement, time, resultID));
    }

    public static synchronized void registerFurnaceFuel(
            int id, int fuelValue, int resultID, double outputChance) {
        if (id < 0 || fuelValue <= 0 || resultID < -1 || !Double.isFinite(outputChance)
                || outputChance < 0.0 || outputChance > 1.0) {
            throw new IllegalArgumentException("Invalid furnace fuel: chance must be 0.0-1.0; -1 means no byproduct");
        }
        FUELS.put(id, new Fuel(id, fuelValue, resultID, outputChance));
    }

    static synchronized Recipe choose(int inputID, Random random) {
        List<Recipe> matches = RECIPES.get(inputID);
        return matches == null || matches.isEmpty() ? null : matches.get(random.nextInt(matches.size()));
    }

    public static synchronized Fuel fuel(int itemID) {
        return FUELS.get(itemID);
    }

    public static final class Recipe {
        public final int inputID, fuelRequirement, time, resultID;

        Recipe(int inputID, int fuelRequirement, int time, int resultID) {
            this.inputID = inputID;
            this.fuelRequirement = fuelRequirement;
            this.time = time;
            this.resultID = resultID;
        }
    }

    public static final class Fuel {
        public final int id, fuelValue, resultID;
        public final double outputChance;

        private Fuel(int id, int fuelValue, int resultID, double outputChance) {
            this.id = id;
            this.fuelValue = fuelValue;
            this.resultID = resultID;
            this.outputChance = outputChance;
        }

        public boolean hasByproduct() {
            return resultID >= 0 && outputChance > 0.0;
        }
    }
}
