package net.ronm19.phantomism.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.ronm19.phantomism.PhantomismClient;
import net.ronm19.phantomism.entity.AbstractPhantom;
import net.ronm19.phantomism.entity.ai.PhantomAssignments.Task;
import net.ronm19.phantomism.network.PhantomismNetwork.Action;
import net.ronm19.phantomism.network.PhantomismNetwork.StaffCommandPayload;

public final class PhantomStaffScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 196;
    private static final int PANEL_HEIGHT = 158;
    private final List<Recipient> recipients = new ArrayList<>();
    private int selectedRecipientIndex;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int selectedCategoryIndex;

    public PhantomStaffScreen() {
        super(Component.translatable("screen.phantomism.staff.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void init() {
        int selectedEntityId = this.selectedRecipientId();
        this.refreshRecipients();
        this.selectedRecipientIndex = this.findRecipientIndex(selectedEntityId);
        this.panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(120, this.width - 20));
        this.panelX = Math.max(10, this.width - this.panelWidth - 10);
        this.panelY = Math.max(10, this.height - PANEL_HEIGHT - 10);

        int arrowY = this.panelY + 23;
        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.cycleRecipient(-1))
                .bounds(this.panelX + 8, arrowY, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.cycleRecipient(1))
                .bounds(this.panelX + this.panelWidth - 28, arrowY, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("×"), button -> this.onClose())
                .bounds(this.panelX + this.panelWidth - 22, this.panelY + 3, 18, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.cycleCategory(-1))
                .bounds(this.panelX + 8, this.panelY + 47, 20, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.cycleCategory(1))
                .bounds(this.panelX + this.panelWidth - 28, this.panelY + 47, 20, 18).build());
        Action[] actions = Category.values()[this.selectedCategoryIndex].actions;
        int gap = 5;
        int buttonWidth = (this.panelWidth - 24 - gap) / 2;
        int left = this.panelX + 8;
        for (int index = 0; index < actions.length; index++) {
            int row = index / 2;
            int column = index % 2;
            Action action = actions[index];
            int x = left + column * (buttonWidth + gap);
            int y = this.panelY + 71 + row * 22;
            int actionWidth = index == actions.length - 1 && column == 0 ? this.panelWidth - 16 : buttonWidth;
            this.addRenderableWidget(Button.builder(
                    Component.translatable("screen.phantomism.staff.action." + action.name().toLowerCase(java.util.Locale.ROOT)),
                    button -> this.select(action)
            ).bounds(x, y, actionWidth, 19)
                    .tooltip(Tooltip.create(Component.translatable(
                            "screen.phantomism.staff.tooltip." + action.name().toLowerCase(java.util.Locale.ROOT)
                    ))).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + PANEL_HEIGHT, 0xD010121A);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 1, 0xFF9B78D0);
        graphics.text(this.font, this.title, this.panelX + 8, this.panelY + 8, 0xFFFFFFFF);
        graphics.centeredText(
                this.font,
                this.selectedRecipient().name(),
                this.panelX + this.panelWidth / 2,
                this.panelY + 29,
                0xFFFFFFFF
        );
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font,
                Component.translatable("screen.phantomism.staff.category." + Category.values()[this.selectedCategoryIndex].name().toLowerCase(java.util.Locale.ROOT)),
                this.panelX + this.panelWidth / 2, this.panelY + 52, 0xFFFFFFFF);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null && this.selectedRecipientId() == -1) {
            List<Task> tasks = minecraft.level.getEntitiesOfClass(AbstractPhantom.class,
                            minecraft.player.getBoundingBox().inflate(128.0D), phantom -> phantom.isAlive() && phantom.isOwnedBy(minecraft.player))
                    .stream().map(AbstractPhantom::getTask).toList();
            graphics.centeredText(this.font, groupActivitySummary(tasks),
                    this.panelX + this.panelWidth / 2, this.panelY + 144, 0xFFCCCCCC);
            if (!tasks.isEmpty() && mouseX >= this.panelX + 8 && mouseX < this.panelX + this.panelWidth - 8
                    && mouseY >= this.panelY + 142 && mouseY < this.panelY + 155) {
                graphics.setComponentTooltipForNextFrame(this.font, groupActivityDetails(tasks), mouseX, mouseY);
            }
        } else if (minecraft.level != null && minecraft.level.getEntity(this.selectedRecipientId()) instanceof AbstractPhantom phantom) {
            Component status = phantom instanceof net.ronm19.phantomism.entity.GolemPhantom golem ? Component.translatable("screen.phantomism.staff.golem_work", Component.translatable("work.phantomism.golem." + golem.workStatus()), golem.occupiedCargoSlots()) : Component.translatable("screen.phantomism.staff.activity_status", activityName(phantom.getTask()));
            graphics.centeredText(this.font, status,
                    this.panelX + this.panelWidth / 2, this.panelY + 144, 0xFFCCCCCC);
        }
    }

    private static Component activityName(Task task) {
        return Component.translatable("screen.phantomism.staff.action." + (task == Task.NONE
                ? "activity_none" : task.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static Component groupActivitySummary(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return Component.translatable("screen.phantomism.staff.group_activity_empty");
        }
        Task first = tasks.getFirst();
        if (tasks.stream().anyMatch(task -> task != first)) {
            return Component.translatable("screen.phantomism.staff.group_activity_mixed");
        }
        return Component.translatable("screen.phantomism.staff.group_activity_status", activityName(first));
    }

    private static List<Component> groupActivityDetails(List<Task> tasks) {
        List<Component> details = new ArrayList<>();
        for (Task activity : Task.values()) {
            long count = tasks.stream().filter(task -> task == activity).count();
            if (count > 0) {
                details.add(Component.translatable("screen.phantomism.staff.group_activity_count", activityName(activity), count));
            }
        }
        return details;
    }

    private void refreshRecipients() {
        this.recipients.clear();
        this.recipients.add(new Recipient(-1, Component.translatable("screen.phantomism.staff.all_nearby")));
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        minecraft.level.getEntitiesOfClass(
                        AbstractPhantom.class,
                        player.getBoundingBox().inflate(128.0D),
                        phantom -> phantom.isOwnedBy(player)
                ).stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .map(phantom -> new Recipient(phantom.getId(), phantom.getDisplayName()))
                .forEach(this.recipients::add);
    }

    private int findRecipientIndex(int entityId) {
        for (int index = 0; index < this.recipients.size(); index++) {
            if (this.recipients.get(index).entityId() == entityId) {
                return index;
            }
        }
        return 0;
    }

    private Recipient selectedRecipient() {
        return this.recipients.get(this.selectedRecipientIndex);
    }

    private int selectedRecipientId() {
        return this.recipients.isEmpty() ? -1 : this.selectedRecipient().entityId();
    }

    private void cycleRecipient(int direction) {
        this.selectedRecipientIndex = Math.floorMod(this.selectedRecipientIndex + direction, this.recipients.size());
    }

    private void cycleCategory(int direction) {
        this.selectedCategoryIndex = Math.floorMod(this.selectedCategoryIndex + direction, Category.values().length);
        this.rebuildWidgets();
    }

    private void select(Action action) {
        int recipientId = this.selectedRecipientId();
        if (action.name().startsWith("GOLEM_")) {
            if (Minecraft.getInstance().level == null || !(Minecraft.getInstance().level.getEntity(recipientId) instanceof net.ronm19.phantomism.entity.GolemPhantom)) { if (Minecraft.getInstance().player != null) { Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("message.phantomism.golem.select")); } return; }
            if (action == Action.GOLEM_PRECISION || action == Action.GOLEM_AREA_A || action == Action.GOLEM_AREA_B || action == Action.GOLEM_CHEST) { PhantomismClient.beginBlockSelection(action, recipientId); Minecraft.getInstance().setScreenAndShow(null); if (Minecraft.getInstance().player != null) { Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("message.phantomism.golem.block")); } return; }
        }
        if (action.name().startsWith("RIFT_") && (Minecraft.getInstance().level == null || !(Minecraft.getInstance().level.getEntity(recipientId) instanceof net.ronm19.phantomism.entity.RiftPhantom))) {
            if (Minecraft.getInstance().player != null) { Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("message.phantomism.rift.select")); } return;
        }
        if (action == Action.RIFT_SEND_ALLY) { PhantomismClient.beginRiftCompanionSelection(recipientId); Minecraft.getInstance().setScreenAndShow(null); if (Minecraft.getInstance().player != null) { Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("screen.phantomism.staff.select_rift_ally")); } return; }
        if (action == Action.RIFT_ANCHOR || action == Action.RIFT_PORTAL_A || action == Action.RIFT_PORTAL_B) { PhantomismClient.beginBlockSelection(action, recipientId); Minecraft.getInstance().setScreenAndShow(null); if (Minecraft.getInstance().player != null) { Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("screen.phantomism.staff.select_rift")); } return; }
        if (action == Action.ATTACK_TARGET || action == Action.SET_HOME || action == Action.GUARD_POSITION) {
            if (action == Action.ATTACK_TARGET) {
                PhantomismClient.beginAttackTargetSelection(recipientId);
            } else {
                PhantomismClient.beginBlockSelection(action, recipientId);
            }
            Minecraft.getInstance().setScreenAndShow(null);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendOverlayMessage(
                        Component.translatable("screen.phantomism.staff." + switch (action) {
                            case SET_HOME -> "select_home";
                            case GUARD_POSITION -> "select_guard";
                            default -> "select_target";
                        })
                );
            }
            return;
        }

        ClientPacketDistributor.sendToServer(new StaffCommandPayload(action.ordinal(), recipientId, -1));
    }

    private record Recipient(int entityId, Component name) {
    }

    private enum Category {
        MOVEMENT(Action.FOLLOW, Action.STAY, Action.GUARD, Action.GUARD_POSITION, Action.WANDER),
        ACTIVITIES(Action.ACTIVITY_NONE, Action.HARVEST, Action.HUNT, Action.COLLECT),
        TARGETS(Action.ATTACK_TARGET, Action.CLEAR_TARGET),
        HOME(Action.SET_HOME, Action.RETURN_HOME),
        UTILITY(Action.RECALL),
        SPATIAL(Action.RIFT_ANCHOR, Action.RIFT_PORTAL_A, Action.RIFT_PORTAL_B, Action.RIFT_ENTER, Action.RIFT_SEND_ALLY, Action.RIFT_CLOSE),
        MINING(Action.GOLEM_PRECISION, Action.GOLEM_AREA_A, Action.GOLEM_AREA_B, Action.GOLEM_OBSIDIAN, Action.GOLEM_DELIVER, Action.GOLEM_CANCEL),
        TITANIUM(Action.GOLEM_CHEST, Action.GOLEM_OWNER, Action.GOLEM_STATUS, Action.GOLEM_GUARD, Action.GOLEM_TITAN, Action.GOLEM_RAM_BREAK),
        ILLUSION(Action.MIRAGE_COPIES, Action.MIRAGE_DOUBLE, Action.MIRAGE_FIELD, Action.MIRAGE_HALL);

        private final Action[] actions;

        Category(Action... actions) {
            this.actions = actions;
        }
    }
}
