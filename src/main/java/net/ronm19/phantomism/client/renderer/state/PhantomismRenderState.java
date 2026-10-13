package net.ronm19.phantomism.client.renderer.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.ronm19.phantomism.client.model.PhantomismAccessoryVisibility;

public class PhantomismRenderState extends LivingEntityRenderState {
    public float flapTime;
    public boolean skyKnightActive;
    public PhantomismAccessoryVisibility accessories = PhantomismAccessoryVisibility.NO_ACCESSORIES;
}
