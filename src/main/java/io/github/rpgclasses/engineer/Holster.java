package io.github.rpgclasses.engineer;

import io.github.rpgclasses.compat.ntgl.NtglCompat;
import io.github.rpgclasses.server.DamagePipeline;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Cartuchera (adenda del Ingeniero, 3.1): las armas de fuego de la barra rápida que no llevas en la mano se recargan
 * solas, más despacio, con munición del inventario. Perk Arma Arrojadiza: con el arma vacía, el clic izquierdo la lanza
 * ({@link ThrownFirearm}); vuelve recargada al inventario a los pocos segundos, y la cartuchera solo recarga las lanzadas.
 */
final class Holster {
    /** Arma lanzada que vuelve: su pila, la ranura de la que salió y el tic de vuelta. */
    private record Pending(ItemStack stack, int slot, long at) {}

    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();
    private static final int STEP = 5;

    private Holster() {
    }

    static void tick(ServerPlayer p, Engineer.State s, long now) {
        List<Pending> pending = PENDING.get(p.getUUID());
        if (pending != null) {
            for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
                Pending r = it.next();
                if (now >= r.at()) {
                    it.remove();
                    give(p, r);
                }
            }
            if (pending.isEmpty()) PENDING.remove(p.getUUID());
        }
        if (now % STEP != 0 || !Engineer.has(p, Engineer.HOLSTER) || Engineer.has(p, Engineer.THROWING)) return;
        double speed = Engineer.mech(p, Engineer.HOLSTER, "speed", 0);
        // Bandolera Giratoria: la cartuchera va más despacio (como mucho, su velocidad).
        if (Engineer.has(p, Engineer.BANDOLIER)) speed = Math.min(speed, Engineer.mech(p, Engineer.BANDOLIER, "speed", .35));
        if (speed <= 0) return;
        var inv = p.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack gun = inv.getItem(slot);
            if (slot == inv.selected || !NtglCompat.isFirearm(gun)) {
                s.holster[slot] = 0;
                continue;
            }
            int ammo = NtglCompat.ammo(gun, p), max = NtglCompat.maxAmmo(gun, p);
            if (ammo < 0 || ammo >= max) {
                s.holster[slot] = 0;
                continue;
            }
            s.holster[slot] += STEP;
            if (s.holster[slot] < NtglCompat.reloadTime(gun, p) / speed) continue;
            s.holster[slot] = 0;
            int wanted = NtglCompat.perCartridge(gun, p) ? 1 : max - ammo;
            int got = ActiveReload.take(p, NtglCompat.currentAmmoId(gun, p), wanted);
            if (got > 0) NtglCompat.setAmmo(gun, p, ammo + got);
        }
    }

    /** Clic izquierdo con el arma vacía y el perk Arma Arrojadiza (paquete del cliente). */
    static void throwGun(ServerPlayer p) {
        ItemStack gun = p.getMainHandItem();
        if (!Engineer.is(p) || !Engineer.has(p, Engineer.THROWING) || !NtglCompat.isFirearm(gun) || NtglCompat.ammo(gun, p) != 0) return;
        int slot = p.getInventory().selected;
        ItemStack thrown = gun.copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ThrownFirearm entity = new ThrownFirearm(p, thrown,
                (float) Engineer.mech(p, Engineer.THROWING, "damage", 4), (int) Engineer.mech(p, Engineer.THROWING, "stun_ticks", 10));
        entity.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.6f, 0.5f);
        // Sin muro de Arco: no es un arco (el pipeline marca así sus propios proyectiles).
        DamagePipeline.markProjectile(entity, 1.0, false);
        p.level().addFreshEntity(entity);
        long back = p.level().getGameTime() + Math.round(Engineer.mech(p, Engineer.THROWING, "return_seconds", 4) * 20);
        PENDING.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Pending(thrown, slot, back));
    }

    private static void give(ServerPlayer p, Pending r) {
        ItemStack gun = r.stack();
        int ammo = NtglCompat.ammo(gun, p), max = NtglCompat.maxAmmo(gun, p);
        if (ammo >= 0 && ammo < max) {
            int got = ActiveReload.take(p, NtglCompat.currentAmmoId(gun, p), max - ammo);
            if (got > 0) NtglCompat.setAmmo(gun, p, ammo + got);
        }
        if (p.getInventory().getItem(r.slot()).isEmpty()) p.getInventory().setItem(r.slot(), gun);
        else ItemHandlerHelper.giveItemToPlayer(p, gun);
    }

    /** Al desconectarse, las armas lanzadas vuelven ya (no se pierden). */
    static void returnAll(ServerPlayer p) {
        List<Pending> pending = PENDING.remove(p.getUUID());
        if (pending != null) for (Pending r : pending) give(p, r);
    }
}
