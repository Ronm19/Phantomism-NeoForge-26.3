package net.ronm19.phantomism.client.vfx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;

/** Closed, camera-independent white beacon prisms, with a separate translucent shell. */
public final class CreatorBeaconBeam {
    private CreatorBeaconBeam() {}

    public static List<VfxMesh.Batch> build(Vec3 start, List<Vec3> endpoints, Vec3 camera,
            double time, float strength, boolean precision, int quality, int limit) {
        if (!finite(start) || !finite(camera) || !Double.isFinite(time)
                || !Float.isFinite(strength) || strength <= .001F || limit < 24) { return List.of(); }
        var valid = endpoints.stream().filter(CreatorBeaconBeam::finite)
                .filter(end -> end.distanceToSqr(start) > .000001D).limit(Math.min(64, limit / 24)).toList();
        if (valid.isEmpty()) { return List.of(); }
        int count = valid.size();
        var core = new Vertices(count * 24, camera);
        boolean halo = quality > 0 && limit >= count * 40;
        var glow = new Vertices(halo ? count * 16 : 0, camera);
        double radius = (precision ? .12D : .075D) * Math.sqrt(Math.clamp(strength, 0, 1));
        double rotation = time * Math.PI / 80D; // Same gentle 2.25 degrees/tick as vanilla beacons.
        float scroll = (float)(-time * .02D - Math.floor(-time * .02D));
        for (Vec3 end : valid) {
            Vec3 delta = end.subtract(start), axis = delta.normalize();
            Vec3 reference = Math.abs(axis.y) > .95D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = axis.cross(reference).normalize(), up = right.cross(axis).normalize();
            prism(core, start, end, right, up, radius, rotation, scroll,
                    (float)(delta.length() * .5D / radius), 1F, true);
            if (halo) {
                prism(glow, start, end, right, up, radius * 2.35D, 0, scroll,
                        (float)delta.length(), Math.clamp(strength, 0, 1) * .17F, false);
            }
        }
        var result = new ArrayList<VfxMesh.Batch>();
        result.add(core.batch(1));
        if (halo) { result.add(glow.batch(2)); }
        return List.copyOf(result);
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static void prism(Vertices mesh, Vec3 start, Vec3 end, Vec3 right, Vec3 up,
            double radius, double rotation, float scroll, float repeats, float opacity, boolean caps) {
        var near = new Vec3[4]; var far = new Vec3[4];
        for (int side = 0; side < 4; side++) {
            double angle = rotation + side * Math.PI / 2D;
            Vec3 corner = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            near[side] = start.add(corner); far[side] = end.add(corner);
        }
        int color = ((int)(Math.clamp(opacity, 0, 1) * 255) << 24) | 0xFFFFFF;
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) % 4;
            mesh.vertex(near[side], 0, scroll, color); mesh.vertex(far[side], 0, scroll + repeats, color);
            mesh.vertex(far[next], 1, scroll + repeats, color); mesh.vertex(near[next], 1, scroll, color);
        }
        if (caps) {
            mesh.vertex(near[0], 0, 0, color); mesh.vertex(near[1], 1, 0, color);
            mesh.vertex(near[2], 1, 1, color); mesh.vertex(near[3], 0, 1, color);
            mesh.vertex(far[3], 0, 0, color); mesh.vertex(far[2], 1, 0, color);
            mesh.vertex(far[1], 1, 1, color); mesh.vertex(far[0], 0, 1, color);
        }
    }

    private static final class Vertices {
        private final float[] positions; private final int[] colors; private final Vec3 camera;
        private int count;
        Vertices(int capacity, Vec3 camera) { positions = new float[capacity * 5]; colors = new int[capacity]; this.camera = camera; }
        void vertex(Vec3 point, float u, float v, int color) {
            Vec3 relative = point.subtract(camera); int offset = count * 5;
            positions[offset] = (float)relative.x; positions[offset + 1] = (float)relative.y; positions[offset + 2] = (float)relative.z;
            positions[offset + 3] = u; positions[offset + 4] = v; colors[count++] = color;
        }
        VfxMesh.Batch batch(int layer) {
            return new VfxMesh.Batch(BeaconRenderer.BEAM_LOCATION, Arrays.copyOf(positions, count * 5),
                    Arrays.copyOf(colors, count), List.of(), camera, 1, BeaconRenderer.BEAM_LOCATION, 0xFFFFFF, layer);
        }
    }
}
