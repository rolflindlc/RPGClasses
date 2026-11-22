package io.github.rpgclasses.druid;

import io.github.rpgclasses.network.DruidPackets;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.server.AbilityService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Envíos del Druida (servidor) y lo que llega del cliente. */
public final class DruidNet {
    private DruidNet() {}

    /** Forma del jugador (o ninguna) a él y a todos los que lo ven. */
    public static void sendForm(ServerPlayer p) {
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), formPacket(p));
    }

    public static void sendFormTo(ServerPlayer viewer, ServerPlayer target) {
        Net.toPlayer(viewer, formPacket(target));
    }

    private static DruidPackets.Form formPacket(ServerPlayer p) {
        FormSpec spec = WildShape.spec(p);
        return new DruidPackets.Form(p.getId(), p.getUUID(), spec == null ? new CompoundTag() : spec.save());
    }

    /** Barras del HUD del propio Druida (lanzamiento, estudio, vuelo, Forma Barata, habilidad de bestia). */
    public static void sendState(ServerPlayer p) {
        FormSpec cast = WildShape.castSpec(p);
        WildShape.Form form = WildShape.form(p);
        long now = p.level().getGameTime();
        int cd = 0, total = 0;
        if (form != null && form.info.ability() != null) {
            cd = (int) AbilityService.remainingTicks(p, BeastAbilities.cooldownKey(form.info.ability()));
            total = (int) Math.round(AbilityService.number(Beasts.abilityParams(form.info.ability(), false), "cooldown_seconds", 0) * 20);
        }
        Net.toPlayer(p, new DruidPackets.State(WildShape.castProgress(p), cast == null ? "" : (cast.isChimera() ? "chimera:" : "") + cast.type,
                Knowledge.studyProgress(p), Knowledge.studyTarget(p),
                form == null ? -1 : (float) form.flightFraction(p), form == null ? -1 : form.ticksLeft(now), cd, total));
    }

    public static void openBestiary(ServerPlayer p) {
        Net.toPlayer(p, new DruidPackets.OpenBestiary());
    }

    public static void onAction(ServerPlayer p, DruidPackets.Action a) {
        switch (a.kind()) {
            case DruidPackets.Action.TRANSFORM -> {
                FormSpec spec = FormSpec.load(a.tag());
                if (spec != null) WildShape.request(p, spec);
            }
            case DruidPackets.Action.RETURN -> WildShape.request(p, null);
            case DruidPackets.Action.BEAST_ABILITY -> BeastAbilities.use(p);
            case DruidPackets.Action.TRACK -> DruidActives.setTracked(p, a.tag().getString("species"));
            case DruidPackets.Action.RITUAL_CHOICE -> Knowledge.ritualChoice(p, a.value());
            case DruidPackets.Action.CHIMERA_REPLACE -> Sculptor.replaceChoice(p, a.value());
            default -> { }
        }
    }
}
