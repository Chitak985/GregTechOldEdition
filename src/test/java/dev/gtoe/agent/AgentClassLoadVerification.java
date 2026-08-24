package dev.gtoe.agent;

/** Loads transformed game classes without initializing LWJGL or opening the game window. */
public final class AgentClassLoadVerification {
    private AgentClassLoadVerification() {
    }

    public static void main(String[] arguments) throws Exception {
        ClassLoader loader = AgentClassLoadVerification.class.getClassLoader();
        String[] classes = {
            "com.mojang.rubydung.RubyDung",
            "com.mojang.rubydung.level.Level",
            "com.mojang.rubydung.level.Tile",
            "com.mojang.rubydung.level.Chunk"
        };

        for (String className : classes) {
            Class.forName(className, false, loader);
        }

        System.out.println("Agent class-load verification passed for all transformed game classes");
    }
}
