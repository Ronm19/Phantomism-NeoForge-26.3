package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModSkeletonAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModSkeletonAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/skeleton_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(); JsonObject clips = result.getAsJsonObject("animations");
        add(clips, "arrow", 0.4D, -8, -4, 10); add(clips, "volley", 0.6D, -12, -6, 18);
        add(clips, "bite", 0.5D, 22, -8, -14); add(clips, "eye", 1.0D, -16, -3, 22);
        add(clips, "guard", 1.0D, -5, -4, 30); add(clips, "rain", 1.5D, -28, -12, 38); add(clips, "presence", 0.7D, -10, 2, 12);
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, -body * 0.5D); channel(bones, "tail2", 0, length, body * 0.3D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", false); clip.addProperty("animation_length", length); clip.add("bones", bones);
        clips.add("animation.skeleton." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * 0.25D, length * 0.7D, length}; double[] values = {0, amount, -amount * 0.2D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Skeleton Adaptive Marksman Animations"; }
}
