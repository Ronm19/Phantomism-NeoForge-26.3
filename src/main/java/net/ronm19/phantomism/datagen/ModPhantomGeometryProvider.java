package net.ronm19.phantomism.datagen;

import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.loading.definition.geometry.GeometryUvPair;
import com.geckolib.loading.definition.geometry.object.GeometryQuadUvs;
import com.geckolib.loading.definition.geometry.object.UvFaceRotation;
import com.geckolib.loading.definition.geometry.object.VertexSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.world.phys.Vec3;
import net.ronm19.phantomism.client.model.PhantomismAdultModel;
import net.ronm19.phantomism.client.model.PhantomismBabyModel;

public final class ModPhantomGeometryProvider implements DataProvider {
    private final PackOutput output;

    public ModPhantomGeometryProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var folder = this.output.getOutputFolder().resolve("assets/phantomism/geckolib/models/entity");
        return CompletableFuture.allOf(
                DataProvider.saveStable(cache, convert(PhantomismAdultModel.createBodyLayer(), "phantom_adult"), folder.resolve("phantom_adult.geo.json")),
                DataProvider.saveStable(cache, convert(PhantomismBabyModel.createBodyLayer(), "phantom_baby"), folder.resolve("phantom_baby.geo.json")));
    }

    public static JsonObject convert(LayerDefinition layer, String name) {
        ModelPart root = layer.bakeRoot();
        Map<String, JsonObject> bones = new LinkedHashMap<>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            Vec3 pivot = ensureBones(root, path, bones);
            bones.get(path).getAsJsonArray("cubes").add(convertCube(cube, pivot));
        });
        JsonObject description = new JsonObject();
        description.addProperty("identifier", "geometry.phantomism." + name);
        description.addProperty("texture_width", 64);
        description.addProperty("texture_height", 64);
        description.addProperty("visible_bounds_width", 4);
        description.addProperty("visible_bounds_height", 3);
        description.add("visible_bounds_offset", vector(0, 0.75D, 0));
        JsonObject definition = new JsonObject();
        definition.add("description", description);
        JsonArray boneList = new JsonArray();
        bones.values().forEach(boneList::add);
        definition.add("bones", boneList);
        JsonArray definitions = new JsonArray();
        definitions.add(definition);
        JsonObject geometry = new JsonObject();
        geometry.addProperty("format_version", "1.12.0");
        geometry.add("minecraft:geometry", definitions);
        return geometry;
    }

    private static Vec3 ensureBones(ModelPart root, String path, Map<String, JsonObject> bones) {
        ModelPart part = root;
        Vec3 pivot = Vec3.ZERO;
        String parent = null;
        String current = "";
        for (String name : path.split("/")) {
            if (name.isEmpty()) {
                continue;
            }
            part = part.getChild(name);
            PartPose pose = part.getInitialPose();
            pivot = pivot.add(pose.x(), pose.y(), pose.z());
            current += "/" + name;
            if (!bones.containsKey(current)) {
                JsonObject bone = new JsonObject();
                bone.addProperty("name", name);
                if (parent != null) {
                    bone.addProperty("parent", parent);
                }
                bone.add("pivot", vector(pivot.x, 24.0D - pivot.y, pivot.z));
                bone.add("rotation", vector(Math.toDegrees(pose.xRot()), Math.toDegrees(pose.yRot()), Math.toDegrees(pose.zRot())));
                bone.add("cubes", new JsonArray());
                bones.put(current, bone);
            }
            parent = name;
        }
        return pivot;
    }

    private static JsonObject convertCube(ModelPart.Cube cube, Vec3 pivot) {
        Vec3 minimum = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 maximum = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (ModelPart.Polygon polygon : cube.polygons) {
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                minimum = new Vec3(Math.min(minimum.x, vertex.x()), Math.min(minimum.y, vertex.y()), Math.min(minimum.z, vertex.z()));
                maximum = new Vec3(Math.max(maximum.x, vertex.x()), Math.max(maximum.y, vertex.y()), Math.max(maximum.z, vertex.z()));
            }
        }
        Vec3 size = maximum.subtract(minimum);
        Vec3 origin = new Vec3(pivot.x + minimum.x, 24.0D - pivot.y - maximum.y, pivot.z + minimum.z);
        VertexSet vertices = new VertexSet(new Vec3(-origin.x - size.x, origin.y, origin.z).scale(1.0D / 16.0D), size.scale(1.0D / 16.0D), 0);
        JsonObject faces = new JsonObject();
        for (ModelPart.Polygon polygon : cube.polygons) {
            ModelPart.Vertex[] corners = polygon.vertices();
            Vec3 firstEdge = point(corners[1]).subtract(point(corners[0]));
            Vec3 secondEdge = point(corners[2]).subtract(point(corners[0]));
            if (firstEdge.cross(secondEdge).lengthSqr() < 1.0E-10D) {
                continue;
            }
            Direction direction = polygon.normal().x() > 0.5F ? Direction.WEST
                    : polygon.normal().x() < -0.5F ? Direction.EAST
                    : polygon.normal().y() > 0.5F ? Direction.DOWN
                    : polygon.normal().y() < -0.5F ? Direction.UP
                    : polygon.normal().z() > 0.5F ? Direction.SOUTH : Direction.NORTH;
            faces.add(direction.getSerializedName(), convertFace(polygon, pivot, vertices, direction));
        }
        JsonObject converted = new JsonObject();
        converted.add("origin", vector(origin.x, origin.y, origin.z));
        converted.add("size", vector(size.x, size.y, size.z));
        converted.add("uv", faces);
        return converted;
    }

    private static JsonObject convertFace(ModelPart.Polygon polygon, Vec3 pivot, VertexSet vertices, Direction direction) {
        double minimumU = 1;
        double maximumU = 0;
        double minimumV = 1;
        double maximumV = 0;
        for (ModelPart.Vertex vertex : polygon.vertices()) {
            minimumU = Math.min(minimumU, vertex.u());
            maximumU = Math.max(maximumU, vertex.u());
            minimumV = Math.min(minimumV, vertex.v());
            maximumV = Math.max(maximumV, vertex.v());
        }
        for (int flipU = 0; flipU < 2; flipU++) {
            for (int flipV = 0; flipV < 2; flipV++) {
                double startU = (flipU == 0 ? minimumU : maximumU) * 64;
                double startV = (flipV == 0 ? minimumV : maximumV) * 64;
                double width = (maximumU - minimumU) * 64 * (flipU == 0 ? 1 : -1);
                double height = (maximumV - minimumV) * 64 * (flipV == 0 ? 1 : -1);
                for (UvFaceRotation rotation : UvFaceRotation.values()) {
                    GeoQuad quad = new GeometryQuadUvs(new GeometryUvPair(startU, startV), new GeometryUvPair(width, height))
                            .bakeQuad(vertices, rotation, false, 64, 64, false, direction);
                    if (matches(polygon, pivot, quad)) {
                        JsonObject face = new JsonObject();
                        face.add("uv", vector(startU, startV));
                        face.add("uv_size", vector(width, height));
                        face.addProperty("uv_rotation", rotation.ordinal() * 90);
                        return face;
                    }
                }
            }
        }
        throw new IllegalStateException("Could not preserve Phantom face UVs for " + direction);
    }

    private static boolean matches(ModelPart.Polygon polygon, Vec3 pivot, GeoQuad quad) {
        for (ModelPart.Vertex original : polygon.vertices()) {
            Vec3 expected = new Vec3(-pivot.x - original.x(), 24.0D - pivot.y - original.y(), pivot.z + original.z()).scale(1.0D / 16.0D);
            boolean matched = false;
            for (GeoVertex converted : quad.vertices()) {
                if (expected.distanceToSqr(new Vec3(converted.posX(), converted.posY(), converted.posZ())) < 1.0E-10D
                        && Math.abs(original.u() - converted.texU()) < 1.0E-6D
                        && Math.abs(original.v() - converted.texV()) < 1.0E-6D) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 point(ModelPart.Vertex vertex) {
        return new Vec3(vertex.x(), vertex.y(), vertex.z());
    }

    public static JsonArray vector(double... values) {
        JsonArray result = new JsonArray();
        for (double value : values) {
            result.add(value);
        }
        return result;
    }

    @Override
    public String getName() {
        return "Phantomism Preserved GeckoLib Geometry";
    }
}
