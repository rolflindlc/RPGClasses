package io.github.ironrituals.ritual.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ironrituals.item.RitualTomeItem;
import io.github.ironrituals.registry.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Definición de un ritual por datapack ({@code data/<ns>/recipes/rituals/<id>.json}, tipo {@code ironrituals:ritual}).
 * Se registra como receta para que Minecraft la sincronice con el cliente (GUI del grimorio, JEI).
 * No se "fabrica" por las vías normales: el Núcleo del Ritual hace su propio reparto de componentes.
 */
public class RitualRecipe implements Recipe<Container> {
    public enum Rarity { COMMON, UNCOMMON, RARE }

    private final ResourceLocation id;
    private final ResourceLocation ritual;
    private final ResourceLocation school;
    private final int minCircle;
    private final boolean extendable;
    private final Rarity rarity;
    private final List<RitualTier> tiers;
    private final List<RitualCatalyst> catalysts;

    public RitualRecipe(ResourceLocation id, ResourceLocation ritual, ResourceLocation school, int minCircle, boolean extendable,
                        Rarity rarity, List<RitualTier> tiers, List<RitualCatalyst> catalysts) {
        this.id = id;
        this.ritual = ritual;
        this.school = school;
        this.minCircle = minCircle;
        this.extendable = extendable;
        this.rarity = rarity;
        List<RitualTier> sorted = new ArrayList<>(tiers);
        sorted.sort(Comparator.comparingInt(RitualTier::level));
        this.tiers = List.copyOf(sorted);
        this.catalysts = List.copyOf(catalysts);
    }

    public ResourceLocation ritual() { return ritual; }
    public ResourceLocation school() { return school; }
    public int minCircle() { return minCircle; }
    public boolean extendable() { return extendable; }
    public Rarity rarity() { return rarity; }
    public List<RitualTier> tiers() { return tiers; }
    public List<RitualCatalyst> catalysts() { return catalysts; }

    @Nullable
    public RitualTier tier(int level) {
        for (RitualTier t : tiers) if (t.level() == level) return t;
        return null;
    }

    /** Busca la definición del ritual de un id (p. ej. {@code ironrituals:sun}). */
    @Nullable
    public static RitualRecipe find(Level level, @Nullable ResourceLocation ritual) {
        if (ritual == null) return null;
        for (RitualRecipe r : level.getRecipeManager().getAllRecipesFor(ModRecipes.RITUAL_TYPE.get())) {
            if (r.ritual.equals(ritual)) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------ Recipe (no se usa para fabricar)

    @Override public boolean matches(Container c, Level level) { return false; }
    @Override public ItemStack assemble(Container c, RegistryAccess access) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int w, int h) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return RitualTomeItem.create(ritual); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return ModRecipes.RITUAL_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModRecipes.RITUAL_TYPE.get(); }
    @Override public boolean isSpecial() { return true; }

    // ------------------------------------------------------------------ Serializer

    public static class Serializer implements RecipeSerializer<RitualRecipe> {
        @Override
        public RitualRecipe fromJson(ResourceLocation id, JsonObject json) {
            ResourceLocation ritual = parse(GsonHelper.getAsString(json, "ritual"));
            ResourceLocation school = parse(GsonHelper.getAsString(json, "school"));
            List<RitualTier> tiers = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "tiers")) tiers.add(RitualTier.fromJson(e.getAsJsonObject()));
            if (tiers.isEmpty()) throw new IllegalArgumentException("el ritual " + id + " no tiene niveles");
            List<RitualCatalyst> cats = new ArrayList<>();
            if (json.has("catalysts")) {
                for (JsonElement e : GsonHelper.getAsJsonArray(json, "catalysts")) cats.add(RitualCatalyst.fromJson(e.getAsJsonObject()));
            }
            int minCircle = GsonHelper.getAsInt(json, "min_circle", tiers.get(0).level());
            Rarity rarity = Rarity.valueOf(GsonHelper.getAsString(json, "rarity", "common").toUpperCase(Locale.ROOT));
            return new RitualRecipe(id, ritual, school, minCircle, GsonHelper.getAsBoolean(json, "extendable", false), rarity, tiers, cats);
        }

        @Override
        public RitualRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ResourceLocation ritual = buf.readResourceLocation();
            ResourceLocation school = buf.readResourceLocation();
            int minCircle = buf.readVarInt();
            boolean extendable = buf.readBoolean();
            Rarity rarity = buf.readEnum(Rarity.class);
            List<RitualTier> tiers = buf.readList(RitualTier::fromNetwork);
            List<RitualCatalyst> cats = buf.readList(RitualCatalyst::fromNetwork);
            return new RitualRecipe(id, ritual, school, minCircle, extendable, rarity, tiers, cats);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, RitualRecipe r) {
            buf.writeResourceLocation(r.ritual);
            buf.writeResourceLocation(r.school);
            buf.writeVarInt(r.minCircle);
            buf.writeBoolean(r.extendable);
            buf.writeEnum(r.rarity);
            buf.writeCollection(r.tiers, (b, t) -> t.toNetwork(b));
            buf.writeCollection(r.catalysts, (b, c) -> c.toNetwork(b));
        }

        private static ResourceLocation parse(String s) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl == null) throw new IllegalArgumentException("id inválido: " + s);
            return rl;
        }
    }
}
