package io.github.rpgclasses.mixin.client;

import io.github.rpgclasses.client.rogue.DisguiseClient;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Cambiarrostros: los jugadores disfrazados no salen en el Tab de los demás. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabHideMixin {
    @Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true, require = 0)
    private void rites_and_paths$hideDisguised(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        if (!DisguiseClient.anyDisguised()) return;
        List<PlayerInfo> infos = cir.getReturnValue();
        if (infos == null) return;
        cir.setReturnValue(infos.stream().filter(i -> !DisguiseClient.hiddenInTab(i.getProfile().getId())).toList());
    }
}
