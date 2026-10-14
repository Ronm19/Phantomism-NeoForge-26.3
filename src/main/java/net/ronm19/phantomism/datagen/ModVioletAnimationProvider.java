package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModVioletAnimationProvider implements DataProvider {
    private final PackOutput output;

    public ModVioletAnimationProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder()
                .resolve("assets/phantomism/geckolib/animations/entity/violet_phantom.animation.json"));
    }

    public static JsonObject createAnimations() {
        JsonObject resource = ModPhantomAnimationProvider.createAnimations().deepCopy();
        JsonObject animations = resource.getAsJsonObject("animations");
        animations.add("animation.phantom.projectile", action(0.2D, new double[]{0, 0.05D, 0.12D, 0.2D},
                new double[]{0, -7, 2, 0}, new double[]{0, -10, 3, 0}, new double[]{0, -8, 2, 0}, false));
        animations.add("animation.phantom.charge", action(0.6D, new double[]{0, 0.12D, 0.3D, 0.45D, 0.6D},
                new double[]{0, -4, 3, -2, 0}, new double[]{0, -14, 17, -6, 0}, new double[]{0, -24, 20, -8, 0}, true));
        animations.add("animation.phantom.empower", action(0.8D, new double[]{0, 0.16D, 0.35D, 0.55D, 0.8D},
                new double[]{0, -10, 5, 1, 0}, new double[]{0, -6, 10, 3, 0}, new double[]{0, -16, 30, 12, 0}, false));
        animations.add("animation.phantom.shield", action(0.8D, new double[]{0, 0.2D, 0.5D, 0.8D},
                new double[]{0, 4, 2, 0}, new double[]{0, 3, 1, 0}, new double[]{0, 14, 8, 0}, false));
        animations.add("animation.phantom.ultimate", action(1.2D, new double[]{0, 0.2D, 0.55D, 0.9D, 1.2D},
                new double[]{0, -12, -7, 2, 0}, new double[]{0, -6, -3, 2, 0}, new double[]{0, -32, -20, -6, 0}, false));
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

    @Override
    public String getName() { return "Phantomism Violet Duelist Animations"; }
}
