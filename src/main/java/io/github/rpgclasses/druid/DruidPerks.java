package io.github.rpgclasses.druid;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Perks de Savia: Savia Venenosa (tus conjuros de naturaleza envenenan) y Savia Vital (los que dañan te curan una
 * parte). Se reconoce el daño de naturaleza de Iron's por su tipo de daño ({@code irons_spellbooks:nature_magic}).
 */
public final class DruidPerks {

    static boolean isNature(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(k -> "irons_spellbooks".equals(k.location().getNamespace()) && k.location().getPath().startsWith("nature"))
                .orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getAmount() <= 0 || !isNature(event.getSource())) return;
        LivingEntity target = event.getEntity();
        if (target == p || !Druid.isDruid(p)) return;
        if (Druid.hasMech(p, Druid.VENOM_SAP)) {
            int ticks = (int) Math.round(Druid.mech(p, Druid.VENOM_SAP, "poison_seconds", 5) * 20);
            int amp = Math.max(0, (int) Druid.mech(p, Druid.VENOM_SAP, "poison_level", 1) - 1);
            target.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amp), p);
        }
        if (Druid.hasMech(p, Druid.VITAL_SAP) && p.getHealth() < p.getMaxHealth()) {
            p.heal((float) (event.getAmount() * Druid.mech(p, Druid.VITAL_SAP, "lifesteal", .15)));
        }
    }
}
