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
