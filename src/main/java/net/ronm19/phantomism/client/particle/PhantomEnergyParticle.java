package net.ronm19.phantomism.client.particle;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.ronm19.phantomism.vfx.PhantomEnergyOptions;
import net.ronm19.phantomism.vfx.PhantomVfxTheme;

public final class PhantomEnergyParticle extends SingleQuadParticle {
    private static final ParticleLimit LIMIT = new ParticleLimit(1024);
    private static final FacingCameraMode GROUND_RING = (rotation, camera, partialTick) -> rotation.rotationX((float) (-Math.PI * 0.5D));
    private final PhantomEnergyOptions options;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double radius;
    private final float initialRoll;
    private double angle;

    private PhantomEnergyParticle(PhantomEnergyOptions options, ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, sprites.get(spriteIndex(options), 10));
        this.options = options;
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.radius = options.scale() * (0.45D + random.nextDouble() * 0.35D);
        this.angle = random.nextDouble() * Math.PI * 2.0D;
        this.lifetime = options.lifetime();
        this.quadSize = options.color() == PhantomVfxTheme.CHERRY.color() && options.shape() == PhantomEnergyOptions.AURA
                ? Math.min(0.3F, options.scale()) : options.scale();
        this.hasPhysics = false;
        this.alpha = 0.0F;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.roll = options.shape() == PhantomEnergyOptions.SLASH || options.shape() == PhantomEnergyOptions.SPARK || options.shape() == PhantomEnergyOptions.PETAL
                ? random.nextFloat() * (float) Math.PI : 0.0F;
        this.initialRoll = this.roll;
        if (options.shape() == PhantomEnergyOptions.SLASH) {
            this.roll -= 0.8F;
        }
        this.oRoll = this.roll;
        this.updateColor(0.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (++this.age >= this.lifetime) {
            this.alpha = 0.0F;
            this.remove();
            return;
        }
        float progress = (float) this.age / this.lifetime;
        if (this.options.shape() == PhantomEnergyOptions.AURA) {
            this.angle += 0.14D;
            this.setPos(this.centerX + Math.cos(this.angle) * this.radius,
                    this.centerY + Math.sin(this.angle * 1.5D) * this.radius * 0.45D,
                    this.centerZ + Math.sin(this.angle) * this.radius);
        } else if (this.options.shape() == PhantomEnergyOptions.PETAL
                || this.options.shape() == PhantomEnergyOptions.SPARK && this.options.color() == PhantomVfxTheme.CHERRY.color()) {
            this.angle += 0.08D;
            this.setPos(this.x + this.xd + Math.cos(this.angle) * 0.012D,
                    this.y + this.yd - 0.018D, this.z + this.zd + Math.sin(this.angle) * 0.012D);
            this.xd *= 0.96D;
            this.yd *= 0.96D;
            this.zd *= 0.96D;
            this.roll += 0.065F;
        } else {
            this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);
            this.xd *= 0.92D;
            this.yd *= 0.92D;
            this.zd *= 0.92D;
        }
        if (this.options.shape() == PhantomEnergyOptions.SLASH) {
            this.roll = this.initialRoll + (smoothStep(progress) - 0.5F) * 1.6F;
        }
        this.alpha = smoothStep(this.age / 2.0F) * (1.0F - smoothStep(progress))
                * (this.options.shape() == PhantomEnergyOptions.AURA ? 0.55F : 0.9F);
        this.updateColor(progress);
    }

    private void updateColor(float progress) {
        int start = this.options.color();
        int end = this.options.fadeColor();
        this.setColor(Mth.lerp(progress, (start >> 16 & 255) / 255.0F, (end >> 16 & 255) / 255.0F),
                Mth.lerp(progress, (start >> 8 & 255) / 255.0F, (end >> 8 & 255) / 255.0F),
                Mth.lerp(progress, (start & 255) / 255.0F, (end & 255) / 255.0F));
    }

    private static float smoothStep(float value) {
        float bounded = Mth.clamp(value, 0.0F, 1.0F);
        return bounded * bounded * (3.0F - 2.0F * bounded);
    }

    private static int spriteIndex(PhantomEnergyOptions options) {
        if (options.shape() == PhantomEnergyOptions.PETAL || options.color() == PhantomVfxTheme.CHERRY.color()
                && (options.shape() == PhantomEnergyOptions.SPARK || options.shape() == PhantomEnergyOptions.AURA)) {
            return 10;
        }
        if (options.shape() != PhantomEnergyOptions.SPARK) {
            return options.shape() == PhantomEnergyOptions.AURA ? 0 : options.shape();
        }
        int color = options.color();
        return color == PhantomVfxTheme.FROST.color() ? 4 : color == PhantomVfxTheme.STORM.color() ? 5
                : color == PhantomVfxTheme.FIRE.color() ? 6 : color == PhantomVfxTheme.WATER.color() ? 7
                : color == PhantomVfxTheme.EARTH.color() ? 8 : color == PhantomVfxTheme.SAND.color() ? 9 : 2;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = Mth.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);
        float growth = this.options.shape() == PhantomEnergyOptions.SHOCKWAVE ? 0.2F + progress * 1.6F
                : this.options.shape() == PhantomEnergyOptions.SLASH ? 0.85F + progress * 0.25F : 0.6F + smoothStep(progress) * 0.55F;
        return this.quadSize * growth;
    }

    @Override
    public FacingCameraMode getFacingCameraMode() {
        return this.options.shape() == PhantomEnergyOptions.SHOCKWAVE ? GROUND_RING : super.getFacingCameraMode();
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    public Optional<ParticleLimit> getParticleLimit() {
        return Optional.of(LIMIT);
    }

    public static final class Provider implements ParticleProvider<PhantomEnergyOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(PhantomEnergyOptions options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return new PhantomEnergyParticle(options, level, x, y, z, xAux, yAux, zAux, this.sprites, random);
        }
    }
}
