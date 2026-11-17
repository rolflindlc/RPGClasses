package io.github.rpgclasses.client;

import io.github.rpgclasses.network.Packets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Cargas de postura del Monje en el cliente.
 * <ul>
 *   <li><b>Barra:</b> una fila de símbolos (fuego, agua o piedra) encima de la vida y la armadura, llenos hasta las
 *       cargas actuales y vacíos hasta el máximo. Con el umbral del efecto propio los símbolos laten; preparado, brillan.</li>
 *   <li><b>Puños:</b> partículas del elemento en las manos vacías del Monje (el propio y los demás), más densas y vivas
 *       cuantas más cargas tenga.</li>
 * </ul>
 */
public final class MonkChargeFx {
    private record Entry(int stance, int charges, int max, int threshold, boolean armed) {}

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static final String[] NAMES = {"none", "water", "stone", "fire"};
    private static final int PER_ROW = 10;

    private MonkChargeFx() {}

    public static void receive(Packets.MonkChargesSync msg) {
        if (msg.stance() <= 0 || msg.max() <= 0) ENTRIES.remove(msg.entityId());
        else ENTRIES.put(msg.entityId(), new Entry(msg.stance(), msg.charges(), msg.max(), msg.threshold(), msg.armed()));
    }

    public static void reset() {
        ENTRIES.clear();
    }

    private static ResourceLocation icon(int stance, boolean full) {
        return new ResourceLocation("rites_and_paths", "textures/gui/charges/" + NAMES[stance] + (full ? "" : "_empty") + ".png");
    }

    // ------------------------------------------------------------------ barra sobre la vida

    /** Se dibuja encima de la barra de armadura (o de la vida) y reserva su altura en el lado izquierdo del HUD. */
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !gui.shouldDrawSurvivalElements()) return;
        Entry e = ENTRIES.get(mc.player.getId());
        if (e == null || e.stance() <= 0 || e.stance() >= NAMES.length) return;
        int rows = (e.max() + PER_ROW - 1) / PER_ROW;
        int left = width / 2 - 91;
        boolean ready = e.charges() >= e.threshold() && e.threshold() > 0;
        long time = mc.level == null ? 0 : mc.level.getGameTime();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (int row = 0; row < rows; row++) {
            int y = height - gui.leftHeight;
            for (int col = 0; col < PER_ROW; col++) {
                int i = row * PER_ROW + col;
                if (i >= e.max()) break;
                boolean full = i < e.charges();
                int x = left + col * 8;
                int dy = ready && full && ((time / 3 + i) % 6 == 0) ? -1 : 0; // laten como los corazones con poca vida
                g.blit(icon(e.stance(), full), x, y + dy, 0, 0, 9, 9, 9, 9);
                if (e.armed() && full) g.fill(x + 1, y + dy + 1, x + 8, y + dy + 8, 0x40FFFFC0);
            }
            gui.leftHeight += 10;
        }
    }

    // ------------------------------------------------------------------ partículas en los puños

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || ENTRIES.isEmpty()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Player p : mc.level.players()) {
            Entry e = ENTRIES.get(p.getId());
            if (e == null || e.charges() <= 0 || p.isInvisible() || p.isSpectator()) continue;
            if (p.position().distanceToSqr(cam) > 48 * 48) continue;
            double f = Mth.clamp(e.charges() / (double) Math.max(1, e.max()), 0, 1);
            boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
            if (p.getMainHandItem().isEmpty() || io.github.rpgclasses.gear.GearRules.isMartial(p.getMainHandItem())) spawn(mc, p, e, f, p.getMainArm(), firstPerson);
            if (p.getOffhandItem().isEmpty()) spawn(mc, p, e, f, p.getMainArm().getOpposite(), firstPerson);
        }
    }

    private static void spawn(Minecraft mc, Player p, Entry e, double f, HumanoidArm arm, boolean firstPerson) {
        RandomSource r = p.getRandom();
        // Pocas cargas: alguna chispa suelta; muchas: un aura densa y con partículas más fuertes.
        if (r.nextDouble() > 0.2 + 0.8 * f) return;
        int count = 1 + (int) Math.round(f * 3) + (e.charges() >= e.threshold() ? 1 : 0);
        Vec3 hand = handPos(p, arm, firstPerson);
        double spread = firstPerson ? 0.05 : 0.08 + 0.05 * f;
        for (int i = 0; i < count; i++) {
            double x = hand.x + (r.nextDouble() - 0.5) * spread * 2;
            double y = hand.y + (r.nextDouble() - 0.5) * spread * 2;
            double z = hand.z + (r.nextDouble() - 0.5) * spread * 2;
            ParticleOptions particle = particle(e.stance(), f, r);
            double vy = e.stance() == 3 ? 0.01 + 0.03 * f : e.stance() == 1 ? -0.02 : 0.0;
            mc.level.addParticle(particle, x, y, z, (r.nextDouble() - 0.5) * 0.01, vy, (r.nextDouble() - 0.5) * 0.01);
        }
    }

    private static ParticleOptions particle(int stance, double f, RandomSource r) {
        double roll = r.nextDouble();
        return switch (stance) {
            case 3 -> f >= 0.99 && roll < 0.15 ? ParticleTypes.LAVA
                    : roll < 0.25 + 0.5 * f ? ParticleTypes.FLAME
                    : roll < 0.9 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE;
            case 1 -> f >= 0.99 && roll < 0.2 ? ParticleTypes.DOLPHIN
                    : roll < 0.3 + 0.4 * f ? ParticleTypes.SPLASH
                    : roll < 0.85 ? ParticleTypes.FALLING_WATER : ParticleTypes.DRIPPING_WATER;
            case 2 -> f >= 0.99 && roll < 0.2 ? ParticleTypes.CRIT
                    : roll < 0.3 + 0.4 * f ? new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState())
                    : new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState());
            default -> ParticleTypes.CLOUD;
        };
    }

    /** Posición aproximada de la mano: delante de la cámara en primera persona; junto al cuerpo (con su tamaño) en tercera. */
    private static Vec3 handPos(Player p, HumanoidArm arm, boolean firstPerson) {
        float pt = Minecraft.getInstance().getFrameTime();
        double side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (firstPerson) {
            Vec3 look = p.getViewVector(pt);
            Vec3 up = p.getUpVector(pt);
            Vec3 right = look.cross(up).normalize();
            return p.getEyePosition(pt).add(look.scale(0.6)).add(right.scale(side * 0.38)).subtract(up.scale(0.34));
        }
        double scale = p.getDimensions(Pose.STANDING).height / 1.8;
        float yaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        double handY = (p.isCrouching() ? 0.55 : 0.7) * scale;
        Vec3 base = p.getPosition(pt);
        return base.add(right.scale(side * 0.37 * scale)).add(forward.scale(0.08 * scale)).add(0, handY, 0);
    }
}
