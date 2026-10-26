package net.ronm19.phantomism.datagen;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.ronm19.phantomism.advancement.PhantomDiscovery;

public final class ModAdvancementProvider extends AdvancementSubProvider {
    public ModAdvancementProvider(BootstrapContext<Advancement> output) {
        super(output);
    }

    @Override
    public void generate() {
        AdvancementHolder root = Advancement.Builder.advancement()
                .rootDisplay(PhantomDiscovery.BLACK.icon(), Component.translatable("advancements.phantomism.discovery.root.title"),
                        Component.translatable("advancements.phantomism.discovery.root.description"),
                        Identifier.withDefaultNamespace("gui/advancements/backgrounds/stone"),
                        AdvancementType.TASK, false, false, false)
                .addCriterion(PhantomDiscovery.CRITERION, CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .save(this.output, PhantomDiscovery.ROOT_ID.toString());
        for (PhantomDiscovery discovery : PhantomDiscovery.values()) {
            Advancement.Builder.advancement()
                    .parent(root)
                    .display(discovery.icon(), Component.translatable(discovery.translationKey("title")),
                            Component.translatable(discovery.translationKey("description")),
                            discovery.rarity().frame(), true, false, false)
                    .addCriterion(PhantomDiscovery.CRITERION, CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                    .save(this.output, discovery.advancementId().toString());
        }
    }
}
