package io.github.rpgclasses.druid;

import io.github.rpgclasses.content.Chronomancy;
import io.github.rpgclasses.network.DruidPackets;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.SessionState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Conocimiento de especies (adenda, 2). Cada punto sale de una de dos fuentes, y cada criatura solo vale una vez para
 * cada una (se puede estudiar y luego usar en un ritual):
 * <ul>
 *   <li><b>Estudio:</b> mantener clic derecho con el Libro de Estudio sobre una criatura viva hasta llenar la barra;
 *       recibir daño la reinicia; el libro se gasta al terminar ({@link StudyBookItem}).</li>
 *   <li><b>Ritual:</b> el conjuro Ritual del Druida (Iron's, sin pergamino) sobre los restos ({@link BeastRemains}) de
 *       una bestia recién muerta, dentro del margen de tiempo. El Escultor elige entre el punto o una parte.</li>
 * </ul>
 */
public final class Knowledge {
    private record Study(int target, int needed) {}

    private record Pending(String species, long until) {}

    private static final Map<UUID, Study> STUDIES = SessionState.track(new HashMap<>());
    private static final Map<UUID, Float> PROGRESS = SessionState.track(new HashMap<>());
    private static final Map<UUID, Pending> PENDING = SessionState.track(new HashMap<>());

    // ------------------------------------------------------------------ estudio

    /** Criatura viva que el jugador mira (hasta 5 bloques), o null. */
    public static LivingEntity lookTarget(Player p) {
        double range = 5;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box,
                e -> e instanceof LivingEntity le && le.isAlive() && e != p);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    /** Tics que tarda un estudio para este jugador (Saber lo acorta, Erudito lo alarga). */
    static int studyTicks(Player p) {
        double seconds = Druid.number(Druid.BEAST_LORE, "study_seconds", 5, p.level().isClientSide)
                * (1 + Druid.mech(p, Druid.LORE, "study_time", 0)) * (1 + Druid.mech(p, Druid.SCHOLAR, "study_time", 0));
        return Math.max(10, (int) Math.round(seconds * 20));
    }

    /** Empieza el estudio (al pulsar con el libro). Devuelve false si no se puede (con su aviso). */
    static boolean startStudy(ServerPlayer p) {
        if (!Druid.isDruid(p) || !Druid.has(p, Druid.BEAST_LORE)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.not_druid"), true);
            return false;
        }
        LivingEntity target = lookTarget(p);
        if (target == null || !Beasts.eligible(target)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.study_invalid"), true);
            return false;
        }
        PlayerClassData data = Druid.data(p);
        if (data == null || Bestiary.used(data, Bestiary.STUDY, target.getUUID())) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.study_already"), true);
            return false;
        }
        STUDIES.put(p.getUUID(), new Study(target.getId(), studyTicks(p)));
        PROGRESS.put(p.getUUID(), 0f);
        return true;
    }

    /** Cada tic con el libro en uso: {@code elapsed} tics desde que se empezó. */
    static void tickStudy(ServerPlayer p, ItemStack book, int elapsed) {
        Study s = STUDIES.get(p.getUUID());
        if (s == null) {
            p.stopUsingItem();
            return;
        }
        Entity target = p.level().getEntity(s.target());
        LivingEntity looked = lookTarget(p);
        if (!(target instanceof LivingEntity le) || !le.isAlive() || looked != le) {
            cancelStudy(p, true);
            p.stopUsingItem();
            return;
        }
        PROGRESS.put(p.getUUID(), Math.min(1f, elapsed / (float) s.needed()));
        if (elapsed % 10 == 0) {
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.ENCHANT, le.getX(), le.getY() + le.getBbHeight() + .3, le.getZ(),
                    4, le.getBbWidth() / 2, .2, le.getBbWidth() / 2, .3);
        }
        if (elapsed < s.needed()) return;
        STUDIES.remove(p.getUUID());
        PROGRESS.remove(p.getUUID());
        PlayerClassData data = Druid.data(p);
        if (data == null) return;
        Bestiary.markUsed(data, Bestiary.STUDY, le.getUUID());
        if (!p.getAbilities().instabuild) book.shrink(1);
        p.stopUsingItem();
        int points = Druid.hasMech(p, Druid.SCHOLAR) ? (int) Druid.mech(p, Druid.SCHOLAR, "study_points", 2) : 1;
        String species = Beasts.id(le.getType());
        grant(p, species, points, "message.rites_and_paths.druid.study_done");
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        DruidNet.sendState(p);
    }

    static void cancelStudy(ServerPlayer p, boolean message) {
        if (STUDIES.remove(p.getUUID()) == null) return;
        PROGRESS.remove(p.getUUID());
        if (message) p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.study_interrupted"), true);
        DruidNet.sendState(p);
    }

    public static boolean studying(ServerPlayer p) {
        return STUDIES.containsKey(p.getUUID());
    }

    static float studyProgress(ServerPlayer p) {
        Float f = PROGRESS.get(p.getUUID());
        return f == null ? -1 : f;
    }

    static int studyTarget(ServerPlayer p) {
        Study s = STUDIES.get(p.getUUID());
        return s == null ? -1 : s.target();
    }

    /** Recibir daño reinicia la barra del estudio. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getAmount() > 0 && studying(p)) {
            cancelStudy(p, true);
            p.stopUsingItem();
        }
    }

    // ------------------------------------------------------------------ restos

    /** Una bestia muere con un Druida cerca: quedan sus restos para el ritual. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (event.isCanceled() || dead.level().isClientSide || dead instanceof Player || !Beasts.eligible(dead)) return;
        double radius = Druid.number(Druid.BEAST_LORE, "remains_radius", 48);
        boolean druidNear = false;
        for (Player p : dead.level().players()) {
            if (p.distanceToSqr(dead) <= radius * radius && Druid.isDruid(p)) {
                druidNear = true;
                break;
            }
        }
        if (!druidNear) return;
        // Vive tanto como el margen más largo posible (Saber lleno); cada Druida mira su propio margen.
        int lifetime = (int) Math.round((Druid.number(Druid.BEAST_LORE, "ritual_margin_seconds", 20) + 10) * 20);
        dead.level().addFreshEntity(BeastRemains.of(dead.level(), dead, lifetime));
    }

    // ------------------------------------------------------------------ ritual

    /** Margen (segundos desde la muerte) que tiene este Druida para el ritual. */
    static double ritualMargin(Player p) {
        return Druid.number(Druid.BEAST_LORE, "ritual_margin_seconds", 20) + Druid.mech(p, Druid.LORE, "ritual_margin", 0)
                + Druid.mech(p, Druid.SCAVENGER, "ritual_margin", 0);
    }

    /** Maná del ritual para este Druida (Carroñero paga el doble). */
    public static int ritualMana(Player p) {
        return (int) Math.round(Druid.number(Druid.BEAST_LORE, "ritual_mana", 35) * (1 + Druid.mech(p, Druid.SCAVENGER, "ritual_mana", 0)));
    }

    /** Restos a los que apunta el jugador (dentro del alcance del ritual), o null. */
    public static BeastRemains lookRemains(ServerPlayer p) {
        double range = Druid.number(Druid.BEAST_LORE, "ritual_range", 8);
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        BeastRemains best = null;
        double bestDist = Double.MAX_VALUE;
        for (BeastRemains r : p.level().getEntitiesOfClass(BeastRemains.class, p.getBoundingBox().inflate(range + 2))) {
            AABB box = r.getBoundingBox().inflate(.6);
            Optional<Vec3> hit = box.clip(eye, stop);
            double d = hit.map(eye::distanceToSqr).orElse(Double.MAX_VALUE);
            // Ayuda: lo que está a menos de 1,5 bloques de donde mira (si mira al suelo junto a los restos).
            if (hit.isEmpty() && r.position().distanceToSqr(stop) < 2.25) d = eye.distanceToSqr(r.position());
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        return best;
    }

    /** ¿Vale este ritual? Avisa si no. */
    public static boolean canRitual(ServerPlayer p, BeastRemains r) {
        if (!Druid.isDruid(p) || !Druid.has(p, Druid.BEAST_LORE)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.not_druid"), true);
            return false;
        }
        if (r == null || r.isRemoved() || Beasts.type(r.species()) == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.ritual_no_target"), true);
            return false;
        }
        if (r.ageSeconds() > ritualMargin(p)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.ritual_too_old"), true);
            return false;
        }
        PlayerClassData data = Druid.data(p);
        if (data == null || Bestiary.used(data, Bestiary.RITUAL, r.creature())) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.ritual_already"), true);
            return false;
        }
        return true;
    }

    /** El ritual se completa (lo llama el conjuro al terminar de lanzarse). {@code extraMana}: lo que falta por pagar. */
    public static void performRitual(ServerPlayer p, BeastRemains r, int extraMana) {
        if (!canRitual(p, r)) return;
        PlayerClassData data = Druid.data(p);
        if (data == null) return;
        if (extraMana > 0 && !p.isCreative() && Chronomancy.mana != null) {
            float mana = Chronomancy.mana.mana(p);
            if (mana < extraMana) {
                p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_mana", extraMana), true);
                return;
            }
            Chronomancy.mana.setMana(p, mana - extraMana);
        }
        Bestiary.markUsed(data, Bestiary.RITUAL, r.creature());
        String species = r.species();
        ServerLevel level = (ServerLevel) p.level();
        level.sendParticles(ParticleTypes.SOUL, r.getX(), r.getY() + .3, r.getZ(), 20, .4, .2, .4, .05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, r.getX(), r.getY() + .3, r.getZ(), 6, .3, .2, .3, .02);
        level.playSound(null, r.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2f, .8f);
        r.discard();
        if (Druid.hasMech(p, Druid.SCULPTOR)) {
            PENDING.put(p.getUUID(), new Pending(species, p.level().getGameTime() + 20 * 60));
            Net.toPlayer(p, new DruidPackets.RitualChoice(species, Beasts.known(p, species)));
            return;
        }
        int points = Druid.hasMech(p, Druid.SCAVENGER) ? (int) Druid.mech(p, Druid.SCAVENGER, "ritual_points", 2) : 1;
        grant(p, species, points, "message.rites_and_paths.druid.ritual_done");
    }

    /** Escultor: elige conocimiento (0) o una parte (1-4: cabeza, torso, patas, instinto). */
    static void ritualChoice(ServerPlayer p, int choice) {
        Pending pending = PENDING.remove(p.getUUID());
        if (pending == null || p.level().getGameTime() > pending.until()) return;
        if (choice >= 1 && choice <= 4 && Beasts.known(p, pending.species()) && Druid.hasMech(p, Druid.SCULPTOR)) {
            ItemStack part = Sculptor.part(choice - 1, pending.species());
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.part_gained", part.getHoverName()), true);
            if (!p.getInventory().add(part)) p.drop(part, false);
            return;
        }
        int points = Druid.hasMech(p, Druid.SCAVENGER) ? (int) Druid.mech(p, Druid.SCAVENGER, "ritual_points", 2) : 1;
        grant(p, pending.species(), points, "message.rites_and_paths.druid.ritual_done");
    }

    /** Suma conocimiento y avisa (también cuando la especie queda aprendida). */
    static void grant(ServerPlayer p, String species, int points, String messageKey) {
        PlayerClassData data = Druid.data(p);
        Beasts.Info info = Beasts.info(species, false);
        if (data == null || info == null) return;
        int needed = Beasts.requiredPoints(p, info);
        boolean before = Bestiary.points(data, species) >= needed;
        int cap = (int) Druid.number(Druid.BEAST_LORE, "max_points", 6);
        int total = Bestiary.addPoints(data, species, points, Math.max(cap, needed));
        Component name = Beasts.name(species);
        if (messageKey.endsWith("study_done")) {
            p.displayClientMessage(Component.translatable(messageKey, name, Math.min(total, needed), needed), true);
        } else {
            p.displayClientMessage(Component.translatable(messageKey, Math.min(total, needed), needed, name), true);
        }
        if (!before && total >= needed) {
            p.sendSystemMessage(Component.translatable("message.rites_and_paths.druid.species_learned", name)
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, .6f, 1.3f);
        }
        DebugLog.event(p, "Druida", "conocimiento de {}: {} (+{}, necesita {})", species, total, points, needed);
        Net.syncProgress(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STUDIES.remove(event.getEntity().getUUID());
        PROGRESS.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }
}
