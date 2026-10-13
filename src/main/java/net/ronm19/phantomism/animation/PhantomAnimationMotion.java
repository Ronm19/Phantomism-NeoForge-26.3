package net.ronm19.phantomism.animation;

import com.geckolib.animation.RawAnimation;

public enum PhantomAnimationMotion {
    HOVER, FLY, FAST, ASCEND, DESCEND, BANK_LEFT, BANK_RIGHT, DIVE,
    SWIM, SWIM_DIVE, SWIM_SURFACE, SWIM_LEFT, SWIM_RIGHT, SIT, SLEEP, BREED;

    private final RawAnimation animation = RawAnimation.begin().thenLoop(
            "animation.phantom." + this.name().toLowerCase(java.util.Locale.ROOT));

    public RawAnimation animation() { return this.animation; }
}
