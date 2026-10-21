package net.ronm19.phantomism.client.vfx;

public record VfxSequence(int charge, int hold, int collapse) {
    public float progress(float age) { return Math.clamp(age / Math.max(1, this.charge + this.hold + this.collapse), 0, 1); }
    public float opacity(float age) { return Math.min(Math.clamp(age / Math.max(1, this.charge), 0, 1), Math.clamp((this.charge + this.hold + this.collapse - age) / Math.max(1, this.collapse), 0, 1)); }
    public float handAngle(float age) { return age < this.charge ? age * .25F : age < this.charge + this.hold ? this.charge * .25F : this.charge * .25F + (age - this.charge - this.hold) * .8F; }
    public static double reversible(double progress, double phase, boolean reverse) { double time = reverse ? 1 - progress : progress; return Math.sin(time * Math.PI * 2 + phase); }
}
