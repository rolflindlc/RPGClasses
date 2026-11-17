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
        DebugLog.event(player, "progreso", "{} '{}' → {}; clase {}, ramas {}, perks {}, subclase {} ({}); raza {}/{} rango {} muros {}", action, arg, result,
                s.classId, s.branchPoints, s.perks, s.subclassId, s.subclassLevel, s.raceId, s.subraceId, s.raceRank, s.humanWalls);

        if (!result.ok()) {
            player.sendSystemMessage(resultMessage(result).withStyle(ChatFormatting.RED));
            return result;
        }
        long delta = currentXp(player) - xpBefore;
        if (delta < 0) {
            player.sendSystemMessage(ProgressionRules.isMaxed(s)
                    ? Component.translatable("msg.rites_and_paths.xp_charged_max", -delta)
                    : Component.translatable("msg.rites_and_paths.xp_charged", -delta,
                            ProgressionRules.nextCost(s, ServerConfig.XP_COST_MULTIPLIER.get())));
        } else if (delta > 0) {
            player.sendSystemMessage(Component.translatable("msg.rites_and_paths.xp_refunded", delta));
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
            case CHOOSE_RACE -> {
                int slash = arg.indexOf('/');
                if (slash <= 0) return Result.UNKNOWN_SUBRACE;
                var race = io.github.rpgclasses.core.definition.RaceStore.SERVER.race(arg.substring(0, slash));
                String subraceId = arg.substring(slash + 1);
                Result r = ProgressionRules.checkChooseRace(s, race, subraceId);
                if (r.ok()) ProgressionRules.chooseRace(s, race, subraceId);
                return r;
            }
            case CHOOSE_WALL -> {
                Result r = ProgressionRules.checkChooseWall(s, classDef, StatEngine.race(s), arg);
                if (r.ok()) s.humanWalls.add(arg);
                return r;
            }
            case CHOOSE_CLASS -> {
                if (s.raceId == null && !io.github.rpgclasses.core.definition.RaceStore.SERVER.ids().isEmpty()) return Result.NO_RACE;
                if (ServerConfig.DISABLED_CLASSES.get().contains(arg)) return Result.CLASS_DISABLED;
                ClassDef chosen = defs.classDef(arg);
                Result r = ProgressionRules.checkChooseClass(s, chosen);
                if (r.ok()) {
                    ProgressionRules.chooseClass(s, chosen);
                    KitService.grant(player, chosen);
                    s.kitGranted = true;
                }
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
            case CHOOSE_PERK -> {
                int slash = arg.indexOf('/');
                if (slash <= 0) return Result.UNKNOWN_PERK;
                String branchId = arg.substring(0, slash);
                String perkId = arg.substring(slash + 1);
                // Elfo × Carnívoro (anexo de razas, sección 4): no puede comer carne ni pescado, así que no puede elegirlo.
                if ("carnivore".equals(perkId) && MechanicService.has(player, "race_no_meat")) return Result.PERK_UNAVAILABLE;
                Result r = ProgressionRules.checkChoosePerk(s, classDef, branchId, perkId);
                if (r.ok()) ProgressionRules.choosePerk(s, branchId, perkId);
                return r;
            }
            case RESPEC_SUBCLASS -> {
                Result r = ProgressionRules.checkRespecSubclass(s, CombatTracker.inCombat(player));
                if (r.ok()) setXp(player, xp + ProgressionRules.respecSubclass(s, mult, respecLoss(s)));
                return r;
            }
            case RESPEC_CLASS -> {
                Result r = ProgressionRules.checkRespecClass(s, CombatTracker.inCombat(player));
                if (r.ok()) {
                    setXp(player, xp + ProgressionRules.respecClass(s, respecLoss(s)));
                    KitService.removeAll(player);
                    PlayerClassData d = PlayerClassDataProvider.get(player);
                    if (d != null) d.clearClassBindings();
                }
                return r;
            }
        }
        return Result.OK;
    }

    /** Pérdida de XP al reiniciar: la del servidor, reducida por el Humano Autodidacta. */
    public static double respecLoss(ProgressState s) {
        return ServerConfig.RESPEC_XP_LOSS.get() * io.github.rpgclasses.core.stat.StatCalculator.respecLossFactor(s, StatEngine.race(s));
    }

    /** Administración: fija (o quita, con race == null) la raza y subraza sin comprobaciones de permanencia. */
    public static boolean adminSetRace(ServerPlayer player, String raceId, String subraceId, int rank) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return false;
        if (raceId != null) {
            var race = io.github.rpgclasses.core.definition.RaceStore.SERVER.race(raceId);
            if (race == null || race.subrace(subraceId) == null) return false;
        }
        data.state.raceId = raceId;
        data.state.subraceId = raceId == null ? null : subraceId;
        data.state.raceRank = Math.max(0, Math.min(ProgressionRules.RACE_MAX_RANK, rank));
        data.state.riteProgress.clear(); // el rito en curso cambia con el rango
        data.state.humanWalls.clear();
        DebugLog.event(player, "raza", "admin: raza {} subraza {} rango {}", raceId, subraceId, data.state.raceRank);
        StatEngine.refresh(player);
        return true;
    }

    public static boolean adminSetRank(ServerPlayer player, int rank) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || data.state.raceId == null) return false;
        data.state.raceRank = Math.max(0, Math.min(ProgressionRules.RACE_MAX_RANK, rank));
        DebugLog.event(player, "raza", "admin: rango {}", data.state.raceRank);
        StatEngine.refresh(player);
        return true;
    }

    /** Reinicio administrativo: sin comprobación de combate y sin devolver XP. */
    public static void adminReset(ServerPlayer player) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null) return;
        ProgressionRules.respecClass(data.state, 1.0);
        KitService.removeAll(player);
        data.clearClassBindings();
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
        return Component.translatable("msg.rites_and_paths.result." + result.name().toLowerCase(Locale.ROOT));
    }

    private ProgressionService() {}
}
