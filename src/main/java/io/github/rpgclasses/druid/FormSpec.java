package io.github.rpgclasses.druid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Forma de bestia: una especie ({@code type}) o una quimera del Escultor (cabeza, torso, patas e instinto de cuatro
 * especies; el cuerpo que se ve es el del torso). Viaja por red y se guarda en el jugador como NBT.
 */
public final class FormSpec {
    /** Especie (o torso de la quimera). */
    public final String type;
    /** Partes de la quimera; null si es una especie normal. */
    public final Chimera chimera;
    private EntityType<?> resolved;

    private FormSpec(String type, Chimera chimera) {
        this.type = type;
        this.chimera = chimera;
    }

    public static FormSpec beast(String type) {
        return new FormSpec(type, null);
    }

    public static FormSpec chimera(Chimera c) {
        return new FormSpec(c.torso(), c);
    }

    public boolean isChimera() {
        return chimera != null;
    }

    /** El tipo de entidad del cuerpo (null si ya no existe en el pack). */
    public EntityType<?> entityType() {
        if (resolved == null) resolved = Beasts.type(type);
        return resolved;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type);
        if (chimera != null) tag.put("chimera", chimera.save());
        return tag;
    }

    /** null si la etiqueta está vacía o no es válida. */
    public static FormSpec load(CompoundTag tag) {
        if (tag == null || !tag.contains("type")) return null;
        if (tag.contains("chimera")) {
            Chimera c = Chimera.load(tag.getCompound("chimera"));
            return c == null ? null : chimera(c);
        }
        String type = tag.getString("type");
        return ResourceLocation.tryParse(type) == null ? null : beast(type);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FormSpec f && f.type.equals(type) && java.util.Objects.equals(f.chimera, chimera);
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 + (chimera == null ? 0 : chimera.hashCode());
    }

    /** Quimera: una especie por parte. */
    public record Chimera(String head, String torso, String legs, String instinct) {
        public static final String[] PARTS = {"head", "torso", "legs", "instinct"};

        public String part(int i) {
            return switch (i) {
                case 0 -> head;
                case 1 -> torso;
                case 2 -> legs;
                default -> instinct;
            };
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("head", head);
            tag.putString("torso", torso);
            tag.putString("legs", legs);
            tag.putString("instinct", instinct);
            return tag;
        }

        public static Chimera load(CompoundTag tag) {
            if (tag == null) return null;
            String h = tag.getString("head"), t = tag.getString("torso"), l = tag.getString("legs"), i = tag.getString("instinct");
            if (h.isEmpty() || t.isEmpty() || l.isEmpty() || i.isEmpty()) return null;
            return new Chimera(h, t, l, i);
        }

        /** ¿Existen aún las cuatro especies en el pack? */
        public boolean valid() {
            for (int i = 0; i < 4; i++) {
                ResourceLocation id = ResourceLocation.tryParse(part(i));
                if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) return false;
            }
            return true;
        }
    }
}
