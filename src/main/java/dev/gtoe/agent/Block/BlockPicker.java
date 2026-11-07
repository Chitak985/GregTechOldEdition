package dev.gtoe.agent;

import java.lang.reflect.Field;

/** CPU voxel picking. This path performs no OpenGL selection, drawing or readback. */
public final class BlockPicker {
    private static final ClassValue<PlayerAccess> PLAYERS = new ClassValue<PlayerAccess>() {
        @Override
        protected PlayerAccess computeValue(Class<?> type) {
            return new PlayerAccess(type);
        }
    };
    private static final ClassValue<Field[]> BOXES = new ClassValue<Field[]>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            return fields(type, "x0", "y0", "z0", "x1", "y1", "z1");
        }
    };

    private BlockPicker() {
    }

    /** Called by the transformed RubyDung.pick(F)V method. */
    public static Hit pick(Object level, Object player, float partialTick) {
        if (level == null || player == null || GuiManager.isOpen()) {
            return null;
        }
        try {
            PlayerAccess access = PLAYERS.get(player.getClass());
            double x = interpolate(access.positions[0], access.positions[3], player, partialTick);
            double y = interpolate(access.positions[1], access.positions[4], player, partialTick);
            double z = interpolate(access.positions[2], access.positions[5], player, partialTick);
            double pitch = Math.toRadians(access.pitch.getFloat(player));
            double yaw = Math.toRadians(access.yaw.getFloat(player));
            double dx = Math.sin(yaw) * Math.cos(pitch);
            double dy = -Math.sin(pitch);
            double dz = -Math.cos(yaw) * Math.cos(pitch);

            // RubyDung's view translates back 0.3 along the camera's local Z axis.
            // Match the rendered camera, including interpolated position and this offset.
            x -= dx * 0.3;
            y -= dy * 0.3;
            z -= dz * 0.3;

            Object box = access.box.get(player);
            Field[] boxFields = BOXES.get(box.getClass());
            int[] bounds = new int[6];
            for (int axis = 0; axis < 3; axis++) {
                bounds[axis] = (int) Math.floor(boxFields[axis].getFloat(box) - 3.0f);
                bounds[axis + 3] = (int) Math.floor(boxFields[axis + 3].getFloat(box) + 4.0f);
            }
            // Preserve the original candidate region and stop when the ray leaves it.
            double reach = Math.min(exitDistance(x, dx, bounds[0], bounds[3]),
                    Math.min(exitDistance(y, dy, bounds[1], bounds[4]),
                            exitDistance(z, dz, bounds[2], bounds[5])));
            return trace(level, x, y, z, dx, dy, dz, reach, bounds);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read rd-132211 camera for block picking", error);
        }
    }

    static Hit trace(Object level, double x, double y, double z,
            double dx, double dy, double dz, double reach) {
        return trace(level, x, y, z, dx, dy, dz, reach, null);
    }

    private static Hit trace(Object level, double x, double y, double z,
            double dx, double dy, double dz, double reach, int[] bounds) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(length) || length == 0.0
                || !Double.isFinite(reach) || reach < 0.0 || reach > 64.0) {
            return null;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        int blockX = initialCell(x, dx);
        int blockY = initialCell(y, dy);
        int blockZ = initialCell(z, dz);
        int stepX = Double.compare(dx, 0.0);
        int stepY = Double.compare(dy, 0.0);
        int stepZ = Double.compare(dz, 0.0);
        double deltaX = dx == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = dy == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = dz == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double nextX = firstBoundary(x, blockX, dx);
        double nextY = firstBoundary(y, blockY, dy);
        double nextZ = firstBoundary(z, blockZ, dz);
        double distance = 0.0;
        int face = Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz)
                ? (dy > 0 ? 0 : 1)
                : Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 4 : 5) : (dz > 0 ? 2 : 3);
        int maximumSteps = (int) Math.ceil(reach * (Math.abs(dx) + Math.abs(dy) + Math.abs(dz))) + 4;
        for (int steps = 0; steps < maximumSteps && distance <= reach; steps++) {
            if (bounds == null || (blockX >= bounds[0] && blockY >= bounds[1] && blockZ >= bounds[2]
                    && blockX < bounds[3] && blockY < bounds[4] && blockZ < bounds[5])) {
                try {
                    if (WorldActions.blockIdAt(level, blockX, blockY, blockZ) > 0) {
                        return new Hit(blockX, blockY, blockZ, face, distance);
                    }
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot read world for block picking", error);
                }
            }

            double next = Math.min(nextX, Math.min(nextY, nextZ));
            // Advance tied axes together so touching only an edge/corner doesn't hit a side cell.
            boolean crossX = nextX == next;
            boolean crossY = nextY == next;
            boolean crossZ = nextZ == next;
            if (crossX) {
                blockX += stepX;
                nextX += deltaX;
            }
            if (crossY) {
                blockY += stepY;
                nextY += deltaY;
            }
            if (crossZ) {
                blockZ += stepZ;
                nextZ += deltaZ;
            }
            face = crossX ? (stepX > 0 ? 4 : 5)
                    : crossY ? (stepY > 0 ? 0 : 1) : (stepZ > 0 ? 2 : 3);
            distance = next;
        }
        return null;
    }

    private static double firstBoundary(double coordinate, int cell, double direction) {
        if (direction == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return direction > 0 ? (cell + 1.0 - coordinate) / direction : (coordinate - cell) / -direction;
    }

    private static double exitDistance(double coordinate, double direction, int minimum, int maximum) {
        if (direction == 0.0) {
            return coordinate < minimum || coordinate >= maximum ? -1.0 : Double.POSITIVE_INFINITY;
        }
        return (direction > 0 ? maximum - coordinate : minimum - coordinate) / direction;
    }

    private static int initialCell(double coordinate, double direction) {
        int cell = (int) Math.floor(coordinate);
        return direction < 0 && coordinate == cell ? cell - 1 : cell;
    }

    private static float interpolate(Field previous, Field current, Object player, float amount)
            throws IllegalAccessException {
        float old = previous.getFloat(player);
        return old + (current.getFloat(player) - old) * amount;
    }

    private static Field[] fields(Class<?> type, String... names) {
        Field[] result = new Field[names.length];
        try {
            for (int index = 0; index < names.length; index++) {
                result[index] = type.getField(names[index]);
            }
        } catch (NoSuchFieldException error) {
            throw new IllegalStateException("Unexpected rd-132211 field layout", error);
        }
        return result;
    }

    private static final class PlayerAccess {
        final Field[] positions;
        final Field pitch;
        final Field yaw;
        final Field box;

        PlayerAccess(Class<?> type) {
            positions = fields(type, "xo", "yo", "zo", "x", "y", "z");
            Field[] remaining = fields(type, "xRot", "yRot", "bb");
            pitch = remaining[0];
            yaw = remaining[1];
            box = remaining[2];
        }
    }

    public static final class Hit {
        public final int x;
        public final int y;
        public final int z;
        public final int face;
        public final double distance;

        Hit(int x, int y, int z, int face, double distance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
            this.distance = distance;
        }
    }
}
