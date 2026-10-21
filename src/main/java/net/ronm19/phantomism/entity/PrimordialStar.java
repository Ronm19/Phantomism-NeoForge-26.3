package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.vfx.PrimordialVfx;

public final class PrimordialStar extends ThrowableItemProjectile {
    public static final double MAX_TRAVEL_DISTANCE=42;
    private UUID session,excluded;
    private double travelled=.8D;
    private int life;
    private boolean fragment,impacted;
    public PrimordialStar(EntityType<? extends PrimordialStar> type,Level level){super(type,level);}
    public void configure(UUID session,boolean fragment){this.session=session;this.fragment=fragment;travelled=fragment?.2D:.8D;}
    public void exclude(UUID target){excluded=target;}
    public boolean fragment(){return fragment;}
    @Override protected Item getDefaultItem(){return Items.AMETHYST_SHARD;}
    @Override protected double getDefaultGravity(){return 0;}
    @Override protected float getAirDrag(){return 1;}
    @Override protected boolean canHitEntity(Entity entity){return super.canHitEntity(entity)&&!entity.getUUID().equals(excluded)&&getOwner() instanceof PrimordialPhantom source&&entity instanceof LivingEntity target&&source.validTarget(target);}
    @Override public void tick(){if(level() instanceof ServerLevel){double maximum=fragment?8:MAX_TRAVEL_DISTANCE;if(!(getOwner() instanceof PrimordialPhantom source)||!source.enabled()||!source.session().equals(session)||travelled>=maximum-.000001D||++life>(fragment?12:40)){discard();return;}if(getDeltaMovement().length()>maximum-travelled){setDeltaMovement(getDeltaMovement().normalize().scale(maximum-travelled));}if(!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))){discard();return;}}Vec3 previous=position();super.tick();travelled+=position().distanceTo(previous);}
    @Override protected void onHit(HitResult hit){if(impacted||!(level() instanceof ServerLevel level)){return;}impacted=true;if(getOwner() instanceof PrimordialPhantom source&&source.session().equals(session)){UUID primary=null;if(hit instanceof EntityHitResult contact&&contact.getEntity() instanceof LivingEntity target){source.damage(level,target,damageSources().thrown(this,source),fragment?3:10);primary=target.getUUID();}Vec3 center=hit instanceof BlockHitResult block?hit.getLocation().add(Vec3.atLowerCornerOf(block.getDirection().getUnitVec3i()).scale(.1D)):hit.getLocation();if(!fragment){source.shatter(level,center,getDeltaMovement().normalize(),primary);}else{PrimordialVfx.emit(source,PrimordialVfx.IMPACT,null,center,Vec3.ZERO,16,.6F);}}discard();}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);travelled=MAX_TRAVEL_DISTANCE;session=null;}
}
