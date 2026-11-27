package io.github.rpgclasses.engineer;

import io.github.rpgclasses.compat.ntgl.NtglCompat;
import java.util.function.Function;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lo que leen los mixins de NTGL (WeaponModifierHelper): cadencia, dispersión, tiempo de recarga y de desenfundar del
 * arma que lleva el Ingeniero en la mano. Los factores salen del servidor ({@link Engineer#serverFactors}) o, en el
 * cliente, del último paquete de sincronización (solo del jugador local: los demás no disparan en este cliente).
 * Factores: [0] cadencia (divide los tics entre disparos), [1] dispersión, [2] tiempo de recarga, [3] 1 = desenfunde
 * instantáneo.
 */
public final class EngineerHooks {
    public static Function<LivingEntity, float[]> server;
    public static Function<LivingEntity, float[]> client;
    /** Cliente: ¿el dueño de este UUID es aliado del jugador local? (alambre de espino y minas). */
    public static java.util.function.Predicate<java.util.UUID> clientAlly;

    private EngineerHooks() {
    }

    private static float[] factors(Object data) {
        LivingEntity wielder = NtglCompat.wielder(data);
        if (!(wielder instanceof Player)) return null;
        ItemStack weapon = NtglCompat.weapon(data);
        // Solo el arma de la mano principal (la de las torretas o la de la otra mano no cambia).
        if (weapon.isEmpty() || wielder.getMainHandItem().getItem() != weapon.getItem()) return null;
        Function<LivingEntity, float[]> source = wielder.level().isClientSide ? client : server;
        return source == null ? null : source.apply(wielder);
    }

    public static int rate(Object data, int base) {
        float[] f = base <= 0 ? null : factors(data);
        return f == null || f[0] <= 0 ? base : Math.max(1, Math.round(base / f[0]));
    }

    public static float spread(Object data, float base) {
        float[] f = factors(data);
        return f == null ? base : base * f[1];
    }

    public static int reloadTime(Object data, int base) {
        float[] f = base <= 0 ? null : factors(data);
        return f == null ? base : Math.max(1, Math.round(base * f[2]));
    }

    public static int equipTime(Object data, int base) {
        float[] f = factors(data);
        return f != null && f[3] > 0 ? 0 : base;
    }
}
