package net.ronm19.phantomism.client.model;

public record PhantomismAccessoryVisibility(
        boolean mushrooms,
        boolean spikes,
        boolean horns,
        boolean tendrils,
        boolean halo,
        boolean wings,
        boolean tail
) {
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
