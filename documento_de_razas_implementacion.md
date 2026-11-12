# Razas: estado de implementación (fase R1)

Acompaña a *Documento de Diseño: Razas* (anexo, rev. 0.5). Describe qué hay programado en el mod, cómo probarlo y qué falta. Fecha: 2026-11-12.

**Cambio de diseño de esta fase (decidido por el usuario):** la invisibilidad **ya no se revela por cercanía a otro jugador**, ni la del Sigilo del Pícaro (documento v2, rev. 2.8) ni la de la Nube de Humo del Elfo Oscuro. Esto sustituye a la propuesta del anexo ("en PvP se revela a menos de 1 bloque"). El revelado "al hacer daño" de la Nube tampoco está programado: mientras dura, la nube vuelve a ocultar a quien esté dentro.

## 1. Qué hay hecho

* **Sistema:** 8 razas × 3 subrazas en el datapack (`data/rpgclasses/rpgclasses/races/*.json`), con todos los valores por Rango. Se pueden cambiar con un datapack y `/reload`.
* **Elección:** al entrar, pantallas **Raza → Subraza → Clase**. La raza y la subraza son permanentes y sobreviven al reinicio de clase. Los jugadores que ya tenían clase eligen raza al entrar. Mientras eligen no reciben daño.
* **Rangos 0–3:** guardados por jugador. **Los ritos aún no existen**: de momento el rango se fija con un comando de administrador.
* **Topes y suelos:** la vida y la velocidad raciales suman con las de clase (tope +100 %); suelos de −50 % de vida (10 HP) y −25 % de velocidad.
* **Tamaño** (Orco +10 %, Enano −25 %, Goblin −50 %): implementación propia, sin librería. Cambia modelo, hitbox y altura de ojos.
* **Supervivencia:** habilidades de clase → Última Resistencia del Orco → Tótem.

### Comandos y teclas

| Qué | Cómo |
| ----- | ----- |
| Ver la raza propia | `/raza info`, o botón **Raza** en el libro de clases (K) |
| Elegir por comando | `/raza elegir <raza> <subraza>`; muro del Humano: `/raza muro <disciplina>` |
| Admin: fijar raza | `/clases raza set <jugador> <raza> <subraza> [rango]` |
| Admin: fijar rango | `/clases raza rango <jugador> <0-3>` |
| Admin: quitar raza | `/clases raza quitar <jugador>` (vuelve a salir la pantalla) |
| Habilidades raciales | teclas **V** y **B**; agachado + V es una tercera ranura |

Ids de raza: `rpgclasses:human`, `orc`, `dwarf`, `elf`, `goblin`, `triton`, `infernal`, `undead`.

### Razas y subrazas

| Raza | Tronco (Rango 0) | Subrazas hechas | Sin hacer |
| ----- | ----- | ----- | ----- |
| **Humano** | Adaptable: muro elegido y rasgos base negativos de clase | Polivalente (`polyvalent`), Especialista (`specialist`), Autodidacta (`autodidact`) | — |
| **Orco** | +10 % tamaño y vida, Última Resistencia, +25 % hambre | Pétreo (`stoneskin`), Devorador (`devourer`), Caudillo (`warlord`, V) | — |
| **Enano** | −25 % tamaño, −10 % velocidad, Minero, Fortuna racial | Profundidades (`deep`, V desde R1), Demoledor (`demolisher`: TNT propia inofensiva y Vetas) | Herrero (`smith`); del Demoledor: +25 % de radio de TNT y Túnel de R3 |
| **Elfo** | Atraviesa y trepa hojas; sin carne ni pescado | Oscuro (`dark`, V desde R1), Arcano (`arcane`), Silvano (`sylvan`) | Del Arcano: el descuento de XP al encantar |
| **Goblin** | −50 % tamaño, −25 % vida, Suerte +1, Saqueo racial | Ladrón (`thief`), Artificiero (`trapper`, V) | Jinete de Lobo (`wolf_rider`) |
| **Tritón** | Respira y pica bajo el agua, +50 % nado, visión subacuática, +30 % daño de fuego | Pescador (`fisher`), Moldeador (`shaper`: V, B y agachado + V), Abisal (`abyssal`) | Del Pescador: los picos un 40 % más rápidos. Del Moldeador: las 2 cargas de Ola en R3 |
| **Infernal** | Inmune al fuego, conjuros raciales (con Iron's), golems hostiles, aldeanos +50 % | Nativo (`native`, V en R3), Fundidor (`smelter`, V en R3) | Pirómano (`pyromaniac`). Del Nativo: +25 % en trueques. Del Fundidor: hornos cercanos más rápidos |
| **No-muerto** | Sol, respiración, hambre fija, regeneración con corte, sin curación de pociones ni conjuros | Vampiro (`vampire`), Esqueleto (`skeleton`), Espectro (`specter`, V desde R1) | — |

Las tres subrazas sin hacer (Herrero, Jinete de Lobo, Pirómano) **salen en la pantalla pero no se pueden elegir**, para que nadie quede atado a una subraza vacía.

## 2. Decisiones que he tomado (pendientes de tu confirmación)

* **Jugadores existentes:** eligen raza al entrar, aunque ya tengan clase.
* **Última Resistencia del Orco:** tras sobrevivir con medio corazón, **1 s** de inmortalidad (antes 2 s; cambio pedido por el usuario).
* **Voz del Caudillo:** purga al Caudillo, a los jugadores que tiene como aliados y a las mascotas e invocaciones de todos ellos (sistema de aliados, regla 15 del documento v2).
* **Nube de Humo y aliados:** la invisibilidad solo se aplica al Elfo y a sus aliados (y sus mascotas); los enemigos dentro no se vuelven invisibles y, desde R3, se ralentizan. Sustituye a "todo lo que hay dentro es invisible" del anexo.
* **Ladrón y Escolta:** Mano Larga no roba a quien el Goblin tiene como aliado; los guardianes del Abisal no atacan a mascotas ni invocaciones de aliados.
* **Fusión Arcana:** cuesta 30 niveles y 1 objeto; solo sube los encantamientos que están a nivel máximo en los dos objetos.
* **Fortuna racial:** solo en bloques del tag `forge:ores`. **Minero:** tag `rpgclasses:dwarf_mineable`.
* **Carne y pescado:** tags `rpgclasses:meat` y `rpgclasses:elf_forbidden_food`. El perk *Carnívoro* del Bárbaro ya cuenta el pescado y **no se puede elegir siendo Elfo**.
* **Mano Larga:** solo golpes cuerpo a cuerpo; no roba a mobs de spawner ni a jefes; el objeto robado solo lo puede recoger el Goblin.
* **Espectro:** la caída lenta se desactiva al agacharse.
* **Sol del No-muerto:** 1 HP por segundo con un tipo de daño propio que ignora la armadura.
* **Conjuros raciales:** aparecen al final de la rueda de Iron's; gastan maná y enfriamiento normales. El muro de Magia se compensa en el daño.
* **Infernal y aldeanos:** el recargo se aplica a aldeanos, no al comerciante errante.

## 3. Qué falta (fase R2)

1. **Ritos y Altar de Raza** (24 líneas de ritos, jefes de rito, ofrendas). Es el bloque más grande.
2. Subrazas **Herrero**, **Jinete de Lobo** y **Pirómano**, y los rasgos sueltos de la tabla.
3. Tamaño con **Epic Fight**: el modelo de Epic Fight puede no escalarse **[MEDIR]**.
4. Integrar el anexo en el documento v2 y actualizar el documento de arquitectura.

## 4. Qué probar primero

Nada de esto se ha probado en el juego: compila y pasa las pruebas, pero es la primera vez que corre.

1. Entrar con un mundo nuevo: pantallas Raza → Subraza → Clase. Entrar con un personaje que ya tenía clase.
2. Goblin y Enano: hitbox, cámara, huecos de 1 bloque. Con Epic Fight instalado, ver si el modelo se escala.
3. Elfo: atravesar copas de árboles, subir y bajar, y que el servidor no expulse por "volar".
4. No-muerto: sol, hambre fija, regeneración y que una poción de curación no cure.
5. Humano: elegir muro tras la clase y comprobar el muro con `/clases debug stats`.
6. Subir el rango con `/clases raza rango` y probar las teclas V y B.
7. Pícaro en PvP: el Sigilo ya no se corta al acercarse a otro jugador.
9. **Apuesta con la Muerte** (Bárbaro Indestructible, `/xp` hasta la subclase): moneda girando sobre la cabeza, inmóvil e inmortal mientras gira, resultado en grande; con el anj, 1 s inmortal. En primera persona, la moneda sale arriba en la pantalla.
8. **Aliados** (dos jugadores, tecla J o `/aliados`): sin marcar, las áreas, trampas y conjuros de uno dañan al otro; tras marcar, no. Estandarte, Baluarte Sanador, curas de Iron's y Voz del Caudillo solo alcanzan a aliados. Las mascotas de un aliado no te atacan. La Nube de Humo solo oculta al Elfo y a sus aliados.

Si algo falla: `/clases debug volcar` (ahora incluye raza, subraza y rango) y `logs/rpgclasses-debug.log`.
