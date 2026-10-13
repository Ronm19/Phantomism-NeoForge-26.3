package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModCherryAnimationProvider implements DataProvider {
    private final PackOutput output;

    public ModCherryAnimationProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder()
                .resolve("assets/phantomism/geckolib/animations/entity/cherry_phantom.animation.json"));
    }

    public static JsonObject createAnimations() {
        JsonObject shared = ModPhantomAnimationProvider.createAnimations().getAsJsonObject("animations");
        JsonObject animations = new JsonObject();
        for (String name : new String[]{"hover", "fly", "fast", "ascend", "descend", "bank_left", "bank_right", "sit", "sleep"}) {
            double length = name.equals("fast") ? 1.8D : name.equals("hover") ? 3.2D
                    : name.equals("sit") || name.equals("sleep") ? 5.0D : 2.8D;
            animations.add("animation.phantom." + name, graceful(shared.getAsJsonObject("animation.phantom." + name), length));
        }
        animations.add("animation.phantom.blessing", action(0.8D, new double[]{0, 0.25D, 0.55D, 0.8D},
                new double[]{0, -5, 2, 0}, new double[]{0, -2, 1, 0}, new double[]{0, -16, -8, 0}));
        animations.add("animation.phantom.projectile", action(0.2D, new double[]{0, 0.07D, 0.14D, 0.2D},
                new double[]{0, -5, 2, 0}, new double[]{0, -2, 1, 0}, new double[]{0, -4, 2, 0}));
        animations.add("animation.phantom.shield", action(0.8D, new double[]{0, 0.25D, 0.55D, 0.8D},
                new double[]{0, 4, 1, 0}, new double[]{0, 2, 1, 0}, new double[]{0, -20, -10, 0}));
        animations.add("animation.phantom.ultimate", action(1.2D, new double[]{0, 0.3D, 0.65D, 0.9D, 1.2D},
                new double[]{0, -8, -3, 3, 0}, new double[]{0, -3, -2, 2, 0}, new double[]{0, -24, -16, -6, 0}));
        JsonObject resource = new JsonObject();
        resource.addProperty("format_version", "1.8.0");
        resource.add("animations", animations);
        return resource;
    }

    private static JsonObject graceful(JsonObject source, double length) {
        JsonObject clip = source.deepCopy();
        double oldLength = clip.get("animation_length").getAsDouble();
        String oldFrequency = "query.anim_time * " + (360.0D / oldLength);
        String newFrequency = "query.anim_time * " + (360.0D / length);
        clip.addProperty("animation_length", length);
        for (var bone : clip.getAsJsonObject("bones").entrySet()) {
            for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                JsonObject timeline = channel.getValue().getAsJsonObject();
                for (var frame : timeline.entrySet()) {
                    JsonArray values = frame.getValue().getAsJsonObject().getAsJsonArray("vector");
                    for (int coordinate = 0; coordinate < values.size(); coordinate++) {
                        if (values.get(coordinate).getAsJsonPrimitive().isString()) {
                            String expression = values.get(coordinate).getAsString().replace(oldFrequency, newFrequency);
                            if (bone.getKey().contains("wing")) { expression = expression.replace(" * math.cos", " * 0.75 * math.cos"); }
                            values.set(coordinate, new com.google.gson.JsonPrimitive(expression));
                        }
                    }
                }
                var end = timeline.remove(Double.toString(oldLength));
                timeline.add(Double.toString(length), end);
            }
        }
        return clip;
    }

    private static JsonObject action(double length, double[] times, double[] head, double[] body, double[] wings) {
        JsonObject bones = new JsonObject();
        channel(bones, "head", 0, times, head, 1.0D);
        channel(bones, "body", 0, times, body, 1.0D);
        channel(bones, "left_wing", 2, times, wings, 1.0D);
        channel(bones, "right_wing", 2, times, wings, -1.0D);
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

    @Override
    public String getName() { return "Phantomism Cherry Phantom Graceful Animations"; }
}
