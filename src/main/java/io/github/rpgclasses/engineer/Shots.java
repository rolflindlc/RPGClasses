package io.github.rpgclasses.engineer;

import io.github.rpgclasses.compat.ntgl.NtglCompat;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Disparos de NTGL del Ingeniero (eventos {@code GunFireEvent.Pre/Post} y {@code GunProjectileSpawnEvent}, en el
 * servidor): balas especiales, racha, calentamiento del Artillero, munición gratis (Último Cargador, Modo Asedio) y
 * Bandolera Giratoria.
 */
final class Shots {
    private Shots() {
    }

    static void onFirePre(NtglCompat.Shot shot) {
        if (shot.client() || !(shot.shooter() instanceof ServerPlayer p) || !Engineer.is(p)) return;
        Engineer.State s = Engineer.state(p);
        s.firingSpecial = s.specialLeft > 0 ? s.loadedSpecial : -1;
    }

    static void onSpawn(NtglCompat.Spawn spawn) {
        if (spawn.projectile() == null || !(spawn.shooter() instanceof ServerPlayer p) || !Engineer.is(p)) return;
        Engineer.State s = Engineer.state(p);
        if (s.firingSpecial >= 0) spawn.projectile().getPersistentData().putInt(ImprovisedAmmo.TAG, s.firingSpecial);
    }

    static void onFirePost(NtglCompat.Shot shot) {
        if (shot.client() || !(shot.shooter() instanceof ServerPlayer p) || !Engineer.is(p)) return;
        Engineer.State s = Engineer.state(p);
        long now = p.level().getGameTime();
        s.lastShot = now;
        if (s.firingSpecial >= 0) {
            s.firingSpecial = -1;
            if (--s.specialLeft <= 0) {
                s.specialLeft = 0;
                s.loadedSpecial = -1;
            }
        }
        ItemStack gun = shot.stack();
        // Cañón Caliente: el fuego continuo con un arma pesada calienta el cañón.
        if (Engineer.heavy(gun) && Engineer.sub(p, Engineer.ARTILLERIST)) {
            double cool = Engineer.mech(p, Engineer.HOT_BARREL, "cool_seconds", 2) * 20;
            if (s.heatStart < 0 || now - s.lastHeavyShot > cool) s.heatStart = now;
            s.lastHeavyShot = now;
        }
        // Último Cargador y Modo Asedio: la bala gastada vuelve al cargador.
        if (now < s.lastMagUntil || now < s.siegeUntil) {
            int ammo = NtglCompat.ammo(gun, p);
            int max = NtglCompat.maxAmmo(gun, p);
            if (ammo >= 0 && ammo < max) NtglCompat.setAmmo(gun, p, ammo + 1);
            return;
        }
        // Bandolera Giratoria: arma vacía -> la siguiente arma cargada de la barra, al instante.
        if (Engineer.has(p, Engineer.BANDOLIER) && NtglCompat.ammo(gun, p) == 0) {
            var inv = p.getInventory();
            for (int i = 1; i < 9; i++) {
                int slot = (inv.selected + i) % 9;
                ItemStack other = inv.getItem(slot);
                if (NtglCompat.isFirearm(other) && NtglCompat.ammo(other, p) > 0) {
                    inv.selected = slot;
                    p.connection.send(new ClientboundSetCarriedItemPacket(slot));
                    break;
                }
            }
        }
    }

    static void tick(ServerPlayer p, Engineer.State s, long now) {
        // Racha Perfecta: se pierde tras un rato sin disparar.
        if (s.streak > 0 && now - s.lastShot > Engineer.mech(p, Engineer.STREAK, "idle_seconds", 15) * 20) s.streak = 0;
        if (s.streak > 0 && !Engineer.has(p, Engineer.STREAK)) s.streak = 0;
        // Cañón Caliente: se enfría tras unos segundos sin disparar.
        if (s.heatStart >= 0 && now - s.lastHeavyShot > Engineer.mech(p, Engineer.HOT_BARREL, "cool_seconds", 2) * 20) s.heatStart = -1;
        ImprovisedAmmo.next(p, s);
    }
}
