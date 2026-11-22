# Descripciones para mods de consulta:
# * Efectos: Just Enough Effect Descriptions (JEED) lee "effect.<modid>.<id>.description".
# * Encantamientos: Enchantment Descriptions lee "enchantment.<modid>.<id>.desc" (ya están en spin.ENCHANTMENTS).
# * JEI: páginas de información de los objetos que no se fabrican (o que tienen una receta especial); las recetas
#   especiales se enseñan además como recetas de mesa de trabajo (compat/jei/RitesJeiPlugin.java).
NS = "rites_and_paths"

EFFECT_DESC = {
    "angelic_form": "Ángel (Clérigo): mientras dura puedes volar, despacio y sin correr.",
    "arcane_step": "Mago (Fluidez): +30 % de velocidad de movimiento tras lanzar un conjuro.",
    "banner": "Estás en el aura del Estandarte de un Paladín: más armadura y efectos negativos más cortos; según su estandarte, también más daño o regeneración para los aliados.",
    "berserker_rage": "Furia Berserker (Bárbaro): atacas más rápido y haces más daño, pero también recibes más.",
    "bulwark": "Baluarte (Paladín): recibes mucho menos daño de todas las fuentes.",
    "challenge": "Poción de Desafío: la dificultad por zona te cuenta un 20 % más de nivel de clase por nivel del efecto, así que los mobs que aparecen cerca son más fuertes. Se quita con leche.",
    "charging_bulwark": "Baluarte de Embestida (Paladín): corres sin parar a gran velocidad y dañas y empujas a quien arrolles; rompes bloques débiles a tu paso.",
    "charmed": "Hechizado (Bardo): no ataca al Bardo ni a sus aliados y pelea contra otros mobs.",
    "condemned": "Condenado (Paladín): recibe más daño del Paladín que lo marcó.",
    "corrosion": "Corrosión (Dracónido Ácido): −5 % de armadura y de dureza de armadura por nivel.",
    "downed": "Tumbado (Milagro del Clérigo): un golpe mortal te ha dejado en el suelo con medio corazón. No puedes hacer nada, pero no recibes daño; si un aliado te cura con un conjuro o una habilidad del Clérigo, te levantas. Si se acaba, mueres.",
    "endurance": "Aguante (Bárbaro): recibes menos daño.",
    "flesh_price": "Precio de la Carne (Druida Escultor): has pagado con sangre el ritual de una quimera; tu vida máxima baja un 30 % mientras dura.",
    "forced_dance": "Baile Forzado (Bardo): baila en el sitio y no puede atacar, lanzar conjuros ni usar objetos.",
    "gigantism": "Gigantismo (mobs de élite): la criatura es más grande; el nivel del efecto indica su tamaño.",
    "golden_spin": "Giro Dorado (Maestro del Giro): estás en pleno Rectángulo Dorado. No te puedes mover ni actuar, pero no recibes daño.",
    "hunters_mark": "Marca de Cazador (Ranger): el marcado recibe más daño de todas las fuentes.",
    "immortal_fury": "Última Furia (Bárbaro): un golpe mortal te ha vuelto inmortal unos segundos. Si matas a tu agresor antes de que acabe, sobrevives.",
    "iron_chorale": "Coral de Hierro (Bardo): recibes menos daño.",
    "mana_cadence": "Cadencia de Maná (Bardo): recuperas maná más rápido (un 1 % por nivel).",
    "march": "Marcha (Bardo): te mueves y atacas más rápido.",
    "plague": "Plaga (Bárbaro): daño con el tiempo que afecta incluso a los no-muertos. Se quita con leche.",
    "requiem_fervor": "Fervor del Réquiem (Bardo): haces más daño unos segundos tras el Réquiem Final.",
    "rest_ballad": "Balada de Reposo (Bardo): regeneras vida cada segundo.",
    "retreat": "Retirada (Ranger): +25 % de velocidad de movimiento.",
    "scale_ward_fire": "Escamas contra el fuego (Bardo): recibes un 25 % menos de daño de fuego.",
    "scale_ward_ice": "Escamas contra el hielo (Bardo): recibes un 25 % menos de daño de hielo.",
    "scale_ward_lightning": "Escamas contra el rayo (Bardo): recibes un 25 % menos de daño de rayo.",
    "shared_step": "Paso Compartido (Bardo): te mueves más rápido.",
    "silenced": "Silencio (Bardo): no puede lanzar conjuros.",
    "taunt_guard": "Provocación: recibes menos daño mientras los mobs provocados van a por ti.",
    "toxin": "Toxina (Pícaro): daño lento con el tiempo, que no se acumula. Se quita con leche.",
    "tranquility": "Poción de Tranquilidad: la dificultad por zona te cuenta un 20 % menos de nivel de clase por nivel del efecto, así que los mobs que aparecen cerca son más débiles. Se quita con leche.",
    "war_hymn": "Himno de Guerra (Bardo): haces más daño.",
}

JEI_INFO = {
    "sphere": "Bola del Maestro del Giro: se lanza desde la Bandolera de Esferas. En la mesa de trabajo, júntala con 4 unidades de un material (2 perlas de Ender) para darle una mejora: slime, cobre, amatista, perla de Ender, oro, redstone, estalactita, pedernal (Barrena) u obsidiana (Mazazo). Una mejora nueva sustituye a la anterior y la bola conserva su durabilidad y sus encantamientos.",
    "sphere_bandolier": "Guarda hasta 9 bolas: clic derecho con una bola en el cursor para meterla, clic derecho sobre un hueco vacío para sacar una. El Maestro del Giro la lleva en la mano para lanzar las bolas; la tecla de cambiar de bola elige cuál.",
    "sheet_music": "Partitura del Bardo. Se hace en la forja de pergaminos de Iron's con un conjuro musical (como un pergamino, con un disco de música, un bloque musical o una gramola como foco). Se guarda en un instrumento en el atril o en la mesa de inscripción, y sube un nivel en la mesa de trabajo con la tinta de la rareza que pida.",
    "eternal_sun_lute": "Instrumento legendario del Bardo: lo sueltan Barako y Umvuthi (Mowzie's Mobs) al morir a manos de un jugador.",
    "abyssal_reed": "Lo suelta el Leviatán (L_Ender's Cataclysm) al morir a manos de un jugador. Con ella se hace el Órgano del Abismo en la mesa de herrería.",
    "colossus_drumskin": "Lo suelta la Monstruosidad de Netherita (L_Ender's Cataclysm) al morir a manos de un jugador. Con él se hace el Tambor del Coloso en la mesa de herrería.",
    "study_book": "Druida: mantén clic derecho apuntando a un animal o una bestia viva para estudiarlo; si recibes daño, el estudio vuelve a empezar. Al terminar se gasta y ganas conocimiento de su especie. Cada criatura solo se estudia una vez.",
    "flesh_vessel": "Druida Escultor: clic derecho para abrirlo y meter cuatro partes de bestia (cabeza, torso, patas e instinto). Con las cuatro, tíralo sobre un Altar de Carne y haz clic derecho en el altar para crear una quimera.",
    "flesh_altar": "Druida Escultor: tira encima un Recipiente de Carne completo y haz clic derecho para el ritual de creación. Cuesta mucho maná y te baja la vida máxima un rato.",
    "beast_head": "Parte de bestia del Druida Escultor: se consigue eligiéndola tras el ritual del Druida sobre los restos de una especie que ya conoce. Va en el Recipiente de Carne.",
    "creative_amulet": "Solo en creativo, para pruebas: mientras lo lleves en el inventario, todos los enfriamientos de clase y de raza son 0.",
}


def lang():
    out = {f"effect.{NS}.{k}.description": v for k, v in EFFECT_DESC.items()}
    out.update({f"jei.{NS}.info.{k}": v for k, v in JEI_INFO.items()})
    return out
