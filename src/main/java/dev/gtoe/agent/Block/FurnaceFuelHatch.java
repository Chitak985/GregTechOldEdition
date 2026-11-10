package dev.gtoe.agent;

/** Persistent per-position fuel slots; the controller owns shared heat and all processing. */
public final class FurnaceFuelHatch extends AdvancedBlock {
    public final MachineSlot input = new MachineSlot();
    public final MachineSlot output = new MachineSlot();
    private MachineMultiblock controller;

    public FurnaceFuelHatch(Object level, int x, int y, int z, int blockId) {
        super(level, x, y, z, blockId);
    }

    boolean attach(MachineMultiblock machine) {
        if (controller != null && controller != machine && controller.isFormed()) { return false; }
        controller = machine;
        return true;
    }

    void detach(MachineMultiblock machine) {
        if (controller == machine) { controller = null; }
    }

    public MachineMultiblock controller() {
        return controller != null && controller.isFormed() ? controller : null;
    }

    public int flamePhase() {
        MachineMultiblock machine = controller();
        return machine == null ? 0 : machine.flamePhase();
    }

    @Override
    public void onRemoved() {
        if (controller != null) { controller.invalidateStructure(); }
    }
}
