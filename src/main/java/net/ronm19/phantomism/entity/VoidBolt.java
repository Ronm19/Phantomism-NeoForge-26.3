package net.ronm19.phantomism.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;
import net.ronm19.phantomism.Config;
import net.ronm19.phantomism.vfx.VoidVfx;

public final class VoidBolt extends ThrowableItemProjectile {
    private UUID session;
    private double travelled=.8D;
    private int life;
    private boolean impacted;
    public VoidBolt(EntityType<? extends VoidBolt> type,Level level){super(type,level);}
    public void configure(UUID session){this.session=session;}
    @Override protected Item getDefaultItem(){return Items.AMETHYST_SHARD;}
    @Override protected double getDefaultGravity(){return 0;}
    @Override protected float getAirDrag(){return 1;}
    @Override protected boolean canHitEntity(Entity entity){return super.canHitEntity(entity)&&getOwner() instanceof VoidPhantom source&&entity instanceof LivingEntity target&&source.validTarget(target);}
    @Override public void tick(){if(level() instanceof ServerLevel){double maximum=Config.voidValue("boltRange");if(!(getOwner() instanceof VoidPhantom source)||!source.enabled()||!source.session().equals(session)||travelled>=maximum-.000001D||++life>80||!Double.isFinite(getDeltaMovement().lengthSqr())){discard();return;}if(getDeltaMovement().length()>maximum-travelled){setDeltaMovement(getDeltaMovement().normalize().scale(maximum-travelled));}if(!source.isLoadedFlightBox(getBoundingBox().expandTowards(getDeltaMovement()))){discard();return;}}Vec3 previous=position();super.tick();travelled+=position().distanceTo(previous);}
    @Override protected void onHit(HitResult hit){if(impacted||!(level() instanceof ServerLevel level)){return;}impacted=true;if(getOwner() instanceof VoidPhantom source&&source.session().equals(session)){if(hit instanceof EntityHitResult contact&&contact.getEntity() instanceof LivingEntity target){source.damage(level,target,damageSources().thrown(this,source),(float)Config.voidValue("boltDamage"));}VoidVfx.emit(source,VoidVfx.IMPACT,null,hit.getLocation(),Vec3.ZERO,20,1.4F);}discard();}
    @Override protected void readAdditionalSaveData(ValueInput input){super.readAdditionalSaveData(input);travelled=42;session=null;}
}
