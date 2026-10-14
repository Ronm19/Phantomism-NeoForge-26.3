package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModRavagerAnimationProvider implements DataProvider {
    private final PackOutput output;

    public ModRavagerAnimationProvider(PackOutput output) { this.output = output; }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder()
                .resolve("assets/phantomism/geckolib/animations/entity/ravager_phantom.animation.json"));
    }

    public static JsonObject createAnimations() {
        JsonObject result = ModPhantomAnimationProvider.createAnimations().deepCopy();
        JsonObject clips = result.getAsJsonObject("animations");
        for (String motion : new String[]{"hover", "fly", "fast", "ascend", "descend", "bank_left", "bank_right"}) {
            JsonObject clip = clips.getAsJsonObject("animation.phantom." + motion);
            double oldLength = clip.get("animation_length").getAsDouble();
            double length = motion.equals("hover") ? 3.2D : motion.equals("fast") ? 1.8D : 2.6D;
            clip.addProperty("animation_length", length);
            for (var bone : clip.getAsJsonObject("bones").entrySet()) {
                for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                    JsonObject timeline = channel.getValue().getAsJsonObject();
                    for (var frame : timeline.entrySet()) {
                        JsonArray vector = frame.getValue().getAsJsonObject().getAsJsonArray("vector");
                        for (int axis = 0; axis < vector.size(); axis++) {
                            if (vector.get(axis).getAsJsonPrimitive().isString()) {
                                vector.set(axis, new com.google.gson.JsonPrimitive(vector.get(axis).getAsString()
                                        .replace("query.anim_time * " + (360.0D / oldLength), "query.anim_time * " + (360.0D / length))));
                            }
                        }
                    }
                    var end = timeline.remove(Double.toString(oldLength));
                    if (end != null) { timeline.add(Double.toString(length), end); }
                }
            }
        }
        add(clips, "charge_windup", 0.6D, new double[]{0, 0.2D, 0.45D, 0.6D}, new double[]{0, 13, 8, 0}, new double[]{0, 7, 4, 0}, new double[]{0, -20, -14, 0});
        add(clips, "charge_impact", 0.4D, new double[]{0, 0.06D, 0.18D, 0.4D}, new double[]{0, -20, 8, 0}, new double[]{0, -10, 4, 0}, new double[]{0, 18, -8, 0});
        add(clips, "bite", 0.4D, new double[]{0, 0.1D, 0.2D, 0.4D}, new double[]{0, -13, 7, 0}, new double[]{0, -5, 3, 0}, new double[]{0, -7, 4, 0});
        add(clips, "dive_impact", 0.8D, new double[]{0, 0.12D, 0.4D, 0.8D}, new double[]{0, 25, -7, 0}, new double[]{0, 12, -3, 0}, new double[]{0, 30, -8, 0});
        add(clips, "ram", 0.6D, new double[]{0, 0.12D, 0.22D, 0.6D}, new double[]{0, -22, 9, 0}, new double[]{0, -8, 6, 0}, new double[]{0, -14, 18, 0});
        add(clips, "roar", 0.9D, new double[]{0, 0.18D, 0.55D, 0.9D}, new double[]{0, -20, -16, 0}, new double[]{0, -6, -4, 0}, new double[]{0, 22, 16, 0});
        add(clips, "slam_windup", 0.6D, new double[]{0, 0.2D, 0.45D, 0.6D}, new double[]{0, -15, -8, 0}, new double[]{0, -14, -10, 0}, new double[]{0, -32, -24, 0});
        add(clips, "slam_impact", 0.9D, new double[]{0, 0.08D, 0.35D, 0.9D}, new double[]{0, 22, -4, 0}, new double[]{0, 20, -5, 0}, new double[]{0, 35, 14, 0});
        add(clips, "fury", 1.0D, new double[]{0, 0.2D, 0.65D, 1.0D}, new double[]{0, -20, -12, 0}, new double[]{0, -8, 3, 0}, new double[]{0, -18, 25, 0});
        JsonObject charge = new JsonObject();
        charge.addProperty("loop", true);
        charge.addProperty("animation_length", 1.0D);
        JsonObject chargeBones = new JsonObject();
        loop(chargeBones, "head", 0, "-10.0");
        loop(chargeBones, "body", 0, "-6.0");
        loop(chargeBones, "left_wing", 2, "-14.0 + 3.0 * math.cos(query.anim_time * 360.0)");
        loop(chargeBones, "right_wing", 2, "14.0 - 3.0 * math.cos(query.anim_time * 360.0)");
        charge.add("bones", chargeBones);
        clips.add("animation.ravager.charge_flight", charge);
        JsonObject fury = clips.getAsJsonObject("animation.phantom.fast").deepCopy();
        loop(fury.getAsJsonObject("bones"), "head", 0, "6.0 + 2.0 * math.cos(query.anim_time * 200.0)");
        clips.add("animation.ravager.fury_flight", fury);
        return result;
    }

    private static void loop(JsonObject bones, String name, int axis, String expression) {
        JsonArray vector = new JsonArray();
        for (int coordinate = 0; coordinate < 3; coordinate++) { vector.add(coordinate == axis ? expression : "0.0"); }
        JsonObject frame = new JsonObject();
        frame.add("vector", vector);
        JsonObject timeline = new JsonObject();
        timeline.add("0.0", frame);
        JsonObject bone = new JsonObject();
        bone.add("rotation", timeline);
        bones.add(name, bone);
    }

    private static void add(JsonObject clips, String name, double length, double[] times, double[] head, double[] body, double[] wings) {
        JsonObject bones = new JsonObject();
        channel(bones, "head", 0, times, head, 1.0D);
        channel(bones, "body", 0, times, body, 1.0D);
        channel(bones, "left_wing", 2, times, wings, 1.0D);
        channel(bones, "right_wing", 2, times, wings, -1.0D);
        JsonObject clip = new JsonObject();
        clip.addProperty("loop", false);
        clip.addProperty("animation_length", length);
        clip.add("bones", bones);
        clips.add("animation.ravager." + name, clip);
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
    public String getName() { return "Phantomism Ravager Heavy Flight and Nine-Ability Actions"; }
}
