package io.github.rpgclasses.engineer;

import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Recarga activa (adenda del Ingeniero, 3): mientras NTGL recarga (su {@code ReloadTracker}), el cliente enseña una barra
 * con un cursor; el jugador vuelve a pulsar recargar y el servidor decide la zona con su propio reloj (el del cliente solo
 * se acepta si está cerca, por la latencia). Perfecta y buena terminan la recarga al instante; fuera de zona, el arma se
 * encasquilla y la recarga se alarga. Si NTGL cancela la recarga al pulsar otra vez (su tecla también la para), el
 * servidor la completa a mano con la munición del inventario.
 */
final class ActiveReload {
    static final int PERFECT = 0, GOOD = 1, JAM = 2;
    /** Tics tras acabar la recarga en los que aún se acepta la pulsación (la de NTGL puede llegar antes y pararla). */
    private static final int GRACE = 6;

    private ActiveReload() {
    }

    static void tick(ServerPlayer p, Engineer.State s, long now) {
        if (!NtglCompat.ready()) return;
        Object tracker = NtglCompat.reloadTracker(p);
        if (tracker != s.tracker) {
            if (s.tracker != null) finish(p, s, now);
            s.tracker = tracker;
            if (tracker != null) start(p, s, now, tracker);
        }
        // Perfecta o buena: NTGL completa la recarga en el tic siguiente (en las de bala a bala, una por tic).
        if (tracker != null && s.autoComplete) NtglCompat.setReloadTicks(tracker, 0);
    }

    private static ItemStack gun(ServerPlayer p) {
        return p.getMainHandItem();
    }

    private static void start(ServerPlayer p, Engineer.State s, long now, Object tracker) {
        ItemStack gun = gun(p);
        int step = Math.max(1, NtglCompat.reloadTicks(tracker));
        int ammo = Math.max(0, NtglCompat.ammo(gun, p));
        int total = step;
        if (NtglCompat.perCartridge(gun, p)) total = step * Math.max(1, NtglCompat.maxAmmo(gun, p) - ammo);
        if (s.jamPending) {
            // Encasquillada tras una recarga que NTGL canceló: la nueva tarda un 50 % más.
            int extra = (int) Math.round(step * Engineer.a(Engineer.ACTIVE_RELOAD, "jam_slow", .5));
            NtglCompat.setReloadTicks(tracker, step + extra);
            total += extra;
            s.jamPending = false;
            s.reloadResolved = true;
        } else {
            s.reloadResolved = false;
        }
        s.reloadStart = now;
        s.reloadTotal = Math.max(1, total);
        s.ammoAtStart = ammo;
        s.autoComplete = false;
        s.retreating = Engineer.has(p, Engineer.RETREAT) && enemyNear(p, Engineer.mech(p, Engineer.RETREAT, "radius", 5));
        // Último Cargador: todas las recargas son perfectas.
        if (now < s.lastMagUntil) {
            resolve(p, s, now, PERFECT);
            return;
        }
        if (!s.reloadResolved && AbilityService.has(p, Engineer.ACTIVE_RELOAD)) {
            var params = Engineer.ability(Engineer.ACTIVE_RELOAD);
            EngineerPackets.bar(p, s.reloadTotal, (float) AbilityService.number(params, "perfect_start", .55),
                    (float) AbilityService.number(params, "perfect_end", .65), (float) AbilityService.number(params, "good_start", .40),
                    (float) AbilityService.number(params, "good_end", .80));
        }
    }

    private static void finish(ServerPlayer p, Engineer.State s, long now) {
        s.reloadEndedAt = now;
        s.autoComplete = false;
        s.retreating = false;
        int ammo = NtglCompat.ammo(gun(p), p);
        if (ammo > s.ammoAtStart) {
            ImprovisedAmmo.onReloaded(p, s);
            s.reloadResolved = true;
        }
        EngineerPackets.bar(p, 0, 0, 0, 0, 0);
    }

    private static boolean enemyNear(ServerPlayer p, double radius) {
        return !p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> e != p && e.isAlive() && (e instanceof Enemy || io.github.rpgclasses.server.Allies.isEnemy(p, e)
                        && e instanceof net.minecraft.world.entity.player.Player)).isEmpty();
    }

    /** El jugador vuelve a pulsar recargar con la barra en pantalla; {@code clientFraction} es dónde veía el cursor. */
    static void press(ServerPlayer p, float clientFraction) {
        if (!Engineer.is(p) || !AbilityService.has(p, Engineer.ACTIVE_RELOAD)) return;
        Engineer.State s = Engineer.state(p);
        long now = p.level().getGameTime();
        boolean active = s.tracker != null || now - s.reloadEndedAt <= GRACE;
        if (!active || s.reloadResolved || s.reloadTotal <= 0) return;
        double fraction = Math.min(1.2, (now - s.reloadStart) / (double) s.reloadTotal);
        if (Math.abs(clientFraction - fraction) < 0.2) fraction = clientFraction;
        var params = Engineer.ability(Engineer.ACTIVE_RELOAD);
        int zone;
        if (fraction >= AbilityService.number(params, "perfect_start", .55) && fraction <= AbilityService.number(params, "perfect_end", .65)) {
            zone = PERFECT;
        } else if (fraction >= AbilityService.number(params, "good_start", .40) && fraction <= AbilityService.number(params, "good_end", .80)) {
            zone = GOOD;
        } else {
            zone = JAM;
        }
        resolve(p, s, now, zone);
    }

    private static void resolve(ServerPlayer p, Engineer.State s, long now, int zone) {
        s.reloadResolved = true;
        EngineerPackets.result(p, zone);
        if (zone == JAM) {
            int remove = Engineer.has(p, Engineer.STEADY) ? (int) Engineer.mech(p, Engineer.STEADY, "jam_loss", 2) : s.streak;
            s.streak = Math.max(0, s.streak - remove);
            if (s.tracker != null) {
                int extra = (int) Math.round(s.reloadTotal * Engineer.a(Engineer.ACTIVE_RELOAD, "jam_slow", .5));
                NtglCompat.setReloadTicks(s.tracker, NtglCompat.reloadTicks(s.tracker) + extra);
            } else {
                s.jamPending = true;
                NtglCompat.startReload(p, InteractionHand.MAIN_HAND);
            }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1f, 0.8f);
            return;
        }
        if (s.tracker != null) {
            s.autoComplete = true;
            NtglCompat.setReloadTicks(s.tracker, 0);
        } else if (fill(p)) {
            ImprovisedAmmo.onReloaded(p, s);
        }
        if (zone != PERFECT) return;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 2.0f);
        s.perfectUntil = now + Math.round(Engineer.a(Engineer.ACTIVE_RELOAD, "perfect_seconds", 4) * 20);
        if (Engineer.has(p, Engineer.STREAK)) {
            s.streak = Math.min((int) Engineer.mech(p, Engineer.STREAK, "max_stacks", 6), s.streak + 1);
            s.lastShot = Math.max(s.lastShot, now);
        }
        if (Engineer.has(p, Engineer.MOVING)) {
            s.burstSpeed = Engineer.has(p, Engineer.RETREAT) ? Engineer.mech(p, Engineer.RETREAT, "perfect_speed", .10)
                    : Engineer.mech(p, Engineer.MOVING, "perfect_speed", .20);
            s.burstUntil = now + Math.round(Engineer.mech(p, Engineer.MOVING, "perfect_speed_seconds", 2) * 20);
        }
        if (Engineer.has(p, Engineer.DASH) && now >= s.dashReadyAt) {
            s.dashReadyAt = now + Math.round(Engineer.mech(p, Engineer.DASH, "icd_seconds", 6) * 20);
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() > 1e-4) {
                // Unos 4 bloques: el impulso horizontal se frena con el rozamiento del suelo.
                double distance = Engineer.mech(p, Engineer.DASH, "distance", 4);
                Vec3 push = flat.normalize().scale(distance * 0.28);
                p.setDeltaMovement(push.x, Math.max(p.getDeltaMovement().y, 0.15), push.z);
                p.hurtMarked = true;
            }
        }
    }

    /** Completa a mano la recarga del arma de la mano con munición del inventario. Devuelve true si cargó algo. */
    static boolean fill(ServerPlayer p) {
        ItemStack gun = gun(p);
        int ammo = NtglCompat.ammo(gun, p), max = NtglCompat.maxAmmo(gun, p);
        if (ammo < 0 || max <= ammo) return false;
        int got = take(p, NtglCompat.currentAmmoId(gun, p), max - ammo);
        if (got <= 0) return false;
        NtglCompat.setAmmo(gun, p, ammo + got);
        return true;
    }

    /** Saca hasta {@code wanted} unidades de esa munición del inventario (en creativo no gasta). Devuelve cuántas. */
    static int take(ServerPlayer p, ResourceLocation ammoId, int wanted) {
        if (ammoId == null || wanted <= 0) return 0;
        Item item = ForgeRegistries.ITEMS.getValue(ammoId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return 0;
        if (p.getAbilities().instabuild) return wanted;
        int got = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && got < wanted; i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) {
                int n = Math.min(wanted - got, st.getCount());
                st.shrink(n);
                got += n;
            }
        }
        return got;
    }
}
