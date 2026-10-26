package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModShadowAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModShadowAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/shadow_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(), clips = result.getAsJsonObject("animations");
        add(clips,"bolt",0.5D,10,-2,-18);
        add(clips,"strike",0.7D,24,-5,-35);
        add(clips,"dive",1.3D,14,-7,-32);
        add(clips,"step",0.7D,-8,-3,20);
        add(clips,"vanish",1D,-12,-3,22);
        add(clips,"recon",1.2D,-8,-3,27);
        add(clips,"guard",0.8D,-16,-3,30);
        add(clips,"nightfall",2D,-20,-5,42);
        add(clips,"hunt",0.7D,22,-5,-36);
        add(clips,"finale",1.2D,26,-6,-45);
        JsonObject bones = new JsonObject(); channel(bones, "left_wing", 0, 3, -2); channel(bones, "right_wing", 0, 3, -2);
        JsonObject quiet = new JsonObject(); quiet.addProperty("loop", true); quiet.addProperty("animation_length", 3); quiet.add("bones", bones); clips.add("animation.shadow.quiet", quiet);
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, -body * .5D); channel(bones, "tail2", 0, length, body * .3D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", false); clip.addProperty("animation_length", length); clip.add("bones", bones); clips.add("animation.shadow." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * .3D, length * .7D, length}, values = {0, amount, -amount * .2D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Shadow Animations"; }
}
