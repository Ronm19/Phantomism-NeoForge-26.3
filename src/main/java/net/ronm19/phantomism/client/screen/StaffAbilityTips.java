package net.ronm19.phantomism.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.ability.PhantomAbilities;

public final class StaffAbilityTips {
    private StaffAbilityTips() {}
    public static String translationKey(PhantomAbilities.View ability) {
        if (ability.automatic()) { return "staff.ability.tip.automatic"; }
        Identifier identity = Identifier.tryParse(ability.id());
        if (identity != null && identity.getNamespace().equals("phantomism")) {
            String key = "staff.ability.tip." + identity.getPath().replace('/', '.');
            if (Language.getInstance().has(key)) { return key; }
        }
        return switch (ability.target()) {
            case ENEMY -> "staff.ability.tip.enemy";
            case POSITION -> "staff.ability.tip.position";
            case NONE -> "staff.ability.tip.nearby";
        };
    }
    public static Component whenToUse(PhantomAbilities.View ability) {
        return Component.translatable("staff.ability.when_to_use", Component.translatable(translationKey(ability))).withStyle(ChatFormatting.AQUA);
    }
    public static Component selection(PhantomAbilities.View ability) {
        return Component.translatable("staff.ability.selection_tip", Component.translatable(ability.nameKey()), whenToUse(ability));
    }
    public static Component tooltip(PhantomAbilities.View ability, Component status) {
        var tooltip = Component.translatable(ability.nameKey());
        if (!ability.automatic()) { tooltip.append("\n").append(whenToUse(ability)).append("\n"); }
        tooltip.append("\n").append(Component.translatable(ability.descriptionKey())).append("\n").append(status);
        if (!ability.reason().isEmpty()) { tooltip.append("\n").append(Component.translatable(ability.reason())); }
        return tooltip;
    }
}
