package net.ronm19.phantomism.validation;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.ronm19.phantomism.ability.*;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.entity.*;
import net.ronm19.phantomism.entity.ai.CelestialBond;
import net.ronm19.phantomism.entity.ai.CelestialPacts;
import net.ronm19.phantomism.entity.spawn.SolarEncounterSpawner;
import net.ronm19.phantomism.registry.PhantomismContent;


public final class SolarRuntimeChecks {
    private static final Vec3 POINT = new Vec3(5120.5D, 203, 5120.5D);
    private static final List<Entity> ENTITIES = new ArrayList<>();
    private static int assertions;
    @SubscribeEvent public static void start(ServerStartedEvent event) throws Exception {
        ServerLevel level = event.getServer().overworld();
        for (int across = 316; across <= 324; across++) { for (int depth = 316; depth <= 324; depth++) { level.getChunk(across, depth); } }
        for (BlockPos position : BlockPos.betweenClosed(5060, 200, 5060, 5180, 216, 5180)) { level.setBlock(position, position.getY() == 200 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2); }
        ServerPlayer owner = player(level, "SunKeeper");
        var failures = new ArrayList<String>();
        boolean previous = net.minecraft.SharedConstants.IS_RUNNING_IN_IDE; net.minecraft.SharedConstants.IS_RUNNING_IN_IDE = true;
        try {
            run("Solar foundations, taming and protected daylight spawning", failures, level, owner, () -> foundation(level, owner));
            run("Quick/Charged damage budgets, walls, cancellation and mounted shared clock", failures, level, owner, () -> beams(level, owner));
            run("Sanctuary, Blitz, bounded four-phase Supernova and mounted motion", failures, level, owner, () -> support(level, owner));
            run("Daylight recovery, persistence, safe cancellation and future Lunar pacts", failures, level, owner, () -> persistence(level, owner));
            run("Autonomous owner defense after manual recovery", failures, level, owner, () -> automation(level, owner));
            run("Staff roster, authorization, forged abilities, real cooldowns and replay", failures, level, owner, () -> staff(level, owner));
            if (!failures.isEmpty()) { throw new IllegalStateException(String.join("; ", failures)); }
            System.out.println("PHANTOMISM_SOLAR_STAFF_SERVER: " + assertions + " native assertions passed.");
        } finally { clear(level, owner); event.getServer().getPlayerList().getPlayersByUUID().remove(owner.getUUID()); owner.discard(); net.minecraft.SharedConstants.IS_RUNNING_IN_IDE = previous; }
    }
    private static ServerPlayer player(ServerLevel level, String name) {
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        player.connection = new Capture(player); player.setGameMode(GameType.SURVIVAL); player.setPos(POINT.add(-4, 0, 0)); level.addNewPlayer(player);
        level.getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player); return player;
    }
    private static void foundation(ServerLevel level, ServerPlayer owner) {
        require(PhantomDiscovery.values().length == 58 && PhantomDiscovery.SOLAR.ordinal() == 45, "Official Solar slot46");
        SolarPhantom source = source(level, owner);
        require(source.getMaxHealth() == 300 && source.getAttributeValue(Attributes.ARMOR) == 18 && source.getAttributeValue(Attributes.ATTACK_DAMAGE) == 20, "Native configured attributes");
        require(!source.validTarget(mob(level, POINT.add(0, 0, 8))), "No unprovoked hunting");
        for (var item : List.of(Items.GLOWSTONE_DUST, Items.GOLDEN_APPLE)) {
            SolarPhantom wild = source(level, null); owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 4));
            for (int attempt = 0; attempt < 3 && !wild.isTame(); attempt++) { wild.mobInteract(owner, InteractionHand.MAIN_HAND); }
            require(wild.isOwnedBy(owner), "Real native food taming: " + item);
        }
        SolarPhantom wild = source(level, null); owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhantomismContent.PHANTOM_STAFF.get()));
        owner.getMainHandItem().getItem().interactLivingEntity(owner.getMainHandItem(), owner, wild, InteractionHand.MAIN_HAND);
        require(wild.isOwnedBy(owner), "Staff instant tame preserved");
        for (var habitat : List.of(Biomes.MEADOW, Biomes.STONY_PEAKS, Biomes.SAVANNA, Biomes.BADLANDS)) {
            var biome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(habitat);
            for (int across = 316; across <= 324; across++) { for (int depth = 316; depth <= 324; depth++) { level.getChunk(across, depth).fillBiomesFromNoise((horizontal, height, vertical) -> biome); } }
            time(level, 6000);
            require(SolarPhantom.checkSpawnRules(PhantomismContent.SOLAR_PHANTOM.get(), level, EntitySpawnReason.NATURAL, BlockPos.containing(POINT), level.getRandom()), "Daylight habitat " + habitat);
            time(level, 18000);
            require(!SolarPhantom.checkSpawnRules(PhantomismContent.SOLAR_PHANTOM.get(), level, EntitySpawnReason.NATURAL, BlockPos.containing(POINT), level.getRandom()), "No night spawns");
        }
        require(SolarEncounterSpawner.MAX_NEARBY_WILD == 1, "Solitary encounter cap");
        boolean spawning = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS);
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, true, level.getServer());
        time(level, 6000); var spawner = new SolarEncounterSpawner();
        for (int tick = 0; tick < 64000; tick++) { spawner.tick(level, true); }
        level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, spawning, level.getServer());
        require(level.getEntitiesOfClass(SolarPhantom.class, new AABB(POINT, POINT).inflate(160), phantom -> !phantom.isTame()).size() == 1, "Native loaded-world encounter really spawns one solitary Solar");
        require(!source.hurtServer(level, source.damageSources().inFire(), 40), "Fire immunity");
        require(!source.hurtServer(level, source.damageSources().lava(), 40), "Lava immunity");
    }
    private static void beams(ServerLevel level, ServerPlayer owner) throws Exception {
        time(level, 18000);
        var source = source(level, owner); var enemy = mob(level, POINT.add(0, 0, 10)); source.setCombatTarget(enemy);
        require(source.annihilation(level, enemy, false), "Quick beam cast");
        for (int tick = 0; tick < 81; tick++) { advance(level, source); }
        near(1000 - enemy.getHealth(), 20, "Quick total includes only four scorch ticks");
        enemy.setHealth(1000); enemy.damageCooldownTime = 0;
        require(source.annihilation(level, enemy, true) && source.beamCharge() == 30, "Charged windup");
        for (int tick = 0; tick < 111; tick++) { advance(level, source); }
        near(1000 - enemy.getHealth(), 36, "Charged total does not double-hit impact target");
        var blocked = source(level, owner); blocked.setCombatTarget(enemy); wall(level, true);
        require(!blocked.annihilation(level, enemy, false), "Wall blocks targeted cast"); wall(level, false);
        require(blocked.annihilation(level, enemy, true), "Cancellable moving-target charge"); enemy.discard(); advance(level, blocked);
        require(blocked.beamCharge() == 0, "Stale target cancels charge without cooldown refund");
        var mounted = source(level, owner); owner.setPos(mounted.position()); owner.setYRot(0); owner.setXRot(0); require(owner.startRiding(mounted), "Mount setup");
        require(mounted.activateRiddenProjectileAbility(owner), "Mounted beam");
        require(mounted.cooldown(0) == 6 && !mounted.activateRiddenProjectileAbility(owner), "One authoritative six-tick shared beam clock");
        owner.stopRiding(); require(!mounted.annihilation(level, mob(level, POINT.add(0, 0, 8)), false), "Dismount does not bypass recovery");
    }
    private static void support(ServerLevel level, ServerPlayer owner) throws Exception {
        time(level, 18000);
        var source = source(level, owner); var ally = source(level, owner); ally.setPos(POINT.add(2, 0, 0)); ally.setHealth(100);
        require(source.sanctuary(level, false) && !source.sanctuary(level, true), "Sanctuary modes share lock");
        for (int tick = 0; tick < 160; tick++) { advance(level, source); }
        near(ally.getHealth(), 116, "Exact standard healing budget");
        require(ally.hasEffect(MobEffects.RESISTANCE), "Sanctuary really applies protection");
        var rush = source(level, owner); var enemy = mob(level, POINT.add(0, 0, 8)); rush.setCombatTarget(enemy);
        require(rush.blitz(level, enemy, false, false), "Blitz starts");
        for (int tick = 0; tick < 25; tick++) { advance(level, rush); }
        near(1000 - enemy.getHealth(), 18, "One hit per Blitz target");
        rush.discard(); enemy.discard();
        for (boolean protective : List.of(false, true)) {
            var ultimate = source(level, owner); var victim = mob(level, POINT.add(0, 0, 8)); ultimate.setCombatTarget(victim); ally.setHealth(100); ally.removeAllEffects();
            require(ultimate.supernova(level, protective), "Supernova form starts");
            require(!ultimate.supernova(level, !protective), "Ultimate forms cannot double-activate");
            for (int tick = 0; tick < 240; tick++) { advance(level, ultimate); }
            near(1000 - victim.getHealth(), protective ? 64 : 119, "Bounded native ultimate damage");
            near(ally.getHealth(), protective ? 136 : 112, "Bounded native ultimate healing");
            require(ultimate.ultimateTicks() == 0 && ultimate.cooldown(3) > 0, "Effects expire; shared recovery remains");
            ultimate.discard(); victim.discard();
        }
        var mounted = source(level, owner); owner.setPos(POINT); owner.startRiding(mounted); Vec3 before = mounted.position();
        require(mounted.blitz(level, null, true, true), "Mounted comet starts with no forced target"); advance(level, mounted);
        require(mounted.position().distanceToSqr(before) < .01D, "Server does not simulate client-authority mounted movement"); owner.stopRiding();
    }
    private static void persistence(ServerLevel level, ServerPlayer owner) throws Exception {
        var source = source(level, owner); time(level, 6000); advance(level, source);
        require(source.empowered() && source.getAttributeValue(Attributes.FLYING_SPEED) > .5D, "Natural sunlight flight bonus");
        source.sanctuary(level, true); int previous = source.cooldown(1);
        for (int tick = 0; tick < 20; tick++) { advance(level, source); }
        require(previous - source.cooldown(1) == 23, "15-percent real recovery acceleration");
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess()); source.saveWithoutId(output); source.discard();
        var loaded = PhantomismContent.SOLAR_PHANTOM.get().create(level, EntitySpawnReason.LOAD); loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult())); loaded.setNoAi(true); level.addFreshEntity(loaded); ENTITIES.add(loaded);
        require(loaded.isOwnedBy(owner) && loaded.cooldown(1) == previous - 23 && loaded.sanctuaryTicks() == 0, "Save retains recovery, never replays active zones");
        loaded.setOrder(AbstractPhantom.Order.STAY); require(!loaded.sanctuary(level, false), "Stay cancels manual and automatic abilities");
        require(CelestialBond.partner(loaded) == null, "Absent Lunar cannot become a fake partner");
        var other = player(level, "MoonKeeper"); var data = CelestialPacts.get(level.getServer()); long now = level.getGameTime();
        try {
            require(data.offer(owner, other.getUUID(), CelestialPacts.Mode.PERSISTENT, 0) && !data.allows(owner.getUUID(), other.getUUID(), now), "One owner cannot consent for another");
            require(data.offer(other, owner.getUUID(), CelestialPacts.Mode.PERSISTENT, 0) && data.allows(owner.getUUID(), other.getUUID(), now), "Mutual persistent agreement");
            var encoded = CelestialPacts.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow(); var restored = CelestialPacts.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            require(restored.allows(owner.getUUID(), other.getUUID(), now), "Native durable agreement codec");
            require(data.revoke(owner, other.getUUID()) && !data.allows(owner.getUUID(), other.getUUID(), now), "Either owner revokes both grants");
            data.offer(owner, other.getUUID(), CelestialPacts.Mode.TIMED, 20); data.offer(other, owner.getUUID(), CelestialPacts.Mode.TIMED, 20);
            require(!data.allows(owner.getUUID(), other.getUUID(), now + 21), "Timed expiry");
            data.offer(owner, other.getUUID(), CelestialPacts.Mode.SESSION, 0); data.offer(other, owner.getUUID(), CelestialPacts.Mode.SESSION, 0);
            restored = CelestialPacts.CODEC.parse(JsonOps.INSTANCE, CelestialPacts.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
            require(!restored.allows(owner.getUUID(), other.getUUID(), now), "Session never survives reload");
        } finally { data.revoke(owner, other.getUUID()); level.getServer().getPlayerList().getPlayersByUUID().remove(other.getUUID()); other.discard(); }
    }
    private static void staff(ServerLevel level, ServerPlayer owner) throws Exception {
        time(level, 18000); owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhantomismContent.PHANTOM_STAFF.get()));
        net.ronm19.phantomism.releaseaudit.StaffClockRegressionChecks.verify(level, owner);
        elapse(level, 4);
        var source = source(level, owner); var request = new StaffAbilityPacket(source.getUUID().toString(), "phantomism:solar_phantom/sanctuary", "", BlockPos.ZERO, true);
        require(StaffAbilityService.activate(owner, request) == StaffAbilityService.Result.SUCCESS, "Native manual Sanctuary proof");
        require(source.cooldown(1) == 500 && !source.sanctuary(level, true), "AI and alternate mode share manual recovery");
        require(StaffAbilityService.activate(owner, request) == StaffAbilityService.Result.THROTTLED, "Same-tick packet replay rejected"); elapse(level, 4);
        require(StaffAbilityService.activate(owner, request) == StaffAbilityService.Result.COOLDOWN, "New packet cannot bypass underlying recovery"); elapse(level, 4);
        require(StaffAbilityService.activate(owner, new StaffAbilityPacket(source.getUUID().toString(), "phantomism:gale_phantom/dominion", "", BlockPos.ZERO, true)) == StaffAbilityService.Result.ABILITY, "Cross-species forged ability rejected"); elapse(level, 4);
        var other = player(level, "NotTheOwner"); other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PhantomismContent.PHANTOM_STAFF.get()));
        require(StaffAbilityService.recipient(other, source.getUUID().toString()) == null, "Multiplayer ownership enforced"); other.discard(); level.getServer().getPlayerList().getPlayersByUUID().remove(other.getUUID());
        owner.setPos(POINT.add(129, 0, 0)); require(StaffAbilityService.recipient(owner, source.getUUID().toString()) == null, "Range enforced"); owner.setPos(POINT.add(-4, 0, 0));
        var enemy = mob(level, POINT.add(0, 0, 8)); source.setCombatTarget(enemy); elapse(level, 4);
        var beam = new StaffAbilityPacket(source.getUUID().toString(), "phantomism:solar_phantom/annihilation", enemy.getUUID().toString(), BlockPos.ZERO, true);
        require(StaffAbilityService.activate(owner, beam) == StaffAbilityService.Result.SUCCESS, "Validated targeted manual beam"); elapse(level, 4);
        enemy.discard(); require(StaffAbilityService.activate(owner, beam) == StaffAbilityService.Result.COOLDOWN, "Cooldown checked before stale targeting");
        source.discard(); elapse(level, 4); require(StaffAbilityService.activate(owner, request) == StaffAbilityService.Result.RECIPIENT, "Despawns cannot retarget reused numerical entity IDs");
        System.out.println("PHANTOMISM_STAFF_COVERAGE: all 58 native clocks plus Solar authorization, cooldown and replay checks passed.");
    }
    private static void automation(ServerLevel level, ServerPlayer owner) {
        time(level, 18000); var source = source(level, owner); source.setNoAi(false); var attacker = mob(level, POINT.add(0, 0, 10));
        attacker.setTarget(owner); owner.setLastHurtByMob(attacker);
        for (int tick = 0; tick < 120; tick++) { advance(level, source); }
        require(source.getTarget() == attacker && attacker.getHealth() < 1000, "Real Brain autonomously defends the owner");
        source.setOrder(AbstractPhantom.Order.STAY); require(source.ultimateTicks() == 0 && source.beamCharge() == 0 && source.dashTicks() == 0, "Stay cancels all critical actions");
        owner.setLastHurtByMob(null);
    }
    private static SolarPhantom source(ServerLevel level, ServerPlayer owner) { var source = PhantomismContent.SOLAR_PHANTOM.get().create(level, EntitySpawnReason.COMMAND); source.setPos(POINT); source.setNoAi(true); source.setNoGravity(true); if (owner != null) { source.tame(owner); } level.addFreshEntity(source); ENTITIES.add(source); return source; }
    private static Mob mob(ServerLevel level, Vec3 position) { var target = EntityTypes.PILLAGER.create(level, EntitySpawnReason.COMMAND); target.setPos(position); target.setNoAi(true); target.setNoGravity(true); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.setHealth(1000); level.addFreshEntity(target); ENTITIES.add(target); return target; }
    private static void time(ServerLevel level, long ticks) { level.clockManager().setTotalTicks(level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD), ticks); level.updateSkyBrightness(); }
    private static void elapse(ServerLevel level, int ticks) { ((ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime() + ticks); }
    private static void advance(ServerLevel level, SolarPhantom source) { elapse(level, 1); for (Entity entity : ENTITIES) { if (entity instanceof LivingEntity target) { target.damageCooldownTime = 0; } } source.commonTick(); source.tick(); }
    private static void wall(ServerLevel level, boolean solid) { for (BlockPos position : BlockPos.betweenClosed(5117, 201, 5125, 5124, 210, 5125)) { level.setBlock(position, solid ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2); } }
    private static void clear(ServerLevel level, ServerPlayer owner) { owner.stopRiding(); for (Entity entity : ENTITIES) { entity.discard(); } ENTITIES.clear(); for (var source : level.getEntitiesOfClass(SolarPhantom.class, new AABB(POINT, POINT).inflate(160))) { source.discard(); } wall(level, false); owner.setPos(POINT.add(-4, 0, 0)); owner.removeAllEffects(); owner.setHealth(20); owner.setLastHurtByMob(null); }
    private static void run(String name, List<String> failures, ServerLevel level, ServerPlayer owner, Scenario scenario) { try { scenario.run(); System.out.println("PHANTOMISM_SOLAR_STAFF_PASS: " + name); } catch (Throwable failure) { failures.add(name + ": " + failure); failure.printStackTrace(); } finally { clear(level, owner); } }
    private static void near(float value, float expected, String message) { require(Math.abs(value - expected) < .05F, message + " got=" + value + " expected=" + expected); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) { throw new IllegalStateException(message); } }
    private interface Scenario { void run() throws Exception; }
    private static final class Capture extends net.minecraft.server.network.ServerGamePacketListenerImpl { private Capture(ServerPlayer player) { super(player.level().getServer(), new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player, net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)); } @Override public boolean hasClientLoaded() { return true; } @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {} @Override public void send(net.minecraft.network.protocol.Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {} }
}
