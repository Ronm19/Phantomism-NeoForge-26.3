package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModGoldAnimationProvider implements DataProvider {
    private final PackOutput output;

    public ModGoldAnimationProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder()
                .resolve("assets/phantomism/geckolib/animations/entity/gold_phantom.animation.json"));
    }

    public static JsonObject createAnimations() {
        JsonObject animations = new JsonObject();
        animations.add("animation.phantom.projectile", action(0.2D, new double[]{0, 0.07D, 0.14D, 0.2D},
                new double[]{0, -9, 4, 0}, new double[]{0, -3, 2, 0}, new double[]{0, -6, 2, 0}));
        animations.add("animation.phantom.charge", action(0.6D, new double[]{0, 0.15D, 0.4D, 0.6D},
                new double[]{0, 14, -10, 0}, new double[]{0, 9, -4, 0}, new double[]{0, -18, -8, 0}));
        animations.add("animation.phantom.shield", action(0.8D, new double[]{0, 0.2D, 0.55D, 0.8D},
                new double[]{0, 8, -3, 0}, new double[]{0, 3, 2, 0}, new double[]{0, 14, 6, 0}));
        animations.add("animation.phantom.ultimate", action(1.2D, new double[]{0, 0.25D, 0.6D, 0.78D, 1.2D},
                new double[]{0, -16, -16, 10, 0}, new double[]{0, -4, -4, 5, 0}, new double[]{0, -14, -14, 12, 0}));
        JsonObject resource = new JsonObject();
        resource.addProperty("format_version", "1.8.0");
        resource.add("animations", animations);
        return resource;
    }

    private static JsonObject action(double duration, double[] times, double[] head, double[] body, double[] wings) {
        JsonObject bones = new JsonObject();
        channel(bones, "head", 0, times, head, 1.0D);
        channel(bones, "body", 0, times, body, 1.0D);
        channel(bones, "left_wing", 2, times, wings, 1.0D);
        channel(bones, "right_wing", 2, times, wings, -1.0D);
        JsonObject animation = new JsonObject();
        animation.addProperty("loop", false);
        animation.addProperty("animation_length", duration);
        animation.add("bones", bones);
        return animation;
    }

    private static void channel(JsonObject bones, String name, int axis, double[] times, double[] values, double multiplier) {
        JsonObject rotation = new JsonObject();
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray();
            for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] * multiplier : 0.0D); }
            JsonObject key = new JsonObject();
            key.add("vector", vector);
            key.addProperty("easing", "easeInOutSine");
            rotation.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject();
        bone.add("rotation", rotation);
        bones.add(name, bone);
    }

    @Override
    public String getName() { return "Phantomism Gold Phantom Action Animations"; }
}
