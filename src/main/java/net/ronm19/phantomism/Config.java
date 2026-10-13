package net.ronm19.phantomism;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_DIVE_UNDEAD_BONUS = BUILDER
            .comment("Extra damage added to a Black Phantom's diving bite against undead enemies. 2 damage = 1 heart.")
            .translation("config.phantomism.blackPhantom.diveUndeadBonus")
            .defineInRange("blackPhantom.diveUndeadBonus", 2.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_DIVE_FLYING_BONUS = BUILDER
            .comment("Extra diving bite damage against flying enemies. Stacks with other applicable dive bonuses.")
            .translation("config.phantomism.blackPhantom.diveFlyingBonus")
            .defineInRange("blackPhantom.diveFlyingBonus", 2.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_DIVE_PHANTOM_BONUS = BUILDER
            .comment("Additional diving bite damage against vanilla hostile Phantoms. Does not allow attacking companion phantoms.")
            .translation("config.phantomism.blackPhantom.divePhantomBonus")
            .defineInRange("blackPhantom.divePhantomBonus", 4.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_SKY_KNIGHT_SPEED_BONUS = BUILDER
            .comment("Flat movement-speed attribute bonus during Sky Knight. The default base movement speed is 0.25.")
            .translation("config.phantomism.blackPhantom.skyKnightSpeedBonus")
            .defineInRange("blackPhantom.skyKnightSpeedBonus", 0.05D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue BLACK_PHANTOM_SKY_KNIGHT_ATTACK_BONUS = BUILDER
            .comment("Flat melee damage bonus during Sky Knight. 2 damage = 1 heart.")
            .translation("config.phantomism.blackPhantom.skyKnightAttackBonus")
            .defineInRange("blackPhantom.skyKnightAttackBonus", 1.0D, 0.0D, 20.0D);

    public static final ModConfigSpec.IntValue PHANTOM_HOME_RADIUS = BUILDER
            .comment("Horizontal radius in blocks around the assigned bed for Wander, activities and base defense.",
                    "Phantoms take short local patrol steps and only inspect loaded chunks; this does not keep a whole base loaded.",
                    "Use 200-300 for large bases, or up to 1000. Existing configured values are preserved.")
            .translation("config.phantomism.phantom.homeRadius")
            .defineInRange("phantom.homeRadius", 200, 4, 1000);

    public static final ModConfigSpec.IntValue FAMILY_DEFENSE_RADIUS = BUILDER
            .comment("Nearby adult companions with the same owner defend a hurt pet within this many blocks.",
                    "Only living attackers trigger defense. Owner/family hits, environmental damage and cleared targets do not.",
                    "Does not take control from riders or replace active targets. Attackers must be within the defender's awareness range.")
            .translation("config.phantomism.phantom.familyDefenseRadius")
            .defineInRange("phantom.familyDefenseRadius", 32, 4, 64);

    public static final PhantomStats BLACK_PHANTOM_STATS = stats("blackPhantom", 32.0D, 4.0D, 2.0D, 0.1D);
    public static final PhantomStats FIRE_PHANTOM_STATS = stats("firePhantom", 36.0D, 5.0D, 3.0D, 0.15D);
    public static final PhantomStats FROST_PHANTOM_STATS = stats("frostPhantom", 36.0D, 4.0D, 4.0D, 0.15D);
    public static final PhantomStats EARTH_PHANTOM_STATS = stats("earthPhantom", 44.0D, 4.0D, 6.0D, 0.25D);
    public static final PhantomStats WATER_PHANTOM_STATS = stats("waterPhantom", 38.0D, 4.0D, 4.0D, 0.15D);
    public static final PhantomStats STORM_PHANTOM_STATS = stats("stormPhantom", 38.0D, 4.0D, 3.0D, 0.15D);
    public static final PhantomStats SAND_PHANTOM_STATS = stats("sandPhantom", 36.0D, 4.0D, 3.0D, 0.15D);
    public static final PhantomStats GOLD_PHANTOM_STATS = stats("goldPhantom", 36.0D, 4.0D, 5.0D, 0.15D);
    public static final PhantomStats CHERRY_PHANTOM_STATS = stats("cherryPhantom", 34.0D, 3.0D, 3.0D, 0.1D);

    static {
        BUILDER.comment("Shared Home patrol and family protection settings.")
                .translation("config.phantomism.phantom").push("phantom").pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    private static PhantomStats stats(String species, double health, double damage, double armor, double knockback) {
        BUILDER.comment("Settings for this species. Defaults preserve its normal balance.")
                .translation("config.phantomism." + species).push(species);
        BUILDER.comment("Base attributes for wild and tamed adults and babies. Ability bonuses are added separately.",
                        "Changes apply to existing loaded phantoms within one second. Raising health does not heal existing pets.",
                        "Very high speeds and awareness distances can affect navigation and server performance.")
                .translation("config.phantomism." + species + ".stats").push("stats");
        PhantomStats stats = new PhantomStats(
                stat(species, "maxHealth", health, 1.0D, 1024.0D, "Maximum health points. 2 points = 1 heart. Lowering this clamps current health; raising it does not heal existing pets."),
                stat(species, "attackDamage", damage, 0.0D, 2048.0D, "Base melee damage before armor and ability bonuses. 2 damage = 1 heart. Does not change projectile or area damage."),
                stat(species, "armor", armor, 0.0D, 30.0D, "Armor points used by Minecraft's damage reduction formula. 2 points = 1 armor icon."),
                stat(species, "armorToughness", 0.0D, 0.0D, 20.0D, "Reduces how strongly large hits penetrate armor. Vanilla diamond armor pieces provide 2 toughness each."),
                stat(species, "knockbackResistance", knockback, 0.0D, 1.0D, "Knockback resistance from 0 (none) to 1 (complete)."),
                stat(species, "movementSpeed", 0.25D, 0.01D, 2.0D, "Base movement-speed attribute. Ability movement bonuses are added to this value."),
                stat(species, "flyingSpeed", 0.4D, 0.01D, 2.0D, "Base flying-speed attribute. Also scales mounted horizontal movement; vertical aiming controls stay unchanged."),
                stat(species, "followRange", 32.0D, 4.0D, 128.0D, "Awareness and autonomous combat range in blocks. Home patrol radius is separate. Larger values increase nearby-entity scanning work."));
        BUILDER.pop(2);
        return stats;
    }

    private static ModConfigSpec.DoubleValue stat(String species, String key, double value, double minimum, double maximum, String description) {
        return BUILDER.comment(description).translation("config.phantomism." + species + ".stats." + key)
                .defineInRange(key, value, minimum, maximum);
    }

    public record PhantomStats(ModConfigSpec.DoubleValue maxHealth, ModConfigSpec.DoubleValue attackDamage,
            ModConfigSpec.DoubleValue armor, ModConfigSpec.DoubleValue armorToughness,
            ModConfigSpec.DoubleValue knockbackResistance, ModConfigSpec.DoubleValue movementSpeed,
            ModConfigSpec.DoubleValue flyingSpeed, ModConfigSpec.DoubleValue followRange) {
        public void apply(LivingEntity phantom) {
            if (!SPEC.isLoaded()) {
                return;
            }
            applyAttribute(phantom, Attributes.MAX_HEALTH, this.maxHealth.get());
            applyAttribute(phantom, Attributes.ATTACK_DAMAGE, this.attackDamage.get());
            applyAttribute(phantom, Attributes.ARMOR, this.armor.get());
            applyAttribute(phantom, Attributes.ARMOR_TOUGHNESS, this.armorToughness.get());
            applyAttribute(phantom, Attributes.KNOCKBACK_RESISTANCE, this.knockbackResistance.get());
            applyAttribute(phantom, Attributes.MOVEMENT_SPEED, this.movementSpeed.get());
            applyAttribute(phantom, Attributes.FLYING_SPEED, this.flyingSpeed.get());
            applyAttribute(phantom, Attributes.FOLLOW_RANGE, this.followRange.get());
            if (phantom.getHealth() > phantom.getMaxHealth()) {
                phantom.setHealth(phantom.getMaxHealth());
            }
        }

        private static void applyAttribute(LivingEntity phantom, Holder<Attribute> attribute, double value) {
            var instance = phantom.getAttribute(attribute);
            if (instance != null && instance.getBaseValue() != value) {
                instance.setBaseValue(value);
            }
        }
    }
}
