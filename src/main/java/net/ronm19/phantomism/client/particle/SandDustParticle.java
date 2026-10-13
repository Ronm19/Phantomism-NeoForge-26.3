package net.ronm19.phantomism.client.particle;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class SandDustParticle extends SingleQuadParticle {
    private static final ParticleLimit LIMIT = new ParticleLimit(512);
    private final SpriteSet sprites;
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double radius;
    private final float angularSpeed;
    private double angle;

    private SandDustParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.centerX = x;
        this.centerY = y + 0.2D + random.nextDouble() * 1.2D;
        this.centerZ = z;
        this.radius = 0.8D + Math.sqrt(random.nextDouble()) * 2.6D;
        this.angle = random.nextDouble() * Math.PI * 2.0D;
        this.angularSpeed = 0.055F + random.nextFloat() * 0.035F;
        this.lifetime = 28 + random.nextInt(13);
        this.quadSize = 0.65F + random.nextFloat() * 0.4F;
        this.hasPhysics = false;
        this.alpha = 0.0F;
        this.setColor(1.0F, 0.84F, 0.30F);
        this.updatePosition(0.0F);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.setSpriteFromAge(sprites);
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
        this.angle += this.angularSpeed;
        this.roll += this.angularSpeed * 0.35F;
        this.updatePosition(progress);
        this.setColor(Mth.lerp(progress, 1.0F, 0.74F), Mth.lerp(progress, 0.84F, 0.53F), Mth.lerp(progress, 0.30F, 0.15F));
        this.alpha = 0.42F * smoothStep(this.age / 5.0F) * (1.0F - smoothStep((progress - 0.55F) / 0.45F));
        this.setSpriteFromAge(this.sprites);
    }

    private void updatePosition(float progress) {
        double distance = this.radius * (1.0D + progress * 0.25D);
        this.setPos(this.centerX + Math.cos(this.angle) * distance,
                this.centerY + this.age * 0.018D + Math.sin(this.angle * 1.7D) * 0.12D,
                this.centerZ + Math.sin(this.angle) * distance);
    }

    private static float smoothStep(float value) {
        float bounded = Mth.clamp(value, 0.0F, 1.0F);
        return bounded * bounded * (3.0F - 2.0F * bounded);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * (0.55F + 0.85F * smoothStep((this.age + partialTick) / this.lifetime));
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

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xAux, double yAux, double zAux, RandomSource random) {
            return new SandDustParticle(level, x, y, z, this.sprites, random);
        }
    }
}
