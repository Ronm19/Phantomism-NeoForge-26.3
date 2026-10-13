package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.ronm19.phantomism.animation.PhantomAnimationAction;
import net.ronm19.phantomism.animation.PhantomAnimationMotion;

public final class ModPhantomAnimationProvider implements DataProvider {
    private final PackOutput output;

    public ModPhantomAnimationProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return DataProvider.saveStable(cache, createAnimations(), this.output.getOutputFolder()
                .resolve("assets/phantomism/geckolib/animations/entity/phantom.animation.json"));
    }

    public static JsonObject createAnimations() {
        JsonObject animations = new JsonObject();
        for (PhantomAnimationMotion motion : PhantomAnimationMotion.values()) {
            JsonObject clip = movement(motion);
            animations.add("animation.phantom." + motion.name().toLowerCase(java.util.Locale.ROOT), clip);
        }
        for (PhantomAnimationAction action : PhantomAnimationAction.values()) {
            animations.add("animation.phantom." + action.key(), action(action.key(), action.duration() / 20.0D));
        }
        for (String name : new String[]{"takeoff", "land", "wake", "recover"}) {
            animations.add("animation.phantom." + name, transition(name));
        }
        JsonObject resource = new JsonObject();
        resource.addProperty("format_version", "1.8.0");
        resource.add("animations", animations);
        return resource;
    }

    private static JsonObject movement(PhantomAnimationMotion motion) {
        boolean resting = motion == PhantomAnimationMotion.SIT || motion == PhantomAnimationMotion.SLEEP;
        boolean swimming = motion.name().startsWith("SWIM");
        double length = resting ? 4.0D : motion == PhantomAnimationMotion.FAST || motion == PhantomAnimationMotion.DIVE ? 1.2D : swimming ? 2.0D : 2.4D;
        double flap = resting ? 2.0D : swimming ? 10.0D : motion == PhantomAnimationMotion.HOVER ? 12.0D : 16.0D;
        double bank = motion == PhantomAnimationMotion.BANK_LEFT || motion == PhantomAnimationMotion.SWIM_LEFT ? 8.0D
                : motion == PhantomAnimationMotion.BANK_RIGHT || motion == PhantomAnimationMotion.SWIM_RIGHT ? -8.0D : 0.0D;
        double pitch = motion == PhantomAnimationMotion.DIVE ? 12.0D : motion == PhantomAnimationMotion.SWIM_DIVE ? 8.0D : motion == PhantomAnimationMotion.SWIM_SURFACE ? -8.0D : 0.0D;
        double wingPitch = motion == PhantomAnimationMotion.ASCEND ? -8.0D : motion == PhantomAnimationMotion.DESCEND ? 8.0D : 0.0D;
        JsonObject bones = new JsonObject();
        wing(bones, "left_wing", length, flap, resting ? -20.0D : 0.0D, 0.0D, wingPitch);
        wing(bones, "right_wing", length, -flap, resting ? 20.0D : 0.0D, 0.0D, wingPitch);
        wing(bones, "left_wing_tip", length, flap * 1.08D, resting ? -35.0D : 0.0D, 0.08D, wingPitch);
        wing(bones, "right_wing_tip", length, -flap * 1.08D, resting ? 35.0D : 0.0D, 0.08D, wingPitch);
        oscillation(bones, "tail", "rotation", length, new double[]{-5, 0, 0}, new double[]{-4, 0, 0}, 0.25D);
        oscillation(bones, "tail2", "rotation", length, new double[]{-3, 0, 0}, new double[]{-3, 0, 0}, 0.25D);
        oscillation(bones, "root", "rotation", length, new double[]{pitch, 0, bank}, new double[]{0, 0, 0}, 0.0D);
        double nod = motion == PhantomAnimationMotion.SLEEP ? 10.0D : motion == PhantomAnimationMotion.BREED ? -4.0D : 0.0D;
        oscillation(bones, "head", "rotation", length, new double[]{nod, 0, 0}, new double[]{1, 0, 0}, 0.25D);
        oscillation(bones, "body", "position", length, new double[]{0, 0, 0}, new double[]{0, 0.15D, 0}, 0.25D);
        return clip(length, true, bones);
    }

    private static void wing(JsonObject bones, String name, double length, double amplitude, double resting, double lag, double pitch) {
        oscillation(bones, name, "rotation", length, new double[]{pitch, 0, resting}, new double[]{0, 0, amplitude}, lag);
    }

    private static void oscillation(JsonObject bones, String bone, String channel, double length, double[] center, double[] amplitude, double lag) {
        JsonArray values = new JsonArray();
        for (int axis = 0; axis < center.length; axis++) {
            if (amplitude[axis] == 0.0D) {
                values.add(center[axis]);
            } else {
                values.add(center[axis] + " + " + amplitude[axis] + " * math.cos(query.anim_time * "
                        + (360.0D / length) + " - " + (lag * 360.0D) + ")");
            }
        }
        JsonObject keyframe = new JsonObject();
        keyframe.add("vector", values);
        keyframe.addProperty("easing", "easeInOutSine");
        JsonObject timeline = new JsonObject();
        timeline.add("0.0", keyframe);
        timeline.add(Double.toString(length), keyframe.deepCopy());
        JsonObject channels = bones.has(bone) ? bones.getAsJsonObject(bone) : new JsonObject();
        channels.add(channel, timeline);
        bones.add(bone, channels);
    }

    private static JsonObject action(String name, double length) {
        double pitch = switch (name) {
            case "bite" -> -14.0D;
            case "critical_bite" -> -20.0D;
            case "projectile" -> -6.0D;
            case "hurt" -> 8.0D;
            case "shield", "barrier" -> 6.0D;
            case "charge" -> 12.0D;
            case "ultimate" -> -12.0D;
            default -> -4.0D;
        };
        double turn = name.equals("sense") ? 12.0D : 0.0D;
        JsonObject bones = new JsonObject();
        channel(bones, "head", "rotation", length, new double[][]{{0, 0, 0}, {pitch, -turn, 0}, {pitch * 0.5D, turn, 0}, {0, 0, 0}});
        channel(bones, "body", "rotation", length, new double[][]{{0, 0, 0}, {pitch * 0.3D, 0, 0}, {-pitch * 0.15D, 0, 0}, {0, 0, 0}});
        return clip(length, false, bones);
    }

    private static JsonObject transition(String name) {
        JsonObject bones = new JsonObject();
        double tilt = name.equals("land") ? -8 : name.equals("takeoff") ? 8 : 4;
        channel(bones, "body", "rotation", 0.4D, new double[][]{{0, 0, 0}, {tilt, 0, 0}, {0, 0, 0}});
        channel(bones, "left_wing", "rotation", 0.4D, new double[][]{{0, 0, 0}, {0, 0, -18}, {0, 0, 0}});
        channel(bones, "right_wing", "rotation", 0.4D, new double[][]{{0, 0, 0}, {0, 0, 18}, {0, 0, 0}});
        return clip(0.4D, false, bones);
    }

    private static void channel(JsonObject bones, String bone, String channel, double length, double[][] frames) {
        JsonObject channels = bones.has(bone) ? bones.getAsJsonObject(bone) : new JsonObject();
        JsonObject timeline = new JsonObject();
        for (int index = 0; index < frames.length; index++) {
            JsonObject keyframe = new JsonObject();
            keyframe.add("post", ModPhantomGeometryProvider.vector(frames[index]));
            keyframe.addProperty("easing", "easeInOutSine");
            timeline.add(Double.toString(length * index / (frames.length - 1)), keyframe);
        }
        channels.add(channel, timeline);
        bones.add(bone, channels);
    }

    private static JsonObject clip(double length, boolean loop, JsonObject bones) {
        JsonObject result = new JsonObject();
        result.addProperty("animation_length", length);
        result.addProperty("loop", loop);
        result.add("bones", bones);
        return result;
    }

    @Override
    public String getName() {
        return "Phantomism Shared GeckoLib Animations";
    }
}
