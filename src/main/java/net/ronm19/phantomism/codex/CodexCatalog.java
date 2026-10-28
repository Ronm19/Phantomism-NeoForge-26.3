package net.ronm19.phantomism.codex;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.ronm19.phantomism.Phantomism;
import net.ronm19.phantomism.advancement.PhantomDiscovery;

public final class CodexCatalog {
    public static final int TOTAL = 58;
    public static final Gson JSON = new Gson();
    private static final java.util.Set<Integer> FEMALE = java.util.Set.of(3, 5, 6, 9, 11, 12, 15, 21, 22, 25, 28, 29, 30, 33, 38, 42, 49, 50, 53, 54, 58);


    private CodexCatalog() {
    }

    public static final int VERSION = 5;
    public static final int MAX_DATA = 196608;

    public record Entry(int number, String nameKey, String officialName, String gender, String category,
            String species, boolean available, boolean discovered, String rarity,
            List<String> habitats, String spawnKey, Guide guide) {
    }

    public record Combat(double health, double attack, double armor, String roleKey, List<String> defenses) {}
    public record Ability(String nameKey, String descriptionKey, int cooldownTicks) {}
    public record TamingFood(String item, float chance, int guaranteedAttempt) {}
    public record Taming(List<String> items, float chance, int guaranteedAttempt, boolean staffOnly, String lure, String noteKey, List<TamingFood> foods) {
        public Taming(List<String> items, float chance, int guaranteedAttempt, boolean staffOnly, String lure, String noteKey) { this(items, chance, guaranteedAttempt, staffOnly, lure, noteKey, List.of()); }
    }
    public record Riding(boolean rideable, String descriptionKey, boolean secondary, int secondaryCooldown) {}
    public record Guide(String overviewKey, Combat combat, List<Ability> abilities, Taming taming, Riding riding, List<String> notes) {}

    public record RecipePage(String nameKey, String id, boolean available, boolean shapeless,
            List<List<String>> ingredients, String output, int outputCount) {
    }

    public record Snapshot(int version, List<Entry> entries, List<RecipePage> recipes) {
        public int discovered() {
            return (int) entries.stream().filter(entry -> entry.available() && entry.discovered()).count();
        }
    }

    public static Snapshot snapshot(ServerPlayer player) {
        List<Entry> entries = new ArrayList<>();
        for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
            int number = discovery.codexNumber();
            var advancement = player.level().getServer().getAdvancements().get(discovery.advancementId());
            boolean discovered = advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
            if (discovery == PhantomDiscovery.CREATOR) { discovered = discovered && net.ronm19.phantomism.progression.CreatorRevelation.revealed(player); }
            String species = discovery.modelName();
            Guide guide = null;
            if (discovered) {
                var phantom = discovery.createPreview(player.level());
                if (phantom != null) {
                    guide = CodexFieldNotes.guide(discovery, phantom);
                }
            }
            var biomeRegistry = player.level().registryAccess().lookupOrThrow(Registries.BIOME);
            var tag = TagKey.create(Registries.BIOME, id(species + "_spawns"));
            List<String> habitats = biomeRegistry.get(tag).map(set -> set.stream().map(holder -> holder.unwrapKey().map(key -> key.identifier().toString()).orElse("Unknown biome")).sorted().toList()).orElse(List.of());
            entries.add(new Entry(number, discovery.entityType().getDescriptionId(), number == 7 ? "Desert" : "",
                    gender(number), number == 59 ? "Creator" : number >= 52 ? "Holiday" : "Standard", species,
                    advancement != null, discovered, discovery.rarity().name(), habitats, "codex.field." + species + ".spawn", guide));
        }
        entries.sort(java.util.Comparator.comparingInt(Entry::number));
        return new Snapshot(VERSION, List.copyOf(entries), List.of(recipe(player, "phantom_codex"), recipe(player, "phantom_staff"), recipe(player, "eye_of_creation")));
    }

    public static boolean valid(Snapshot snapshot) {
        try {
            if (snapshot == null || snapshot.version() != VERSION || snapshot.entries() == null || snapshot.entries().size() != TOTAL
                    || snapshot.recipes() == null || snapshot.recipes().size() != 3) { return false; }
            List<PhantomDiscovery> roster = java.util.Arrays.stream(PhantomDiscovery.values())
                    .sorted(java.util.Comparator.comparingInt(PhantomDiscovery::codexNumber)).toList();
            if (roster.size() != TOTAL) { return false; }
            for (int index = 0; index < TOTAL; index++) {
                PhantomDiscovery discovery = roster.get(index);
                int number = discovery.codexNumber();
                Entry entry = snapshot.entries().get(index);
                if (entry == null || entry.number() != number || !text(entry.nameKey(), 160) || !text(entry.officialName(), 80)
                        || !entry.gender().equals(gender(number)) || !List.of("Standard", "Creator", "Holiday").contains(entry.category())
                        || !text(entry.rarity(), 32) || !keys(entry.habitats(), 128, 256) || !text(entry.spawnKey(), 160)
                        || entry.discovered() && !entry.available() || !entry.discovered() && entry.guide() != null) { return false; }
                String expected = discovery.modelName();
                if (!expected.equals(entry.species()) || expected.isEmpty() && (entry.available() || entry.discovered() || entry.guide() != null)) { return false; }
                for (String habitat : entry.habitats()) { if (Identifier.tryParse(habitat) == null) { return false; } }
                Guide guide = entry.guide();
                if (guide != null) {
                    if (!text(guide.overviewKey(), 160) || !keys(guide.notes(), 12, 160) || guide.abilities() == null || guide.abilities().size() > 20) { return false; }
                    Combat combat = guide.combat();
                    if (combat == null || !numberValid(combat.health()) || !numberValid(combat.attack()) || !numberValid(combat.armor())
                            || !text(combat.roleKey(), 160) || !keys(combat.defenses(), 12, 160)) { return false; }
                    for (Ability ability : guide.abilities()) {
                        if (ability == null || !text(ability.nameKey(), 160) || !text(ability.descriptionKey(), 160) || ability.cooldownTicks() < 0 || ability.cooldownTicks() > 72000) { return false; }
                    }
                    Taming taming = guide.taming();
                    if (taming == null || !keys(taming.items(), 8, 160) || taming.items().isEmpty() || !Float.isFinite(taming.chance()) || taming.chance() < 0 || taming.chance() > 1
                            || taming.guaranteedAttempt() < 0 || taming.guaranteedAttempt() > 100 || !text(taming.lure(), 160) || !text(taming.noteKey(), 160)) { return false; }
                    for (String item : taming.items()) { if (!itemValid(item) || item.equals("minecraft:air")) { return false; } }
                    if (!taming.lure().isEmpty() && !itemValid(taming.lure())) { return false; }
                    if (taming.foods() == null || taming.foods().size() > 8) { return false; }
                    for (TamingFood food : taming.foods()) { if (food == null || !itemValid(food.item()) || !taming.items().contains(food.item()) || !Float.isFinite(food.chance()) || food.chance() < 0 || food.chance() > 1 || food.guaranteedAttempt() < 0 || food.guaranteedAttempt() > 100) { return false; } }
                    Riding riding = guide.riding();
                    if (riding == null || !text(riding.descriptionKey(), 160) || riding.secondaryCooldown() < 0 || riding.secondaryCooldown() > 72000) { return false; }
                }
            }
            var recipeIds = new java.util.HashSet<String>();
            for (RecipePage recipe : snapshot.recipes()) {
                if (recipe == null || !text(recipe.nameKey(), 160) || !List.of(id("phantom_codex").toString(), id("phantom_staff").toString(), id("eye_of_creation").toString()).contains(recipe.id())
                        || !recipeIds.add(recipe.id())
                        || recipe.ingredients() == null || recipe.ingredients().size() != 9 || !itemValid(recipe.output()) || recipe.outputCount() < 0 || recipe.outputCount() > 99) { return false; }
                for (List<String> choices : recipe.ingredients()) {
                    if (!keys(choices, 256, 160)) { return false; }
                    for (String choice : choices) { if (!itemValid(choice)) { return false; } }
                }
            }
            return true;
        } catch (RuntimeException failure) { return false; }
    }

    private static boolean text(String value, int limit) { return value != null && value.length() <= limit; }
    private static boolean keys(List<String> values, int count, int length) { return values != null && values.size() <= count && values.stream().allMatch(value -> text(value, length)); }
    private static boolean numberValid(double value) { return Double.isFinite(value) && value >= 0 && value <= 1000000; }
    private static boolean itemValid(String value) { Identifier item = Identifier.tryParse(value); return item != null && BuiltInRegistries.ITEM.containsKey(item); }

    public static String gender(int number) {
        return FEMALE.contains(number) ? "Female" : "Male";
    }

    public static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static String seconds(int ticks) {
        return number(ticks / 20D) + " s";
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Phantomism.MOD_ID, path);
    }

    private static List<String> alternatives(Optional<Ingredient> ingredient) {
        return ingredient.map(value -> value.items().limit(256).map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).toList()).orElse(List.of());
    }

    private static RecipePage recipe(ServerPlayer player, String name) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id(name));
        var holder = Optional.ofNullable(player.level().recipeAccess().recipeMap().byKey(key)).map(value -> value.value());
        List<List<String>> grid = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            grid.add(List.of());
        }
        ItemStack output = ItemStack.EMPTY;
        boolean shapeless = false;
        if (holder.isPresent() && holder.get() instanceof ShapedRecipe shaped && shaped.getWidth() <= 3 && shaped.getHeight() <= 3) {
            int offset = (3 - shaped.getWidth()) / 2;
            for (int row = 0; row < shaped.getHeight(); row++) {
                for (int column = 0; column < shaped.getWidth(); column++) {
                    grid.set(row * 3 + column + offset, alternatives(shaped.pattern.ingredients().get(row * shaped.getWidth() + column)));
                }
            }
            output = shaped.assemble(CraftingInput.EMPTY);
        } else if (holder.isPresent() && holder.get() instanceof ShapelessRecipe recipe) {
            shapeless = true;
            List<Ingredient> ingredients = recipe.placementInfo().ingredients();
            for (int index = 0; index < Math.min(9, ingredients.size()); index++) {
                grid.set(index, alternatives(Optional.of(ingredients.get(index))));
            }
            output = recipe.assemble(CraftingInput.EMPTY);
        }
        return new RecipePage("item.phantomism." + name, key.identifier().toString(), !output.isEmpty(), shapeless,
                List.copyOf(grid), BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount());
    }
}
