package dev.gtoe.agent;

import java.lang.instrument.Instrumentation;

/** Entry point loaded by the JVM before Minecraft starts. */
public final class GtoeAgent {
    private GtoeAgent() {
    }

    public static void premain(String agentArguments, Instrumentation instrumentation) {
        TerrainLayers.printConfiguration();
        instrumentation.addTransformer(new GtoeTransformer(), false);
        System.out.println("[gtoe] Transformer installed for RubyDung, Level, Tile, and Chunk");
    }
}
