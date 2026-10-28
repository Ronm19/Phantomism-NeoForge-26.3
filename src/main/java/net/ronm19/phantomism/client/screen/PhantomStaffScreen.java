package net.ronm19.phantomism.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.ronm19.phantomism.PhantomismClient;
import net.ronm19.phantomism.ability.PhantomAbilities;
import net.ronm19.phantomism.ability.StaffAbilityPacket;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.GolemPhantom;
import net.ronm19.phantomism.entity.RiftPhantom;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.network.PhantomismNetwork.Action;
import net.ronm19.phantomism.network.PhantomismNetwork.StaffCommandPayload;

public final class PhantomStaffScreen extends Screen {
    private record Recipient(int entityId, UUID uuid, Component name) {}
    public record Layout(int x, int y, int width, int height, int rows, int columns) {
        public static Layout fit(int width, int height) {
            int panelWidth = Math.max(1, Math.min(328, width - 12)), panelHeight = Math.max(1, Math.min(246, height - 12));
            return new Layout(Math.max(0, width - panelWidth - 6), Math.max(0, height - panelHeight - 6), panelWidth, panelHeight,
                    Math.max(1, (panelHeight - 119) / 23), panelWidth >= 240 ? 2 : 1);
        }
    }
    private enum Category {
        MOVEMENT(Action.FOLLOW, Action.STAY, Action.GUARD, Action.GUARD_POSITION, Action.WANDER),
        COMBAT(Action.ATTACK_TARGET, Action.CLEAR_TARGET),
        ACTIVITIES(Action.ACTIVITY_NONE, Action.HARVEST, Action.HUNT, Action.COLLECT),
        HOME_RECALL(Action.SET_HOME, Action.RETURN_HOME, Action.RECALL),
        ABILITIES(),
        SPATIAL(Action.RIFT_ANCHOR, Action.RIFT_PORTAL_A, Action.RIFT_PORTAL_B, Action.RIFT_ENTER, Action.RIFT_SEND_ALLY, Action.RIFT_CLOSE),
        MINING(Action.GOLEM_PRECISION, Action.GOLEM_AREA_A, Action.GOLEM_AREA_B, Action.GOLEM_OBSIDIAN, Action.GOLEM_DELIVER, Action.GOLEM_CANCEL),
        TITANIUM(Action.GOLEM_CHEST, Action.GOLEM_OWNER, Action.GOLEM_STATUS, Action.GOLEM_RAM_BREAK);
        final List<Action> actions;
        Category(Action... actions) { this.actions = List.of(actions); }
        String key() { return "screen.phantomism.staff.category." + name().toLowerCase(Locale.ROOT); }
    }
    private final List<Recipient> recipients = new ArrayList<>();
    private List<PhantomAbilities.View> abilities = List.of();
    private int selectedRecipientIndex, categoryIndex, page, ticks;
    private Layout layout;
    private Component activity = Component.empty();
    private long snapshotAt = Long.MIN_VALUE;

    public PhantomStaffScreen() { super(Component.translatable("screen.phantomism.staff.title")); StaffAbilityClient.cancel(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void extractTransparentBackground(GuiGraphicsExtractor graphics) {}

    private AbstractPhantom phantom() {
        var client = Minecraft.getInstance();
        if (recipients.isEmpty() || client.level == null || !(client.level.getEntity(selected().entityId()) instanceof AbstractPhantom phantom)
                || !phantom.getUUID().equals(selected().uuid()) || client.player == null || !phantom.isOwnedBy(client.player) || !phantom.isAlive()) { return null; }
        return phantom;
    }
    private Recipient selected() { return recipients.get(Math.clamp(selectedRecipientIndex, 0, recipients.size() - 1)); }
    private List<Category> categories() {
        var result = new ArrayList<>(List.of(Category.MOVEMENT, Category.COMBAT, Category.ACTIVITIES, Category.HOME_RECALL, Category.ABILITIES));
        if (phantom() instanceof RiftPhantom) { result.add(Category.SPATIAL); }
        if (phantom() instanceof GolemPhantom) { result.add(Category.MINING); result.add(Category.TITANIUM); }
        return result;
    }
    private Category category() { var options = categories(); return options.get(Math.floorMod(categoryIndex, options.size())); }
    private void refreshRecipients() {
        UUID previous = recipients.isEmpty() ? null : selected().uuid();
        recipients.clear(); recipients.add(new Recipient(-1, null, Component.translatable("screen.phantomism.staff.all_nearby")));
        var client = Minecraft.getInstance();
        if (client.player != null && client.level != null) {
            client.level.getEntitiesOfClass(AbstractPhantom.class, client.player.getBoundingBox().inflate(128), phantom -> phantom.isAlive() && phantom.isOwnedBy(client.player))
                    .stream().sorted(Comparator.comparingDouble(client.player::distanceToSqr)).limit(256)
                    .forEach(phantom -> recipients.add(new Recipient(phantom.getId(), phantom.getUUID(), phantom instanceof net.ronm19.phantomism.entity.CreatorPhantom ? CreatorIdentityClient.name() : phantom.getDisplayName())));
        }
        selectedRecipientIndex = 0;
        for (int index = 1; index < recipients.size(); index++) { if (recipients.get(index).uuid().equals(previous)) { selectedRecipientIndex = index; break; } }
    }

    @Override protected void init() {
        refreshRecipients(); layout = Layout.fit(width, height);
        int left = layout.x(), top = layout.y(), span = layout.width();
        widget("<", left + 7, top + 25, 20, () -> cycleRecipient(-1));
        widget(">", left + span - 27, top + 25, 20, () -> cycleRecipient(1));
        widget("×", left + span - 25, top + 3, 18, this::onClose);
        addRenderableWidget(Button.builder(Component.translatable("screen.phantomism.staff.select_all"), ignored -> selectAllNearby())
                .bounds(left + span - 115, top + 25, 84, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.phantomism.staff.select_all_tooltip"))).build());
        widget("<", left + 7, top + 51, 20, () -> cycleCategory(-1));
        widget(">", left + span - 27, top + 51, 20, () -> cycleCategory(1));
        int total = category() == Category.ABILITIES ? abilities.size() : category().actions.size();
        int columns = category() == Category.ABILITIES ? 1 : layout.columns();
        int capacity = layout.rows() * columns, pages = Math.max(1, (total + capacity - 1) / capacity);
        page = Math.clamp(page, 0, pages - 1);
        if (category() == Category.ABILITIES && selected().uuid() == null) {
            var button = Button.builder(Component.translatable("staff.ability.select_one"), ignored -> {}).bounds(left + 7, top + 80, span - 14, 20)
                    .tooltip(Tooltip.create(Component.translatable("staff.ability.group_disabled"))).build();
            button.active = false; addRenderableWidget(button);
        } else {
            for (int index = page * capacity; index < Math.min(total, (page + 1) * capacity); index++) {
                int cell = index - page * capacity, column = cell % columns, row = cell / columns;
                int buttonWidth = (span - 14 - (columns - 1) * 5) / columns;
                int buttonX = left + 7 + column * (buttonWidth + 5), buttonY = top + 80 + row * 23;
                if (category() == Category.ABILITIES) { abilityButton(abilities.get(index), buttonX, buttonY, buttonWidth); }
                else {
                    Action action = category().actions.get(index);
                    String suffix = action.name().toLowerCase(Locale.ROOT);
                    addRenderableWidget(Button.builder(fit(Component.translatable("screen.phantomism.staff.action." + suffix), buttonWidth - 8), ignored -> select(action))
                            .bounds(buttonX, buttonY, buttonWidth, 20).tooltip(Tooltip.create(Component.translatable("screen.phantomism.staff.tooltip." + suffix))).build());
                }
            }
        }
        widget("<", left + 7, top + layout.height() - 36, 20, () -> { page = Math.floorMod(page - 1, pages); rebuildWidgets(); });
        widget(">", left + span - 27, top + layout.height() - 36, 20, () -> { page = (page + 1) % pages; rebuildWidgets(); });
        updateActivity();
    }

    private Component fit(Component component, int pixels) { return Component.literal(font.plainSubstrByWidth(component.getString(), Math.max(1, pixels))).withStyle(component.getStyle()); }
    private void widget(String text, int left, int top, int width, Runnable action) { addRenderableWidget(Button.builder(Component.literal(text), ignored -> action.run()).bounds(left, top, width, 18).build()); }
    private void abilityButton(PhantomAbilities.View ability, int left, int top, int width) {
        Component status = ability.automatic() ? Component.translatable("staff.ability.automatic") : ability.remaining() > 0
                ? Component.translatable("staff.ability.cooldown", String.format(Locale.ROOT, "%.1f", ability.remaining() / 20D))
                : Component.translatable("staff.ability.status." + ability.status().name().toLowerCase(Locale.ROOT));
        Component label = Component.translatable(ability.nameKey()).append(" · ").append(status);
        Component tooltip = StaffAbilityTips.tooltip(ability, status);
        var button = Button.builder(fit(label, width - 8), ignored -> StaffAbilityClient.activate(selected().uuid(), ability))
                .bounds(left, top, width, 20).tooltip(Tooltip.create(tooltip)).build();
        var client = Minecraft.getInstance();
        button.active = phantom() != null && ability.status() == PhantomAbilities.Status.READY && client.level != null && client.level.getGameTime() - snapshotAt < 40;
        addRenderableWidget(button);
    }

    public void abilities(String identity, List<PhantomAbilities.View> entries) {
        if (selected().uuid() == null || !selected().uuid().toString().equals(identity)) { return; }
        abilities = List.copyOf(entries); snapshotAt = Minecraft.getInstance().level.getGameTime(); rebuildWidgets();
    }
    private void request() {
        if (selected().uuid() != null && category() == Category.ABILITIES) { ClientPacketDistributor.sendToServer(new StaffAbilityPacket(selected().uuid().toString(), "", "", net.minecraft.core.BlockPos.ZERO, false)); }
    }
    private void cycleRecipient(int direction) {
        selectedRecipientIndex = Math.floorMod(selectedRecipientIndex + direction, recipients.size());
        categoryIndex = Math.min(categoryIndex, categories().size() - 1); page = 0; abilities = List.of(); snapshotAt = Long.MIN_VALUE;
        rebuildWidgets(); request();
    }
    private void selectAllNearby() {
        Category previous = category();
        selectedRecipientIndex = 0;
        if (!List.of(Category.MOVEMENT, Category.COMBAT, Category.ACTIVITIES, Category.HOME_RECALL).contains(previous)) { categoryIndex = 0; }
        page = 0; abilities = List.of(); snapshotAt = Long.MIN_VALUE;
        rebuildWidgets();
    }
    private void cycleCategory(int direction) { categoryIndex = Math.floorMod(categoryIndex + direction, categories().size()); page = 0; rebuildWidgets(); request(); }
    private void updateActivity() {
        var source = phantom();
        if (source instanceof GolemPhantom golem) { activity = Component.translatable("screen.phantomism.staff.golem_work", Component.translatable("work.phantomism.golem." + golem.workStatus()), golem.occupiedCargoSlots()); }
        else if (source instanceof net.ronm19.phantomism.entity.AutumnPhantom autumn) { activity = Component.translatable("screen.phantomism.staff.autumn_status", Component.translatable("state.phantomism.autumn_harvest." + autumn.harvest().mode().name().toLowerCase(Locale.ROOT))); }
        else if (source instanceof net.ronm19.phantomism.entity.LunarPhantom lunar) { activity = lunarStatus(lunar.charge(), lunar.pressure(), lunar.veiled()); }
        else if (source instanceof net.ronm19.phantomism.entity.InfernalPhantom infernal) { activity = infernalStatus(infernal.heat(), infernal.guarded()); }
        else if (source != null) { activity = Component.translatable("screen.phantomism.staff.activity_status", activityName(source.getTask())); }
        else {
            var client = Minecraft.getInstance();
            List<Task> tasks = client.level == null || client.player == null ? List.of() : client.level.getEntitiesOfClass(AbstractPhantom.class, client.player.getBoundingBox().inflate(128), phantom -> phantom.isOwnedBy(client.player) && phantom.isAlive()).stream().map(AbstractPhantom::getTask).toList();
            activity = groupActivitySummary(tasks);
        }
    }
    private static Component lunarStatus(int charge, int pressure, boolean veiled) { return Component.translatable("screen.phantomism.staff.lunar_status", Math.clamp(charge, 0, 3), Math.clamp(pressure, 0, 3), Component.translatable(veiled ? "state.phantomism.moonlit_veil.active" : "state.phantomism.moonlit_veil.automatic")); }
    private static Component infernalStatus(int heat, boolean guarded) { heat = Math.clamp(heat, 0, 3); return Component.translatable("screen.phantomism.staff.infernal_status", heat, Component.translatable("state.phantomism.infernal_heat." + heat), Component.translatable(guarded ? "state.phantomism.cinder_guard.active" : "state.phantomism.cinder_guard.automatic")); }
    private static Component activityName(Task task) { return Component.translatable("screen.phantomism.staff.action." + (task == Task.NONE ? "activity_none" : task.name().toLowerCase(Locale.ROOT))); }
    private static Component groupActivitySummary(List<Task> tasks) {
        if (tasks.isEmpty()) { return Component.translatable("screen.phantomism.staff.group_activity_empty"); }
        Task first = tasks.getFirst();
        return tasks.stream().anyMatch(task -> task != first) ? Component.translatable("screen.phantomism.staff.group_activity_mixed") : Component.translatable("screen.phantomism.staff.group_activity_status", activityName(first));
    }
    private static List<Component> groupActivityDetails(List<Task> tasks) {
        List<Component> result = new ArrayList<>();
        for (Task task : Task.values()) { long count = tasks.stream().filter(value -> value == task).count(); if (count > 0) { result.add(Component.translatable("screen.phantomism.staff.group_activity_count", activityName(task), count)); } }
        return result;
    }
    @Override public void tick() { if (++ticks % 20 == 0) { refreshRecipients(); updateActivity(); request(); rebuildWidgets(); } }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = layout.x(), top = layout.y(), middle = left + layout.width() / 2;
        graphics.fill(left, top, left + layout.width(), top + layout.height(), 0xEC10121A);
        graphics.fill(left, top, left + layout.width(), top + 1, 0xFF9B78D0);
        graphics.text(font, title, left + 8, top + 8, 0xFFFFFFFF);
        graphics.text(font, fit(selected().name(), layout.width() - 152), left + 33, top + 30, 0xFFFFFFFF);
        graphics.centeredText(font, fit(Component.translatable(category().key()), layout.width() - 64), middle, top + 56, 0xFFCCB4ED);
        graphics.centeredText(font, Component.translatable("staff.page", page + 1), middle, top + layout.height() - 31, 0xFFCCCCCC);
        graphics.centeredText(font, fit(activity, layout.width() - 14), middle, top + layout.height() - 12, 0xFFBBBBBB);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (mouseX >= left && mouseX < left + layout.width() && mouseY >= top + layout.height() - 20 && mouseY < top + layout.height()) { graphics.setComponentTooltipForNextFrame(font, List.of(activity), mouseX, mouseY); }
        if (mouseX >= left + 30 && mouseX < left + layout.width() - 119 && mouseY >= top + 25 && mouseY < top + 43) {
            graphics.setComponentTooltipForNextFrame(font, List.of(selected().name()), mouseX, mouseY);
        }
    }

    private void select(Action action) {
        int recipientId = selected().entityId();
        if (action.name().startsWith("GOLEM_") && (action == Action.GOLEM_PRECISION || action == Action.GOLEM_AREA_A || action == Action.GOLEM_AREA_B || action == Action.GOLEM_CHEST)) { PhantomismClient.beginBlockSelection(action, recipientId); Minecraft.getInstance().setScreenAndShow(null); return; }
        if (action == Action.RIFT_SEND_ALLY) { PhantomismClient.beginRiftCompanionSelection(recipientId); Minecraft.getInstance().setScreenAndShow(null); return; }
        if (action == Action.RIFT_ANCHOR || action == Action.RIFT_PORTAL_A || action == Action.RIFT_PORTAL_B || action == Action.ATTACK_TARGET || action == Action.SET_HOME || action == Action.GUARD_POSITION) {
            if (action == Action.ATTACK_TARGET) { PhantomismClient.beginAttackTargetSelection(recipientId); } else { PhantomismClient.beginBlockSelection(action, recipientId); }
            Minecraft.getInstance().setScreenAndShow(null);
            if (Minecraft.getInstance().player != null) { Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("screen.phantomism.staff." + (action == Action.SET_HOME ? "select_home" : action == Action.GUARD_POSITION ? "select_guard" : action == Action.ATTACK_TARGET ? "select_target" : "select_rift"))); }
            return;
        }
        ClientPacketDistributor.sendToServer(new StaffCommandPayload(action.networkId(), recipientId, -1));
    }
}
