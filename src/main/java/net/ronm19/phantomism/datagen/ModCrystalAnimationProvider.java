package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModCrystalAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModCrystalAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/crystal_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(), clips = result.getAsJsonObject("animations");
        add(clips,"shard",0.5D,8,-2,-18);
        add(clips,"burst",0.8D,-7,-3,30);
        add(clips,"counter",0.8D,26,-7,-38);
        add(clips,"barrier",1.1D,-12,-3,28);
        add(clips,"shell",0.9D,-16,-3,32);
        add(clips,"dominion",2D,-20,-6,44);
        add(clips,"pulse",0.7D,10,-3,-24);
        add(clips,"shatter",1.2D,28,-7,-45);
        add(clips,"combat",0.7D,20,-4,-32);
        add(clips,"recover",0.8D,-5,0,8);
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, -body * .5D); channel(bones, "tail2", 0, length, body * .3D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", false); clip.addProperty("animation_length", length); clip.add("bones", bones); clips.add("animation.crystal." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * .3D, length * .7D, length}, values = {0, amount, -amount * .2D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Crystal Animations"; }
}
