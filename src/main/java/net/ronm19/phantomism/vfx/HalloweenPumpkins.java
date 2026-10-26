package net.ronm19.phantomism.vfx;

import com.mojang.math.Transformation;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.HalloweenPhantom;
import org.joml.Vector3f;

@EventBusSubscriber(modid = Phantomism.MOD_ID)
public final class HalloweenPumpkins {
    public static final int COUNT = 6;
    private static final String CASTER = "PhantomismHalloweenPumpkinCaster";
    private static final String SESSION = "PhantomismHalloweenPumpkinSession";
    private static final String EXPIRES = "PhantomismHalloweenPumpkinExpires";
    private static final String LIT = "PhantomismHalloweenPumpkinLit";

    private HalloweenPumpkins() {}

    public static BlockState blockState(Level level) {
        return (HalloweenPhantom.hauntedNight(level) ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN).defaultBlockState();
    }

    public static Display.BlockDisplay create(HalloweenPhantom caster, Vec3 center, int slot) {
        var level = (ServerLevel)caster.level();
        var display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        output.store(Display.BlockDisplay.TAG_BLOCK_STATE, BlockState.CODEC, blockState(level));
        output.store(Display.TAG_TRANSFORMATION, Transformation.EXTENDED_CODEC,
                new Transformation(new Vector3f(-.5F, -.5F, -.5F), null, new Vector3f(1), null));
        boolean lit = HalloweenPhantom.hauntedNight(level);
        if (lit) { output.store("brightness", Brightness.CODEC, new Brightness(15, 15)); }
        output.putInt("teleport_duration", 1);
        output.putFloat(Display.TAG_WIDTH, 2);
        output.putFloat(Display.TAG_HEIGHT, 2);
        display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
        display.getPersistentData().putString(CASTER, caster.getUUID().toString());
        display.getPersistentData().putString(SESSION, caster.session().toString());
        display.getPersistentData().putLong(EXPIRES, level.getGameTime() + HalloweenPhantom.FRIGHT_DURATION);
        display.getPersistentData().putBoolean(LIT, lit);
        move(display, center, 0, slot);
        level.addFreshEntity(display);
        return display;
    }

    public static void move(Display.BlockDisplay display, Vec3 center, int elapsed, int slot) {
        boolean lit = HalloweenPhantom.hauntedNight(display.level());
        if (display.getPersistentData().getBooleanOr(LIT, false) != lit) {
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, display.registryAccess());
            display.saveWithoutId(output);
            output.store(Display.BlockDisplay.TAG_BLOCK_STATE, BlockState.CODEC, blockState(display.level()));
            if (lit) { output.store("brightness", Brightness.CODEC, new Brightness(15, 15)); }
            var tag = output.buildResult();
            if (!lit) { tag.remove("brightness"); }
            display.load(TagValueInput.create(ProblemReporter.DISCARDING, display.registryAccess(), tag));
            display.getPersistentData().putBoolean(LIT, lit);
        }
        double turn = elapsed * .035D + slot * Math.PI * 2 / COUNT;
        display.setPos(center.add(Math.cos(turn) * 8.82D, 2 + Math.sin(elapsed * .09D + slot) * .6D, Math.sin(turn) * 8.82D));
        display.setYRot((float)(90 - Math.toDegrees(turn)));
        display.needsSync = true;
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Display.BlockDisplay display) || !(display.level() instanceof ServerLevel level)) { return; }
        var data = display.getPersistentData();
        String identity = data.getStringOr(CASTER, "");
        if (identity.isEmpty()) { return; }
        try {
            if (level.getGameTime() >= data.getLongOr(EXPIRES, 0)
                    || !(level.getEntity(UUID.fromString(identity)) instanceof HalloweenPhantom caster)
                    || !caster.isAlive() || caster.isHolidayDefeated() || caster.frightTicks() <= 0
                    || !caster.session().toString().equals(data.getStringOr(SESSION, ""))) { display.discard(); }
        } catch (IllegalArgumentException invalidIdentity) { display.discard(); }
    }
}
