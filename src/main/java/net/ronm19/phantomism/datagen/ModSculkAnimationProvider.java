package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModSculkAnimationProvider implements DataProvider {
    private final PackOutput output;

    public ModSculkAnimationProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder()
                .resolve("assets/phantomism/geckolib/animations/entity/sculk_phantom.animation.json"));
    }

    public static JsonObject createAnimations() {
        JsonObject resource = ModPhantomAnimationProvider.createAnimations().deepCopy();
        JsonObject animations = resource.getAsJsonObject("animations");
        slowHover(animations.getAsJsonObject("animation.phantom.hover"));
        animations.add("animation.phantom.projectile", action(0.2D, new double[]{0, 0.05D, 0.12D, 0.2D},
                new double[]{0, -10, 4, 0}, new double[]{0, -10, 3, 0}, new double[]{0, -8, 2, 0}, false));
        animations.add("animation.phantom.charge", action(0.6D, new double[]{0, 0.12D, 0.3D, 0.45D, 0.6D},
                new double[]{0, -4, 3, -2, 0}, new double[]{0, 10, -6, -2, 0}, new double[]{0, -25, -15, -6, 0}, false));
        animations.add("animation.phantom.empower", action(0.8D, new double[]{0, 0.16D, 0.35D, 0.55D, 0.8D},
                new double[]{0, -10, 5, 1, 0}, new double[]{0, -6, 10, 3, 0}, new double[]{0, -8, 24, 10, 0}, false));
        animations.add("animation.phantom.shield", action(0.8D, new double[]{0, 0.2D, 0.5D, 0.8D},
                new double[]{0, 4, 2, 0}, new double[]{0, 3, 1, 0}, new double[]{0, 14, 8, 0}, false));
        animations.add("animation.phantom.ultimate", action(1.2D, new double[]{0, 0.2D, 0.55D, 0.9D, 1.2D},
                new double[]{0, -12, -7, 2, 0}, new double[]{0, -6, -3, 2, 0}, new double[]{0, -48, -32, -12, 0}, false));
        return resource;
    }

    private static JsonObject action(double length, double[] times, double[] head, double[] body, double[] wings, boolean crosscut) {
        JsonObject bones = new JsonObject();
        channel(bones, "head", 0, times, head, 1.0D);
        channel(bones, "body", crosscut ? 2 : 0, times, body, 1.0D);
        channel(bones, "left_wing", 2, times, wings, 1.0D);
        channel(bones, "right_wing", 2, times, wings, crosscut ? 0.65D : -1.0D);
        channel(bones, "left_wing_tip", 2, times, wings, 0.45D);
        channel(bones, "right_wing_tip", 2, times, wings, crosscut ? 0.3D : -0.45D);
        JsonObject clip = new JsonObject();
        clip.addProperty("loop", false);
        clip.addProperty("animation_length", length);
        clip.add("bones", bones);
        return clip;
    }

    private static void channel(JsonObject bones, String name, int axis, double[] times, double[] values, double multiplier) {
        JsonObject timeline = new JsonObject();
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray();
            for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] * multiplier : 0.0D); }
            JsonObject key = new JsonObject();
            key.add("vector", vector);
            key.addProperty("easing", "easeInOutSine");
            timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject();
        bone.add("rotation", timeline);
        bones.add(name, bone);
    }


    private static void slowHover(JsonObject clip) {
        double oldLength = clip.get("animation_length").getAsDouble();
        double length = 3.2D;
        clip.addProperty("animation_length", length);
        for (var bone : clip.getAsJsonObject("bones").entrySet()) {
            for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                JsonObject timeline = channel.getValue().getAsJsonObject();
                for (var frame : timeline.entrySet()) {
                    JsonArray values = frame.getValue().getAsJsonObject().getAsJsonArray("vector");
                    for (int coordinate = 0; coordinate < values.size(); coordinate++) {
                        if (values.get(coordinate).getAsJsonPrimitive().isString()) {
                            values.set(coordinate, new com.google.gson.JsonPrimitive(values.get(coordinate).getAsString()
                                    .replace("query.anim_time * " + (360.0D / oldLength), "query.anim_time * " + (360.0D / length))));
                        }
                    }
                }
                var end = timeline.remove(Double.toString(oldLength));
                timeline.add(Double.toString(length), end);
            }
        }
    }

    @Override
    public String getName() { return "Phantomism Sculk Sonic Animations"; }
}
