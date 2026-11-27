package io.github.rpgclasses.compat.ntgl;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies the NTGL mixins only when NTGL is installed (Create: Gunsmithing; Engineer addendum). */
public class NtglMixinPlugin implements IMixinConfigPlugin {
    private Boolean present;

    private boolean present() {
        if (present == null) {
            boolean found;
            try {
                found = FMLLoader.getLoadingModList() != null && FMLLoader.getLoadingModList().getModFileById("ntgl") != null;
            } catch (RuntimeException | LinkageError e) {
                found = false;
            }
            present = found;
        }
        return present;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return present();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
