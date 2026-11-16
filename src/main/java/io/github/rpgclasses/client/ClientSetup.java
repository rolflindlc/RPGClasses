package io.github.rpgclasses.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.rpgclasses.content.MovementPerks;
import io.github.rpgclasses.content.RangerAbilities;
import io.github.rpgclasses.core.ability.UnlockResolver;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.network.Packets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import io.github.rpgclasses.registry.ModEntities;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/** Registro de todo lo que solo existe en el cliente. Se llama desde el mod a través de DistExecutor. */
public final class ClientSetup {
    public static final KeyMapping OPEN_BOOK = new KeyMapping(
            "key.rpgclasses.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.rpgclasses");

    public static final KeyMapping[] ABILITY_KEYS = {
            new KeyMapping("key.rpgclasses.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.rpgclasses"),
            new KeyMapping("key.rpgclasses.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.rpgclasses"),
            new KeyMapping("key.rpgclasses.ability_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.rpgclasses"),
    };

    /** Habilidades raciales: agachado se usan las ranuras 2 y 3. */
    public static final KeyMapping OPEN_ALLIES = new KeyMapping(
            "key.rpgclasses.allies", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.rpgclasses");

    public static final KeyMapping[] RACE_KEYS = {
            new KeyMapping("key.rpgclasses.race_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.rpgclasses"),
            new KeyMapping("key.rpgclasses.race_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.rpgclasses"),
    };

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(OPEN_BOOK);
            event.register(OPEN_ALLIES);
            for (KeyMapping key : ABILITY_KEYS) event.register(key);
            for (KeyMapping key : RACE_KEYS) event.register(key);
        });
        io.github.rpgclasses.content.race.Races.clientLookup = (player, id) ->
                player == Minecraft.getInstance().player ? localMechanic(id) : null;
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, ClientSetup::onRenderScaledPre);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, ClientSetup::onRenderScaledPost);
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("abilities", AbilityHud::render));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("coin_flip", CoinFlipFx::renderHud));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("wolf_jump", WolfJumpGauge::renderHud));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAboveAll("centaur_jump", RaceBodyClient::renderHud));
        MinecraftForge.EVENT_BUS.addListener(RaceBodyClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(RaceBodyClient::onMovementInput);
        // Cargas del Monje: encima de la armadura (y de la vida), reservando su altura en el lado izquierdo.
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAbove(
                net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.ARMOR_LEVEL.id(), "monk_charges", MonkChargeFx::renderHud));
        MinecraftForge.EVENT_BUS.addListener(MonkChargeFx::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(CoinFlipFx::onRenderPlayer);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(CoinFlipFx::onMovementInput);
        MinecraftForge.EVENT_BUS.addListener(CoinFlipFx::onInteractionKey);
        MinecraftForge.EVENT_BUS.addListener(CoinFlipFx::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onFov);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onMovementInput);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onRenderPlayer);
        MinecraftForge.EVENT_BUS.addListener(ChickenFx::onRenderPlayer);
        MinecraftForge.EVENT_BUS.addListener(RabbitFx::onRenderPlayer);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(ModEntities.DECOY.get(), DecoyRenderer::new));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientHandlers.reset());
    }

    /**
     * Sigilo del Pícaro: invisible de verdad, sin armadura ni objetos a la vista. Vanilla sí dibuja el equipo de los
     * jugadores invisibles, así que no se dibuja nada. Se reconoce por invisibilidad + agachado (el sigilo exige agacharse).
     */
    private static void onRenderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        if (player.isInvisible() && player.isCrouching() && !player.isSpectator()) event.setCanceled(true);
    }

    /** Enano Herrero: los objetos con Forja Enana lo indican en su descripción. */
    private static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        var tag = event.getItemStack().getTag();
        if (tag == null || !tag.contains(io.github.rpgclasses.content.race.CraftTraits.FORGED_TAG)) return;
        int pct = Math.round(tag.getFloat(io.github.rpgclasses.content.race.CraftTraits.FORGED_TAG) * 100);
        if (pct > 0) event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.rpgclasses.forged", pct)
                .withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    private static boolean scaledRender;

    /** Tamaño racial: el modelo se dibuja a escala (la hitbox la cambia RaceTraits en ambos lados). */
    private static void onRenderScaledPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
        float s = io.github.rpgclasses.content.race.RaceTraits.scale(event.getEntity());
        scaledRender = s != 1f;
        if (!scaledRender) return;
        event.getPoseStack().pushPose();
        event.getPoseStack().scale(s, s, s);
    }

    private static void onRenderScaledPost(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
        if (!scaledRender) return;
        scaledRender = false;
        event.getPoseStack().popPose();
    }

    /** Elfo, Trepar Hojas: dentro de las hojas se sostiene; saltar sube y agacharse baja (como en un andamio). */
    private static void leafClimb(LocalPlayer p, boolean jumping) {
        if (ModAttributes.value(p, ModAttributes.LEAF_WALKER) <= 0 || p.getAbilities().flying || p.isPassenger()) return;
        var level = p.level();
        boolean inLeaves = level.getBlockState(p.blockPosition()).is(net.minecraft.tags.BlockTags.LEAVES)
                || level.getBlockState(net.minecraft.core.BlockPos.containing(p.getX(), p.getY() + 1.0, p.getZ())).is(net.minecraft.tags.BlockTags.LEAVES);
        if (!inLeaves) return;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, jumping ? 0.2 : p.isShiftKeyDown() ? -0.15 : 0.0, v.z);
        p.resetFallDistance();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        WolfJumpGauge.tick();
        if (mc.player == null) return;

        // Sin raza: primero se elige raza y subraza (permanentes); después, la clase.
        if (mc.screen == null && ClientHandlers.dataReceived() && ClientHandlers.LOCAL_DATA.state.raceId == null
                && !io.github.rpgclasses.core.definition.RaceStore.CLIENT.ids().isEmpty()) {
            mc.setScreen(new RaceScreen(RaceScreen.Mode.SELECT));
            return;
        }
        // Sin clase: la pantalla de selección vuelve a abrirse siempre que no haya otra pantalla.
        if (ClientHandlers.needsSelection() && mc.screen == null && !DefinitionStore.CLIENT.classIds().isEmpty()) {
            mc.setScreen(new ClassScreen(true));
            return;
        }
        // Humano: tras elegir clase, elige el muro (o muros) de Adaptable.
        if (mc.screen == null && RaceScreen.needsWall()) {
            mc.setScreen(new RaceScreen(RaceScreen.Mode.WALL));
            return;
        }
        leafClimb(mc.player, mc.screen == null && mc.options.keyJump.isDown());
        if (hasLocalMechanic("rpgclasses:race_lava_walk")) io.github.rpgclasses.content.MonkSubclasses.fluidWalk(mc.player, false, true);
        for (int i = 0; i < RACE_KEYS.length; i++) {
            while (RACE_KEYS[i].consumeClick()) {
                if (mc.screen == null) Net.CHANNEL.sendToServer(new Packets.UseRaceAbility(i + (mc.player.isShiftKeyDown() ? 2 : 0)));
            }
        }
        airJump(mc);
        wallClimb(mc);
        elementalMarch(mc);
        tickZoom(mc.player);
        MovementPerks.lightFeetTick(mc.player, hasLocalMechanic("rpgclasses:light_feet"));
        if (hasLocalMechanic("rpgclasses:feather_fall")) MovementPerks.featherFallTick(mc.player, mc.options.keyJump.isDown());
        for (int i = 0; i < ABILITY_KEYS.length; i++) {
            while (ABILITY_KEYS[i].consumeClick()) {
                if (mc.screen == null) Net.CHANNEL.sendToServer(new Packets.UseAbility(i));
            }
        }
        while (OPEN_BOOK.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ClassScreen(false));
        }
        while (OPEN_ALLIES.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new AllyScreen(null));
        }
    }

    /**
     * Escalada de Paredes (Explorador Ágil): agachado junto a una pared el jugador se adhiere (no cae);
     * con saltar o avanzar sube. Tensar un arco o ballesta lo suelta.
     */
    private static void wallClimb(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!p.isShiftKeyDown() || p.onGround() && !mc.options.keyJump.isDown() && !mc.options.keyUp.isDown()) return;
        java.util.Set<String> unlocked = UnlockResolver.unlocked(ClientHandlers.LOCAL_DATA.state,
                DefinitionStore.CLIENT.classDef(ClientHandlers.LOCAL_DATA.state.classId),
                DefinitionStore.CLIENT.subclassDef(ClientHandlers.LOCAL_DATA.state.subclassId));
        if (!unlocked.contains(io.github.rpgclasses.content.RangerSubclasses.WALL_CLIMB)
                || !io.github.rpgclasses.content.RangerSubclasses.canClimb(p)) return;
        boolean up = mc.screen == null && (mc.options.keyJump.isDown() || mc.options.keyUp.isDown());
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, up ? 0.2 : 0.0, v.z);
        p.resetFallDistance();
    }

    /** Marcha Elemental (Paso Ligero): sobre el agua; sobre la lava solo en Postura de Fuego. */
    private static void elementalMarch(Minecraft mc) {
        var data = ClientHandlers.LOCAL_DATA;
        java.util.Set<String> unlocked = UnlockResolver.unlocked(data.state, DefinitionStore.CLIENT.classDef(data.state.classId),
                DefinitionStore.CLIENT.subclassDef(data.state.subclassId));
        if (!unlocked.contains(io.github.rpgclasses.content.MonkAbilities.LIGHT_STEP)) return;
        int stance = data.counters.getOrDefault(io.github.rpgclasses.content.MonkAbilities.STANCE_COUNTER, 0);
        boolean fire = stance > 0 && stance < io.github.rpgclasses.content.MonkAbilities.STANCE_NAMES.length
                && "fire".equals(io.github.rpgclasses.content.MonkAbilities.STANCE_NAMES[stance]);
        io.github.rpgclasses.content.MonkSubclasses.fluidWalk(mc.player, fire);
    }

    private static boolean jumpWasDown;
    private static int airJumpsUsed;
    private static int airTicks;

    /**
     * Doble y triple salto: pulsar saltar de nuevo en el aire. El movimiento lo ejecuta el cliente
     * (como todo el movimiento del jugador) y el servidor valida y reinicia la distancia de caída.
     */
    private static void airJump(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean down = mc.options.keyJump.isDown();
        if (p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger()) {
            airJumpsUsed = 0;
            airTicks = 0;
        } else {
            airTicks++;
            java.util.Set<String> unlocked = UnlockResolver.unlocked(ClientHandlers.LOCAL_DATA.state,
                    DefinitionStore.CLIENT.classDef(ClientHandlers.LOCAL_DATA.state.classId),
                    DefinitionStore.CLIENT.subclassDef(ClientHandlers.LOCAL_DATA.state.subclassId));
            int allowed = (unlocked.contains(RangerAbilities.DOUBLE_JUMP) ? 1 : 0) + (unlocked.contains(RangerAbilities.TRIPLE_JUMP) ? 1 : 0);
            boolean pressed = down && !jumpWasDown;
            if (pressed && airTicks > 2 && airJumpsUsed < allowed && mc.screen == null
                    && !p.getAbilities().flying && !p.isFallFlying()) {
                double boost = p.hasEffect(MobEffects.JUMP) ? 0.1 * (p.getEffect(MobEffects.JUMP).getAmplifier() + 1) : 0;
                double jump = 0.42 * Math.sqrt(1 + ModAttributes.value(p, ModAttributes.JUMP_BONUS)) + boost;
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, jump, v.z);
                p.hasImpulse = true;
                p.resetFallDistance();
                airJumpsUsed++;
                Net.CHANNEL.sendToServer(new Packets.AirJump());
            }
        }
        jumpWasDown = down;
    }

    private static float sneakProgress, sneakPrev, drawProgress, drawPrev;
    /** Parte del zoom que aporta cada situación; se suman (agachado + tensando = ambas). */
    private static final double SNEAK_SHARE = 0.6, DRAW_SHARE = 0.6, MIN_FACTOR = 0.2;

    /** Avanza las animaciones del zoom: agacharse con arco/ballesta en la mano, y tensarlo. Son independientes. */
    private static void tickZoom(LocalPlayer p) {
        boolean holding = p.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || p.getOffhandItem().getItem() instanceof ProjectileWeaponItem;
        boolean drawing = p.isUsingItem() && p.getUseItem().getItem() instanceof ProjectileWeaponItem;
        boolean has = ModAttributes.value(p, ModAttributes.ZOOM) > 0;
        sneakPrev = sneakProgress;
        drawPrev = drawProgress;
        sneakProgress = ease(sneakProgress, has && holding && p.isShiftKeyDown());
        drawProgress = ease(drawProgress, has && drawing);
    }

    private static float ease(float v, boolean on) {
        v += ((on ? 1f : 0f) - v) * 0.4f;
        return Math.abs(v) < 0.001f ? 0f : v;
    }

    /**
     * Visión de Halcón. Se cambia el campo de visión real (no el "efecto de FOV"), así que funciona
     * aunque el jugador tenga los efectos de FOV al mínimo. El zoom de agacharse y el de tensar se suman
     * y además se acumulan con el zoom propio del arco de vanilla.
     */
    private static void onFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !event.usedConfiguredFov()) return;
        float pt = (float) event.getPartialTick();
        double sneak = sneakPrev + (sneakProgress - sneakPrev) * pt;
        double draw = drawPrev + (drawProgress - drawPrev) * pt;
        if (sneak <= 0 && draw <= 0) return;
        double zoom = ModAttributes.value(mc.player, ModAttributes.ZOOM);
        double factor = Math.max(MIN_FACTOR, 1 - zoom * (sneak * SNEAK_SHARE + draw * DRAW_SHARE));
        event.setFOV(event.getFOV() * factor);
    }

    /** Velocidad vanilla al agacharse: el movimiento se multiplica por 0,3. */
    private static final float VANILLA_SNEAK_FACTOR = 0.3f;

    /** Agilidad de las Sombras: sube la velocidad agachado hasta igualar la de caminar (atributo = 1). */
    private static void onMovementInput(MovementInputUpdateEvent event) {
        var player = event.getEntity();
        // Baluarte de Embestida: el Paladín corre hacia delante sin parar (gira con la cámara).
        if (player.hasEffect(io.github.rpgclasses.registry.ModEffects.CHARGING_BULWARK.get()) && !player.isPassenger()
                && !player.getAbilities().flying) {
            event.getInput().forwardImpulse = 1.0f;
            event.getInput().up = true;
            event.getInput().down = false;
            event.getInput().shiftKeyDown = false;
            player.setSprinting(true);
        }
        // Tirador Ágil (arco) y Pies Ligeros (cualquier objeto): usar no ralentiza
        // (vanilla multiplica el movimiento por 0,2 después de este evento).
        if (player.isUsingItem() && !player.isPassenger()
                && ((player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem && hasLocalMechanic("rpgclasses:draw_no_slow"))
                    || hasLocalMechanic("rpgclasses:light_feet"))) {
            event.getInput().leftImpulse *= 5f;
            event.getInput().forwardImpulse *= 5f;
        }
        if (!player.isShiftKeyDown() || player.isPassenger() || player.getAbilities().flying) return;
        double bonus = Math.min(1.0, ModAttributes.value(player, ModAttributes.SNEAK_SPEED));
        if (bonus <= 0) return;
        float factor = (float) (VANILLA_SNEAK_FACTOR + (1 - VANILLA_SNEAK_FACTOR) * bonus) / VANILLA_SNEAK_FACTOR;
        event.getInput().leftImpulse *= factor;
        event.getInput().forwardImpulse *= factor;
    }

    /** Epic Fight en el cliente: las esquivas del jugador local consultan sus mecánicas sincronizadas. */
    public static void initEpicFight() {
        io.github.rpgclasses.compat.epicfight.EpicFightCompat.clientMechanic = (player, id) ->
                player == Minecraft.getInstance().player ? localMechanic(id) : null;
    }

    /** La mecánica del jugador local con ese id completo, o null. */
    static io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic localMechanic(String id) {
        var s = ClientHandlers.LOCAL_DATA.state;
        var classDef = io.github.rpgclasses.core.definition.DefinitionStore.CLIENT.classDef(s.classId);
        if (classDef != null) {
            for (var m : io.github.rpgclasses.core.stat.StatCalculator.mechanics(s, classDef,
                    io.github.rpgclasses.core.definition.DefinitionStore.CLIENT.subclassDef(s.subclassId))) {
                if (m.id().equals(id)) return m;
            }
        }
        return io.github.rpgclasses.core.stat.StatCalculator.raceMechanic(s,
                io.github.rpgclasses.core.definition.RaceStore.CLIENT.race(s.raceId), id);
    }

    /** Si el jugador local tiene una mecánica (rama, perk o subclase), según los datos sincronizados. */
    static boolean hasLocalMechanic(String id) {
        return localMechanic(id) != null;
    }

    private ClientSetup() {}
}
