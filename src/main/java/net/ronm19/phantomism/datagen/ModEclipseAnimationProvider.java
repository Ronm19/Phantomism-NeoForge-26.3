package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModEclipseAnimationProvider implements DataProvider {
    private final PackOutput output;
    public ModEclipseAnimationProvider(PackOutput output) { this.output = output; }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), output.getOutputFolder().resolve("assets/phantomism/geckolib/animations/entity/eclipse_phantom.animation.json"));
    }
    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy(), clips = result.getAsJsonObject("animations");
        add(clips,"solar_guard",1.2D,-18,-4,35);
        add(clips,"solar_pulse",1.3D,-10,-4,32);
        add(clips,"lunar_fang",0.6D,24,-4,-34);
        add(clips,"lunar_rush",1.1D,12,-6,-28);
        add(clips,"bolt",0.5D,10,-2,-18);
        add(clips,"hybrid_rend",1D,22,-5,-40);
        add(clips,"hybrid_guard",0.8D,-14,-3,30);
        add(clips,"alignment",2D,-22,-5,45);
        add(clips,"sovereignty",2D,-12,-2,30);
        add(clips,"judgment",1.2D,22,-5,-45);
        add(clips,"shift",1D,-8,-3,24);
        for (String stance : new String[]{"solar", "lunar"}) {
            JsonObject bones = new JsonObject(); channel(bones, "head", 0, 3, stance.equals("solar") ? -3 : 3);
            channel(bones, "left_wing", 0, 3, stance.equals("solar") ? 3 : -3); channel(bones, "right_wing", 0, 3, stance.equals("solar") ? -3 : 3);
            JsonObject clip = new JsonObject(); clip.addProperty("loop", true); clip.addProperty("animation_length", 3); clip.add("bones", bones); clips.add("animation.eclipse." + stance, clip);
        }
        return result;
    }
    private static void add(JsonObject clips, String name, double length, double head, double body, double wings) {
        JsonObject bones = new JsonObject(); channel(bones, "head", 0, length, head); channel(bones, "body", 0, length, body);
        channel(bones, "left_wing", 2, length, wings); channel(bones, "right_wing", 2, length, -wings);
        channel(bones, "tail", 0, length, -body * .5D); channel(bones, "tail2", 0, length, body * .3D);
        JsonObject clip = new JsonObject(); clip.addProperty("loop", false); clip.addProperty("animation_length", length); clip.add("bones", bones); clips.add("animation.eclipse." + name, clip);
    }
    private static void channel(JsonObject bones, String name, int axis, double length, double amount) {
        JsonObject timeline = new JsonObject(); double[] times = {0, length * .3D, length * .7D, length}, values = {0, amount, -amount * .2D, 0};
        for (int frame = 0; frame < times.length; frame++) {
            JsonArray vector = new JsonArray(); for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? values[frame] : 0); }
            JsonObject key = new JsonObject(); key.add("vector", vector); key.addProperty("easing", "easeInOutSine"); timeline.add(Double.toString(times[frame]), key);
        }
        JsonObject bone = new JsonObject(); bone.add("rotation", timeline); bones.add(name, bone);
    }
    @Override public String getName() { return "Phantomism Eclipse Animations"; }
}
