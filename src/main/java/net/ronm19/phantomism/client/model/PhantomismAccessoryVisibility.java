package net.ronm19.phantomism.client.model;

public record PhantomismAccessoryVisibility(
        boolean mushrooms,
        boolean spikes,
        boolean horns,
        boolean tendrils,
        boolean halo,
        boolean wings,
        boolean tail,
        boolean fangs
) {
    public PhantomismAccessoryVisibility(boolean mushrooms, boolean spikes, boolean horns, boolean tendrils,
                                        boolean halo, boolean wings, boolean tail) {
        this(mushrooms, spikes, horns, tendrils, halo, wings, tail, false);
    }

    public PhantomismAccessoryVisibility withFangs(boolean visible) {
        return new PhantomismAccessoryVisibility(this.mushrooms, this.spikes, this.horns, this.tendrils,
                this.halo, this.wings, this.tail, visible);
    }

    public static final PhantomismAccessoryVisibility ALL_VISIBLE = new PhantomismAccessoryVisibility(
            true,
            true,
            true,
            true,
            true,
            true,
            true
    );

    public static final PhantomismAccessoryVisibility NO_ACCESSORIES = new PhantomismAccessoryVisibility(
            false,
            false,
            false,
            false,
            false,
            true,
            true
    );
}
