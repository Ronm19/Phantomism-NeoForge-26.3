package net.ronm19.phantomism.animation;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.constant.dataticket.DataTicket;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.entity.AbstractPhantom;

public final class PhantomAnimations {
    public static final Identifier RESOURCE = id("entity/phantom");
    public static final Identifier ADULT_MODEL = id("entity/phantom_adult");
    public static final Identifier BABY_MODEL = id("entity/phantom_baby");
    public static final DataTicket<PhantomAnimationMotion> MOTION = DataTicket.create("phantomism_motion", PhantomAnimationMotion.class);
    private static final RawAnimation TAKEOFF = transition("takeoff", PhantomAnimationMotion.FLY);
    private static final RawAnimation LAND = transition("land", PhantomAnimationMotion.SIT);
    private static final RawAnimation WAKE = transition("wake", PhantomAnimationMotion.HOVER);
    private static final RawAnimation RECOVER = transition("recover", PhantomAnimationMotion.FLY);
    private static final String[] TRANSITIONS = {"animation.phantom.takeoff", "animation.phantom.land", "animation.phantom.wake", "animation.phantom.recover"};

    private PhantomAnimations() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path);
    }

    private static RawAnimation transition(String name, PhantomAnimationMotion next) {
        return RawAnimation.begin().thenPlay("animation.phantom." + name)
                .thenLoop("animation.phantom." + next.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static void register(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<AbstractPhantom>("movement", 6, PhantomAnimations::movement));
        AnimationController<AbstractPhantom> actions = new AnimationController<>("action", 2, test -> PlayState.STOP);
        for (PhantomAnimationAction action : PhantomAnimationAction.values()) {
            actions.triggerableAnim(action.key(), action.animation());
        }
        registrar.add(actions.additiveAnimations());
    }

    private static PlayState movement(AnimationTest<AbstractPhantom> test) {
        PhantomAnimationMotion motion = test.getDataOrDefault(MOTION, PhantomAnimationMotion.HOVER);
        for (String transition : TRANSITIONS) {
            if (test.isCurrentAnimationStage(transition) && !test.controller().hasAnimationFinished()) {
                return PlayState.CONTINUE;
            }
        }
        if (test.isCurrentAnimation(PhantomAnimationMotion.SLEEP.animation()) && motion != PhantomAnimationMotion.SLEEP) {
            return test.setAndContinue(WAKE);
        }
        if (test.isCurrentAnimation(PhantomAnimationMotion.SIT.animation()) && motion != PhantomAnimationMotion.SIT
                && motion != PhantomAnimationMotion.SLEEP) {
            return test.setAndContinue(TAKEOFF);
        }
        if (motion == PhantomAnimationMotion.SIT && !test.isCurrentAnimation(PhantomAnimationMotion.SIT.animation())
                && !test.isCurrentAnimation(LAND)) {
            return test.setAndContinue(LAND);
        }
        if (test.isCurrentAnimation(PhantomAnimationMotion.DIVE.animation()) && motion != PhantomAnimationMotion.DIVE) {
            return test.setAndContinue(RECOVER);
        }
        return test.setAndContinue(test.animatable().getMovementAnimation(motion));
    }
}
