package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModEchoAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModEchoAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/echo_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(); JsonObject clips = result.getAsJsonObject("animations");
        add(clips, "pulse", 0.55D, -12, -5, 16, false);
        add(clips, "strike", 0.7D, -16, -10, -20, false);
        add(clips, "strike_impact", 0.45D, 22, 10, -28, false);
        add(clips, "aid", 0.6D, -10, -4, 18, false);
        add(clips, "chain", 0.9D, -14, -7, 30, false);
        add(clips, "guard", 0.9D, -8, 5, 32, false);
        add(clips, "perfect", 1.6D, -24, -12, 40, false);
        add(clips, "sense", 0.65D, -7, -2, 10, false);
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings, boolean loop) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, body * -0.6D); channel(bones, "tail2", 0, length, body * 0.4D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", loop); clip.addProperty("animation_length", length); clip.add("bones", bones);
        clips.add("animation.echo." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * 0.28D, length * 0.68D, length}; double[] values = {0, amount, -amount * 0.25D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Echo Coordinated Resonance Animations"; }
}
