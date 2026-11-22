package io.github.rpgclasses.druid;

import com.google.gson.JsonObject;
import io.github.rpgclasses.content.Chronomancy;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.definition.Definitions;
import io.github.rpgclasses.network.DruidPackets;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.SessionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Escultor (adenda, 6.3): rituales oscuros con partes de criaturas.
 * <ol>
 *   <li>El ritual sobre unos restos da, a elegir, el punto de conocimiento o una parte (cabeza, torso, patas o
 *       instinto) de una especie que ya conoce ({@link Knowledge#ritualChoice}).</li>
 *   <li>Las partes van en un Recipiente de Carne ({@link FleshVesselItem}).</li>
 *   <li>Con el recipiente completo lanzado sobre un Altar de Carne, clic derecho en el altar: paga mucho maná y empieza
 *       una creación de unos segundos (la quimera se forma a trozos sobre el altar).</li>
 *   <li>Al terminar, la quimera pasa a su bestiario (como mucho {@code max_chimeras}; si ya las tiene, elige cuál
 *       sustituir) y paga con vida máxima durante un rato (efecto Precio de la Carne).</li>
 * </ol>
 */
public final class Sculptor {
    private static final class Forming {
        final UUID owner;
        final ServerLevel level;
        final BlockPos pos;
        final FormSpec.Chimera chimera;
        final ItemStack vessel;
        final int total;
        final int replace;
        int left;

        Forming(UUID owner, ServerLevel level, BlockPos pos, FormSpec.Chimera chimera, ItemStack vessel, int total, int replace) {
            this.owner = owner;
            this.level = level;
            this.pos = pos;
            this.chimera = chimera;
            this.vessel = vessel;
            this.total = total;
            this.left = total;
            this.replace = replace;
        }
    }

    private record PendingReplace(BlockPos pos, int vesselEntity, long until) {}

    private static final Map<UUID, Forming> FORMING = SessionState.track(new HashMap<>());
    private static final Map<UUID, PendingReplace> PENDING = SessionState.track(new HashMap<>());

    /** Número de la mecánica del Escultor (datos de la subclase), también para el efecto Precio de la Carne. */
    public static double param(String key, double fallback) {
        Definitions.SubclassDef sub = DefinitionStore.SERVER.subclassDef(io.github.rpgclasses.core.Ids.NS + ":sculptor");
        if (sub == null) return fallback;
        for (Definitions.EffectDef e : sub.effects()) {
            JsonObject p = e.params();
            if (p.has("id") && p.get("id").getAsString().endsWith(":" + Druid.SCULPTOR) && p.has(key) && p.get(key).isJsonPrimitive()) {
                return p.get(key).getAsDouble();
            }
        }
        return fallback;
    }

    /** Parte de bestia de esa especie (0 cabeza, 1 torso, 2 patas, 3 instinto). */
    public static ItemStack part(int index, String species) {
        ItemStack stack = new ItemStack(DruidRegistry.PARTS.get(Math.max(0, Math.min(3, index))).get());
        stack.getOrCreateTag().putString(BeastPartItem.SPECIES, species);
        return stack;
    }

    // ------------------------------------------------------------------ altar

    static void tryCreate(ServerPlayer p, BlockPos pos) {
        if (!Druid.isDruid(p) || !Druid.hasMech(p, Druid.SCULPTOR)) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.chimera_not_sculptor"), true);
            return;
        }
        if (FORMING.containsKey(p.getUUID())) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.chimera_busy"), true);
            return;
        }
        ItemEntity vessel = findVessel(p.serverLevel(), pos);
        if (vessel == null) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.chimera_no_vessel"), true);
            return;
        }
        FormSpec.Chimera chimera = FleshVesselItem.chimera(vessel.getItem());
        if (chimera == null || !chimera.valid()) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.chimera_incomplete"), true);
            return;
        }
        int cost = (int) Math.round(Druid.mech(p, Druid.SCULPTOR, "chimera_mana", 120));
        if (!p.isCreative() && Chronomancy.mana != null && Chronomancy.mana.mana(p) < cost) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_mana", cost), true);
            return;
        }
        PlayerClassData data = Druid.data(p);
        int max = (int) Math.round(Druid.mech(p, Druid.SCULPTOR, "max_chimeras", 3));
        if (data != null && Bestiary.chimeras(data).size() >= max) {
            PENDING.put(p.getUUID(), new PendingReplace(pos.immutable(), vessel.getId(), p.level().getGameTime() + 20 * 60));
            Net.toPlayer(p, new DruidPackets.ChimeraReplace());
            return;
        }
        start(p, pos, vessel, chimera, -1, cost);
    }

    /** Respuesta de la pantalla de sustituir quimera ({@code index} −1 = no crearla). */
    static void replaceChoice(ServerPlayer p, int index) {
        PendingReplace pending = PENDING.remove(p.getUUID());
        if (pending == null || index < 0 || p.level().getGameTime() > pending.until()) return;
        if (!(p.level().getEntity(pending.vesselEntity()) instanceof ItemEntity vessel) || !vessel.isAlive()) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.chimera_no_vessel"), true);
            return;
        }
        FormSpec.Chimera chimera = FleshVesselItem.chimera(vessel.getItem());
        if (chimera == null) return;
        int cost = (int) Math.round(Druid.mech(p, Druid.SCULPTOR, "chimera_mana", 120));
        if (!p.isCreative() && Chronomancy.mana != null && Chronomancy.mana.mana(p) < cost) {
            p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_mana", cost), true);
            return;
        }
        start(p, pending.pos(), vessel, chimera, index, cost);
    }

    private static ItemEntity findVessel(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos.above()).inflate(.4, .6, .4);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (e.isAlive() && e.getItem().getItem() instanceof FleshVesselItem) return e;
        }
        return null;
    }

    private static void start(ServerPlayer p, BlockPos pos, ItemEntity vesselEntity, FormSpec.Chimera chimera, int replace, int cost) {
        if (!p.isCreative() && Chronomancy.mana != null) Chronomancy.mana.setMana(p, Chronomancy.mana.mana(p) - cost);
        ItemStack vessel = vesselEntity.getItem().copy();
        vesselEntity.discard();
        int ticks = Math.max(20, (int) Math.round(Druid.mech(p, Druid.SCULPTOR, "cinematic_seconds", 4) * 20));
        ServerLevel level = p.serverLevel();
        FORMING.put(p.getUUID(), new Forming(p.getUUID(), level, pos.immutable(), chimera, vessel, ticks, replace));
        Net.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), new DruidPackets.ChimeraFx(pos, chimera.save(), ticks));
        p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.chimera_start"), true);
        level.playSound(null, pos, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 1f, .6f);
        DebugLog.event(p, "Druida", "Escultor: empieza la quimera {} en {} ({} de maná)", chimera, pos, cost);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FORMING.isEmpty()) return;
        Iterator<Forming> it = FORMING.values().iterator();
        while (it.hasNext()) {
            Forming f = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(f.owner);
            if (p == null || !p.isAlive()) {
                // El escultor no está: la creación se deshace y el recipiente vuelve al altar.
                Vec3 at = Vec3.atCenterOf(f.pos.above());
                f.level.addFreshEntity(new ItemEntity(f.level, at.x, at.y, at.z, f.vessel));
                it.remove();
                continue;
            }
            f.left--;
            tickFx(f);
            if (f.left > 0) continue;
            it.remove();
            finish(p, f);
        }
    }

    private static void tickFx(Forming f) {
        Vec3 c = Vec3.atCenterOf(f.pos.above());
        float t = 1f - f.left / (float) f.total;
        // Trozos que llegan de la nada hacia el centro.
        for (int i = 0; i < 3; i++) {
            double ang = f.level.random.nextDouble() * Math.PI * 2, d = 2.5 - t * 1.5;
            double x = c.x + Math.cos(ang) * d, z = c.z + Math.sin(ang) * d, y = c.y + f.level.random.nextDouble();
            f.level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(i == 0 ? Items.BONE : Items.ROTTEN_FLESH)),
                    x, y, z, 0, c.x - x, c.y + .3 - y, c.z - z, .12);
        }
        f.level.sendParticles(new DustParticleOptions(new Vector3f(.55f, .02f, .05f), 1.2f), c.x, c.y + .3, c.z, 4, .35, .35, .35, 0);
        if (f.left % 15 == 0) f.level.playSound(null, f.pos, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1f, .5f + t * .4f);
        if (f.left % 25 == 0) f.level.playSound(null, f.pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1f, 1f);
    }

    private static void finish(ServerPlayer p, Forming f) {
        PlayerClassData data = Druid.data(p);
        if (data == null) return;
        Bestiary.putChimera(data, f.chimera, f.replace);
        int seconds = (int) Math.round(Druid.mech(p, Druid.SCULPTOR, "penalty_seconds", 600));
        if (seconds > 0) {
            p.addEffect(new MobEffectInstance(DruidRegistry.FLESH_PRICE.get(), seconds * 20, 0, false, true, true));
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
        Vec3 c = Vec3.atCenterOf(f.pos.above());
        f.level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + .5, c.z, 20, .5, .5, .5, .05);
        f.level.sendParticles(new DustParticleOptions(new Vector3f(.55f, .02f, .05f), 2f), c.x, c.y + .5, c.z, 30, .6, .6, .6, 0);
        f.level.playSound(null, f.pos, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, .8f, 1.3f);
        Component name = Component.translatable("druid.rites_and_paths.chimera", Beasts.name(f.chimera.torso()));
        p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.chimera_done", name), true);
        DebugLog.event(p, "Druida", "Escultor: quimera creada {} (sustituye {})", f.chimera, f.replace);
        Net.syncProgress(p);
    }

    /** El cuerpo de la quimera que se forma (para la red). */
    static CompoundTag save(FormSpec.Chimera c) {
        return c.save();
    }
}
