package io.github.rpgclasses.server;

import io.github.rpgclasses.core.ability.UnlockResolver;
import io.github.rpgclasses.core.definition.DefinitionStore;
import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.player.PlayerClassData;
import io.github.rpgclasses.player.PlayerClassDataProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /clases debug volcar}: escribe en {@code logs/} un informe con TODO lo necesario para depurar a un jugador
 * (estado de clase, pasivas activas con sus parámetros, enfriamientos, atributos, efectos, equipo, últimos eventos
 * y últimos errores). Es el archivo que Daniel tiene que pasar cuando algo falla.
 */
public final class DebugDump {

    public static Path write(ServerPlayer player) throws IOException {
        List<String> out = new ArrayList<>();
        out.add("=== RPG Classes " + DebugLog.modVersion() + " — volcado de " + player.getGameProfile().getName()
                + " (" + LocalDateTime.now() + ") ===");
        out.add("Mods: forge " + version("forge") + ", irons_spellbooks " + version("irons_spellbooks") + ", epicfight " + version("epicfight")
                + ", curios " + version("curios") + ", cataclysm " + version("cataclysm") + ", mowziesmobs " + version("mowziesmobs"));
        out.add("Registro de eventos: " + (DebugLog.enabled() ? "activo" : "DESACTIVADO (solo errores)"));

        PlayerClassData data = PlayerClassDataProvider.get(player);
        out.add("");
        out.add("--- Progreso ---");
        if (data == null) {
            out.add("sin datos de clase (capability ausente)");
        } else {
            out.add("clase " + data.state.classId + " | nivel base " + data.state.basePoints() + " | subclase " + data.state.subclassId
                    + " nivel " + data.state.subclassLevel);
            out.add("raza " + data.state.raceId + " | subraza " + data.state.subraceId + " | rango " + data.state.raceRank
                    + " | muros de Adaptable " + data.state.humanWalls);
            out.add("ramas " + data.state.branchPoints + " | perks " + data.state.perks);
            java.util.List<String> allyNames = new java.util.ArrayList<>();
            for (java.util.UUID id : Allies.book(player.server).alliesOf(player.getUUID())) allyNames.add(Allies.nameOf(player.server, id));
            java.util.List<String> byNames = new java.util.ArrayList<>();
            for (java.util.UUID id : Allies.book(player.server).alliedBy(player.getUUID())) byNames.add(Allies.nameOf(player.server, id));
            out.add("aliados " + allyNames + " | le tienen como aliado " + byNames);
            out.add("xp pagada base " + data.state.xpPaidBase + ", subclase " + data.state.xpPaidSubclass + " | kit entregado "
                    + data.state.kitGranted + " | xp actual " + ProgressionService.currentXp(player));
            out.add("contadores " + data.counters + " | textos " + data.texts);
            long now = player.level().getGameTime();
            List<String> cds = new ArrayList<>();
            for (Map.Entry<String, Long> e : data.cooldowns.entrySet()) {
                if (e.getValue() > now) cds.add(e.getKey() + "=" + DebugLog.num((e.getValue() - now) / 20.0) + "s");
            }
            out.add("enfriamientos " + cds);
        }

        out.add("");
        out.add("--- Habilidades desbloqueadas ---");
        java.util.Set<String> unlocked = AbilityService.unlocked(player);
        out.add("todas " + unlocked);
        out.add("activas por ranura (Z/X/C) " + UnlockResolver.actives(unlocked, DefinitionStore.SERVER::abilityDef));

        out.add("");
        out.add("--- Pasivas/mecánicas activas (con parámetros) ---");
        for (ActiveMechanic m : MechanicService.all(player)) {
            out.add(m.id() + " nivel " + m.level() + "/" + m.maxLevel() + " " + m.params());
        }

        out.add("");
        out.add("--- Atributos del mod ---");
        StatEngine.recompute(player);
        out.addAll(StatEngine.describe(player));
        out.add("vida " + DebugLog.num(player.getHealth()) + "/" + DebugLog.num(player.getMaxHealth()) + ", absorción "
                + DebugLog.num(player.getAbsorptionAmount()) + ", armadura " + player.getArmorValue());
        String last = DamagePipeline.lastBreakdown(player);
        if (last != null) out.add("último golpe: " + last);

        out.add("");
        out.add("--- Movimiento (por qué no se puede correr) ---");
        out.add("hambre " + player.getFoodData().getFoodLevel() + "/20 (hace falta más de 6 para correr), saturación "
                + DebugLog.num(player.getFoodData().getSaturationLevel()) + " | corriendo " + player.isSprinting()
                + " | en agua " + player.isInWater() + " (bajo el agua " + player.isUnderWater() + ") | en el suelo "
                + player.onGround() + " | agachado " + player.isShiftKeyDown() + " | usando objeto " + player.isUsingItem()
                + " | ceguera " + player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS));
        var speedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            out.add("velocidad " + DebugLog.num(speedAttr.getValue()) + " (base " + DebugLog.num(speedAttr.getBaseValue()) + ")");
            for (var m : speedAttr.getModifiers()) {
                out.add("  modificador " + m.getName() + " " + m.getOperation() + " " + DebugLog.num(m.getAmount()));
            }
        }
        out.add("Monje: postura " + io.github.rpgclasses.content.MonkCharges.activeStance(player) + ", cargas "
                + io.github.rpgclasses.content.MonkCharges.charges(player));

        out.add("");
        out.add("--- Efectos ---");
        for (MobEffectInstance e : player.getActiveEffects()) {
            out.add(String.valueOf(net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect()))
                    + " nivel " + (e.getAmplifier() + 1) + " " + DebugLog.num(e.getDuration() / 20.0) + "s");
        }

        out.add("");
        out.add("--- Equipo ---");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!player.getItemBySlot(slot).isEmpty()) out.add(slot.getName() + ": " + player.getItemBySlot(slot)
                    + (player.getItemBySlot(slot).hasTag() ? " " + player.getItemBySlot(slot).getTag() : ""));
        }
        WeaponClassifier.Classification weapon = WeaponClassifier.classify(player.getMainHandItem());
        out.add("arma: " + (weapon.unarmed() ? "desarmado" : weapon.discipline()) + " (" + weapon.rule() + ")");

        out.add("");
        out.add("--- Últimos eventos del jugador ---");
        out.addAll(DebugLog.recent(player.getUUID(), 400));
        out.add("");
        out.add("--- Últimos eventos globales ---");
        out.addAll(DebugLog.recent(null, 80));
        out.add("");
        out.add("--- Últimos avisos y errores ---");
        out.addAll(DebugLog.errors());

        Path dir = FMLPaths.GAMEDIR.get().resolve("logs");
        Files.createDirectories(dir);
        Path file = dir.resolve("rpgclasses-volcado-" + player.getGameProfile().getName() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        Files.write(file, out, StandardCharsets.UTF_8);
        DebugLog.event(player, "volcado", "escrito en {}", file.getFileName());
        return file;
    }

    /** Mensaje de chat con la ruta; en un jugador local, al pulsarlo se abre el archivo. */
    public static Component link(Path file) {
        String path = file.toAbsolutePath().toString();
        return Component.translatable("message.rpgclasses.debug.dump_written", Component.literal(file.getFileName().toString())
                .withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(path)))));
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString()).orElse("no");
    }

    private DebugDump() {}
}
