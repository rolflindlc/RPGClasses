package io.github.rpgclasses.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.AttributeEffect;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.AbilityDef;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.EffectDef;
import io.github.rpgclasses.core.definition.Definitions.MilestoneDef;
import io.github.rpgclasses.core.definition.Definitions.PerkDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.XpMath;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.core.value.LevelValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Genera el texto de las pestañas de una clase a partir de las definiciones sincronizadas.
 * Los números de "Datos exactos" salen de los mismos JSON que usa el servidor: no pueden desfasarse.
 */
public final class ClassPages {
    public enum Tab { PROGRESS, SUMMARY, BRANCHES, SUBCLASSES, DATA }

    static final Set<String> SKIPPED_PARAMS = Set.of("type", "id", "schema_version", "passive", "condition", "modifiers");
    private static final int LEVELS = 10;
    private static Map<String, String> paramUnits;

    public static List<Component> build(ClassDef c, Tab tab) {
        List<Component> out = new ArrayList<>();
        switch (tab) {
            case PROGRESS -> { } // la dibuja ClassScreen con botones
            case SUMMARY -> summary(c, out);
            case BRANCHES -> branches(c, out);
            case SUBCLASSES -> subclasses(c, out);
            case DATA -> data(c, out);
        }
        return out;
    }

    // --- Pestañas ---

    private static void summary(ClassDef c, List<Component> out) {
        out.add(Component.translatable(Ids.langKey("class", c.id(), "desc")));
        out.add(Component.empty());
        if (!c.roles().isEmpty()) {
            out.add(Component.translatable("ui.rites_and_paths.roles", String.join(", ", c.roles())).withStyle(ChatFormatting.GRAY));
        }
        out.add(Component.translatable(c.startingKit().isEmpty() ? "ui.rites_and_paths.no_kit" : "ui.rites_and_paths.kit").withStyle(ChatFormatting.GRAY));
        List<String> affine = new ArrayList<>();
        for (String d : new TreeSet<>(Ids.DISCIPLINES)) {
            if (!c.walls().containsKey(d) && !(Ids.ARMOR.equals(d) && c.armorDisablesPassives())) affine.add(discipline(d).getString());
        }
        out.add(Component.empty());
        out.add(Component.translatable("ui.rites_and_paths.affine", String.join(", ", affine)).withStyle(ChatFormatting.GREEN));
        if (c.armorDisablesPassives()) {
            out.add(Component.translatable("ui.rites_and_paths.armor_rule").withStyle(ChatFormatting.YELLOW));
        }
        if (!c.traits().isEmpty()) {
            out.add(Component.empty());
            out.add(Component.translatable(Ids.langKey("class", c.id(), "trait")).withStyle(ChatFormatting.YELLOW));
            for (EffectDef e : c.traits()) effect(e, 1, out);
        }
        walls(c, out);
    }

    private static void branches(ClassDef c, List<Component> out) {
        out.add(header("ui.rites_and_paths.branches_header"));
        for (BranchDef b : c.branches()) {
            out.add(Component.empty());
            out.add(branchName(c, b).withStyle(ChatFormatting.GOLD));
            out.add(Component.translatable(branchKey(c, b, "desc")));
            if (!b.perks().isEmpty()) {
                out.add(Component.translatable("ui.rites_and_paths.perks_line").withStyle(ChatFormatting.GRAY));
                for (PerkDef p : b.perks()) {
                    out.add(indent(Component.translatable("ui.rites_and_paths.perk_option",
                            perkName(c, b, p).withStyle(ChatFormatting.LIGHT_PURPLE), Component.translatable(perkKey(c, b, p, "desc")))));
                }
            }
        }
        out.add(Component.empty());
        out.add(header("ui.rites_and_paths.abilities_header"));
        for (MilestoneDef m : c.milestones()) milestone(m, out);
    }

    private static void subclasses(ClassDef c, List<Component> out) {
        out.add(Component.translatable("ui.rites_and_paths.subclass_note").withStyle(ChatFormatting.GRAY));
        for (String subId : c.subclasses()) {
            SubclassDef sub = DefinitionStore.CLIENT.subclassDef(subId);
            if (sub == null) continue;
            out.add(Component.empty());
            out.add(Component.translatable(Ids.langKey("subclass", subId, "name")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            out.add(Component.translatable(Ids.langKey("subclass", subId, "desc")));
            for (MilestoneDef m : sub.milestones()) milestone(m, out);
        }
    }

    private static void data(ClassDef c, List<Component> out) {
        double base = critBase();
        out.add(Component.translatable("ui.rites_and_paths.data.crit",
                percent(StatMath.critChance(base, c.critChanceBonus(), false)), number(StatMath.BASE_CRIT_MULTIPLIER)));
        walls(c, out);
        if (!c.traits().isEmpty()) {
            out.add(Component.empty());
            out.add(header("ui.rites_and_paths.traits_header"));
            for (EffectDef e : c.traits()) effect(e, 1, out);
        }

        out.add(Component.empty());
        out.add(header("ui.rites_and_paths.branches_header"));
        for (BranchDef b : c.branches()) {
            out.add(branchName(c, b).withStyle(ChatFormatting.GOLD));
            for (EffectDef e : b.effects()) effect(e, b.maxPoints(), out);
            for (PerkDef p : b.perks()) {
                out.add(indent(perkName(c, b, p).withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(" ").append(Component.translatable("ui.rites_and_paths.data.perk_fixed").withStyle(ChatFormatting.GRAY))));
                for (EffectDef e : p.effects()) effect(e, 1, out);
            }
        }

        out.add(Component.empty());
        out.add(header("ui.rites_and_paths.abilities_header"));
        for (MilestoneDef m : c.milestones()) abilityData(m, out);

        for (String subId : c.subclasses()) {
            SubclassDef sub = DefinitionStore.CLIENT.subclassDef(subId);
            if (sub == null) continue;
            out.add(Component.empty());
            out.add(Component.translatable(Ids.langKey("subclass", subId, "name")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (!sub.effects().isEmpty()) {
                out.add(Component.translatable("ui.rites_and_paths.data.subclass_levels").withStyle(ChatFormatting.GRAY));
                for (EffectDef e : sub.effects()) effect(e, ProgressionRules.SUBCLASS_MAX, out);
            }
            for (MilestoneDef m : sub.milestones()) abilityData(m, out);
        }

        out.add(Component.empty());
        out.add(header("ui.rites_and_paths.data.xp_header"));
        double mult = xpMultiplier();
        out.add(Component.translatable("ui.rites_and_paths.data.xp_line",
                "(80 + 6n)" + (mult == 1.0 ? "" : " x " + number(mult)),
                XpMath.totalCost(0, 30, mult), XpMath.totalCost(30, 40, mult)));
    }

    // --- Bloques ---

    private static void walls(ClassDef c, List<Component> out) {
        if (c.walls().isEmpty()) return;
        out.add(Component.empty());
        out.add(header("ui.rites_and_paths.walls_header"));
        for (String d : new TreeSet<>(c.walls().keySet())) {
            out.add(Component.translatable("ui.rites_and_paths.wall_line", discipline(d), percent(c.walls().get(d))).withStyle(ChatFormatting.RED));
        }
        String example = new TreeSet<>(c.walls().keySet()).first();
        out.add(Component.translatable("ui.rites_and_paths.walls_note", 15,
                "-" + percent(StatMath.wallPenalty(c.walls().get(example), 15)) + " (" + discipline(example).getString() + ")")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void milestone(MilestoneDef m, List<Component> out) {
        AbilityDef ability = DefinitionStore.CLIENT.abilityDef(m.grants());
        boolean passive = ability != null && ability.params().has("passive") && ability.params().get("passive").getAsBoolean();
        MutableComponent line = Component.translatable(m.subclassLevel() ? "ui.rites_and_paths.unlock_sub" : "ui.rites_and_paths.unlock_total", m.level())
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable(Ids.langKey("ability", m.grants(), "name")).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        if (passive) {
            line.append(Component.literal(" (").append(Component.translatable("ui.rites_and_paths.passive")).append(")").withStyle(ChatFormatting.GRAY));
        }
        out.add(Component.empty());
        out.add(line);
        out.add(Component.translatable(Ids.langKey("ability", m.grants(), "desc")));
        if (m.upgrades() != null) {
            out.add(Component.translatable("ui.rites_and_paths.upgrades",
                    Component.translatable(Ids.langKey("ability", m.upgrades(), "name"))).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void abilityData(MilestoneDef m, List<Component> out) {
        out.add(Component.translatable(Ids.langKey("ability", m.grants(), "name")).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.literal(" (").append(Component.translatable(
                        m.subclassLevel() ? "ui.rites_and_paths.unlock_sub" : "ui.rites_and_paths.unlock_total", m.level())).append(")")
                        .withStyle(ChatFormatting.GRAY)));
        AbilityDef ability = DefinitionStore.CLIENT.abilityDef(m.grants());
        int before = out.size();
        if (ability != null) params(ability.params(), LEVELS, out);
        if (out.size() == before) out.add(indent(Component.translatable("ui.rites_and_paths.data.no_data").withStyle(ChatFormatting.DARK_GRAY)));
    }

    /** Una pasiva: modificadores de atributo, o los parámetros de una mecánica. */
    private static void effect(EffectDef e, int maxLevel, List<Component> out) {
        if (Ids.EFFECT_ATTRIBUTE.equals(e.type())) {
            AttributeEffect parsed;
            try {
                parsed = AttributeEffect.parse(e.params(), maxLevel);
            } catch (RuntimeException ex) {
                return;
            }
            for (AttributeEffect.Modifier m : parsed.modifiers()) {
                boolean pct = isPercent(m);
                MutableComponent line = levelValue(attributeName(m.attribute()), m.value(), pct ? "%" : "", true);
                if (parsed.condition() != null) {
                    line.append(" ").append(Component.translatable("ui.rites_and_paths.data.condition_no_armor").withStyle(ChatFormatting.GRAY));
                }
                out.add(indent(line));
            }
            return;
        }
        if (Ids.EFFECT_MECHANIC.equals(e.type())) {
            String id = e.params().get("id").getAsString();
            out.add(indent(Component.translatable(Ids.langKey("mechanic", id, "name")).withStyle(ChatFormatting.WHITE)));
        }
        params(e.params(), maxLevel, out);
    }

    /** Vuelca los parámetros numéricos de una mecánica o habilidad, con su nombre y unidad. */
    private static void params(JsonObject params, int maxLevel, List<Component> out) {
        for (Map.Entry<String, JsonElement> entry : params.entrySet()) {
            String key = entry.getKey();
            if (SKIPPED_PARAMS.contains(key)) continue;
            Component label = Component.translatableWithFallback("param.rites_and_paths." + key, key);
            String unit = units().getOrDefault(key, "");
            JsonElement v = entry.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                out.add(indent2(Component.translatable("ui.rites_and_paths.data.fixed", label, format(v.getAsDouble(), unit, false))));
            } else if (v.isJsonArray()) {
                out.add(indent2(Component.translatable("ui.rites_and_paths.data.fixed", label, list(v.getAsJsonArray(), unit))));
            } else if (v.isJsonObject()) {
                try {
                    out.add(indent2(levelValue(label, LevelValue.fromJson(v.getAsJsonObject(), maxLevel), unit, false)));
                } catch (RuntimeException ignored) {
                    // objeto que no es un valor por nivel: no se muestra
                }
            }
        }
    }

    /** Describe un valor que escala: "X por punto, Y con 10", "de X a Y" o la tabla completa. */
    private static MutableComponent levelValue(Component label, LevelValue value, String unit, boolean signed) {
        double first = value.at(1);
        double max = value.max();
        int n = value.maxLevel();
        if (n == 1) return Component.translatable("ui.rites_and_paths.data.fixed", label, format(max, unit, signed));
        boolean fromZero = Math.abs(first * n - max) < 1e-9;
        boolean linear = true;
        double step = n > 1 ? (max - first) / (n - 1) : 0;
        for (int i = 1; i <= n; i++) linear &= Math.abs(value.at(i) - (first + step * (i - 1))) < 1e-9;
        if (fromZero && linear) {
            return Component.translatable("ui.rites_and_paths.data.per_point", label, format(first, unit, signed), format(max, unit, signed));
        }
        if (linear) {
            return Component.translatable("ui.rites_and_paths.data.range", label, format(first, unit, signed), format(max, unit, signed));
        }
        List<String> all = new ArrayList<>();
        for (int i = 1; i <= n; i++) all.add(format(value.at(i), unit, false));
        return Component.translatable("ui.rites_and_paths.data.table", label, String.join(" / ", all));
    }

    // --- Formato ---

    static String format(double v, String unit, boolean signed) {
        if ("sí".equals(unit)) return v != 0 ? "sí" : "no";
        if (unit.startsWith("+")) {
            signed = true;
            unit = unit.substring(1);
        }
        String sign = signed && v > 0 ? "+" : "";
        return switch (unit) {
            case "%" -> sign + number(v * 100) + "%";
            case "s" -> sign + number(v) + " s";
            default -> sign + number(v);
        };
    }

    private static String percent(double fraction) {
        return number(fraction * 100) + "%";
    }

    private static String number(double v) {
        double rounded = Math.round(v * 100) / 100.0;
        if (rounded == Math.rint(rounded)) return String.format(Locale.ROOT, "%d", (long) rounded);
        return String.format(Locale.ROOT, "%.2f", rounded).replaceAll("0+$", "").replace('.', ',');
    }

    private static String list(JsonArray array, String unit) {
        List<String> parts = new ArrayList<>();
        for (JsonElement e : array) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                parts.add(format(e.getAsDouble(), unit, false));
            } else {
                String s = e.getAsString();
                parts.add(Ids.DISCIPLINES.contains(s) ? discipline(s).getString() : registryName(s));
            }
        }
        return String.join(" / ", parts);
    }

    /** Nombre traducido de un efecto (o de un objeto) a partir de su id; si no existe, el id tal cual. */
    private static String registryName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return id;
        net.minecraft.world.effect.MobEffect effect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(rl);
        if (effect != null) return effect.getDisplayName().getString();
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        if (item != null && item != net.minecraft.world.item.Items.AIR) return item.getDescription().getString();
        return id;
    }

    /** Los multiplicadores y las fracciones propias del mod se muestran como porcentaje; el resto, como número. */
    static boolean isPercent(AttributeEffect.Modifier m) {
        if (!"add".equals(m.operation())) return true;
        if (m.attribute().equals("minecraft:generic.knockback_resistance")) return true;
        return m.attribute().startsWith(Ids.NS + ":") && !m.attribute().equals(Ids.NS + ":unarmed_damage");
    }

    static Component attributeName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Attribute attribute = key == null ? null : ForgeRegistries.ATTRIBUTES.getValue(key);
        return attribute != null ? Component.translatable(attribute.getDescriptionId()) : Component.literal(id);
    }

    static MutableComponent discipline(String id) {
        return Component.translatable(Ids.langKey("discipline", id, "name"));
    }

    /**
     * Valores de unos efectos en un nivel concreto, para la ayuda de cada nodo del árbol. Con {@code delta}
     * (y nivel > 1) muestra "antes → ahora". De las mecánicas solo salen los parámetros que escalan: los fijos
     * están en "Datos exactos".
     */
    public static List<Component> levelLines(List<EffectDef> effects, int maxLevel, int level, boolean delta) {
        List<Component> out = new ArrayList<>();
        boolean showDelta = delta && level > 1;
        for (EffectDef e : effects) {
            if (Ids.EFFECT_ATTRIBUTE.equals(e.type())) {
                AttributeEffect parsed;
                try {
                    parsed = AttributeEffect.parse(e.params(), maxLevel);
                } catch (RuntimeException ex) {
                    continue;
                }
                for (AttributeEffect.Modifier m : parsed.modifiers()) {
                    out.add(valueLine(attributeName(m.attribute()), m.value(), level, isPercent(m) ? "%" : "", true, showDelta));
                }
                continue;
            }
            if (Ids.EFFECT_MECHANIC.equals(e.type()) && e.params().has("id")) {
                out.add(Component.translatable(Ids.langKey("mechanic", e.params().get("id").getAsString(), "name")).withStyle(ChatFormatting.WHITE));
            }
            for (Map.Entry<String, JsonElement> entry : e.params().entrySet()) {
                if (SKIPPED_PARAMS.contains(entry.getKey()) || !entry.getValue().isJsonObject()) continue;
                LevelValue value;
                try {
                    value = LevelValue.fromJson(entry.getValue().getAsJsonObject(), maxLevel);
                } catch (RuntimeException ignored) {
                    continue;
                }
                Component label = Component.translatableWithFallback("param.rites_and_paths." + entry.getKey(), entry.getKey());
                out.add(Component.literal("  ").append(valueLine(label, value, level, units().getOrDefault(entry.getKey(), ""), false, showDelta)));
            }
        }
        return out;
    }

    private static MutableComponent valueLine(Component label, LevelValue value, int level, String unit, boolean signed, boolean showDelta) {
        MutableComponent line = Component.empty().append(label.copy().withStyle(ChatFormatting.GRAY)).append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        if (showDelta) {
            line.append(Component.literal(format(value.at(level - 1), unit, signed) + " → ").withStyle(ChatFormatting.DARK_GRAY));
        }
        return line.append(Component.literal(format(value.at(level), unit, signed)).withStyle(ChatFormatting.GREEN));
    }

    public static MutableComponent branchTitle(ClassDef c, BranchDef b) {
        return Component.translatable(branchKey(c, b, "name"));
    }

    public static Component branchDesc(ClassDef c, BranchDef b) {
        return Component.translatable(branchKey(c, b, "desc"));
    }

    /** Texto de ayuda de un perk: nombre, descripción y sus números exactos, separados por saltos de línea. */
    public static Component perkTooltip(ClassDef c, BranchDef b, PerkDef p) {
        List<Component> lines = perkLines(c, b, p);
        MutableComponent out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append("\n");
            out.append(lines.get(i));
        }
        return out;
    }

    /** Líneas de la ayuda de un perk: nombre, descripción y números exactos. */
    public static List<Component> perkLines(ClassDef c, BranchDef b, PerkDef p) {
        List<Component> lines = new ArrayList<>();
        lines.add(perkName(c, b, p).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable(perkKey(c, b, p, "desc")));
        for (EffectDef e : p.effects()) effect(e, 1, lines);
        return lines;
    }

    public static MutableComponent perkName(ClassDef c, BranchDef b, PerkDef p) {
        return Component.translatable(perkKey(c, b, p, "name"));
    }

    private static String perkKey(ClassDef c, BranchDef b, PerkDef p, String suffix) {
        return Ids.langKey("perk", c.id() + "." + b.id() + "." + p.id(), suffix);
    }

    private static MutableComponent branchName(ClassDef c, BranchDef b) {
        return Component.translatable(branchKey(c, b, "name")).withStyle(ChatFormatting.BOLD);
    }

    private static String branchKey(ClassDef c, BranchDef b, String suffix) {
        return Ids.langKey("branch", c.id() + "." + b.id(), suffix);
    }

    private static MutableComponent header(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE);
    }

    private static Component indent(Component c) {
        return Component.literal("  ").append(c);
    }

    private static Component indent2(Component c) {
        return Component.literal("    ").append(c).withStyle(ChatFormatting.GRAY);
    }

    private static double critBase() {
        try {
            return ServerConfig.BASE_CRIT_CHANCE.get();
        } catch (IllegalStateException notLoaded) {
            return 0.30;
        }
    }

    private static double xpMultiplier() {
        try {
            return ServerConfig.XP_COST_MULTIPLIER.get();
        } catch (IllegalStateException notLoaded) {
            return 1.0;
        }
    }

    /** Unidades de cada parámetro ("%" o "s"), generadas junto a los textos. */
    static Map<String, String> units() {
        if (paramUnits == null) {
            Map<String, String> loaded = new HashMap<>();
            try (Reader reader = Minecraft.getInstance().getResourceManager()
                    .openAsReader(RpgClasses.id("rites_and_paths_param_units.json"))) {
                JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) loaded.put(e.getKey(), e.getValue().getAsString());
            } catch (Exception e) {
                RpgClasses.LOGGER.warn("[rites_and_paths] No se pudieron leer las unidades de los parámetros", e);
            }
            paramUnits = loaded;
        }
        return paramUnits;
    }

    private ClassPages() {}
}
