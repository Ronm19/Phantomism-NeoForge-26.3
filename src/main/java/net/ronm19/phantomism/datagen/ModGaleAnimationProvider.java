package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModGaleAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModGaleAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/gale_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(), clips = result.getAsJsonObject("animations");
        add(clips,"bolt",0.5D,5,-2,-18);
        add(clips,"slipstream",0.7D,12,-2,-26);
        add(clips,"updraft",1D,-12,-3,26);
        add(clips,"crosswind",0.8D,-8,-2,36);
        add(clips,"rush",0.8D,24,-5,-38);
        add(clips,"blade",0.7D,16,-4,-34);
        add(clips,"vortex",1D,-10,-4,34);
        add(clips,"rescue",0.8D,-6,-2,28);
        add(clips,"tailwind",0.7D,-4,0,12);
        add(clips,"dominion",2D,-18,-5,44);
        add(clips,"tempest",0.7D,12,-3,-28);
        add(clips,"judgment",1.1D,24,-6,-44);
        add(clips,"combat",0.6D,20,-3,-30);
        add(clips,"fast",0.7D,8,-1,-20);
        add(clips,"hover",0.9D,-4,0,16);
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, -body * .5D); channel(bones, "tail2", 0, length, body * .3D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", false); clip.addProperty("animation_length", length); clip.add("bones", bones); clips.add("animation.gale." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * .3D, length * .7D, length}, values = {0, amount, -amount * .2D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Gale Animations"; }
}
