package io.github.rpgclasses.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.rpgclasses.core.ally.AllyBook;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * /aliados (cualquier jugador): lista | agregar &lt;jugador&gt; | quitar &lt;jugador&gt; | limpiar.
 * /clases aliados ver &lt;jugador&gt; (operadores).
 * También hay pantalla (tecla J o botón "Aliados" del libro de clases).
 */
public final class AllyCommands {

    private static final SuggestionProvider<CommandSourceStack> OWN_ALLIES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        List<String> names = new ArrayList<>();
        if (player != null) {
            for (UUID id : Allies.book(player.server).alliesOf(player.getUUID())) names.add(Allies.nameOf(player.server, id));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aliados")
                .executes(c -> list(c.getSource(), c.getSource().getPlayerOrException()))
                .then(Commands.literal("lista").executes(c -> list(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.literal("agregar").then(Commands.argument("jugador", GameProfileArgument.gameProfile())
                        .executes(c -> set(c, true))))
                .then(Commands.literal("quitar").then(Commands.argument("jugador", GameProfileArgument.gameProfile()).suggests(OWN_ALLIES)
                        .executes(c -> set(c, false))))
                .then(Commands.literal("limpiar").executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    int n = Allies.clear(player);
                    c.getSource().sendSuccess(() -> Component.translatable("msg.rpgclasses.ally.cleared", n), false);
                    return n;
                })));

        dispatcher.register(Commands.literal("clases").requires(s -> s.hasPermission(2))
                .then(Commands.literal("aliados").then(Commands.literal("ver")
                        .then(Commands.argument("jugador", GameProfileArgument.gameProfile()).executes(c -> {
                            int n = 0;
                            for (GameProfile profile : GameProfileArgument.getGameProfiles(c, "jugador")) {
                                n += show(c.getSource(), profile.getId(), profile.getName());
                            }
                            return n;
                        })))));
    }

    private static int set(CommandContext<CommandSourceStack> c, boolean ally) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "jugador");
        int n = 0;
        for (GameProfile profile : profiles) {
            if (profile.getId() == null) continue;
            Allies.set(player, profile.getId(), profile.getName(), ally);
            n++;
        }
        return n;
    }

    private static int list(CommandSourceStack source, ServerPlayer player) {
        return show(source, player.getUUID(), player.getGameProfile().getName());
    }

    private static int show(CommandSourceStack source, UUID id, String name) {
        MinecraftServer server = source.getServer();
        AllyBook book = Allies.book(server);
        List<UUID> allies = book.alliesOf(id);
        List<UUID> by = book.alliedBy(id);
        source.sendSuccess(() -> Component.translatable("msg.rpgclasses.ally.list_header", name, allies.size())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("msg.rpgclasses.ally.list_allies",
                allies.isEmpty() ? Component.translatable("msg.rpgclasses.ally.none") : names(server, allies, book, id)), false);
        source.sendSuccess(() -> Component.translatable("msg.rpgclasses.ally.list_by",
                by.isEmpty() ? Component.translatable("msg.rpgclasses.ally.none") : names(server, by, null, null)), false);
        source.sendSuccess(() -> Component.translatable("msg.rpgclasses.ally.list_hint").withStyle(ChatFormatting.GRAY), false);
        return allies.size();
    }

    /** Nombres separados por comas; los aliados mutuos se marcan con ★. */
    private static Component names(MinecraftServer server, List<UUID> ids, AllyBook book, UUID owner) {
        StringBuilder sb = new StringBuilder();
        for (UUID id : ids) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Allies.nameOf(server, id));
            if (book != null && book.mutual(owner, id)) sb.append(" ★");
            if (server.getPlayerList().getPlayer(id) == null) sb.append(" (desc.)");
        }
        return Component.literal(sb.toString());
    }

    private AllyCommands() {}
}
