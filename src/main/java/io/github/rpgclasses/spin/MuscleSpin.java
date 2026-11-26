package io.github.rpgclasses.spin;

import io.github.rpgclasses.content.race.Races;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Giro muscular (bola sobrecargada: el clic derecho mantenido {@code muscle_charge_seconds}): como en la serie, el giro
 * pasa a los músculos del objetivo y le obliga a usar lo que lleva en la mano principal.
 * <ul>
 *   <li>Arma, puños o algo sin uso: ataca a lo que tiene delante (nunca al lanzador).</li>
 *   <li>Arco: lo tensa del todo y dispara. Ballesta: si está cargada dispara; si no, la carga. Tridente: lo lanza.</li>
 *   <li>Comida o bebida: se la toma entera (aunque esté lleno).</li>
 *   <li>Bastón mágico de Iron's (o cualquier objeto con uso): lo usa; el bastón lanza el conjuro seleccionado.</li>
 * </ul>
 * Lo que tiene tiempo de uso se hace entero: mientras dura, el objetivo no puede soltarlo (se cancela el
 * {@code LivingEntityUseItemEvent.Stop}) ni cancelar el conjuro (mixin de {@code ServerboundCancelCast}). Los jefes no
 * se ven afectados.
 */
public final class MuscleSpin {
    /** Uso forzado en curso: hasta qué tic dura, la mano y si el objetivo es un mob (que se simula a mano). */
    private static final class Forced {
        final LivingEntity entity;
        final InteractionHand hand;
        final ItemStack stack;
        final long start, end;
        final Kind kind;

        Forced(LivingEntity entity, InteractionHand hand, ItemStack stack, long start, long end, Kind kind) {
            this.entity = entity;
            this.hand = hand;
            this.stack = stack;
            this.start = start;
            this.end = end;
            this.kind = kind;
        }
    }

    private enum Kind { RELEASE, NATURAL, CAST, MOB_BOW, MOB_CROSSBOW, MOB_FOOD }

    private static final Map<Integer, Forced> FORCED = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    /** Mientras se suelta a propósito el uso forzado, el evento Stop no se cancela. */
    private static boolean releasing;
    private static final int MAX_USE_TICKS = 200, CAST_MAX_TICKS = 20 * 12, LONG_USE_TICKS = 40;

    private MuscleSpin() {}

    /** La bola sobrecargada impacta: el objetivo usa lo que lleva en la mano. */
    static void trigger(SphereEntity s, ServerPlayer owner, LivingEntity target) {
        if (!target.isAlive() || Races.isBoss(target) || FORCED.containsKey(target.getId())) return;
        ServerLevel level = (ServerLevel) target.level();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = target.getItemInHand(hand);
        long now = level.getGameTime();
        boolean used = target instanceof ServerPlayer pl ? forcePlayer(pl, owner, hand, stack, now) : forceMob(target, owner, hand, stack, now);
        if (!used) melee(target, owner);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.6f);
        SpinFxSender.onomatopoeia(level, target.getEyePosition(), SpinFxSender.WORD_GOGOGO);
        if (target instanceof ServerPlayer pl) {
            pl.displayClientMessage(Component.translatable("message.rites_and_paths.spin.muscle").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            SpinFxSender.shake(pl, 0.4f);
        }
    }

    // ------------------------------------------------------------------ jugadores

    private static boolean forcePlayer(ServerPlayer p, ServerPlayer owner, InteractionHand hand, ItemStack stack, long now) {
        if (stack.isEmpty()) return false;
        if (p.isUsingItem()) p.stopUsingItem();
        // Comida y bebida: se toman aunque esté lleno.
        if (stack.isEdible() || stack.getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK) {
            p.startUsingItem(hand);
            return track(p, hand, stack, now, Kind.NATURAL, Math.min(MAX_USE_TICKS, stack.getUseDuration()));
        }
        // Ballesta cargada: dispara ya.
        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            CrossbowItem.performShooting(p.level(), p, hand, stack, 3.15f, 1.0f);
            CrossbowItem.setCharged(stack, false);
            return true;
        }
        InteractionResultHolder<ItemStack> r = stack.use(p.level(), p, hand);
        if (r.getObject() != stack) p.setItemInHand(hand, r.getObject());
        if (!r.getResult().consumesAction()) return false;
        if (!p.isUsingItem()) return true; // uso instantáneo (perla de Ender, poción arrojadiza...)
        ItemStack using = p.getUseItem();
        if (using.getItem() instanceof BowItem) return track(p, hand, using, now, Kind.RELEASE, 20);
        if (using.getItem() instanceof CrossbowItem) return track(p, hand, using, now, Kind.RELEASE, CrossbowItem.getChargeDuration(using) + 1);
        if (using.getItem() instanceof TridentItem) return track(p, hand, using, now, Kind.RELEASE, 12);
        if (isCastingItem(using)) return track(p, hand, using, now, Kind.CAST, CAST_MAX_TICKS);
        int d = using.getUseDuration();
        return d <= MAX_USE_TICKS ? track(p, hand, using, now, Kind.NATURAL, d) : track(p, hand, using, now, Kind.RELEASE, LONG_USE_TICKS);
    }

    private static boolean isCastingItem(ItemStack stack) {
        try {
            return Class.forName("io.redspace.ironsspellbooks.item.CastingItem").isInstance(stack.getItem());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ mobs

    private static boolean forceMob(LivingEntity m, ServerPlayer owner, InteractionHand hand, ItemStack stack, long now) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof BowItem) {
            m.startUsingItem(hand);
            return track(m, hand, stack, now, Kind.MOB_BOW, 20);
        }
        if (stack.getItem() instanceof CrossbowItem) {
            if (CrossbowItem.isCharged(stack)) {
                CrossbowItem.performShooting(m.level(), m, hand, stack, 1.6f, 1.0f);
                CrossbowItem.setCharged(stack, false);
                return true;
            }
            m.startUsingItem(hand);
            return track(m, hand, stack, now, Kind.MOB_CROSSBOW, CrossbowItem.getChargeDuration(stack) + 1);
        }
        if (stack.isEdible() || stack.getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK) {
            m.startUsingItem(hand);
            return track(m, hand, stack, now, Kind.MOB_FOOD, Math.max(1, Math.min(MAX_USE_TICKS, stack.getUseDuration()) - 1));
        }
        return false;
    }

    private static boolean track(LivingEntity e, InteractionHand hand, ItemStack stack, long now, Kind kind, int ticks) {
        FORCED.put(e.getId(), new Forced(e, hand, stack, now, now + Math.max(1, ticks), kind));
        return true;
    }

    // ------------------------------------------------------------------ golpe

    /** Ataca a lo que tiene delante (al alcance de su brazo), sin contar al lanzador. Si no hay nada, golpea al aire. */
    private static void melee(LivingEntity e, ServerPlayer owner) {
        e.swing(InteractionHand.MAIN_HAND, true);
        double reach = e instanceof Player ? 3.0 : Math.max(2.0, e.getBbWidth() * 2 + 1);
        Vec3 eye = e.getEyePosition();
        Vec3 end = eye.add(e.getLookAngle().scale(reach));
        AABB box = e.getBoundingBox().expandTowards(e.getLookAngle().scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(e.level(), e, eye, end, box,
                o -> o instanceof LivingEntity && o.isAlive() && o != owner && !o.isSpectator() && o.isPickable());
        Entity victim = hit == null ? null : hit.getEntity();
        if (victim == null) return;
        if (e instanceof Player pl) pl.attack(victim);
        else if (e instanceof Mob mob) mob.doHurtTarget(victim);
    }

    // ------------------------------------------------------------------ duración del uso forzado

    /** Mientras dura el uso forzado no se puede soltar (arco, ballesta, comida, bastón). */
    @SubscribeEvent
    public void onStop(LivingEntityUseItemEvent.Stop event) {
        if (!releasing && FORCED.containsKey(event.getEntity().getId()) && !event.getEntity().level().isClientSide) event.setCanceled(true);
    }

    /** El objetivo de un conjuro forzado no lo puede cancelar (lo llama el mixin de ServerboundCancelCast). */
    public static boolean blocksCancel(ServerPlayer p) {
        Forced f = FORCED.get(p.getId());
        return f != null && f.kind == Kind.CAST && f.entity == p;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FORCED.isEmpty()) return;
        Iterator<Forced> it = FORCED.values().iterator();
        while (it.hasNext()) {
            Forced f = it.next();
            LivingEntity e = f.entity;
            if (e.isRemoved() || !e.isAlive()) {
                it.remove();
                continue;
            }
            long now = e.level().getGameTime();
            switch (f.kind) {
                case NATURAL -> {
                    // Se completa solo (comida, poción...). Si dejó de usarlo (cambió de objeto), se acaba.
                    if (!e.isUsingItem() || now > f.end + 2) it.remove();
                }
                case CAST -> {
                    boolean casting = net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")
                            && e instanceof ServerPlayer sp && io.github.rpgclasses.compat.irons.IronsCompat.isCasting(sp);
                    if ((!casting && now - f.start > 2) || now >= f.end) {
                        it.remove();
                        if (e.isUsingItem()) release(e);
                    }
                }
                case RELEASE -> {
                    if (!e.isUsingItem()) {
                        it.remove();
                    } else if (now >= f.end) {
                        it.remove();
                        release(e);
                    }
                }
                case MOB_BOW, MOB_CROSSBOW, MOB_FOOD -> {
                    // A los mobs su IA les puede quitar el uso: se mantiene la animación y el efecto se hace a mano.
                    if (e.getItemInHand(f.hand) != f.stack) {
                        it.remove();
                        continue;
                    }
                    if (!e.isUsingItem()) e.startUsingItem(f.hand);
                    if (now >= f.end) {
                        it.remove();
                        finishMob(e, f);
                    }
                }
            }
        }
    }

    private static void release(LivingEntity e) {
        releasing = true;
        try {
            e.releaseUsingItem();
        } finally {
            releasing = false;
        }
    }

    private static void finishMob(LivingEntity m, Forced f) {
        ItemStack stack = f.stack;
        switch (f.kind) {
            case MOB_BOW -> {
                AbstractArrow arrow = ProjectileUtil.getMobArrow(m, new ItemStack(Items.ARROW), 1.0f);
                arrow.shootFromRotation(m, m.getXRot(), m.getYHeadRot(), 0, 1.6f, 1.0f);
                m.level().addFreshEntity(arrow);
                m.level().playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.SKELETON_SHOOT, m.getSoundSource(), 1.0f, 1.0f);
            }
            case MOB_CROSSBOW -> {
                int remaining = stack.getUseDuration() - CrossbowItem.getChargeDuration(stack) - 1;
                releasing = true;
                try {
                    stack.releaseUsing(m.level(), m, Math.max(0, remaining));
                } finally {
                    releasing = false;
                }
            }
            case MOB_FOOD -> {
                ItemStack result = stack.finishUsingItem(m.level(), m);
                if (result != stack) m.setItemInHand(f.hand, result);
            }
            default -> {}
        }
        m.stopUsingItem();
    }
}
