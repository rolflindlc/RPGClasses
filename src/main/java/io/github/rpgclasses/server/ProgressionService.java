package io.github.rpgclasses.server;

import io.github.rpgclasses.config.ServerConfig;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions.ClassDef;
import io.github.rpgclasses.core.definition.Definitions.SubclassDef;
import io.github.rpgclasses.core.progression.ProgressState;
import io.github.rpgclasses.core.progression.ProgressionRules;
import io.github.rpgclasses.core.progression.ProgressionRules.Result;
import io.github.rpgclasses.core.progression.XpMath;
import io.github.rpgclasses.network.Packets.ClassAction.Action;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Único punto de entrada para cambiar el progreso de un jugador. Cada operación es atómica:
 * comprobar reglas, cobrar o devolver XP, modificar el estado y sincronizar. Solo servidor.
 */
public final class ProgressionService {

    /** Ejecuta la acción y envía al jugador el mensaje de resultado. */
    public static Result performAndReport(ServerPlayer player, Action action, String arg) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return Result.NO_CLASS;
        ProgressState s = data.state;
        long xpBefore = currentXp(player);

        Result result = perform(player, s, action, arg == null ? "" : arg);

        if (!result.ok()) {
            player.sendSystemMessage(resultMessage(result).withStyle(ChatFormatting.RED));
            return result;
        }
        long delta = currentXp(player) - xpBefore;
        if (delta < 0) {
            player.sendSystemMessage(ProgressionRules.isMaxed(s)
                    ? Component.translatable("msg.rpgclasses.xp_charged_max", -delta)
                    : Component.translatable("msg.rpgclasses.xp_charged", -delta,
                            ProgressionRules.nextCost(s, ServerConfig.XP_COST_MULTIPLIER.get())));
        } else if (delta > 0) {
            player.sendSystemMessage(Component.translatable("msg.rpgclasses.xp_refunded", delta));
        } else {
            player.sendSystemMessage(resultMessage(result).withStyle(ChatFormatting.GREEN));
        }
        StatEngine.refresh(player);
        return result;
    }

    private static Result perform(ServerPlayer player, ProgressState s, Action action, String arg) {
        DefinitionStore defs = DefinitionStore.SERVER;
        double mult = ServerConfig.XP_COST_MULTIPLIER.get();
        long xp = currentXp(player);
        ClassDef classDef = defs.classDef(s.classId);

        switch (action) {
            case CHOOSE_CLASS -> {
                if (ServerConfig.DISABLED_CLASSES.get().contains(arg)) return Result.CLASS_DISABLED;
                ClassDef chosen = defs.classDef(arg);
                Result r = ProgressionRules.checkChooseClass(s, chosen);
                if (r.ok()) ProgressionRules.chooseClass(s, chosen);
                return r;
            }
            case SPEND_BRANCH -> {
                Result r = ProgressionRules.checkSpendBranch(s, classDef, arg, xp, mult);
                if (r.ok()) setXp(player, xp - ProgressionRules.spendBranch(s, arg, mult));
                return r;
            }
            case CHOOSE_SUBCLASS -> {
                SubclassDef sub = defs.subclassDef(arg);
                Result r = ProgressionRules.checkChooseSubclass(s, classDef, sub);
                if (r.ok()) ProgressionRules.chooseSubclass(s, sub);
                return r;
            }
            case SPEND_SUBCLASS -> {
                if (s.subclassId != null && defs.subclassDef(s.subclassId) == null) return Result.UNKNOWN_SUBCLASS;
                Result r = ProgressionRules.checkSpendSubclass(s, xp, mult);
                if (r.ok()) setXp(player, xp - ProgressionRules.spendSubclass(s, mult));
                return r;
            }
            case RESPEC_SUBCLASS -> {
                Result r = ProgressionRules.checkRespecSubclass(s, CombatTracker.inCombat(player));
                if (r.ok()) setXp(player, xp + ProgressionRules.respecSubclass(s, mult, ServerConfig.RESPEC_XP_LOSS.get()));
                return r;
            }
            case RESPEC_CLASS -> {
                Result r = ProgressionRules.checkRespecClass(s, CombatTracker.inCombat(player));
                if (r.ok()) setXp(player, xp + ProgressionRules.respecClass(s, ServerConfig.RESPEC_XP_LOSS.get()));
                return r;
            }
        }
        return Result.OK;
    }

    /** Reinicio administrativo: sin comprobación de combate y sin devolver XP. */
    public static void adminReset(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        ProgressionRules.respecClass(data.state, 1.0);
        StatEngine.refresh(player);
    }

    public static long currentXp(ServerPlayer player) {
        return XpMath.totalPoints(player.experienceLevel, player.experienceProgress);
    }

    /** Fija la XP exacta del jugador (nivel + resto), evitando los redondeos de sumar puntos negativos. */
    private static void setXp(ServerPlayer player, long total) {
        long clamped = Math.max(0, Math.min(total, Integer.MAX_VALUE));
        int level = XpMath.levelFromTotal(clamped);
        player.setExperienceLevels(level);
        player.setExperiencePoints((int) (clamped - XpMath.totalXpAtLevel(level)));
    }

    public static net.minecraft.network.chat.MutableComponent resultMessage(Result result) {
        return Component.translatable("msg.rpgclasses.result." + result.name().toLowerCase(Locale.ROOT));
    }

    private ProgressionService() {}
}
