package net.ronm19.phantomism.client.renderer;

import com.geckolib.cache.model.*;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.renderer.base.*;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.client.renderer.state.PhantomGeoRenderState;
import net.ronm19.phantomism.entity.PhantomKing;

public final class KingCrownLayer extends GeoRenderLayer<PhantomKing, Void, PhantomGeoRenderState> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, "textures/vfx/royal_crown.png");
    private final Map<GeoBone, List<GeoQuad>> meshes = new IdentityHashMap<>(2);
    public KingCrownLayer(GeoRenderer<PhantomKing, Void, PhantomGeoRenderState> renderer) { super(renderer); }
    @Override public void addPerBoneRender(RenderPassInfo<PhantomGeoRenderState> pass, BiConsumer<GeoBone, PerBoneRender<PhantomGeoRenderState>> consumer) {
        if (pass.willRender() && !pass.renderState().isInvisible) { pass.model().getBone("head").filter(CuboidGeoBone.class::isInstance).ifPresent(bone -> consumer.accept(bone, this::render)); }
    }
    private void render(RenderPassInfo<PhantomGeoRenderState> pass, GeoBone bone, SubmitNodeCollector tasks) {
        if (meshes.size() >= 2 && !meshes.containsKey(bone)) { meshes.clear(); }
        List<GeoQuad> quads = meshes.computeIfAbsent(bone, KingCrownLayer::build);
        tasks.submitCustomGeometry(pass.poseStack(), RenderTypes.entityTranslucentEmissive(TEXTURE), (pose, buffer) -> {
            PoseStack stack = new PoseStack(); stack.last().set(pose); bone.translateAwayFromPivotPoint(stack);
            for (GeoQuad quad : quads) { quad.render(stack.last().pose(), stack.last().normal().transform(quad.normalVec()), buffer, 15728880, pass.packedOverlay(), pass.renderColor()); }
        });
    }
    public static List<GeoQuad> build(GeoBone bone) {
        if (!(bone instanceof CuboidGeoBone head) || head.cubes.length == 0) { return List.of(); }
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (GeoQuad quad : head.cubes[0].quads()) { if (quad != null) { for (GeoVertex vertex : quad.vertices()) { minX = Math.min(minX, vertex.posX()); maxX = Math.max(maxX, vertex.posX()); maxY = Math.max(maxY, vertex.posY()); minZ = Math.min(minZ, vertex.posZ()); maxZ = Math.max(maxZ, vertex.posZ()); } } }
        return geometry(minX, maxY, minZ, maxX, maxZ);
    }
    public static List<GeoQuad> geometry(double minX, double top, double minZ, double maxX, double maxZ) {
        List<GeoQuad> quads = new ArrayList<>(); double rim = .035D, base = top + .012D;
        box(quads, minX - .012D, base, minZ - .012D, maxX + .012D, base + rim, minZ + rim);
        box(quads, minX - .012D, base, maxZ - rim, maxX + .012D, base + rim, maxZ + .012D);
        box(quads, minX - .012D, base, minZ, minX + rim, base + rim, maxZ);
        box(quads, maxX - rim, base, minZ, maxX + .012D, base + rim, maxZ);
        for (int point = 0; point < 5; point++) { double across = minX + (maxX - minX) * (point + .5D) / 5; pyramid(quads, across, base + rim, minZ + .012D, .028D, point == 2 ? .17D : point % 2 == 0 ? .13D : .1D); }
        for (int point = 0; point < 3; point++) { double across = minX + (maxX - minX) * (point + .5D) / 3; pyramid(quads, across, base + rim, maxZ - .012D, .03D, .11D); }
        return List.copyOf(quads);
    }
    private static void box(List<GeoQuad> quads, double left, double bottom, double back, double right, double top, double front) {
        Vec3[] points = {new Vec3(left, bottom, back), new Vec3(right, bottom, back), new Vec3(right, top, back), new Vec3(left, top, back), new Vec3(left, bottom, front), new Vec3(right, bottom, front), new Vec3(right, top, front), new Vec3(left, top, front)};
        for (int[] face : new int[][]{{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}}) { quad(quads, points[face[0]], points[face[1]], points[face[2]], points[face[3]]); }
    }
    private static void pyramid(List<GeoQuad> quads, double across, double base, double depth, double half, double height) {
        Vec3 peak = new Vec3(across, base + height, depth); Vec3[] points = {new Vec3(across - half, base, depth - half), new Vec3(across + half, base, depth - half), new Vec3(across + half, base, depth + half), new Vec3(across - half, base, depth + half)};
        for (int side = 0; side < 4; side++) { quad(quads, points[side], peak, peak, points[(side + 1) % 4]); }
    }
    private static void quad(List<GeoQuad> quads, Vec3 first, Vec3 second, Vec3 third, Vec3 fourth) {
        Vec3 normal = second.subtract(first).cross(fourth.subtract(first)).normalize();
        quads.add(new GeoQuad(new GeoVertex[]{new GeoVertex(first.x, first.y, first.z).withUVs(0, 1), new GeoVertex(second.x, second.y, second.z).withUVs(0, 0), new GeoVertex(third.x, third.y, third.z).withUVs(1, 0), new GeoVertex(fourth.x, fourth.y, fourth.z).withUVs(1, 1)}, (float) normal.x, (float) normal.y, (float) normal.z, Direction.UP));
    }
}
