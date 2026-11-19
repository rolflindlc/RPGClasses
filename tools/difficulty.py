# Dificultad por zona (adenda de dificultad por zona): datos del módulo io.github.rpgclasses.difficulty.
# Lo importa gen_data.py. Los jefes ya quedan fuera por tener más de 80 de vida base; este tag es la
# red de seguridad para jefes con menos vida y el sitio donde añadir mobs de otros mods.
import os

NS = "rites_and_paths"


def opt(i): return {"id": i, "required": False}


EXEMPT = [
    "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian",
    opt("mowziesmobs:ferrous_wroughtnaut"), opt("mowziesmobs:frostmaw"), opt("mowziesmobs:barako"),
    opt("mowziesmobs:umvuthi"), opt("mowziesmobs:sculptor"),
    opt("cataclysm:ender_golem"), opt("cataclysm:ender_guardian"), opt("cataclysm:netherite_monstrosity"),
    opt("cataclysm:ignis"), opt("cataclysm:the_harbinger"), opt("cataclysm:the_leviathan"),
    opt("cataclysm:ancient_remnant"), opt("cataclysm:scylla"), opt("cataclysm:maledictus"),
]


# Pociones de Tranquilidad y de Desafío (niveles I a IV) y efecto invisible de gigantismo (rasgo Coloso). El nombre del objeto es el mismo en todos los niveles.
LANG = {
    f"effect.{NS}.tranquility": "Tranquilidad",
    f"effect.{NS}.challenge": "Desafío",
    f"effect.{NS}.gigantism": "Gigantismo",
    "rites_and_paths.welcome.title": "¡Bienvenido a Rolflin's Rites & Paths!",
    "rites_and_paths.welcome.classes": "Elige una clase y una raza, sube de nivel y desbloquea ramas, habilidades y subclases. Abre el libro de clase con %s.",
    "rites_and_paths.welcome.difficulty": "Los enemigos se adaptan: según el nivel de los jugadores o la distancia al spawn, llevan mejor equipo, tienen más vida y daño, aparecen en mayor número y pueden ser de élite con rasgos especiales. Las pociones de Tranquilidad y de Desafío ajustan esa dificultad.",
    "rites_and_paths.welcome.remind": "Recuerda configurar la dificultad antes de empezar:",
    "rites_and_paths.welcome.link": "[Ajustes de dificultad]",
    "rites_and_paths.welcome.link.hover": "Haz clic para abrir los ajustes de dificultad",
    "rites_and_paths.difficulty.settings.title": "Ajustes de dificultad",
    "rites_and_paths.difficulty.settings.mode": "Modo:",
    "rites_and_paths.difficulty.settings.intensity": "Intensidad:",
    "rites_and_paths.difficulty.mode.players": "Jugadores",
    "rites_and_paths.difficulty.mode.distance": "Distancia",
    "rites_and_paths.difficulty.mode.mixed": "Mixto",
    "rites_and_paths.difficulty.mode.players.desc": "La dificultad depende del nivel de clase de los jugadores cercanos.",
    "rites_and_paths.difficulty.mode.distance.desc": "La dificultad solo crece cuanto más lejos del spawn del mundo estés.",
    "rites_and_paths.difficulty.mode.mixed.desc": "Media entre el nivel de los jugadores y la distancia al spawn.",
    "rites_and_paths.difficulty.intensity.off": "Apagado",
    "rites_and_paths.difficulty.intensity.easy": "Fácil",
    "rites_and_paths.difficulty.intensity.normal": "Normal",
    "rites_and_paths.difficulty.intensity.hard": "Difícil",
    "rites_and_paths.difficulty.intensity.off.desc": "El mod no aumenta la dificultad.",
    "rites_and_paths.difficulty.intensity.easy.desc": "Los incrementos de dificultad se reducen a la mitad.",
    "rites_and_paths.difficulty.intensity.normal.desc": "Los incrementos son los previstos por el mod.",
    "rites_and_paths.difficulty.intensity.hard.desc": "Los incrementos de dificultad se duplican.",
    "item.minecraft.potion.effect.tranquility": "Poción de Tranquilidad",
    "item.minecraft.splash_potion.effect.tranquility": "Poción arrojadiza de Tranquilidad",
    "item.minecraft.lingering_potion.effect.tranquility": "Poción persistente de Tranquilidad",
    "item.minecraft.tipped_arrow.effect.tranquility": "Flecha de Tranquilidad",
    "item.minecraft.potion.effect.challenge": "Poción de Desafío",
    "item.minecraft.splash_potion.effect.challenge": "Poción arrojadiza de Desafío",
    "item.minecraft.lingering_potion.effect.challenge": "Poción persistente de Desafío",
    "item.minecraft.tipped_arrow.effect.challenge": "Flecha de Desafío",
}


def build(dump, out_dir):
    data = os.path.join(out_dir, "data")
    dump(os.path.join(data, NS, "tags", "entity_types", "difficulty_exempt.json"), {"replace": False, "values": EXEMPT})
    return dict(LANG)
