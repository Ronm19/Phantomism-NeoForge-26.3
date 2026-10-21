package net.ronm19.phantomism.datagen;

import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModMixinConfigProvider implements DataProvider {
    private final PackOutput output;

    public ModMixinConfigProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonObject configuration = new JsonObject();
        configuration.addProperty("required", true);
        configuration.addProperty("package", "net.ronm19.phantomism.mixin");
        configuration.addProperty("compatibilityLevel", "JAVA_25");
        JsonArray mixins = new JsonArray();
        mixins.add("PowderSnowBlockMixin");
        mixins.add("ServerLevelMixin");
        mixins.add("SpectralVehicleMixin");
        mixins.add("SpectralPlayerSaveMixin");
        configuration.add("mixins", mixins);
        JsonArray clientMixins = new JsonArray();
        clientMixins.add("client.GuiGraphicsExtractorMixin");
        configuration.add("client", clientMixins);
        JsonObject injectors = new JsonObject();
        injectors.addProperty("defaultRequire", 1);
        configuration.add("injectors", injectors);
        JsonObject overwrites = new JsonObject();
        overwrites.addProperty("requireAnnotations", true);
        configuration.add("overwrites", overwrites);
        return DataProvider.saveStable(cache, configuration, this.output.getOutputFolder().resolve("phantomism.mixins.json"));
    }

    @Override
    public String getName() {
        return "Phantomism Mixin Configuration";
    }
}
