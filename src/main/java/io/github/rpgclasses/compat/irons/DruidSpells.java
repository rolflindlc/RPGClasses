package io.github.rpgclasses.compat.irons;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.content.Chronomancy;
import io.github.rpgclasses.druid.BeastRemains;
import io.github.rpgclasses.druid.Druid;
import io.github.rpgclasses.druid.Knowledge;
import io.github.rpgclasses.druid.WildShape;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.config.SpellConfigManager;
import io.redspace.ironsspellbooks.api.config.SpellConfigParameter;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Druida × Iron's Spells (adenda del Druida, 2 y 6):
 * <ul>
 *   <li>Ritual del Druida ({@code rites_and_paths:druid_ritual}): conjuro de naturaleza, exclusivo de la clase y sin
 *       pergamino (siempre en su rueda). Se lanza sobre los restos de una bestia recién muerta.</li>
 *   <li>Forma Sobrenatural: transformado, los conjuros de naturaleza cuestan más maná.</li>
 * </ul>
 */
public final class DruidSpells {
    public static final ResourceLocation NATURE = new ResourceLocation("irons_spellbooks", "nature");
    static final String SLOT = "rites_and_paths_druid";
    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, RpgClasses.MODID);
    public static final RegistryObject<AbstractSpell> RITUAL = SPELLS.register("druid_ritual", RitualSpell::new);

    private DruidSpells() {}

    public static void init(IEventBus modBus) {
        SPELLS.register(modBus);
        MinecraftForge.EVENT_BUS.register(new Events());
    }

    public static final class Events {
        /** El Ritual está siempre en la rueda del Druida (no ocupa ranura de libro). */
        @SubscribeEvent
        public void onSpellSelection(SpellSelectionManager.SpellSelectionEvent event) {
            if (!Druid.isDruid(event.getEntity()) || !Druid.has(event.getEntity(), Druid.BEAST_LORE)) return;
            event.addSelectionOption(new SpellData(RITUAL.get(), 1, true), SLOT, 0);
        }

        /** Solo el Druida puede lanzar el Ritual (desde cualquier fuente). */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onPreCast(SpellPreCastEvent event) {
            if (!RITUAL.getId().toString().equals(event.getSpellId())) return;
            if (event.getEntity() instanceof ServerPlayer p && !Druid.isDruid(p)) {
                p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.not_druid"), true);
                event.setCanceled(true);
            }
        }

        /** Forma Sobrenatural: transformado, los conjuros de naturaleza cuestan más maná. */
        @SubscribeEvent
        public void onCast(SpellOnCastEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer p) || !WildShape.inForm(p) || !Druid.hasMech(p, Druid.SUPERNATURAL)) return;
            AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(event.getSpellId()));
            if (spell == null || !NATURE.equals(spell.getSchoolType().getId())) return;
            if (event.getCastSource().consumesMana()) {
                event.setManaCost((int) Math.round(event.getManaCost() * (1 + Druid.mech(p, Druid.SUPERNATURAL, "spell_cost", .25))));
            }
        }
    }

    /** Ritual del Druida: lanzamiento largo (2 s, se interrumpe con daño) sobre los restos que se miran. */
    static final class RitualSpell extends AbstractSpell {
        private static final Map<UUID, Integer> TARGETS = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
        private final DefaultConfig config = new DefaultConfig().setMinRarity(SpellRarity.COMMON).setSchoolResource(NATURE)
                .setMaxLevel(1).setCooldownSeconds(0).setAllowCrafting(false).build();

        RitualSpell() {
            this.baseManaCost = 35;
            this.manaCostPerLevel = 0;
            this.baseSpellPower = 1;
            this.spellPowerPerLevel = 0;
            this.castTime = 40;
        }

        @Override
        public ResourceLocation getSpellResource() {
            return RITUAL.getId();
        }

        @Override
        public DefaultConfig getDefaultConfig() {
            return config;
        }

        @Override
        public CastType getCastType() {
            return CastType.LONG;
        }

        @Override
        public int getManaCost(int level) {
            double mult = SpellConfigManager.getSpellConfigValue(this, SpellConfigParameter.MANA_MULTIPLIER);
            return (int) Math.round(Druid.number(Druid.BEAST_LORE, "ritual_mana", 35) * mult);
        }

        @Override
        public boolean allowLooting() {
            return false;
        }

        @Override
        public boolean allowCrafting() {
            return false;
        }

        @Override
        public List<MutableComponent> getUniqueInfo(int level, LivingEntity caster) {
            return List.of(Component.translatable("spell.rites_and_paths.druid_ritual.guide"));
        }

        /** Antes de gastar maná: sin restos válidos a la vista no se lanza. */
        @Override
        public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
            if (!(entity instanceof ServerPlayer p)) return true;
            BeastRemains r = Knowledge.lookRemains(p);
            if (!Knowledge.canRitual(p, r)) return false;
            int extra = Knowledge.ritualMana(p) - getManaCost(spellLevel);
            if (extra > 0 && !p.isCreative() && Chronomancy.mana != null && Chronomancy.mana.mana(p) < Knowledge.ritualMana(p)) {
                p.displayClientMessage(Component.translatable("message.rites_and_paths.druid.no_mana", Knowledge.ritualMana(p)), true);
                return false;
            }
            TARGETS.put(p.getUUID(), r.getId());
            return true;
        }

        @Override
        public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
            if (entity instanceof ServerPlayer p) {
                Integer id = TARGETS.remove(p.getUUID());
                Entity e = id == null ? null : p.level().getEntity(id);
                BeastRemains r = e instanceof BeastRemains br ? br : Knowledge.lookRemains(p);
                int extra = castSource.consumesMana() ? Math.max(0, Knowledge.ritualMana(p) - getManaCost(spellLevel)) : 0;
                Knowledge.performRitual(p, r, extra);
            }
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
        }
    }
}
