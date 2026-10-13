package net.ronm19.phantomism.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class ModParticleProvider implements DataProvider {
    private final PackOutput output;

    public ModParticleProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var folder = this.output.getOutputFolder().resolve("assets/phantomism/particles");
        return CompletableFuture.allOf(
                DataProvider.saveStable(cache, sandstormDust(), folder.resolve("sandstorm_dust.json")),
                DataProvider.saveStable(cache, phantomEnergy(), folder.resolve("phantom_energy.json")));
    }

    public static JsonObject sandstormDust() {
        JsonArray textures = new JsonArray();
        for (int frame = 7; frame >= 0; frame--) {
            textures.add("phantomism:sand_cloud_" + frame);
        }
        JsonObject definition = new JsonObject();
        definition.add("textures", textures);
        return definition;
    }

    public static JsonObject phantomEnergy() {
        JsonArray textures = new JsonArray();
        for (String name : new String[]{"vfx_glow", "vfx_slash", "vfx_spark", "vfx_ring", "vfx_shard", "vfx_bolt",
                "vfx_flame", "vfx_droplet", "vfx_leaf", "vfx_grain", "vfx_petal"}) {
            textures.add("phantomism:" + name);
        }
        JsonObject definition = new JsonObject();
        definition.add("textures", textures);
        return definition;
    }

    @Override
    public String getName() {
        return "Phantomism Native Particle Definitions";
    }
}
