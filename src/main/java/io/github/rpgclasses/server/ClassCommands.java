package io.github.rpgclasses.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.BranchDef;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.stat.StatCalculator;
import io.github.rpgclasses.core.stat.StatMath;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.network.Packets.ClassAction.Action;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /clase  (cualquier jugador): elegir, punto, subclase, subpunto, reiniciar, info.
 * /clases (operadores): info, reset, validar.
 * Los comandos de jugador pasan por ProgressionService, igual que hará la interfaz gráfica.
 */
public final class ClassCommands {

    private static final SuggestionProvider<CommandSourceStack> CLASS_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(DefinitionStore.SERVER.classIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> BRANCH_IDS = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        ServerPlayer player = ctx.getSource().getPlayer();
        PlayerClassData data = player == null ? null : PlayerClassDataProvider.get(player);
        ClassDef def = data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
        if (def != null) for (BranchDef b : def.branches()) ids.add(b.id());
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUBCLASS_IDS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        PlayerClassData data = player == null ? null : PlayerClassDataProvider.get(player);
        ClassDef def = data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
        return SharedSuggestionProvider.suggest(def == null ? List.<String>of() : def.subclasses(), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("clase")
                .then(Commands.literal("elegir").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(CLASS_IDS)
                        .executes(c -> act(c, Action.CHOOSE_CLASS, ResourceLocationArgument.getId(c, "id").toString()))))
                .then(Commands.literal("punto").then(Commands.argument("rama", StringArgumentType.word()).suggests(BRANCH_IDS)
                        .executes(c -> act(c, Action.SPEND_BRANCH, StringArgumentType.getString(c, "rama")))))
                .then(Commands.literal("subclase").then(Commands.argument("id", ResourceLocationArgument.id()).suggests(SUBCLASS_IDS)
                        .executes(c -> act(c, Action.CHOOSE_SUBCLASS, ResourceLocationArgument.getId(c, "id").toString()))))
                .then(Commands.literal("subpunto").executes(c -> act(c, Action.SPEND_SUBCLASS, "")))
                .then(Commands.literal("reiniciar")
                        .then(Commands.literal("clase").executes(c -> act(c, Action.RESPEC_CLASS, "")))
                        .then(Commands.literal("subclase").executes(c -> act(c, Action.RESPEC_SUBCLASS, ""))))
                .then(Commands.literal("info").executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))));

        dispatcher.register(Commands.literal("clases").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").then(Commands.argument("jugador", EntityArgument.player())
                        .executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "jugador")))))
                .then(Commands.literal("reset").then(Commands.argument("jugador", EntityArgument.player())
                        .executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "jugador");
                            ProgressionService.adminReset(target);
                            c.getSource().sendSuccess(() -> Component.translatable("msg.rpgclasses.reset_done", target.getDisplayName()), true);
                            return 1;
                        })))
                .then(Commands.literal("validar").executes(c -> validate(c.getSource())))
                .then(Commands.literal("debug")
                        .then(Commands.literal("item").executes(c -> debugItem(c.getSource())))
                        .then(Commands.literal("stats").executes(c -> debugStats(c.getSource())))));
    }

    private static int act(CommandContext<CommandSourceStack> ctx, Action action, String arg) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return ProgressionService.performAndReport(player, action, arg).ok() ? 1 : 0;
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        PlayerClassData data = PlayerClassDataProvider.get(target);
        if (data == null) return 0;
        ProgressState s = data.state;
        Component none = Component.translatable("msg.rpgclasses.none");
        Component className = s.classId == null ? none : Component.translatable(Ids.langKey("class", s.classId, "name"));
        Component subName = s.subclassId == null ? none : Component.translatable(Ids.langKey("subclass", s.subclassId, "name"));
        int cap = ProgressionRules.subclassCap(s.basePoints());
        Component next = ProgressionRules.isMaxed(s)
                ? Component.translatable("msg.rpgclasses.max_level")
                : Component.translatable("msg.rpgclasses.next_cost", ProgressionRules.nextCost(s, ServerConfig.XP_COST_MULTIPLIER.get()));
        source.sendSuccess(() -> Component.translatable("msg.rpgclasses.info",
                className, s.basePoints(), subName, s.subclassLevel, cap, next), false);
        if (s.classId != null && DefinitionStore.SERVER.classDef(s.classId) == null) {
            source.sendSuccess(() -> Component.translatable("msg.rpgclasses.suspended").withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int validate(CommandSourceStack source) {
        Map<String, List<String>> errors = DefinitionStore.SERVER.errors();
        if (errors.isEmpty()) {
            int count = DefinitionStore.SERVER.classIds().size();
            source.sendSuccess(() -> Component.translatable("msg.rpgclasses.validate_ok", count), false);
            return 1;
        }
        for (Map.Entry<String, List<String>> e : errors.entrySet()) {
            for (String message : e.getValue()) {
                source.sendFailure(Component.literal(e.getKey() + ": " + message));
            }
        }
        return 0;
    }

    /** Disciplina del objeto en la mano principal y la regla que la decide. */
    private static int debugItem(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WeaponClassifier.Classification c = WeaponClassifier.classify(player.getMainHandItem());
        String discipline = c.unarmed() ? "desarmado" : c.discipline() == null ? "ninguna" : c.discipline();
        source.sendSuccess(() -> Component.literal(discipline + " (" + c.rule() + ")"), false);
        return 1;
    }

    /** Modificadores activos del mod, muros, crítico y desglose del último golpe. */
    private static int debugStats(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerClassData data = PlayerClassDataProvider.get(player);
        ClassDef def = data == null ? null : DefinitionStore.SERVER.classDef(data.state.classId);
        if (def == null) {
            source.sendFailure(ProgressionService.resultMessage(ProgressionRules.Result.NO_CLASS));
            return 0;
        }
        StatEngine.recompute(player);
        List<String> lines = new ArrayList<>(StatEngine.describe(player));
        for (String discipline : new java.util.TreeSet<>(Ids.DISCIPLINES)) {
            double wall = StatCalculator.wall(data.state, def, DefinitionStore.SERVER.subclassDef(data.state.subclassId), discipline);
            if (wall > 0) lines.add(String.format(java.util.Locale.ROOT, "muro %s: -%.1f%%", discipline, wall * 100));
        }
        lines.add(String.format(java.util.Locale.ROOT, "crítico: %.0f%% x%.2f | sin armadura: %s",
                StatMath.critChance(ServerConfig.BASE_CRIT_CHANCE.get(), def.critChanceBonus(), false) * 100,
                StatMath.critMultiplier(ModAttributes.value(player, ModAttributes.CRIT_DAMAGE)),
                StatEngine.isWearingArmor(player) ? "no" : "sí"));
        String last = DamagePipeline.lastBreakdown(player);
        if (last != null) lines.add("último golpe: " + last);
        for (String line : lines) source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private ClassCommands() {}
}
