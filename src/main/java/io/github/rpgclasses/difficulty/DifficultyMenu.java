package io.github.rpgclasses.difficulty;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.rpgclasses.difficulty.DifficultySettings.Intensity;
import io.github.rpgclasses.difficulty.DifficultySettings.Mode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Ajustes de dificultad dentro de la partida: un menú de chat con botones en los que se hace clic
 * ({@code /clases dificultad ajustes}) y los subcomandos {@code modo} e {@code intensidad} que esos botones ejecutan.
 */
public final class DifficultyMenu {
    public static final String COMMAND = "/clases dificultad ajustes";
    private static final String P = "rites_and_paths.difficulty.";

    private DifficultyMenu() {}

    /** Rama {@code dificultad} de /clases: sin argumentos, el diagnóstico; con ellos, los ajustes. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dificultad").executes(c -> DifficultyInfo.show(c.getSource()));
        root.then(Commands.literal("ajustes").requires(DifficultySettings::canConfigure).executes(c -> show(c.getSource())));
        LiteralArgumentBuilder<CommandSourceStack> mode = Commands.literal("modo").requires(DifficultySettings::canConfigure);
        for (Mode m : Mode.values()) {
            mode.then(Commands.literal(m.literal).executes(c -> {
                DifficultySettings.setMode(m);
                return show(c.getSource());
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> intensity = Commands.literal("intensidad").requires(DifficultySettings::canConfigure);
        for (Intensity i : Intensity.values()) {
            intensity.then(Commands.literal(i.literal).executes(c -> {
                DifficultySettings.setIntensity(i);
                return show(c.getSource());
            }));
        }
        return root.then(mode).then(intensity);
    }

    public static int show(CommandSourceStack source) {
        Mode mode = DifficultySettings.mode();
        Intensity intensity = DifficultySettings.intensity();
        send(source, Component.translatable(P + "settings.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        MutableComponent modes = Component.translatable(P + "settings.mode").withStyle(ChatFormatting.WHITE);
        for (Mode m : Mode.values()) {
            modes.append(" ").append(button(P + "mode." + m.id, P + "mode." + m.id + ".desc", "/clases dificultad modo " + m.literal, m == mode));
        }
        send(source, modes);
        send(source, Component.translatable(P + "mode." + mode.id + ".desc").withStyle(ChatFormatting.GRAY));

        MutableComponent intensities = Component.translatable(P + "settings.intensity").withStyle(ChatFormatting.WHITE);
        for (Intensity i : Intensity.values()) {
            intensities.append(" ").append(button(P + "intensity." + i.id, P + "intensity." + i.id + ".desc",
                    "/clases dificultad intensidad " + i.literal, i == intensity));
        }
        send(source, intensities);
        send(source, Component.translatable(P + "intensity." + intensity.id + ".desc").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /** Botón de chat: el ajuste actual va en verde y no hace nada; los demás son azules y lo cambian al hacer clic. */
    private static MutableComponent button(String labelKey, String descKey, String command, boolean selected) {
        MutableComponent text = Component.literal("[").append(Component.translatable(labelKey)).append("]");
        Component hover = Component.translatable(descKey);
        if (selected) return text.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        return text.withStyle(ChatFormatting.AQUA).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }
}
