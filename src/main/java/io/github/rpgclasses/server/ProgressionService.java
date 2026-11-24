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
        int balanceBefore = s.pointBalance;

        Result result = perform(player, s, action, arg == null ? "" : arg);
        DebugLog.event(player, "progreso", "{} '{}' → {}; clase {}, ramas {}, perks {}, subclase {} ({}); raza {}/{} rango {} muros {}", action, arg, result,
                s.classId, s.branchPoints, s.perks, s.subclassId, s.subclassLevel, s.raceId, s.subraceId, s.raceRank, s.humanWalls);

        if (!result.ok()) {
            player.sendSystemMessage(resultMessage(result).withStyle(ChatFormatting.RED));
            return result;
        }
        long xpSpent = xpBefore - currentXp(player);
        int gained = s.pointBalance - balanceBefore;
        switch (action) {
            case BUY_POINTS -> player.sendSystemMessage((s.ownedPoints() >= ProgressionRules.MAX_POINTS
                    ? Component.translatable("msg.rites_and_paths.points_bought_max", gained, xpSpent, s.pointBalance)
                    : Component.translatable("msg.rites_and_paths.points_bought", gained, xpSpent, s.pointBalance,
                            ProgressionRules.nextCost(s, ServerConfig.XP_COST_MULTIPLIER.get(), ClassPoints.discount(player))))
                    .withStyle(ChatFormatting.GREEN));
            case RESPEC_CLASS, RESPEC_SUBCLASS -> player.sendSystemMessage(
                    Component.translatable("msg.rites_and_paths.points_refunded", gained, s.pointBalance).withStyle(ChatFormatting.GREEN));
            case USE_OBLIVION -> player.sendSystemMessage(
                    Component.translatable("msg.rites_and_paths.oblivion_used", gained, s.pointBalance).withStyle(ChatFormatting.LIGHT_PURPLE));
            case SAVE_BUILD -> player.sendSystemMessage(
                    Component.translatable("msg.rites_and_paths.build_saved", Integer.parseInt(arg.trim()) + 1).withStyle(ChatFormatting.GREEN));
            case LOAD_BUILD -> player.sendSystemMessage(Component.translatable("msg.rites_and_paths.build_loaded",
                    Integer.parseInt(arg.trim()) + 1, s.totalLevel(), s.pointBalance).withStyle(ChatFormatting.GREEN));
            default -> player.sendSystemMessage(resultMessage(result).withStyle(ChatFormatting.GREEN));
        }
        ClassPoints.touch(player);
        StatEngine.refresh(player);
        return result;
    }

    /** Acciones que puede llevar un plan del árbol de habilidades (nada de reinicios ni de elegir clase o raza). */
    private static final java.util.Set<Action> PLAN_ACTIONS =
            java.util.EnumSet.of(Action.SPEND_BRANCH, Action.CHOOSE_PERK, Action.CHOOSE_SUBCLASS, Action.SPEND_SUBCLASS);
    public static final int MAX_PLAN = 64;

    /**
     * Aplica en orden los pasos de un plan del árbol y avisa con un solo mensaje. Cada paso se valida igual que
     * si llegara suelto; el primero que falla detiene el plan (los anteriores se quedan aplicados).
     */
    public static void performPlan(ServerPlayer player, java.util.List<io.github.rpgclasses.network.Packets.ClassAction> steps) {
        PlayerClassData data = PlayerClassDataProvider.get(player);
        if (data == null || steps.isEmpty() || steps.size() > MAX_PLAN) return;
        for (var step : steps) if (!PLAN_ACTIONS.contains(step.action())) return;
        ProgressState s = data.state;
        int balanceBefore = s.pointBalance;
        int done = 0;
        Result failure = null;
        for (var step : steps) {
            String arg = step.arg() == null ? "" : step.arg();
            Result r = perform(player, s, step.action(), arg);
            DebugLog.event(player, "progreso", "plan {} '{}' → {}", step.action(), arg, r);
            if (!r.ok()) {
                failure = r;
                break;
            }
            done++;
        }
        DebugLog.event(player, "progreso", "plan {}/{}; clase {}, ramas {}, perks {}, subclase {} ({})", done, steps.size(),
                s.classId, s.branchPoints, s.perks, s.subclassId, s.subclassLevel);
        int spent = Math.max(0, balanceBefore - s.pointBalance);
        if (done > 0) {
            player.sendSystemMessage(Component.translatable("msg.rites_and_paths.plan_applied_points", done, spent, s.pointBalance)
                    .withStyle(ChatFormatting.GREEN));
        }
        if (failure != null) {
            player.sendSystemMessage(Component.translatable("msg.rites_and_paths.plan_partial", done, steps.size(), resultMessage(failure))
                    .withStyle(ChatFormatting.RED));
        }
        StatEngine.refresh(player);
    }

    private static Result perform(ServerPlayer player, ProgressState s, Action action, String arg) {
        DefinitionStore defs = DefinitionStore.SERVER;
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
                Result r = ProgressionRules.checkSpendBranch(s, classDef, arg);
                if (r.ok()) ProgressionRules.spendBranch(s, arg);
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
                Result r = ProgressionRules.checkSpendSubclass(s);
                if (r.ok()) ProgressionRules.spendSubclass(s);
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
                if (r.ok()) ProgressionRules.respecSubclass(s, respecLoss(s));
                return r;
            }
            case RESPEC_CLASS -> {
                Result r = ProgressionRules.checkRespecClass(s, CombatTracker.inCombat(player));
                if (r.ok()) respecClassNow(player, s, respecLoss(s));
                return r;
            }
            case BUY_POINTS -> {
                return buyPoints(player, s, "max".equalsIgnoreCase(arg.trim()) ? ProgressionRules.MAX_POINTS : 1);
            }
            case SAVE_BUILD -> {
                int slot = slot(arg);
                if (slot < 0) return Result.UNKNOWN_BUILD;
                if (s.classId == null) return Result.NO_CLASS;
                s.builds[slot] = io.github.rpgclasses.core.progression.SavedBuild.of(s);
                return Result.OK;
            }
            case LOAD_BUILD -> {
                return loadBuild(player, s, slot(arg));
            }
            case USE_OBLIVION -> {
                return useOblivion(player, s);
            }
        }
        return Result.OK;
    }

    /** Reinicia la clase devolviendo los puntos con esa pérdida, y quita el kit y lo propio de la clase. */
    private static void respecClassNow(ServerPlayer player, ProgressState s, double loss) {
        ProgressionRules.respecClass(s, loss);
        KitService.removeAll(player);
        PlayerClassData d = PlayerClassDataProvider.get(player);
        if (d != null) d.clearClassBindings();
    }

    /** Compra hasta {@code max} puntos con XP al precio de la curva (con la ayuda a rezagados). */
    private static Result buyPoints(ServerPlayer player, ProgressState s, int max) {
        if (!ServerConfig.BUY_POINTS_WITH_XP.get()) return Result.XP_PURCHASE_DISABLED;
        double mult = ServerConfig.XP_COST_MULTIPLIER.get();
        double discount = ClassPoints.discount(player);
        int bought = 0;
        Result last = Result.OK;
        while (bought < max) {
            long xp = currentXp(player);
            int cost = ProgressionRules.nextCost(s, mult, discount);
            last = ProgressionRules.checkBuyPoint(s, xp, cost);
            if (!last.ok()) break;
            setXp(player, xp - cost);
            ProgressionRules.buyPoint(s);
            bought++;
        }
        return bought > 0 ? Result.OK : last;
    }

    private static int slot(String arg) {
        try {
            int slot = Integer.parseInt(arg.trim());
            return slot >= 0 && slot < io.github.rpgclasses.core.progression.SavedBuild.SLOTS ? slot : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Carga una build: reinicia la clase con la pérdida normal (si tiene), elige la clase de la build y gasta el saldo
     * siguiéndola hasta donde llegue.
     */
    private static Result loadBuild(ServerPlayer player, ProgressState s, int slot) {
        if (slot < 0 || s.builds[slot] == null) return Result.UNKNOWN_BUILD;
        var build = s.builds[slot];
        DefinitionStore defs = DefinitionStore.SERVER;
        ClassDef def = defs.classDef(build.classId());
        if (def == null) return Result.UNKNOWN_CLASS;
        if (ServerConfig.DISABLED_CLASSES.get().contains(build.classId())) return Result.CLASS_DISABLED;
        if (s.raceId == null && !io.github.rpgclasses.core.definition.RaceStore.SERVER.ids().isEmpty()) return Result.NO_RACE;
        if (s.classId != null) {
            Result r = ProgressionRules.checkRespecClass(s, CombatTracker.inCombat(player));
            if (!r.ok()) return r;
            respecClassNow(player, s, respecLoss(s));
        }
        ProgressionRules.chooseClass(s, def);
        KitService.grant(player, def);
        s.kitGranted = true;
        SubclassDef sub = build.subclassId() == null ? null : defs.subclassDef(build.subclassId());
        io.github.rpgclasses.core.progression.BuildRules.apply(s, def, sub, build);
        return Result.OK;
    }

    /** Libro de Olvido: reinicio completo sin pérdida, gastando el libro de la mano. */
    private static Result useOblivion(ServerPlayer player, ProgressState s) {
        if (s.classId == null) return Result.NO_CLASS;
        if (CombatTracker.inCombat(player)) return Result.IN_COMBAT;
        net.minecraft.world.InteractionHand hand = null;
        for (net.minecraft.world.InteractionHand h : net.minecraft.world.InteractionHand.values()) {
            if (player.getItemInHand(h).is(io.github.rpgclasses.registry.ClassBooks.OBLIVION_BOOK.get())) hand = h;
        }
        if (hand == null) return Result.NO_OBLIVION_BOOK;
        respecClassNow(player, s, 0.0);
        if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1f, .6f);
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

    /** Reinicio administrativo: sin comprobación de combate y sin devolver puntos (el saldo que tuviera se queda). */
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
