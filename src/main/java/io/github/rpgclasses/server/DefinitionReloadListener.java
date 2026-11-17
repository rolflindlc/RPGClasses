package io.github.rpgclasses.server;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.core.definition.DefinitionStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee data/&lt;namespace&gt;/rites_and_paths/classes/*.json y .../subclasses/*.json al cargar el mundo y en /reload.
 * Un archivo erróneo desactiva su clase y deja el error en el log; nunca detiene la carga.
 */
public final class DefinitionReloadListener extends SimpleJsonResourceReloadListener {
    private static final String FOLDER = "rites_and_paths";

    public DefinitionReloadListener() {
        super(new Gson(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, String> classes = new HashMap<>();
        Map<String, String> subclasses = new HashMap<>();
        Map<String, String> abilities = new HashMap<>();
        Map<String, String> races = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            String path = e.getKey().getPath();
            if (path.startsWith("classes/")) {
                classes.put(e.getKey().getNamespace() + ":" + path.substring("classes/".length()), e.getValue().toString());
            } else if (path.startsWith("subclasses/")) {
                subclasses.put(e.getKey().getNamespace() + ":" + path.substring("subclasses/".length()), e.getValue().toString());
            } else if (path.startsWith("abilities/")) {
                abilities.put(e.getKey().getNamespace() + ":" + path.substring("abilities/".length()), e.getValue().toString());
            } else if (path.startsWith("races/")) {
                races.put(e.getKey().getNamespace() + ":" + path.substring("races/".length()), e.getValue().toString());
            }
        }
        DefinitionStore.SERVER.load(classes, subclasses, abilities);
        io.github.rpgclasses.core.definition.RaceStore.SERVER.load(races);
        for (Map.Entry<String, List<String>> err : io.github.rpgclasses.core.definition.RaceStore.SERVER.errors().entrySet()) {
            for (String message : err.getValue()) {
                RpgClasses.LOGGER.error("[rites_and_paths] Raza inválida '{}': {}", err.getKey(), message);
            }
        }
        RpgClasses.LOGGER.info("[rites_and_paths] {} razas cargadas", io.github.rpgclasses.core.definition.RaceStore.SERVER.ids().size());

        for (Map.Entry<String, List<String>> err : DefinitionStore.SERVER.errors().entrySet()) {
            for (String message : err.getValue()) {
                RpgClasses.LOGGER.error("[rites_and_paths] Definición inválida '{}': {}", err.getKey(), message);
            }
        }
        RpgClasses.LOGGER.info("[rites_and_paths] {} clases cargadas, {} con errores",
                DefinitionStore.SERVER.classIds().size(), DefinitionStore.SERVER.errors().size());
    }
}
