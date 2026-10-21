package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModEndAnimationProvider implements DataProvider {
    private final PackOutput output;

    public ModEndAnimationProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder()
                .resolve("assets/phantomism/geckolib/animations/entity/end_phantom.animation.json"));
    }

    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy();
        JsonObject clips = result.getAsJsonObject("animations");
        add(clips, "rescue", 0.6D, -8, -12, -28);
        add(clips, "catch", 0.5D, 12, 8, 16);
        add(clips, "rescue_land", 0.8D, 6, -5, -12);
        add(clips, "blink", 0.35D, -12, -8, -42);
        add(clips, "teleport", 0.65D, -16, -10, -36);
        add(clips, "bite", 0.4D, -14, -5, 12);
        add(clips, "rift_strike", 0.55D, -20, -12, -34);
        add(clips, "shield", 0.8D, -8, -4, 22);
        add(clips, "eye", 1.0D, -12, -6, 28);
        add(clips, "evade", 0.4D, -5, -4, -24);
        clips.add("animation.end.eye_flight", clips.getAsJsonObject("animation.phantom.fast").deepCopy());
        JsonObject glide = new JsonObject();
        glide.addProperty("loop", true);
        glide.addProperty("animation_length", 1.6D);
        JsonObject bones = new JsonObject();
        channel(bones, "head", 0, 1.6D, -3);
        channel(bones, "body", 0, 1.6D, -4);
        channel(bones, "left_wing", 2, 1.6D, -10);
        channel(bones, "right_wing", 2, 1.6D, 10);
        glide.add("bones", bones);
        clips.add("animation.end.rescue_glide", glide);
        return result;
    }

    private static void add(JsonObject clips, String name, double length, double head, double body, double wings) {
        JsonObject bones = new JsonObject();
        channel(bones, "head", 0, length, head);
        channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings);
        channel(bones, "right_wing", 2, length, -wings);
        JsonObject clip = new JsonObject();
        clip.addProperty("loop", false);
        clip.addProperty("animation_length", length);
        clip.add("bones", bones);
        clips.add("animation.end." + name, clip);
    }

    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject();
        double[] times = {0, length * 0.2D, length * 0.6D, length};
        double[] values = {0, amount, amount * -0.3D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray();
            for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
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
    public String getName() { return "Phantomism End Rescue and Rift Actions"; }
}
