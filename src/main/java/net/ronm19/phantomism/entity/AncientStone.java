package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;

public final class AncientStone extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE=42;
    private UUID session;
    private double travelled=.8D;
    private int life;
    private boolean impacted;
    public AncientStone(EntityType<? extends AncientStone> type,Level level){super(type,level);}
    public void configure(UUID session){this.session=session;}
    @Override protected Item getDefaultItem(){return Items.STONE_BRICKS;}
    @Override protected double getDefaultGravity(){return 0;}
    @Override protected float getAirDrag(){return 1;}
    @Override protected boolean canHitEntity(Entity entity){return super.canHitEntity(entity)&&getOwner() instanceof AncientPhantom source&&entity instanceof LivingEntity target&&source.validTarget(target);}
    @Override public void tick(){if(level() instanceof ServerLevel){if(!(getOwner() instanceof AncientPhantom source)||!source.enabled()||!source.session().equals(session)||travelled>=MAX_TRAVEL_DISTANCE-.000001D||++life>40){discard();return;}if(getDeltaMovement().length()>MAX_TRAVEL_DISTANCE-travelled){setDeltaMovement(getDeltaMovement().normalize().scale(MAX_TRAVEL_DISTANCE-travelled));}if(!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))){discard();return;}}Vec3 previous=position();super.tick();travelled+=position().distanceTo(previous);}
    @Override protected void onHit(HitResult hit){if(impacted||!(level() instanceof ServerLevel level)){return;}impacted=true;if(getOwner() instanceof AncientPhantom source&&source.session().equals(session)){Vec3 center=hit instanceof BlockHitResult block?hit.getLocation().add(Vec3.atLowerCornerOf(block.getDirection().getUnitVec3i()).scale(.08D)):hit.getLocation();source.stoneImpact(level,center);}discard();}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);travelled=MAX_TRAVEL_DISTANCE;session=null;}
}
