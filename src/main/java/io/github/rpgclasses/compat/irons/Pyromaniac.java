package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.content.race.Races;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.Allies;
import io.github.rpgclasses.server.DebugLog;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Infernal Pirómano (anexo de razas, 3.7). Solo los conjuros raciales (los de la ranura racial de la rueda):
 * <ul>
 *   <li>R0–R1: el Disparo de fuego atraviesa 1 y luego 2 enemigos (el "pierce" propio de Iron's).</li>
 *   <li>R2: la Bomba de magma deja un charco de llamas de 4 s que prende a los enemigos.</li>
 *   <li>R3: la misma Bomba de magma deja un charco de lava de 5 s que prende y ralentiza a los enemigos.</li>
 * </ul>
 * Los charcos son zonas de efecto (sin bloques de fuego ni de lava, para no quemar construcciones).
 */
public final class Pyromaniac {
    private static final ResourceLocation FIREBOLT = new ResourceLocation(IronsCompat.IRONS, "firebolt");
    private static final ResourceLocation MAGMA = new ResourceLocation(IronsCompat.IRONS, "magma_ball");

    private record Tracked(UUID owner, boolean lava) {}
    private record Pool(ResourceKey<Level> dim, Vec3 center, UUID owner, long end, double radius, int burnSeconds,
                        int slowness, boolean lava) {}

    private static final Map<Integer, Tracked> TRACKED = new HashMap<>();
    private static final List<Pool> POOLS = new ArrayList<>();

    private static boolean racialSelected(ServerPlayer p) {
        try {
            SpellSelectionManager.SelectionOption option = new SpellSelectionManager(p).getSelection();
            return option != null && RacialSpells.SLOT.equals(option.slot);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) return;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
        if (!FIREBOLT.equals(type) && !MAGMA.equals(type)) return;
        if (!(projectile.getOwner() instanceof ServerPlayer p) || !Races.has(p, "race_piercing_fire") || !racialSelected(p)) return;
        if (FIREBOLT.equals(type)) {
            int pierce = (int) Races.number(p, "race_piercing_fire", "pierce", 0);
            if (pierce > 0 && setPierce(projectile, pierce)) {
                projectile.getPersistentData().putBoolean(io.github.rpgclasses.cleric.Sanctuary.PIERCING_MARK, true); // atraviesa el Santuario
                DebugLog.event(p, "raza", "Fuego Penetrante: atraviesa {}", pierce);
            }
        } else if (MAGMA.equals(type) && Races.has(p, "race_lava_pool")) {
            TRACKED.put(projectile.getId(), new Tracked(p.getUUID(), true));
        } else if (MAGMA.equals(type) && Races.has(p, "race_flame_pool")) {
            TRACKED.put(projectile.getId(), new Tracked(p.getUUID(), false));
        }
    }

    private static boolean setPierce(Entity projectile, int level) {
        try {
            Method m = projectile.getClass().getMethod("setPierceLevel", int.class);
            m.invoke(projectile, level);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** El proyectil desaparece al impactar: ahí se crea el charco. */
    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (TRACKED.isEmpty() || event.getLevel().isClientSide()) return;
        Tracked t = TRACKED.remove(event.getEntity().getId());
        Entity e = event.getEntity();
        if (t == null || e.getRemovalReason() == null || !e.getRemovalReason().shouldDestroy()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(t.owner());
        if (owner == null) return;
        ActiveMechanic m = Races.find(owner, t.lava() ? "race_lava_pool" : "race_flame_pool");
        if (m == null) return;
        POOLS.add(new Pool(level.dimension(), e.position(), t.owner(),
                level.getGameTime() + Math.round(m.number("duration_seconds", t.lava() ? 5 : 4) * 20),
                m.number("radius", 2.5), (int) m.number("burn_seconds", 3), (int) m.number("slowness", 0), t.lava()));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || POOLS.isEmpty()) return;
        var server = event.getServer();
        Iterator<Pool> it = POOLS.iterator();
        while (it.hasNext()) {
            Pool pool = it.next();
            ServerLevel level = server.getLevel(pool.dim());
            if (level == null || level.getGameTime() >= pool.end()) {
                it.remove();
                continue;
            }
            if (level.getGameTime() % 5 != 0) continue;
            double r = pool.radius();
            level.sendParticles(pool.lava() ? ParticleTypes.LAVA : ParticleTypes.FLAME, pool.center().x, pool.center().y + 0.1,
                    pool.center().z, pool.lava() ? 6 : 14, r * 0.5, 0.05, r * 0.5, 0.01);
            if (pool.lava()) level.sendParticles(ParticleTypes.LARGE_SMOKE, pool.center().x, pool.center().y + 0.2,
                    pool.center().z, 3, r * 0.4, 0.05, r * 0.4, 0.01);
            AABB box = new AABB(pool.center(), pool.center()).inflate(r, 1.5, r);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    x -> x.position().distanceToSqr(pool.center()) <= r * r && Allies.isEnemyOf(server, pool.owner(), x))) {
                e.setSecondsOnFire(pool.burnSeconds());
                if (pool.slowness() > 0 && !Races.isBoss(e)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, pool.slowness() - 1, true, false, true));
                }
            }
        }
    }
}
