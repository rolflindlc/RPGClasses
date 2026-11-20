package io.github.rpgclasses.compat.irons.music;

import io.github.rpgclasses.RpgClasses;
import io.github.rpgclasses.bard.Bard;
import io.github.rpgclasses.bard.BardItems;
import io.github.rpgclasses.bard.BardMagic;
import io.github.rpgclasses.bard.BardState;
import io.github.rpgclasses.bard.CuttingNoteEntity;
import io.github.rpgclasses.bard.Instrument;
import io.github.rpgclasses.bard.MusicSpellInfo;
import io.github.rpgclasses.bard.Sheets;
import io.github.rpgclasses.compat.irons.IronsCompat;
import io.github.rpgclasses.content.Chronomancy;
import io.github.rpgclasses.registry.ModAttributes;
import io.github.rpgclasses.server.AbilityService;
import io.redspace.ironsspellbooks.api.events.InscribeSpellEvent;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Escuela musical del Bardo en Iron's Spells: registro de la escuela ({@code rites_and_paths:music}, con poder
 * {@code rites_and_paths:music_spell_power}) y de sus 21 conjuros, y las reglas:
 * <ul>
 *   <li>Los conjuros musicales solo se lanzan tocando el instrumento (clic derecho): la tecla de lanzar de Iron's no
 *       sirve, ni los libros ni los pergaminos (no se pueden inscribir).</li>
 *   <li>Cada conjuro exige un combo mínimo al lanzarlo (Oído Fino del Juglar lo reduce).</li>
 *   <li>Recital (Juglar, hito 5): tras tres conjuros distintos del instrumento en 12 s, el siguiente es gratis y pierde
 *       la mitad de su recarga.</li>
 * </ul>
 */
public final class MusicBridge implements BardMagic {
    public static final ResourceLocation SCHOOL_ID = RpgClasses.id("music");

    private static final DeferredRegister<SchoolType> SCHOOLS = DeferredRegister.create(SchoolRegistry.SCHOOL_REGISTRY_KEY, RpgClasses.MODID);
    private static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, RpgClasses.MODID);

    public static final RegistryObject<SchoolType> MUSIC = SCHOOLS.register("music", () -> new SchoolType(
            SCHOOL_ID,
            ItemTags.create(RpgClasses.id("music_focus")),
            Component.translatable("school.rites_and_paths.music").withStyle(s -> s.withColor(TextColor.fromRgb(0xF2C94C))),
            () -> ModAttributes.MUSIC_SPELL_POWER.get(),
            () -> ModAttributes.MUSIC_MAGIC_RESIST.get(),
            () -> SoundEvents.NOTE_BLOCK_CHIME.value(),
            CuttingNoteEntity.MUSIC_MAGIC));

    static {
        for (MusicSpellInfo info : MusicSpellInfo.values()) SPELLS.register(info.id, () -> MusicSpells.create(info));
    }

    /** Lanzamiento en curso desde el instrumento (el único permitido para los conjuros musicales). */
    private static final ThreadLocal<Boolean> FROM_INSTRUMENT = ThreadLocal.withInitial(() -> false);
    /**
     * Lanzamiento iniciado desde el instrumento que aún no se ha completado. Iron's no lanza el conjuro dentro de
     * {@code attemptInitiateCast}: lo deja iniciado y lo completa en un tick posterior (evento {@code SpellOnCastEvent}),
     * así que la rotación de la selección se hace entonces y no justo después de iniciarlo.
     */
    private record Pending(String spellId, SpellSelectionManager.SelectionOption option, long tick) {}

    private static final Map<UUID, Pending> PENDING = io.github.rpgclasses.server.SessionState.track(new HashMap<>());
    private static final Map<UUID, Long> FREE_CAST = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    private static final class Recital {
        final Deque<Object[]> recent = new ArrayDeque<>();
        boolean armed;
        String halveNext;
        long icdUntil;
    }

    private static final Map<UUID, Recital> RECITALS = io.github.rpgclasses.server.SessionState.track(new HashMap<>());

    public static void init(IEventBus modBus) {
        SCHOOLS.register(modBus);
        SPELLS.register(modBus);
        MinecraftForge.EVENT_BUS.register(new MusicBridge());
        Bard.magic = new MusicBridge();
        RpgClasses.LOGGER.info("[rites_and_paths] Escuela musical del Bardo registrada en Iron's Spells");
    }

    static boolean isMusic(String spellId) {
        return MusicSpellInfo.byId(spellId) != null && spellId.startsWith(RpgClasses.MODID + ":");
    }

    // ------------------------------------------------------------------ BardMagic

    @Override
    public void castFromInstrument(ServerPlayer player, InteractionHand hand, ItemStack instrument) {
        SpellSelectionManager manager = new SpellSelectionManager(player);
        SpellSelectionManager.SelectionOption option = manager.getSelection();
        String slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND.getName() : EquipmentSlot.OFFHAND.getName();
        SpellSelectionManager.SelectionOption chosen = null;
        if (option != null && slot.equals(option.slot) && isMusic(option.spellData.getSpell().getSpellId())) {
            chosen = option;
        } else {
            // Si en la rueda hay elegido otro conjuro, se toca el primero del instrumento que se pueda lanzar.
            SpellSelectionManager.SelectionOption first = null;
            for (SpellSelectionManager.SelectionOption o : manager.getSpellsForSlot(slot)) {
                if (!isMusic(o.spellData.getSpell().getSpellId())) continue;
                if (first == null) first = o;
                if (MusicRotation.castable(player, o)) {
                    chosen = o;
                    break;
                }
            }
            if (chosen == null) chosen = first;
        }
        if (chosen == null) {
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.not_instrument"), true);
            return;
        }
        SpellData data = chosen.spellData;
        FROM_INSTRUMENT.set(true);
        boolean started = false;
        try {
            started = data.getSpell().attemptInitiateCast(instrument, data.getLevel(), player.level(), player, CastSource.SPELLBOOK, true, slot);
        } finally {
            FROM_INSTRUMENT.set(false);
        }
        // Cuando Iron's complete el lanzamiento (onCast), la selección pasa al siguiente conjuro que se pueda tocar.
        if (started) PENDING.put(player.getUUID(), new Pending(data.getSpell().getSpellId(), chosen, player.level().getGameTime()));
        else PENDING.remove(player.getUUID());
    }

    @Override
    public void rotateSpell(ServerPlayer player, boolean afterCast) {
        MusicRotation.rotate(player, afterCast, null);
    }

    /** Solo Magistral: el siguiente conjuro (que no sea el propio Solo) no tiene recarga durante este tiempo. */
    static void armFreeCast(ServerPlayer player, long ticks) {
        FREE_CAST.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    @Override
    public void syncContainer(ServerPlayer holder, ItemStack instrument) {
        Instrument kind = BardItems.instrumentOf(instrument);
        if (kind == null) return;
        int capacity = Math.max(1, Bard.capacity(holder, kind));
        List<Sheets.Entry> active = Sheets.active(instrument, capacity);
        ISpellContainer current = ISpellContainer.isSpellContainer(instrument) ? ISpellContainer.get(instrument) : null;
        if (current != null && same(current, active, capacity)) return;
        ISpellContainerMutable m = ISpellContainer.create(capacity, true, false).mutableCopy();
        int index = 0;
        for (Sheets.Entry e : active) {
            AbstractSpell spell = SpellRegistry.getSpell(e.spell().rl());
            if (spell == null || "irons_spellbooks:none".equals(spell.getSpellId())) continue;
            m.addSpellAtIndex(spell, e.level(), index++, true);
        }
        ISpellContainer.set(instrument, m.toImmutable());
    }

    @Override
    public MusicSpellInfo spellAt(ItemStack instrument, int index) {
        if (index < 0 || !ISpellContainer.isSpellContainer(instrument)) return null;
        ISpellContainer c = ISpellContainer.get(instrument);
        if (c == null || index >= c.getMaxSpellCount()) return null;
        SpellData data = c.getSpellAtIndex(index);
        if (data == null || data == SpellData.EMPTY) return null;
        String id = data.getSpell().getSpellId();
        return isMusic(id) ? MusicSpellInfo.byId(id) : null;
    }

    private static boolean same(ISpellContainer c, List<Sheets.Entry> active, int capacity) {
        if (c.getMaxSpellCount() != capacity || !c.isSpellWheel() || c.mustEquip()) return false;
        List<SpellSlot> slots = c.getActiveSpells();
        if (slots.size() != active.size()) return false;
        for (int i = 0; i < slots.size(); i++) {
            SpellSlot s = slots.get(i);
            Sheets.Entry e = active.get(i);
            if (!s.getSpell().getSpellId().equals(e.spell().fullId()) || s.getLevel() != e.level() || s.index() != i) return false;
        }
        return true;
    }

    @Override
    public void refundAllCooldowns(ServerPlayer player, double fraction) {
        IronsCompat.refundAllCooldowns(player, fraction);
    }

    @Override
    public float mana(ServerPlayer player) {
        return Chronomancy.mana == null ? 0 : Chronomancy.mana.mana(player);
    }

    @Override
    public float maxMana(ServerPlayer player) {
        return Chronomancy.mana == null ? 0 : Chronomancy.mana.maxMana(player);
    }

    @Override
    public void addMana(ServerPlayer player, float amount) {
        if (Chronomancy.mana != null) Chronomancy.mana.setMana(player, mana(player) + amount);
    }

    // ------------------------------------------------------------------ reglas

    /** Solo desde el instrumento, solo el Bardo y con el combo mínimo. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreCast(SpellPreCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isMusic(event.getSpellId())) return;
        MusicSpellInfo info = MusicSpellInfo.byId(event.getSpellId());
        if (!FROM_INSTRUMENT.get()) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.use_instrument"), true);
            return;
        }
        if (!Bard.isBard(player)) {
            event.setCanceled(true);
            return;
        }
        int needed = BardState.comboNeeded(player, info);
        int combo = BardState.combo(player);
        if (combo < needed) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.combo_needed",
                    Component.translatable(info.nameKey()), needed, combo), true);
        }
    }

    /** Los conjuros musicales no se inscriben en libros de Iron's: van en partituras dentro de un instrumento. */
    @SubscribeEvent
    public void onInscribe(InscribeSpellEvent event) {
        if (isMusic(event.getSpellData().getSpell().getSpellId())) event.setCanceled(true);
    }

    /** Recital: cuenta los conjuros distintos y deja el siguiente gratis. */
    @SubscribeEvent
    public void onCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isMusic(event.getSpellId())) return;
        // Rotación: el conjuro tocado desde el instrumento ya está lanzado; la selección avanza al siguiente que se pueda tocar.
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null && pending.spellId().equals(event.getSpellId()) && player.level().getGameTime() - pending.tick() <= 40) {
            MusicRotation.rotate(player, true, pending.option());
        }
        if (!AbilityService.has(player, Bard.RECITAL)) return;
        var params = AbilityService.params(Bard.RECITAL);
        Recital r = RECITALS.computeIfAbsent(player.getUUID(), k -> new Recital());
        long now = player.level().getGameTime();
        if (r.armed) {
            r.armed = false;
            event.setManaCost(0);
            r.halveNext = event.getSpellId();
            r.icdUntil = now + AbilityService.ticks(params, "icd_seconds", 20);
            r.recent.clear();
            return;
        }
        r.recent.addLast(new Object[] {event.getSpellId(), now});
        long window = AbilityService.ticks(params, "window_seconds", 12);
        while (!r.recent.isEmpty() && now - (Long) r.recent.peekFirst()[1] > window) r.recent.removeFirst();
        Set<String> distinct = new HashSet<>();
        for (Object[] o : r.recent) distinct.add((String) o[0]);
        if (distinct.size() >= (int) AbilityService.number(params, "distinct_spells", 3) && now >= r.icdUntil) {
            r.armed = true;
            player.displayClientMessage(Component.translatable("msg.rites_and_paths.bard.recital"), true);
        }
    }

    @SubscribeEvent
    public void onCooldown(SpellCooldownAddedEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Solo Magistral: el siguiente conjuro sale sin recarga.
        Long freeUntil = FREE_CAST.get(player.getUUID());
        if (freeUntil != null && isMusic(event.getSpell().getSpellId())
                && !event.getSpell().getSpellId().equals(MusicSpellInfo.GRAND_SOLO.fullId())) {
            FREE_CAST.remove(player.getUUID());
            if (freeUntil >= player.level().getGameTime()) {
                event.setEffectiveCooldown(0);
                return;
            }
        }
        Recital r = RECITALS.get(player.getUUID());
        if (r == null || r.halveNext == null || !r.halveNext.equals(event.getSpell().getSpellId())) return;
        r.halveNext = null;
        double cut = AbilityService.number(AbilityService.params(Bard.RECITAL), "cooldown_cut", 0.5);
        event.setEffectiveCooldown((int) Math.round(event.getEffectiveCooldown() * (1 - cut)));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECITALS.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
        FREE_CAST.remove(event.getEntity().getUUID());
        MusicSpells.forget(event.getEntity().getUUID());
    }
}
