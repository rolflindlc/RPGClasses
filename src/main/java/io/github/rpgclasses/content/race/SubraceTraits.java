package io.github.rpgclasses.content.race;

import io.github.rpgclasses.core.stat.StatCalculator.ActiveMechanic;
import io.github.rpgclasses.server.AbilityService;
import io.github.rpgclasses.server.DebugLog;
import io.github.rpgclasses.server.KitService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rasgos pasivos de las subrazas (anexo de razas, sección 3). Las habilidades activas están en {@link RaceAbilities}
 * y lo que cambia el botín de bloques en {@link RaceLootModifier}.
 */
public final class SubraceTraits {
    private static final UUID FISHER_LUCK = UUID.fromString("5b0f6f0e-2c57-4f63-9d0b-6a1f0c9e7a11");
    private static final String STEAL_TAG = "rpgclasses.stolen";

    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();
    /** Vampiro: [segundo de juego, curado en ese segundo] para el tope por segundo. */
    private static final Map<UUID, double[]> THIRST_WINDOW = new HashMap<>();
    private static final Map<String, Long> STEAL_COOLDOWN = new HashMap<>();

    /** Una vez por segundo y jugador, desde {@link RaceTraits}. */
    static void secondTick(ServerPlayer p) {
        // Espectro: caída lenta (agacharse la suelta).
        if (!p.isShiftKeyDown() && !p.onGround() && Races.has(p, "race_weightless")) {
            p.addEffect(Races.hidden(MobEffects.SLOW_FALLING, 25, 0));
        }
        // Tritón Abisal: invisible bajo el agua mientras está quieto.
        if (p.isUnderWater() && Races.has(p, "race_camouflage")) {
            Vec3 last = LAST_POSITION.put(p.getUUID(), p.position());
            if (last != null && last.distanceToSqr(p.position()) < 0.01) p.addEffect(Races.hidden(MobEffects.INVISIBILITY, 30, 0));
        } else {
            LAST_POSITION.remove(p.getUUID());
        }
        // Tritón Pescador: Suerte del Mar racial mientras lleva una caña.
        AttributeInstance luck = p.getAttribute(Attributes.LUCK);
        if (luck != null) {
            boolean rod = p.getMainHandItem().getItem() instanceof FishingRodItem || p.getOffhandItem().getItem() instanceof FishingRodItem;
            double bonus = rod ? Races.number(p, "race_fisher", "luck", 0) : 0;
            AttributeModifier current = luck.getModifier(FISHER_LUCK);
            if (current != null && current.getAmount() != bonus) luck.removeModifier(FISHER_LUCK);
            if (bonus > 0 && luck.getModifier(FISHER_LUCK) == null) {
                luck.addTransientModifier(new AttributeModifier(FISHER_LUCK, "rpgclasses_fisher_luck", bonus, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    // ------------------------------------------------------------------ muertes y daño

    @SubscribeEvent
    public void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        LivingEntity dead = event.getEntity();
        if (dead == p) return;

        // Orco Devorador: Devorar y Festín.
        ActiveMechanic devour = Races.find(p, "race_devour");
        if (devour != null && ready(p, "race_devour", devour.number("icd_seconds", 1))) {
            int food = Math.max(1, (int) devour.number("food", 2));
            p.getFoodData().eat(food, (float) (devour.number("saturation", 1) / (2.0 * food)));
        }
        ActiveMechanic feast = Races.find(p, "race_feast");
        boolean boss = Races.isBoss(dead);
        if (feast != null && (boss || dead instanceof Enemy) && ready(p, "race_feast", feast.number("icd_seconds", 15))) {
            double seconds = dead.getMaxHealth() * (boss ? 1 : feast.number("mob_factor", 0.5));
            MobEffect[] options = {MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.REGENERATION};
            MobEffect chosen = options[p.getRandom().nextInt(options.length)];
            p.addEffect(new MobEffectInstance(chosen, (int) Math.round(seconds * 20), 0));
            DebugLog.event(p, "raza", "Festín: {} durante {} s", chosen.getDescriptionId(), seconds);
        }
        // No-muerto Vampiro: Sed.
        double killHeal = Races.number(p, "race_thirst", "kill_heal", 0);
        if (killHeal > 0) thirstHeal(p, killHeal);
    }

    @SubscribeEvent
    public void onDamageDealt(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (victim.level().isClientSide() || event.getAmount() <= 0) return;

        // Tritón Abisal: los guardianes cercanos defienden al Tritón.
        if (victim instanceof ServerPlayer hurt && source.getEntity() instanceof LivingEntity attacker && attacker != hurt) {
            escort(hurt, attacker);
        }
        if (!(source.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || victim == p) return;
        escort(p, victim);

        double lifesteal = Races.number(p, "race_thirst", "lifesteal", 0);
        if (lifesteal > 0) thirstHeal(p, event.getAmount() * lifesteal);

        ActiveMechanic hand = Races.find(p, "race_long_hand");
        if (hand != null && source.getDirectEntity() == p) steal(p, victim, source, hand);
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        // No-muerto Esqueleto: menos daño de proyectiles.
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            double reduction = Races.number(p, "race_bones", "projectile_reduction", 0);
            if (reduction > 0) event.setAmount((float) (event.getAmount() * (1 - Math.min(1, reduction))));
        }
    }

    /** Vampiro: la curación de Sed tiene un tope por segundo. */
    private static void thirstHeal(ServerPlayer p, double amount) {
        double cap = Races.number(p, "race_thirst", "cap_per_second", 5);
        long secondNow = p.level().getGameTime() / 20;
        double[] window = THIRST_WINDOW.computeIfAbsent(p.getUUID(), k -> new double[2]);
        if (window[0] != secondNow) {
            window[0] = secondNow;
            window[1] = 0;
        }
        double heal = Math.min(amount, cap - window[1]);
        if (heal <= 0) return;
        window[1] += heal;
        p.heal((float) heal);
    }

    private static void escort(ServerPlayer p, LivingEntity enemy) {
        if (enemy instanceof Guardian || enemy instanceof Player) return;
        ActiveMechanic m = Races.find(p, "race_escort");
        if (m == null) return;
        for (Guardian g : p.level().getEntitiesOfClass(Guardian.class, p.getBoundingBox().inflate(m.number("radius", 16)))) {
            if (g.isAlive() && g.getTarget() != enemy) g.setTarget(enemy);
        }
    }

    // ------------------------------------------------------------------ Goblin Ladrón

    private static void steal(ServerPlayer thief, LivingEntity victim, DamageSource source, ActiveMechanic m) {
        RandomSource rnd = thief.getRandom();
        long now = thief.level().getGameTime();
        ItemStack stolen = ItemStack.EMPTY;
        if (victim instanceof ServerPlayer target) {
            String key = thief.getUUID() + ">" + target.getUUID();
            if (now < STEAL_COOLDOWN.getOrDefault(key, 0L) || target.isAlliedTo(thief)) return;
            if (rnd.nextDouble() >= m.number("player_chance", 0)) return;
            if (rnd.nextDouble() < m.number("equip_share_player", 0)) stolen = takeEquipment(target, rnd);
            if (stolen.isEmpty()) {
                List<Integer> slots = new ArrayList<>();
                for (int i = 9; i < 36; i++) if (stealable(target.getInventory().getItem(i))) slots.add(i);
                if (slots.isEmpty()) return;
                stolen = target.getInventory().removeItemNoUpdate(slots.get(rnd.nextInt(slots.size())));
            }
            STEAL_COOLDOWN.put(key, now + Math.round(m.number("player_cooldown_seconds", 30) * 20));
            target.displayClientMessage(Component.translatable("message.rpgclasses.robbed", thief.getDisplayName()), true);
        } else if (victim instanceof Mob mob && mob instanceof Enemy && !Races.isBoss(mob) && mob.getSpawnType() != MobSpawnType.SPAWNER) {
            int done = mob.getPersistentData().getInt(STEAL_TAG);
            if (done >= (int) m.number("steals_per_mob", 1) || rnd.nextDouble() >= m.number("mob_chance", 0)) return;
            if (rnd.nextDouble() < m.number("equip_share_mob", 0)) stolen = takeEquipment(mob, rnd);
            if (stolen.isEmpty() && thief.level() instanceof ServerLevel level) {
                LootTable table = level.getServer().getLootData().getLootTable(mob.getLootTable());
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, mob)
                        .withParameter(LootContextParams.ORIGIN, mob.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                        .withOptionalParameter(LootContextParams.KILLER_ENTITY, thief)
                        .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, thief)
                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, thief)
                        .withLuck(thief.getLuck())
                        .create(LootContextParamSets.ENTITY);
                List<ItemStack> rolled = new ArrayList<>(table.getRandomItems(params));
                rolled.removeIf(ItemStack::isEmpty);
                if (rolled.isEmpty()) return;
                stolen = rolled.get(rnd.nextInt(rolled.size()));
            }
            mob.getPersistentData().putInt(STEAL_TAG, done + 1);
        }
        if (stolen.isEmpty()) return;
        ItemEntity drop = victim.spawnAtLocation(stolen, 0.5f);
        if (drop != null) {
            drop.setTarget(thief.getUUID()); // prioridad de recogida para el Goblin
            drop.setPickUpDelay(5);
        }
        thief.displayClientMessage(Component.translatable("message.rpgclasses.stole", stolen.getHoverName()), true);
        DebugLog.event(thief, "raza", "Mano Larga: roba {} a {}", stolen, DebugLog.describe(victim));
    }

    private static boolean stealable(ItemStack stack) {
        return !stack.isEmpty() && !(stack.hasTag() && stack.getTag().contains(KitService.KIT_TAG));
    }

    private static ItemStack takeEquipment(LivingEntity e, RandomSource rnd) {
        List<EquipmentSlot> slots = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) if (stealable(e.getItemBySlot(slot))) slots.add(slot);
        if (slots.isEmpty()) return ItemStack.EMPTY;
        EquipmentSlot slot = slots.get(rnd.nextInt(slots.size()));
        ItemStack taken = e.getItemBySlot(slot).copy();
        e.setItemSlot(slot, ItemStack.EMPTY);
        return taken;
    }

    // ------------------------------------------------------------------ comida y efectos

    @SubscribeEvent
    public void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        ItemStack stack = event.getItem();
        FoodProperties food = stack.getFoodProperties(p);
        if (food == null) return;
        boolean flesh = stack.is(Races.MEAT) || stack.is(ItemTags.FISHES) || food.isMeat();

        // Orco Devorador: Estómago de Hierro.
        if (flesh && Races.has(p, "race_iron_stomach")) {
            p.removeEffect(MobEffects.HUNGER);
            p.removeEffect(MobEffects.POISON);
            p.removeEffect(MobEffects.CONFUSION);
            p.level().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(stack.copyWithCount(1)), p.level())
                    .map(r -> r.getResultItem(p.level().registryAccess()).getFoodProperties(p))
                    .filter(cooked -> cooked.getNutrition() > food.getNutrition())
                    .ifPresent(cooked -> p.getFoodData().eat(cooked.getNutrition() - food.getNutrition(), cooked.getSaturationModifier()));
        }
        // Elfo Silvano: la comida vegetal sacia más.
        double plant = flesh ? 0 : Races.number(p, "race_plant_food", "bonus", 0);
        if (plant > 0) p.getFoodData().eat((int) Math.ceil(food.getNutrition() * plant), food.getSaturationModifier());
    }

    @SubscribeEvent
    public void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        MobEffect effect = event.getEffectInstance().getEffect();
        boolean deny = (effect == MobEffects.HUNGER && Races.has(p, "race_hunger_immune"))
                || (effect == MobEffects.DARKNESS && Races.has(p, "race_cave_eyes"))
                || (effect == MobEffects.WITHER && Races.has(p, "race_wither_immune"))
                || ((effect == MobEffects.DIG_SLOWDOWN || effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS)
                    && p.isInWater() && Races.has(p, "race_abyss_immunity"))
                || RaceAbilities.isControlImmune(p, effect);
        if (deny) event.setResult(Event.Result.DENY);
    }

    /** Criaturas neutrales con ciertas subrazas, salvo que el jugador las haya atacado. */
    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        LivingEntity mob = event.getEntity();
        if (mob.getLastHurtByMob() == p) return;
        String mechanic = mob instanceof Drowned || mob instanceof Guardian ? "race_sea_friend"
                : mob instanceof AbstractPiglin || mob instanceof Hoglin ? "race_nether_native"
                : mob instanceof AbstractSkeleton ? "race_bones" : null;
        if (mechanic != null && Races.has(p, mechanic)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && Races.has(p, "race_weightless")) event.setDamageMultiplier(0);
    }

    // ------------------------------------------------------------------ Tritón Pescador

    @SubscribeEvent
    public void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
        ActiveMechanic m = Races.find(p, "race_fisher");
        if (m == null) return;
        RandomSource rnd = p.getRandom();
        List<ItemStack> extra = new ArrayList<>();
        double net = m.number("extra_catch", 0);
        for (ItemStack drop : event.getDrops()) if (rnd.nextDouble() < net) extra.add(drop.copy());
        if (rnd.nextDouble() < m.number("treasure_chance", 0)) {
            ItemStack rod = p.getMainHandItem().getItem() instanceof FishingRodItem ? p.getMainHandItem() : p.getOffhandItem();
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, event.getHookEntity().position())
                    .withParameter(LootContextParams.TOOL, rod)
                    .withParameter(LootContextParams.THIS_ENTITY, event.getHookEntity())
                    .withLuck(p.getLuck())
                    .create(LootContextParamSets.FISHING);
            extra.addAll(level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING_TREASURE).getRandomItems(params));
        }
        for (ItemStack stack : extra) if (!stack.isEmpty() && !p.addItem(stack)) p.drop(stack, false);
        double xp = m.number("xp_bonus", 0);
        if (xp > 0) level.addFreshEntity(new ExperienceOrb(level, p.getX(), p.getY() + 0.5, p.getZ(), (int) Math.max(1, Math.round((rnd.nextInt(6) + 1) * xp))));
    }

    // ------------------------------------------------------------------ Elfo Arcano y Silvano

    /** La mesa de encantamientos cuenta como si el nivel fuera mayor para un Elfo Arcano que la esté usando. */
    @SubscribeEvent
    public void onEnchantLevel(EnchantmentLevelSetEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEnchantLevel() <= 0) return;
        Vec3 table = Vec3.atCenterOf(event.getPos());
        double best = 0;
        for (ServerPlayer p : level.players()) {
            if (p.containerMenu instanceof EnchantmentMenu && p.distanceToSqr(table) < 64) {
                best = Math.max(best, Races.number(p, "race_arcane", "enchant_bonus", 0));
            }
        }
        if (best > 0) event.setEnchantLevel((int) Math.round(event.getEnchantLevel() * (1 + best)));
    }

    /** Fusión Arcana (Rango 3): dos encantamientos iguales a nivel máximo dan un nivel por encima del máximo. */
    @SubscribeEvent
    public void onAnvil(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        ItemStack left = event.getLeft(), right = event.getRight();
        if (player == null || left.isEmpty() || right.isEmpty()) return;
        if (!right.is(Items.ENCHANTED_BOOK) && !right.is(left.getItem())) return;
        ActiveMechanic m = Races.find(player, "race_arcane");
        if (m == null || m.number("fusion", 0) < 1) return;
        Map<Enchantment, Integer> mine = EnchantmentHelper.getEnchantments(left);
        Map<Enchantment, Integer> out = new LinkedHashMap<>(mine);
        boolean fused = false;
        for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(right).entrySet()) {
            Enchantment en = e.getKey();
            Integer have = mine.get(en);
            if (have != null && en.getMaxLevel() > 1 && have == en.getMaxLevel() && e.getValue() == en.getMaxLevel()) {
                out.put(en, have + 1);
                fused = true;
            }
        }
        if (!fused) return; // sin fusión posible: el yunque funciona como siempre
        ItemStack result = left.copy();
        EnchantmentHelper.setEnchantments(out, result);
        event.setOutput(result);
        event.setCost((int) m.number("fusion_cost_levels", 30));
        event.setMaterialCost(1);
    }

    /** Toque Verde: con la mano vacía, polvo de hueso gratis sobre un cultivo. */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !p.getMainHandItem().isEmpty()) return;
        BlockPos pos = event.getPos();
        BlockState state = p.level().getBlockState(pos);
        if (!state.is(BlockTags.CROPS) && !state.is(BlockTags.SAPLINGS) && !(state.getBlock() instanceof StemBlock)) return;
        ActiveMechanic m = Races.find(p, "race_green_touch");
        if (m == null || AbilityService.remainingTicks(p, "race_green_touch") > 0) return;
        if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), p.level(), pos, p)) {
            p.level().levelEvent(1505, pos, 0);
            AbilityService.setCooldown(p, "race_green_touch", (int) Math.round(m.number("cooldown_seconds", 5) * 20));
        }
    }

    private static boolean veining;

    /** Enano Demoledor, Vetas: al romper un mineral se rompen los del mismo tipo conectados (agachado no). */
    @SubscribeEvent
    public void onBreakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (veining || !(event.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isShiftKeyDown()) return;
        BlockState state = event.getState();
        if (!state.is(net.minecraftforge.common.Tags.Blocks.ORES)) return;
        int max = (int) Races.number(p, "race_vein_miner", "max_blocks", 0);
        if (max <= 0) return;
        List<BlockPos> vein = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(event.getPos());
        seen.add(event.getPos());
        while (!queue.isEmpty() && vein.size() < max) {
            BlockPos at = queue.poll();
            for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                if (vein.size() >= max) break;
                BlockPos pos = n.immutable();
                if (seen.add(pos) && p.level().getBlockState(pos).is(state.getBlock())) {
                    vein.add(pos);
                    queue.add(pos);
                }
            }
        }
        veining = true;
        try {
            for (BlockPos pos : vein) {
                if (p.getMainHandItem().isEmpty()) break; // la herramienta se ha roto
                p.gameMode.destroyBlock(pos);
            }
        } finally {
            veining = false;
        }
    }

    /** true si ha pasado el enfriamiento interno {@code key}; en ese caso lo reinicia. */
    private static boolean ready(ServerPlayer p, String key, double seconds) {
        if (AbilityService.remainingTicks(p, key) > 0) return false;
        AbilityService.setCooldown(p, key, (int) Math.round(seconds * 20));
        return true;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POSITION.remove(event.getEntity().getUUID());
        THIRST_WINDOW.remove(event.getEntity().getUUID());
    }
}
