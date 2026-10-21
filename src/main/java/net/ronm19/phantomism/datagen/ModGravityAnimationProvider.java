package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModGravityAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModGravityAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/gravity_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(); JsonObject clips = result.getAsJsonObject("animations");
        add(clips, "cast", 0.65D, -12, -4, 22, false);
        add(clips, "crush", 0.8D, 26, 8, -28, false);
        add(clips, "lift", 1.0D, -20, -8, 35, false);
        add(clips, "throw", 1.1D, -12, 6, 30, false);
        add(clips, "lock", 0.9D, 18, 4, 20, false);
        add(clips, "field", 1.1D, -8, 0, 40, false);
        add(clips, "horizon", 1.8D, -26, -10, 46, false);
        add(clips, "bite", 0.5D, 18, -6, -12, false);
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings, boolean loop) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, body * -0.6D); channel(bones, "tail2", 0, length, body * 0.4D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", loop); clip.addProperty("animation_length", length); clip.add("bones", bones);
        clips.add("animation.gravity." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * 0.28D, length * 0.68D, length}; double[] values = {0, amount, -amount * 0.25D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Gravity Gravity Control and Orbital Animations"; }
}
