package net.ronm19.phantomism.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.ronm19.phantomism.client.gui.PhantomCodexPortraits;
import net.ronm19.phantomism.advancement.PhantomDiscovery;
import net.ronm19.phantomism.codex.CodexCatalog;
import net.ronm19.phantomism.codex.CodexPacket;

public final class PhantomCodexScreen extends Screen {
    private static final int INK = 0xFFE5E2F4, MUTED = 0xFFB1AACB, ACCENT = 0xFFC4A6F2;
    private static final String[] TABS = {"start", "species", "crafting", "progress"};
    private static final String[] FILTERS = {"all", "unseen", "found", "holiday"};
    private CodexCatalog.Snapshot snapshot;
    private List<CodexCatalog.Entry> filtered = List.of();
    private final List<ContentLine> lines = new ArrayList<>();
    private final UUID viewer;
    private final String dimension;
    private EditBox search;
    private Button filterButton, previous, next;
    private int tab, filter, selected = 1, listScroll, detailScroll;
    private int left, top, panelWidth, panelHeight, listWidth, contentTop, contentBottom;
    private String query = "";
    private record ContentLine(FormattedCharSequence text, int color, String item, int indent, int height) {}

    public PhantomCodexScreen(CodexCatalog.Snapshot snapshot) {
        super(Component.translatable("screen.phantomism.codex.title"));
        this.snapshot = snapshot;
        Minecraft client = Minecraft.getInstance();
        viewer = client == null || client.player == null ? null : client.player.getUUID();
        dimension = client == null || client.level == null ? "" : client.level.dimension().toString();
    }

    public void update(CodexCatalog.Snapshot snapshot) { this.snapshot = snapshot; refreshList(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { PhantomCodexPortraits.clear(); super.removed(); }
    @Override public void tick() {
        super.tick();
        if (viewer == null) { return; }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !viewer.equals(client.player.getUUID()) || !dimension.equals(client.level.dimension().toString())) { onClose(); }
    }

    private static Component ui(String key, Object... arguments) { return Component.translatable("codex.ui." + key, arguments); }
    private static Component translated(String key) {
        Component value = Component.translatable(key);
        return value.getString().equals(key) ? ui("profile.missing") : value;
    }

    @Override protected void init() {
        panelWidth = Math.min(820, width - 16); panelHeight = Math.min(520, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listWidth = Math.clamp(panelWidth / 3, 110, 220); contentTop = top + 61; contentBottom = top + panelHeight - 29;
        int tabWidth = (panelWidth - 20) / 4;
        for (int index = 0; index < TABS.length; index++) {
            final int target = index;
            addRenderableWidget(Button.builder(ui("tab." + TABS[index]), button -> selectTab(target)).bounds(left + 10 + index * tabWidth, top + 32, tabWidth - 3, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("×"), button -> onClose()).bounds(left + panelWidth - 27, top + 6, 18, 18).build());
        search = addRenderableWidget(new EditBox(font, left + 12, contentTop + 1, listWidth - 16, 18, ui("search")));
        search.setMaxLength(128); search.setHint(ui("search.hint")); search.setValue(query);
        search.setResponder(value -> { query = value; selected = 0; listScroll = 0; detailScroll = 0; refreshList(); });
        filterButton = addRenderableWidget(Button.builder(ui("filter." + FILTERS[filter]), button -> {
            filter = (filter + 1) % FILTERS.length; selected = 0; listScroll = 0; detailScroll = 0; refreshList();
        }).bounds(left + 12, contentTop + 24, listWidth - 16, 18).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> cycle(-1)).bounds(left + 12, top + panelHeight - 23, 24, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> cycle(1)).bounds(left + 40, top + panelHeight - 23, 24, 18).build());
        addRenderableWidget(Button.builder(ui("refresh"), button -> ClientPacketDistributor.sendToServer(new CodexPacket.Request())).bounds(left + panelWidth - 79, top + panelHeight - 23, 66, 18).build());
        refreshList();
    }

    private void selectTab(int target) { tab = target; detailScroll = 0; refreshList(); }
    public static String name(CodexCatalog.Entry entry) {
        if (entry.number() == 59) { return net.ronm19.phantomism.progression.CreatorRevelation.name(entry.discovered()).getString(); }
        if (!entry.officialName().isEmpty()) { return entry.officialName().contains("Phantom") ? entry.officialName() : entry.officialName() + " Phantom"; }
        String label = Component.translatable(entry.nameKey()).getString();
        return label.equals(entry.nameKey()) ? readable(entry.species()) : label;
    }
    public static String readable(String identifier) {
        Identifier parsed = Identifier.tryParse(identifier);
        String value = parsed == null ? identifier : parsed.getPath();
        return java.util.Arrays.stream(value.replace('/', ' ').replace('_', ' ').split(" +"))
                .filter(word -> !word.isEmpty()).map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1)).collect(java.util.stream.Collectors.joining(" "));
    }
    private static Component displayName(CodexCatalog.Entry entry) {
        return entry.number() == 59 ? net.ronm19.phantomism.progression.CreatorRevelation.name(entry.discovered()) : Component.literal(name(entry));
    }
    public static String habitat(String identifier) {
        Identifier biome = Identifier.tryParse(identifier);
        if (biome == null) { return readable(identifier); }
        String key = "biome." + biome.getNamespace() + "." + biome.getPath().replace('/', '.');
        String label = Component.translatable(key).getString();
        return label.equals(key) ? readable(identifier) : label;
    }
    public static boolean matches(CodexCatalog.Entry entry, String query, int filter) {
        boolean category = switch (filter) {
            case 1 -> entry.available() && !entry.discovered();
            case 2 -> entry.discovered();
            case 3 -> entry.category().equals("Holiday");
            default -> true;
        };
        String searchable = String.format(Locale.ROOT, "%03d %s %s %s %s", entry.number(), name(entry), entry.gender(), entry.category(),
                String.join(" ", entry.habitats().stream().map(PhantomCodexScreen::habitat).toList()));
        return category && searchable.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private void refreshList() {
        filtered = snapshot.entries().stream().filter(entry -> matches(entry, query, filter)).toList();
        if (filtered.isEmpty()) { selected = 0; detailScroll = 0; }
        else if (filtered.stream().noneMatch(entry -> entry.number() == selected)) { selected = filtered.getFirst().number(); detailScroll = 0; }
        listScroll = Math.clamp(listScroll, 0, Math.max(0, filtered.size() - visibleRows()));
        if (search != null) {
            search.visible = tab == 1; filterButton.visible = tab == 1; filterButton.setMessage(ui("filter." + FILTERS[filter]));
            previous.visible = tab == 1; next.visible = tab == 1; previous.active = !filtered.isEmpty(); next.active = !filtered.isEmpty();
        }
        rebuildText(); clampScroll();
    }
    private int visibleRows() { return Math.max(1, (contentBottom - contentTop - 48) / 26); }
    private void cycle(int direction) {
        if (filtered.isEmpty()) { return; }
        int current = 0;
        for (int index = 0; index < filtered.size(); index++) { if (filtered.get(index).number() == selected) { current = index; break; } }
        int target = Math.floorMod(current + direction, filtered.size()); selected = filtered.get(target).number();
        listScroll = Math.clamp(listScroll, Math.max(0, target - visibleRows() + 1), target); detailScroll = 0; refreshList();
    }
    private CodexCatalog.Entry selection() { return filtered.stream().filter(entry -> entry.number() == selected).findFirst().orElse(null); }
    private int detailLeft() { return left + (tab == 1 ? listWidth + 12 : 16); }
    private int textWidth() { return left + panelWidth - 22 - detailLeft(); }
    private int detailStart() { return contentTop + (tab == 1 ? Math.clamp((contentBottom - contentTop) / 4, 40, 64) : 24); }
    private int lineHeight() { return lines.stream().mapToInt(ContentLine::height).sum(); }
    private int totalHeight() { return tab == 2 ? snapshot.recipes().stream().mapToInt(this::recipeHeight).sum() : lineHeight(); }
    private void clampScroll() { detailScroll = Math.clamp(detailScroll, 0, Math.max(0, totalHeight() - Math.max(1, contentBottom - detailStart()))); }
    private void blank() { lines.add(new ContentLine(FormattedCharSequence.EMPTY, INK, "", 0, 5)); }
    private void text(Component value, int color) {
        for (FormattedCharSequence line : font.split(value, Math.max(30, textWidth()))) { lines.add(new ContentLine(line, color, "", 0, 11)); }
    }
    private void paragraph(Component value) { text(value, INK); blank(); }
    private void heading(String key) { blank(); text(ui("section." + key), ACCENT); }
    private void itemLine(String identifier) {
        List<FormattedCharSequence> label = font.split(stack(identifier, 1).getHoverName(), Math.max(25, textWidth() - 24));
        lines.add(new ContentLine(label.isEmpty() ? FormattedCharSequence.EMPTY : label.getFirst(), INK, identifier, 24, 20));
        for (int index = 1; index < label.size(); index++) { lines.add(new ContentLine(label.get(index), INK, "", 24, 11)); }
    }
    private Component status(CodexCatalog.Entry entry) { return ui(entry.discovered() ? "status.found" : entry.available() ? "status.unseen" : "status.unknown"); }

    private void rebuildText() {
        lines.clear(); if (font == null) { return; }
        if (tab == 0) {
            for (String part : List.of("welcome", "finding", "taming", "staff", "home", "riding", "codex", "care")) {
                text(ui("start." + part + ".title"), ACCENT); paragraph(ui("start." + part));
            }
        } else if (tab == 3) {
            text(ui("progress.total", snapshot.discovered()), ACCENT); blank();
            paragraph(ui("progress.percent", CodexCatalog.number(snapshot.discovered() * 100D / CodexCatalog.TOTAL)));
            paragraph(ui("progress.remaining", CodexCatalog.TOTAL - snapshot.discovered()));
            paragraph(ui("progress.holiday", snapshot.entries().stream().filter(entry -> entry.category().equals("Holiday") && entry.discovered()).count()));
            paragraph(ui("progress.keep"));
        } else if (tab == 1 && selection() != null) {
            var entry = selection();
            if (!entry.available()) { paragraph(ui("unavailable")); return; }
            paragraph(ui("rarity", entry.rarity().isEmpty() ? ui("unknown") : ui("rarity." + entry.rarity().toLowerCase(Locale.ROOT))));
            paragraph(ui("habitats", entry.habitats().isEmpty() ? ui("special.habitat") : String.join(", ", entry.habitats().stream().map(PhantomCodexScreen::habitat).toList())));
            paragraph(translated(entry.spawnKey()));
            if (!entry.discovered()) { paragraph(ui("locked")); return; }
            CodexCatalog.Guide guide = entry.guide();
            if (guide == null) { paragraph(ui("profile.missing")); return; }
            heading("overview"); paragraph(translated(guide.overviewKey()));
            heading("combat"); var combat = guide.combat();
            text(ui("role", translated(combat.roleKey())), MUTED);
            text(ui("health", CodexCatalog.number(combat.health()), CodexCatalog.number(combat.health() / 2)), INK);
            if (combat.attack() > 0) { text(ui("attack", CodexCatalog.number(combat.attack())), INK); }
            if (combat.armor() > 0) { text(ui("armor", CodexCatalog.number(combat.armor())), INK); }
            blank(); for (String defense : combat.defenses()) { paragraph(translated(defense)); }
            heading("abilities");
            for (CodexCatalog.Ability ability : guide.abilities()) {
                text(translated(ability.nameKey()), ACCENT); text(translated(ability.descriptionKey()), INK);
                if (ability.cooldownTicks() > 0) { text(ui("cooldown", CodexCatalog.seconds(ability.cooldownTicks())), MUTED); }
                blank();
            }
            heading("taming"); var taming = guide.taming();
            if (entry.number() == PhantomDiscovery.CREATOR.codexNumber()) {
                for (String item : taming.items()) { itemLine(item); }
                // The Eye is an acquisition ritual, not a food roll or Survival Staff tame.
                if (!taming.noteKey().isEmpty()) { paragraph(translated(taming.noteKey())); }
            } else {
                if (taming.staffOnly()) { paragraph(ui("staff.only")); }
                else { paragraph(ui("tame.interact")); }
                if (taming.foods().isEmpty()) {
                    for (String item : taming.items()) { itemLine(item); }
                    if (!taming.staffOnly()) { paragraph(taming.guaranteedAttempt() == 0 ? ui("tame.independent_chance", CodexCatalog.number(taming.chance() * 100D)) : ui("tame.chance", CodexCatalog.number(taming.chance() * 100D), taming.guaranteedAttempt())); }
                } else {
                    for (var food : taming.foods()) { itemLine(food.item()); paragraph(food.guaranteedAttempt() == 0 ? ui("tame.independent_chance", CodexCatalog.number(food.chance() * 100D)) : ui("tame.chance", CodexCatalog.number(food.chance() * 100D), food.guaranteedAttempt())); }
                }
                if (!taming.staffOnly()) { paragraph(ui("staff.alternative")); }
                if (!taming.noteKey().isEmpty()) { paragraph(translated(taming.noteKey())); }
                if (!taming.lure().isEmpty()) { paragraph(ui("lure", stack(taming.lure(), 1).getHoverName())); itemLine(taming.lure()); }
            }
            heading("riding"); var riding = guide.riding();
            paragraph(ui(riding.rideable() ? "rideable" : "unrideable")); paragraph(translated(riding.descriptionKey()));
            if (riding.secondary() && riding.secondaryCooldown() > 0) { text(ui("secondary.cooldown", CodexCatalog.seconds(riding.secondaryCooldown())), MUTED); blank(); }
            if (!guide.notes().isEmpty()) { heading("notes"); for (String note : guide.notes()) { paragraph(translated(note)); } }
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0141321);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 2, ACCENT);
        graphics.fill(left + 8, top + 55, left + panelWidth - 8, top + 56, 0xFF514463);
        graphics.text(font, title, left + 13, top + 9, INK);
        String progress = snapshot.discovered() + " / 58";
        graphics.text(font, progress, left + panelWidth - 38 - font.width(progress), top + 10, MUTED);
        graphics.fill(left + 13, top + 23, left + panelWidth - 13, top + 26, 0xFF393148);
        graphics.fill(left + 13, top + 23, left + 13 + (panelWidth - 26) * snapshot.discovered() / 58, top + 26, ACCENT);
        if (tab == 1) {
            drawList(graphics, mouseX, mouseY);
            CodexCatalog.Entry entry = selection();
            if (entry != null) {
                int size = detailStart() - contentTop - 4;
                portrait(graphics, entry, detailLeft(), contentTop, size);
                Component header = Component.literal(String.format(Locale.ROOT, "#%03d ", entry.number())).append(displayName(entry));
                int titleLeft = detailLeft() + size + 6, down = contentTop + 2;
                for (FormattedCharSequence line : font.split(header, Math.max(24, textWidth() - size - 7)).stream().limit(3).toList()) { graphics.text(font, line, titleLeft, down, INK); down += 10; }
                graphics.text(font, font.plainSubstrByWidth(status(entry).getString(), Math.max(24, textWidth() - size - 7)), titleLeft, Math.max(down, detailStart() - 13), MUTED);
                if (mouseY >= contentTop && mouseY < detailStart() && mouseX >= detailLeft() && mouseX < left + panelWidth - 16) { graphics.setTooltipForNextFrame(header.copy().append(" - ").append(status(entry)), mouseX, mouseY); }
            }
        } else { graphics.text(font, ui("tab." + TABS[tab]), detailLeft(), contentTop + 5, ACCENT); }
        graphics.enableScissor(detailLeft(), detailStart(), left + panelWidth - 12, contentBottom);
        if (tab == 2) { drawRecipes(graphics, mouseX, mouseY); }
        else {
            int positionY = detailStart() + 2 - detailScroll;
            for (ContentLine line : lines) {
                if (positionY + line.height() > detailStart() && positionY < contentBottom) {
                    if (!line.item().isEmpty()) { icon(graphics, line.item(), 1, detailLeft(), positionY, mouseX, mouseY); }
                    graphics.text(font, line.text(), detailLeft() + line.indent(), positionY + (line.item().isEmpty() ? 0 : 4), line.color());
                }
                positionY += line.height();
            }
        }
        graphics.disableScissor();
        scrollbar(graphics, left + panelWidth - 11, detailStart(), contentBottom, detailScroll, Math.max(1, totalHeight()), contentBottom - detailStart());
        if (panelWidth > 350) { graphics.text(font, ui("scroll"), left + 74, top + panelHeight - 18, MUTED); }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void portrait(GuiGraphicsExtractor graphics, CodexCatalog.Entry entry, int positionX, int positionY, int size) {
        graphics.fill(positionX, positionY, positionX + size, positionY + size, 0xFF211C30);
        if (entry.species().isEmpty() || !PhantomCodexPortraits.extract(graphics, entry.species(), positionX, positionY, size)) {
            graphics.centeredText(font, Component.literal("?"), positionX + size / 2, positionY + (size - 8) / 2, MUTED);
        }
    }
    private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(left + listWidth + 2, contentTop, left + listWidth + 3, contentBottom, 0xFF514463);
        int start = contentTop + 48;
        for (int row = 0; row < visibleRows() && row + listScroll < filtered.size(); row++) {
            var entry = filtered.get(row + listScroll); int positionY = start + row * 26;
            boolean hover = mouseX >= left + 10 && mouseX < left + listWidth - 3 && mouseY >= positionY && mouseY < positionY + 25;
            graphics.fill(left + 10, positionY, left + listWidth - 3, positionY + 25, entry.number() == selected ? 0xFF453650 : hover ? 0xFF292538 : 0xFF1C1929);
            portrait(graphics, entry, left + 12, positionY + 2, 21);
            graphics.text(font, String.format(Locale.ROOT, "%03d", entry.number()), left + 37, positionY + 3, entry.discovered() ? 0xFF93D5B2 : MUTED);
            graphics.text(font, Component.literal(font.plainSubstrByWidth(name(entry), listWidth - 48)).withStyle(displayName(entry).getStyle()), left + 37, positionY + 14, entry.available() ? INK : MUTED);
            if (hover) { graphics.setTooltipForNextFrame(displayName(entry).copy().append(" - ").append(status(entry)), mouseX, mouseY); }
        }
        if (filtered.isEmpty()) { graphics.text(font, ui("empty"), left + 14, start + 6, MUTED); }
        scrollbar(graphics, left + listWidth - 1, start, contentBottom, listScroll, Math.max(1, filtered.size()), visibleRows());
    }
    private static ItemStack stack(String id, int count) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)), count); }
    private void icon(GuiGraphicsExtractor graphics, String id, int count, int positionX, int positionY, int mouseX, int mouseY) {
        ItemStack item = stack(id, count); graphics.item(item, positionX, positionY); graphics.itemDecorations(font, item, positionX, positionY);
        if (mouseX >= positionX && mouseX < positionX + 16 && mouseY >= positionY && mouseY < positionY + 16 && mouseY >= detailStart() && mouseY < contentBottom) { graphics.setTooltipForNextFrame(font, item, mouseX, mouseY); }
    }
    private String ingredients(CodexCatalog.RecipePage recipe) {
        var quantities = new LinkedHashMap<String, Integer>();
        for (List<String> alternatives : recipe.ingredients()) { if (!alternatives.isEmpty()) { quantities.merge(alternatives.getFirst(), 1, Integer::sum); } }
        return String.join(", ", quantities.entrySet().stream().map(entry -> entry.getValue() + " × " + stack(entry.getKey(), 1).getHoverName().getString()).toList());
    }
    private int recipeHeight(CodexCatalog.RecipePage recipe) { return Math.max(128, 112 + font.split(Component.literal(ingredients(recipe)), Math.max(30, textWidth() - 16)).size() * 10); }
    private void drawRecipes(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int positionY = detailStart() + 2 - detailScroll;
        for (var recipe : snapshot.recipes()) {
            int positionX = detailLeft(), height = recipeHeight(recipe);
            graphics.fill(positionX, positionY, positionX + textWidth() - 1, positionY + height - 6, 0xFF252132);
            graphics.text(font, Component.translatable(recipe.nameKey()), positionX + 8, positionY + 7, INK);
            graphics.text(font, ui(recipe.available() ? recipe.shapeless() ? "recipe.shapeless" : "recipe.shaped" : "recipe.missing"), positionX + 8, positionY + 20, MUTED);
            if (recipe.available()) {
                for (int slot = 0; slot < 9; slot++) {
                    int across = positionX + 9 + slot % 3 * 20, down = positionY + 35 + slot / 3 * 20;
                    graphics.fill(across, down, across + 19, down + 19, 0xFF100E19);
                    List<String> alternatives = recipe.ingredients().get(slot);
                    if (!alternatives.isEmpty()) { icon(graphics, alternatives.get((int)((System.currentTimeMillis() / 1500) % alternatives.size())), 1, across + 1, down + 1, mouseX, mouseY); }
                }
                graphics.text(font, "→", positionX + 78, positionY + 60, ACCENT);
                graphics.fill(positionX + 98, positionY + 53, positionX + 120, positionY + 75, 0xFF100E19);
                icon(graphics, recipe.output(), recipe.outputCount(), positionX + 101, positionY + 56, mouseX, mouseY);
                int row = positionY + 99;
                for (FormattedCharSequence line : font.split(Component.literal(ingredients(recipe)), Math.max(30, textWidth() - 16))) { graphics.text(font, line, positionX + 8, row, MUTED); row += 10; }
            }
            positionY += height;
        }
    }
    private void scrollbar(GuiGraphicsExtractor graphics, int positionX, int positionY, int bottom, int scroll, int total, int visible) {
        if (total <= visible || bottom <= positionY) { return; }
        int height = bottom - positionY, thumb = Math.min(height, Math.max(9, height * visible / total));
        int position = positionY + (height - thumb) * scroll / Math.max(1, total - visible);
        graphics.fill(positionX, positionY, positionX + 2, bottom, 0xFF393148); graphics.fill(positionX, position, positionX + 2, position + thumb, ACCENT);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 1 && mouseX >= left + 10 && mouseX < left + listWidth && mouseY >= contentTop + 48 && mouseY < contentBottom) {
            listScroll = Math.clamp(listScroll - (int)Math.signum(scrollY) * 3, 0, Math.max(0, filtered.size() - visibleRows()));
        } else if (mouseX >= detailLeft() && mouseX < left + panelWidth - 10 && mouseY >= detailStart() && mouseY < contentBottom) {
            detailScroll -= (int)Math.signum(scrollY) * 33; clampScroll();
        }
        return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tab == 1 && event.button() == 0 && event.x() >= left + 10 && event.x() < left + listWidth - 3
                && event.y() >= contentTop + 48 && event.y() < Math.min(contentBottom, contentTop + 48 + visibleRows() * 26)) {
            int index = listScroll + (int)(event.y() - contentTop - 48) / 26;
            if (index < filtered.size()) { selected = filtered.get(index).number(); detailScroll = 0; rebuildText(); clampScroll(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
