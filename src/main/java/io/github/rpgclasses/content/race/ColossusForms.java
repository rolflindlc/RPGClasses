package io.github.rpgclasses.content.race;

import io.github.rpgclasses.core.Ids;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coloso Titán (adenda 0.7, sección 4.4): la forma <b>Crecer</b> (tecla racial 1; volver a pulsarla la termina).
 * <ul>
 *   <li>Tamaño ×4 (el Coloso ya es ×2): +2 al atributo de tamaño, con alcance 7,5/9, paso 2,4, salto 5 y caída segura 12.</li>
 *   <li>Segunda barra de vida: absorción del % de la vida máxima del Rango, que se gasta primero y se pierde al terminar.</li>
 *   <li>Rotura de área 3×3×3 (agachado, 1), hambre +100 % y detección +100 % (totales: sustituyen a las del tronco).</li>
 *   <li>Al caminar rompe hojas (sin soltar nada) y convierte el césped en tierra, fuera de zonas protegidas.</li>
 *   <li>Pisotones: temblor de pantalla a los jugadores cercanos (el Titán lo nota al 30 %), sonido audible a ~64 bloques y
 *       vibraciones (sculk y Warden) de más alcance.</li>
 *   <li>No se activa si hay bloques sólidos en la nueva hitbox (hojas y cultivos no cuentan). El enfriamiento empieza al
 *       terminar la forma.</li>
 * </ul>
 * Todos los cambios son modificadores transitorios con UUID fijos y nombres fuera del prefijo del {@code StatEngine}
 * (que no los borra al recalcular). El de tamaño sirve además de marca sincronizada: el cliente sabe quién está crecido.
 */
public final class ColossusForms {
    public static final UUID SCALE_ID = UUID.fromString("9a1d7c3e-4b2f-4e8a-8c61-3f0e2b7d5a01");
    private static final UUID BLOCK_REACH_ID = UUID.fromString("9a1d7c3e-4b2f-4e8a-8c61-3f0e2b7d5a02");
    private static final UUID ENTITY_REACH_ID = UUID.fromString("9a1d7c3e-4b2f-4e8a-8c61-3f0e2b7d5a03");
    private static final UUID STEP_ID = UUID.fromString("9a1d7c3e-4b2f-4e8a-8c61-3f0e2b7d5a04");
    private static final UUID JUMP_ID = UUID.fromString("9a1d7c3e-4b2f-4e8a-8c61-3f0e2b7d5a05");
    private static final UUID DETECTION_ID = UUID.fromString("9a1d7c3e-4b2f-4e8a-8c61-3f0e2b7d5a06");
    private static final String MODIFIER_NAME = "rpgclasses titan form";
    private static final String KEY = Ids.NS + ":race_grow";

    // Tabla 4.2 de la adenda: lo que la forma suma a un Coloso (×2 → ×4).
    private static final double SCALE_ADD = 2.0;      // tamaño ×2 → ×4
    private static final double REACH_ADD = 3.0;      // alcance 4,5 / 6 → 7,5 / 9
    private static final double STEP_ADD = 1.2;       // paso 1,2 → 2,4
    private static final double JUMP_ADD = 2.0;       // salto 2,5 → 5 bloques (altura × (1 + bono))
    private static final double DETECTION_ADD = 0.5;  // detección +50 % → +100 %
    /** Hambre extra en forma Titán (+100 %); sustituye al +50 % del tronco. */
    public static final double HUNGER = 1.0;
    /** Bloques caminados entre dos pisotones. */
    private static final double STRIDE = 2.6;
    /** Las vibraciones se emiten también a esta distancia en las 4 direcciones (más alcance que un paso normal). */
    private static final double VIBRATION_SPREAD = 8;

    private static final class Form {
        long end;
        float barrier;
        Vec3 lastPos;
        double walked;
    }

    private static final Map<UUID, Form> FORMS = new HashMap<>();

    public ColossusForms() {
        Rites.STATE_SOURCES.put("titan", p -> FORMS.containsKey(p.getUUID()));
    }

    // ------------------------------------------------------------------ consultas (cliente y servidor)

    /** true mientras dura la forma Titán (marca sincronizada en el atributo de tamaño). */
    public static boolean isGrown(Player p) {
        if (!p.isAddedToWorld()) return false;
        AttributeInstance scale = p.getAttribute(ModAttributes.SCALE.get());
        return scale != null && scale.getModifier(SCALE_ID) != null;
    }

    /** Lado del cubo de rotura de la forma (1 = la forma no lo cambia). */
    public static int areaSize(Player p) {
        return isGrown(p) ? (int) Math.round(Races.number(p, "race_grow", "size", 3)) : 1;
    }

    /** Caída segura de la forma en bloques (0 = la forma no la cambia). */
    public static double fallHeight(Player p) {
        return isGrown(p) ? Races.number(p, "race_grow", "fall_height", 12) : 0;
    }

    /** Fracción de hambre extra: la de la forma si está crecido; si no, la del tronco. */
    public static double hungerFactor(Player p, double base) {
        return isGrown(p) ? HUNGER : base;
    }

    // ------------------------------------------------------------------ activar y terminar

    /** Tecla racial 1 del Titán. */
    static boolean grow(ServerPlayer p, ActiveMechanic m) {
        if (isGrown(p) || p.isPassenger() || p.isSleeping()) return false;
        ServerLevel level = p.serverLevel();
        float now = RaceTraits.scale(p);
        double ratio = (now + SCALE_ADD) / now;
        EntityDimensions dims = p.getDimensions(Pose.STANDING); // ya escalada por PlayerSizeMixin
        double half = dims.width * ratio / 2;
        AABB box = new AABB(p.getX() - half, p.getY(), p.getZ() - half, p.getX() + half, p.getY() + dims.height * ratio, p.getZ() + half);
        if (!fits(level, box.deflate(0.01))) {
            p.displayClientMessage(Component.translatable("message.rpgclasses.titan_no_room"), true);
            return false;
        }
        add(p, ModAttributes.SCALE.get(), SCALE_ID, SCALE_ADD);
        add(p, forge("block_reach"), BLOCK_REACH_ID, REACH_ADD);
        add(p, forge("entity_reach"), ENTITY_REACH_ID, REACH_ADD);
        add(p, forge("step_height_addition"), STEP_ID, STEP_ADD);
        add(p, ModAttributes.JUMP_BONUS.get(), JUMP_ID, JUMP_ADD);
        add(p, ModAttributes.MOB_DETECTION.get(), DETECTION_ID, DETECTION_ADD);
        Form f = new Form();
        f.end = level.getGameTime() + Math.round(m.number("duration_seconds", 30) * 20);
        f.barrier = (float) (p.getMaxHealth() * m.number("absorption", 0.5));
        f.lastPos = p.position();
        p.setAbsorptionAmount(p.getAbsorptionAmount() + f.barrier);
        FORMS.put(p.getUUID(), f);
        p.refreshDimensions();
        clearSoftBlocks(p, box);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3f, 0.5f);
        level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1, p.getZ(), 6, half, 1, half, 0);
        stomp(p, 1f);
        DebugLog.event(p, "raza", "Titán: crece ({} de barra extra, {} s)", f.barrier, m.number("duration_seconds", 30));
        return true;
    }

    /** Termina la forma (se acaba el tiempo, vuelve a pulsar la tecla, muere o sale). El enfriamiento empieza ahora. */
    public static void shrink(ServerPlayer p) {
        Form f = FORMS.remove(p.getUUID());
        boolean was = isGrown(p);
        removeModifiers(p);
        if (f != null) {
            float left = Math.min(f.barrier, p.getAbsorptionAmount());
            if (left > 0) p.setAbsorptionAmount(p.getAbsorptionAmount() - left);
            ActiveMechanic m = Races.find(p, "race_grow");
            if (m != null) {
                AbilityService.setCooldown(p, KEY, (int) Math.round(m.number("cooldown_seconds", 225) * 20));
                Net.syncPlayer(p);
            }
        }
        if (!was && f == null) return;
        p.refreshDimensions();
        if (p.isAlive()) {
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1.5f, 0.6f);
            p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 2, p.getZ(), 30, 1.2, 2, 1.2, 0.05);
        }
        DebugLog.event(p, "raza", "Titán: vuelve a su tamaño");
    }

    /** ¿Cabe la nueva hitbox? Solo cuentan los bloques sólidos (ni hojas ni cultivos). */
    private static boolean fits(ServerLevel level, AABB box) {
        VoxelShape boxShape = Shapes.create(box);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            BlockState st = level.getBlockState(pos);
            if (st.isAir() || st.is(BlockTags.LEAVES) || st.is(BlockTags.CROPS) || st.is(BodyTraits.TRAMPLABLE)) continue;
            VoxelShape shape = st.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            if (Shapes.joinIsNotEmpty(shape.move(pos.getX(), pos.getY(), pos.getZ()), boxShape, BooleanOp.AND)) return false;
        }
        return true;
    }

    private static Attribute forge(String path) {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("forge", path));
    }

    private static void add(Player p, Attribute attribute, UUID id, double amount) {
        if (attribute == null) return;
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, MODIFIER_NAME, amount, AttributeModifier.Operation.ADDITION));
    }

    private static void remove(Player p, Attribute attribute, UUID id) {
        if (attribute == null) return;
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst != null) inst.removeModifier(id);
    }

    private static void removeModifiers(Player p) {
        remove(p, ModAttributes.SCALE.get(), SCALE_ID);
        remove(p, forge("block_reach"), BLOCK_REACH_ID);
        remove(p, forge("entity_reach"), ENTITY_REACH_ID);
        remove(p, forge("step_height_addition"), STEP_ID);
        remove(p, ModAttributes.JUMP_BONUS.get(), JUMP_ID);
        remove(p, ModAttributes.MOB_DETECTION.get(), DETECTION_ID);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        Form f = FORMS.get(p.getUUID());
        if (f == null) {
            // Restos de una forma que no se cerró (no debería pasar): se quitan.
            if (p.tickCount % 20 == 0 && isGrown(p)) {
                removeModifiers(p);
                p.refreshDimensions();
            }
            return;
        }
        DebugLog.guard(p, "Titán", () -> tick(p, f));
    }

    private static void tick(ServerPlayer p, Form f) {
        if (!p.isAlive() || !Races.has(p, "race_grow") || p.level().getGameTime() >= f.end) {
            shrink(p);
            return;
        }
        f.barrier = Math.min(f.barrier, p.getAbsorptionAmount());
        // Pisotones: cada STRIDE bloques caminados por el suelo (la posición la mueve el cliente: se compara con la anterior).
        Vec3 pos = p.position();
        double moved = Math.sqrt(pos.subtract(f.lastPos).horizontalDistanceSqr());
        f.lastPos = pos;
        if (p.onGround() && moved < 3) f.walked += moved;
        if (f.walked >= STRIDE) {
            f.walked = 0;
            stomp(p, 0.8f);
        }
        if (p.tickCount % 2 == 0) walkBreak(p);
    }

    /** Pisotón: sonido fuerte, polvo, vibraciones y temblor de pantalla a los jugadores cercanos. */
    private static void stomp(ServerPlayer p, float power) {
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_STEP, SoundSource.PLAYERS, 4f, 0.6f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 4f, 0.5f);
        BlockState ground = level.getBlockState(p.getOnPos());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.getX(), p.getY() + 0.1, p.getZ(),
                    24, p.getBbWidth() / 2, 0.1, p.getBbWidth() / 2, 0.15);
        }
        if (!p.isShiftKeyDown()) {
            level.gameEvent(p, GameEvent.HIT_GROUND, p.position());
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                Vec3 at = p.position().add(Math.cos(a) * VIBRATION_SPREAD, 0, Math.sin(a) * VIBRATION_SPREAD);
                if (level.isLoaded(BlockPos.containing(at))) level.gameEvent(p, GameEvent.HIT_GROUND, at);
            }
        }
        double radius = Races.number(p, "race_grow", "shake_radius", 24);
        for (ServerPlayer other : level.players()) {
            double d = other.distanceTo(p);
            if (d > radius) continue;
            float strength = (float) (power * (1 - d / radius) * (other == p ? 0.3 : 1));
            if (strength > 0.02f) Net.toPlayer(other, new Packets.Shake(strength));
        }
    }

    /** Rompe las hojas que atraviesa (sin soltar nada) y pisa el césped hasta dejarlo en tierra. */
    private static void walkBreak(ServerPlayer p) {
        if (p.isSpectator() || !p.mayBuild()) return;
        ServerLevel level = p.serverLevel();
        AABB box = p.getBoundingBox().inflate(0.3, 0, 0.3);
        int below = Mth.floor(box.minY - 0.01);
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                if (p.onGround()) {
                    BlockPos ground = new BlockPos(x, below, z);
                    if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK) && allowed(p, ground)) {
                        level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
                    }
                }
                for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(BlockTags.LEAVES) && allowed(p, pos)) level.destroyBlock(pos, false, p);
                }
            }
        }
    }

    /** Al crecer: las hojas de la nueva hitbox se rompen (sin soltar nada). */
    private static void clearSoftBlocks(ServerPlayer p, AABB box) {
        if (!p.mayBuild()) return;
        ServerLevel level = p.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES) && allowed(p, pos)) level.destroyBlock(pos.immutable(), false, p);
        }
    }

    /** Zonas protegidas: spawn, permisos de construcción y mods de parcelas (evento de rotura cancelado). */
    private static boolean allowed(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        return level.mayInteract(p, pos)
                && net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(level, p.gameMode.getGameModeForPlayer(), p, pos) != -1;
    }

    // ------------------------------------------------------------------ muerte y salida

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FORMS.containsKey(p.getUUID())) shrink(p);
    }

    /** Al salir la forma termina antes de guardar (la absorción extra no debe quedarse en el jugador). */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FORMS.containsKey(p.getUUID())) shrink(p);
        FORMS.remove(event.getEntity().getUUID());
    }

    /** Al parar el servidor (antes de guardar a los jugadores). */
    @SubscribeEvent
    public void onStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) if (FORMS.containsKey(p.getUUID())) shrink(p);
        FORMS.clear();
    }
}
