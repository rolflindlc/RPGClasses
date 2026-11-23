package io.github.rpgclasses.druid;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.rpgclasses.network.Net;
import io.github.rpgclasses.player.PlayerClassData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Administración del bestiario del Druida (permiso 2), dentro de {@code /clases}:
 * <pre>
 * /clases bestiario &lt;jugador&gt; ver
 * /clases bestiario &lt;jugador&gt; dar &lt;especie&gt; [puntos]     sin puntos: los que pide la especie (la deja aprendida)
 * /clases bestiario &lt;jugador&gt; quitar &lt;especie&gt;             borra el conocimiento; si está en esa forma, vuelve
 * </pre>
 * Solo acepta especies válidas para el Druida (animales y bestias, más la lista permitida y sin la vetada).
 */
public final class BestiaryCommand {
    private BestiaryCommand() {}

    private static final SuggestionProvider<CommandSourceStack> BEASTS = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            if (Beasts.eligible(type, false)) ids.add(Beasts.id(type));
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, builder) -> {
        try {
            return SharedSuggestionProvider.suggest(Bestiary.species(Druid.data(EntityArgument.getPlayer(ctx, "jugador"))), builder);
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
    };

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("bestiario").then(Commands.argument("jugador", EntityArgument.player())
                .then(Commands.literal("ver").executes(c -> list(c.getSource(), EntityArgument.getPlayer(c, "jugador"))))
                .then(Commands.literal("dar").then(Commands.argument("especie", ResourceLocationArgument.id()).suggests(BEASTS)
                        .executes(c -> give(c, -1))
                        .then(Commands.argument("puntos", IntegerArgumentType.integer(1, 100))
                                .executes(c -> give(c, IntegerArgumentType.getInteger(c, "puntos"))))))
                .then(Commands.literal("quitar").then(Commands.argument("especie", ResourceLocationArgument.id()).suggests(KNOWN)
                        .executes(BestiaryCommand::remove))));
    }

    private static int list(CommandSourceStack source, ServerPlayer target) {
        PlayerClassData data = Druid.data(target);
        List<String> species = Bestiary.species(data);
        if (species.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.rites_and_paths.bestiary.empty", target.getDisplayName()), false);
            return 0;
        }
        MutableComponent line = Component.empty();
        for (int i = 0; i < species.size(); i++) {
            String id = species.get(i);
            Beasts.Info info = Beasts.info(id, false);
            int needed = info == null ? 0 : Beasts.requiredPoints(target, info);
            int points = Bestiary.points(data, id);
            if (i > 0) line.append(", ");
            line.append(Beasts.name(id).copy().withStyle(points >= needed ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(" " + points + "/" + needed).withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> Component.translatable("commands.rites_and_paths.bestiary.list", target.getDisplayName(), line), false);
        return species.size();
    }

    private static int give(CommandContext<CommandSourceStack> c, int wanted) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "jugador");
        String species = ResourceLocationArgument.getId(c, "especie").toString();
        PlayerClassData data = Druid.data(target);
        Beasts.Info info = Beasts.info(species, false);
        if (data == null || info == null || !Beasts.eligible(info.type(), false)) {
            c.getSource().sendFailure(Component.translatable("commands.rites_and_paths.bestiary.not_beast", species));
            return 0;
        }
        int needed = Beasts.requiredPoints(target, info);
        int points = wanted < 0 ? needed : wanted;
        int current = Bestiary.points(data, species);
        int total = Bestiary.addPoints(data, species, points - current, Math.max(points, needed));
        Net.syncProgress(target);
        c.getSource().sendSuccess(() -> Component.translatable("commands.rites_and_paths.bestiary.added",
                target.getDisplayName(), Beasts.name(species), total, needed), true);
        return total;
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "jugador");
        String species = ResourceLocationArgument.getId(c, "especie").toString();
        PlayerClassData data = Druid.data(target);
        if (data == null || Bestiary.points(data, species) <= 0) {
            c.getSource().sendFailure(Component.translatable("commands.rites_and_paths.bestiary.unknown", target.getDisplayName(), Beasts.name(species)));
            return 0;
        }
        Bestiary.removeSpecies(data, species);
        FormSpec spec = WildShape.spec(target);
        if (spec != null && !spec.isChimera() && species.equals(spec.type)) WildShape.revert(target, WildShape.Reason.LOST_CLASS);
        Net.syncProgress(target);
        c.getSource().sendSuccess(() -> Component.translatable("commands.rites_and_paths.bestiary.removed",
                target.getDisplayName(), Beasts.name(species)), true);
        return 1;
    }
}
