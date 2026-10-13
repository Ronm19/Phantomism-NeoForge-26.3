package net.ronm19.phantomism.animation;

import com.geckolib.animation.RawAnimation;

public enum PhantomAnimationAction {
    HURT(1, 6),
    SENSE(1, 16),
    BLESSING(1, 16),
    BITE(2, 8),
    CRITICAL_BITE(2, 10),
    PROJECTILE(2, 4),
    EMPOWER(2, 16),
    SHIELD(3, 16),
    BARRIER(3, 16),
    CHARGE(2, 12),
    ULTIMATE(4, 24);

    private final int priority;
    private final int duration;
    private final String key;
    private final RawAnimation animation;

    PhantomAnimationAction(int priority, int duration) {
        this.priority = priority;
        this.duration = duration;
        this.key = this.name().toLowerCase(java.util.Locale.ROOT);
        this.animation = RawAnimation.begin().thenPlay("animation.phantom." + this.key);
    }

    public int priority() { return this.priority; }
    public int duration() { return this.duration; }
    public String key() { return this.key; }
    public RawAnimation animation() { return this.animation; }
}
