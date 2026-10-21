package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModDemonAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModDemonAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/demon_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(); JsonObject clips = result.getAsJsonObject("animations");
        add(clips, "bite", .6D, 24, -7, -22, false);
        add(clips, "breath", 1.6D, -16, -3, 24, false);
        add(clips, "dash", .65D, 12, -9, -25, false);
        add(clips, "wave", 1.0D, -12, 7, 40, false);
        add(clips, "meteor", 1.1D, -14, -3, 34, false);
        add(clips, "drain", .8D, -9, -3, 22, false);
        add(clips, "howl", 1.2D, -28, 4, 38, false);
        add(clips, "form", 1.5D, -16, -5, 42, false);
        add(clips, "apocalypse", 1.8D, -24, -7, 48, false);
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings, boolean loop) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, body * -0.6D); channel(bones, "tail2", 0, length, body * 0.4D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", loop); clip.addProperty("animation_length", length); clip.add("bones", bones);
        clips.add("animation.demon." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * 0.28D, length * 0.68D, length}; double[] values = {0, amount, -amount * 0.25D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Demon Infernal Animations"; }
}
