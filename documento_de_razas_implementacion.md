# Razas: estado de implementación (fases R1 y R2)

Acompaña a *Documento de Diseño: Razas* (anexo, rev. 0.6). Describe qué hay programado en el mod, cómo probarlo y qué falta. Fecha: 2026-11-13 (10.º relevo, con los ajustes de balance del diseño v2 rev. 2.13).

**Cambio de diseño de esta fase (decidido por el usuario):** la invisibilidad **ya no se revela por cercanía a otro jugador**, ni la del Sigilo del Pícaro (documento v2, rev. 2.8) ni la de la Nube de Humo del Elfo Oscuro. Esto sustituye a la propuesta del anexo ("en PvP se revela a menos de 1 bloque"). El revelado "al hacer daño" de la Nube tampoco está programado: mientras dura, la nube vuelve a ocultar a quien esté dentro.

## 1. Qué hay hecho

* **Sistema:** 8 razas × 3 subrazas en el datapack (`data/rpgclasses/rpgclasses/races/*.json`), con todos los valores por Rango. Se pueden cambiar con un datapack y `/reload`.
* **Elección:** al entrar, pantallas **Raza → Subraza → Clase**. La raza y la subraza son permanentes y sobreviven al reinicio de clase. Los jugadores que ya tenían clase eligen raza al entrar. Mientras eligen no reciben daño.
* **Rangos 0–3:** guardados por jugador; suben con los **ritos** (también se pueden fijar con un comando de administrador).
* **Ritos (fase R2b):** los 72 ritos del anexo (3 por subraza, en orden). Las hazañas cuentan solas; las ofrendas y construcciones se entregan en el **Altar de Raza** (clic derecho), y los jefes de rito se invocan en él (**agachado + clic derecho**). `/raza ritos` muestra el rito en curso.
* **Altar de Raza:** receta `vela · libro · vela / oro · diamante · oro / 3 ladrillos de piedra`. Aparece en la pestaña de bloques funcionales.
* **Jefes de rito:** uno al azar del tag `rpgclasses:rite_bosses` (jefes medianos de Cataclysm y Mowzie's si están instalados); si no hay ninguno, un devastador o un evocador con el doble de vida. Solo cuentan para quien los invoca, que debe hacerles al menos el 25 % del daño. Desaparecen si su dueño muere, se desconecta, se aleja más de 64 bloques durante 30 s o pasan 10 minutos; 1 minuto de espera entre invocaciones.
* **Topes y suelos:** la vida y la velocidad raciales suman con las de clase (tope +100 %); suelos de −50 % de vida (10 HP) y −25 % de velocidad.
* **Tamaño** (Orco +10 %, Enano −25 %, Goblin −50 %): implementación propia, sin librería. Cambia modelo, hitbox (el mixin `PlayerSizeMixin` escala `getDimensions` en **todas las poses**, para que quepan huecos de 1 bloque y las paredes no den problemas) y altura de ojos.
* **Interfaz:** los menús de clase y raza usan **retratos** (tus iconos a resolución completa y con fondo) para clases, subclases, razas y subrazas, y **texturas propias** de panel, cabecera y botones (`textures/gui/ui/`, generadas con `tools/art/make_ui.py`; se pueden sustituir por otras). Los estados (efectos) tienen su icono en `textures/mob_effect/`.
* **Cambio de raza:** solo por administrador (`/clases raza quitar|set`); el jugador no puede cambiarla.
* **Supervivencia:** habilidades de clase → Última Resistencia del Orco → Tótem.

### Comandos y teclas

| Qué | Cómo |
| ----- | ----- |
| Ver la raza propia | `/raza info`, o botón **Raza** en el libro de clases (K) |
| Elegir por comando | `/raza elegir <raza> <subraza>`; muro del Humano: `/raza muro <disciplina>` |
| Admin: fijar raza | `/clases raza set <jugador> <raza> <subraza> [rango]` |
| Admin: fijar rango | `/clases raza rango <jugador> <0-3>` |
| Admin: quitar raza | `/clases raza quitar <jugador>` (vuelve a salir la pantalla) |
| Libro de Vínculos (Maestro de Bestias) | objeto propio (no tiene tecla): receta libro + hueso + correa; el kit lo da al coger la subclase |
| Habilidades raciales | teclas **V** y **B**; agachado + V es una tercera ranura |

Ids de raza: `rpgclasses:human`, `orc`, `dwarf`, `elf`, `goblin`, `triton`, `infernal`, `undead`.

### Razas y subrazas

| Raza | Tronco (Rango 0) | Subrazas | Pendiente |
| ----- | ----- | ----- | ----- |
| **Humano** | Adaptable: muro elegido y rasgos base negativos de clase | Polivalente (`polyvalent`), Especialista (`specialist`), Autodidacta (`autodidact`) | — |
| **Orco** | +10 % tamaño y vida, Última Resistencia, +25 % hambre | Pétreo (`stoneskin`), Devorador (`devourer`), Caudillo (`warlord`, V) | — |
| **Enano** | −25 % tamaño, −10 % velocidad, Minero, Fortuna racial | Profundidades (`deep`, V desde R1), Herrero (`smith`), Demoledor (`demolisher`: TNT inofensiva y +25 % de radio, Vetas, Túnel) | — |
| **Elfo** | Atraviesa y trepa hojas; sin carne ni pescado | Oscuro (`dark`, V desde R1), Arcano (`arcane`), Silvano (`sylvan`) | — |
| **Goblin** | −50 % tamaño, −25 % vida, Suerte +1, Saqueo racial | Jinete de Lobo (`wolf_rider`), Ladrón (`thief`, Mano Larga con 2 s de enfriamiento), Artificiero (`trapper`, V: Cepo) | — |
| **Tritón** | Respira y pica bajo el agua, +50 % nado, visión subacuática, +30 % daño de fuego | Pescador (`fisher`), Moldeador (`shaper`: V, B y agachado + V), Abisal (`abyssal`) | Moldeador: sustituir sus habilidades por conjuros de *T.O Magic 'n Extras* (R0 Hydroshot nv1, R1 Overflow nv3, R2 Jetstream nv1, R3 Tidal Grasp nv1); faltan los ids |
| **Infernal** | Inmune al fuego, conjuros raciales con muro de Magia (con Iron's: Fire Bolt, **Magma Bomb** y **Raise Hell**), golems hostiles, aldeanos +50 % | Nativo (`native`, V en R3), Fundidor (`smelter`, V en R3), Pirómano (`pyromaniac`, con Iron's) | — |
| **No-muerto** | Sol, respiración, hambre fija, regeneración que **tarda el doble en empezar** (pausa de 6 s; Esqueleto 6/6/6/3 s), sin curación de pociones ni conjuros | Vampiro (`vampire`), Esqueleto (`skeleton`), Espectro (`specter`, V desde R1) | — |

**Fase R2 (hecha):** las **24 subrazas** con todos sus rasgos y sus **72 ritos**.

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
* **Conjuros raciales:** aparecen al final de la rueda de Iron's; gastan maná y enfriamiento normales. **Desde la rev. 2.13 sufren el muro de Magia de la clase** (decisión del usuario), igual que los del Moldeador cuando estén (escuela de **agua** de *T.O Magic 'n Extras*).
* **Infernal y aldeanos:** el recargo se aplica a aldeanos, no al comerciante errante.
* **Herrero:** la durabilidad extra va marcada en el objeto (se ve en la descripción) y viaja con él. Cuenta lo que fabricas (mesa y herrería) y lo que reparas o combinas en el yunque; Irrompibilidad +1 solo en lo fabricado. El descuento del yunque (−50 %) es de niveles y no salva el "Demasiado caro".
* **Demoledor:** su TNT se repite con radio 5 (vanilla 4): también alcanza un poco más a las entidades. Túnel: agachado, solo en bloques del tag `rpgclasses:dwarf_mineable`, frente a la cara que miras; +0,1 de agotamiento por bloque extra.
* **Elfo Arcano:** en la mesa se devuelve el porcentaje de la experiencia gastada; en el yunque se reducen los niveles. No se suma al descuento del Herrero (cuenta el mayor).
* **Jinete de Lobo:** clic derecho con la mano vacía en tu lobo adulto para montarlo; agachado conserva el "sentar" de vanilla. El lobo se mueve con la velocidad del lobo (sin tope del jugador). **Salto cargado como el del caballo:** mantén saltar hasta 1 s (mínimo 35 %), con factor de salto 1,6 y una barra propia (`WolfJumpGauge`) en lugar de la de vanilla. R1: +25 % de vida a tus lobos cercanos (48 bloques). R2: el lobo **montado** muerde a lo que golpeas (1 s entre mordiscos); sin montar ya lo hacen los lobos vanilla.
* **Pescador:** cada espera hasta que pica un pez se acorta un 40 %.
* **Moldeador:** las 2 cargas de Ola tienen enfriamientos independientes. **Pendiente de cambio** (decidido): sus habilidades pasarán a ser conjuros de *T.O Magic 'n Extras*; faltan los ids exactos (F3+H sobre el pergamino).
* **Artificiero (Cepo):** **sin tiempo máximo** (no caduca) y más visible: partículas verdes solo para su dueño cada 4 ticks. Daño **2/4/6/8** por rango 0–3, tratado como vida (HP) y como daño de jugador.
* **Ladrón (Mano Larga):** 2 s de enfriamiento.
* **Nativo:** 25 % de que un trueque con un piglin salga doble (cuenta el Nativo a 10 bloques o menos del piglin).
* **Fundidor:** +50 % de velocidad de cocción en hornos, altos hornos y ahumadores a 8 bloques, sin gastar más combustible; dos fundidores no lo duplican.
* **Ritos, interpretaciones:** "enemigo" = mob hostil, jefe de rito o jugador enemigo; "bajo tierra" = sin cielo encima; luz medida en el enemigo; "sobre lava" = en lava o encima de ella; el arma "fabricada/encantada por ti" va marcada en el objeto (solo desde que eres Herrero o Arcano). Muro Armadura = llevar 3+ piezas; muro Escudos = 10 bloqueos en los últimos 30 s (o en el combate contra el jefe). "Mapa completamente explorado" = 90 % pintado. "Equipo de mob" (Ladrón 3) = equipo robado o soltado por mobs que mataste siendo Ladrón (Manos Vacías llega en el Rango 3, después de este rito). Las 16 TNT del Demoledor deben fabricarse mientras haces ese rito. Abisal 3: también vale matar a cualquier guardián ancestral.
* **Rasgo de combate de las subrazas de oficio (Rango 2, rev. 2.13):** Autodidacta +15 % de experiencia de los enemigos; Herrero +0,5 de dureza por pieza de armadura fabricada por él; Silvano Regeneración I en biomas de bosque; Pescador +25 % de daño con tridente (cuerpo a cuerpo o lanzado); Fundidor prende 2 s con los golpes cuerpo a cuerpo.
* **Visuales de razas (rev. 2.13):** chispas de piedra en los golpes que amortigua Piel Gruesa (Orco Pétreo) y rastro de sangre en la Sed del Vampiro.
* **Infernal (conjuros raciales):** la racial que era bola de fuego pasa a **Magma Bomb** y la que era Magma Bomb pasa a **Raise Hell**.
* **Pirómano:** solo los conjuros raciales (ranura racial de la rueda). Los charcos de llamas (R2) y de lava (R3) ahora salen de la **Bomba de magma**; Raise Hell no deja charco. Son **zonas de efecto** (prenden a los enemigos; la lava además da Lentitud II, menos a jefes), **sin colocar bloques** de fuego ni de lava para no quemar construcciones.
* **Libro de Vínculos (Maestro de Bestias):** objeto propio con la imagen (huevo de aparición de la especie), el nombre y las estadísticas de cada mascota vinculada, y permite elegir a dónde se teletransportan al morir (**3 puntos guardados + punto de aparición**; la Salvaguarda lo usa). Máximo **8 vínculos**; radio de **16 bloques** para vincular o romper; receta libro + hueso + correa. Entra en el kit de la subclase (se da al elegirla y se retira al perderla). Los vínculos antiguos migran al verse. Las mascotas no cargadas no se pueden romper desde el libro.

## 3. Qué falta

1. Tamaño con **Epic Fight**: el modelo de Epic Fight puede no escalarse **[MEDIR]**.
   Goblin montado en lobo no puede atacar con Epic Fight (pedir log).
2. Calibrar cantidades de ritos y valores **[MEDIR]**.
3. Integrar el anexo en el documento v2 y actualizar el documento de arquitectura.
4. Moldeador: conjuros de *T.O Magic 'n Extras* (ids pendientes).
5. Traducción `en_us` (los textos nuevos solo están en `es_es`), README y publicación en CurseForge.
6. HUD de Conjuros Gratuitos y enfriamientos raciales; cooldown del escudo por muro (aparcado); ballestas en Conservación.
7. Opcional: texturas propias para los botones estándar de los menús (hoy solo usan las propias los paneles, las cabeceras y las tarjetas con retrato).

## 4. Qué probar

Nada de esto se ha probado en el juego. Perfil: Forge 1.20.1 (47.4.x) + Iron's Spells 3.16.3 + Epic Fight 20.15.x + el jar del último run verde; Cataclysm o Mowzie's opcionales (jefes de rito). Varias pruebas necesitan un segundo jugador (LAN). Si algo falla: `/clases debug volcar`, `logs/rpgclasses-debug.log` y, si hay, el crash-report.

**A. Arranque.** El juego abre y entra al mundo sin errores de Mixin en el log (hay 14 mixins). Servidor y cliente con el mismo jar (protocolo de red 11).

**B. Elección.** Mundo nuevo: Raza → Subraza → Clase. Personaje que ya tenía clase: le pide la raza. Las 24 subrazas se pueden elegir.

**C. Razas (tronco).** Tamaño de Goblin, Enano y Orco (hitbox, cámara, huecos de 1 bloque, con y sin Epic Fight). Elfo en copas de árbol (atravesar, subir, bajar, sin expulsión por "volar"). No-muerto: sol, hambre fija, regeneración que se corta, poción de curación que no cura. Humano: elegir muro y verlo en `/clases debug stats`. Orco: Última Resistencia con 1 s de inmortalidad. Infernal: conjuros raciales en la rueda de Iron's (Magma Bomb, Raise Hell). Goblin: tamaño por paredes y huecos de 1 bloque. No-muerto: la regeneración tarda el doble en empezar.

**D. Subrazas** (subir rango con `/clases raza rango <jugador> <0-3>`; habilidades con V, B y agachado + V). Las de la fase R1 y además:
1. Herrero: fabricar y reparar (durabilidad en la descripción), yunque a mitad, Irrompibilidad +1 en Rango 3.
2. Demoledor: TNT propia (radio mayor, sin daño), Vetas (Rango 1), Túnel agachado (Rango 3).
3. Elfo Arcano: experiencia devuelta al encantar en la mesa, yunque más barato, Fusión Arcana (Rango 3).
4. Jinete de Lobo: montar (clic derecho con mano vacía), moverse, saltar, bajarse agachado; vida extra (R1), mordisco (R2), regeneración (R3). Probar también con Epic Fight.
5. Pescador: picos más rápidos. Moldeador: 2 cargas de Ola en Rango 3.
6. Nativo: trueques dobles. Fundidor: hornos más rápidos cerca.
7. Pirómano (con Iron's): Disparo de fuego racial que atraviesa; charco de llamas (R2) y de lava (R3) desde la Bomba de magma.
8. Artificiero: el Cepo no caduca, se ve con partículas verdes solo para su dueño y daña 2/4/6/8 por rango. Ladrón: Mano Larga cada 2 s.
9. Jinete de Lobo: salto cargado con barra propia (mantener saltar hasta 1 s).

**E. Ritos.**
1. Fabricar el Altar de Raza; clic derecho muestra el rito; `/raza ritos` también.
2. Una hazaña de cada tipo: derrotas (p. ej. Orco Pétreo: golpes + enemigos), variedad (Autodidacta), estilos (Polivalente), robos (Ladrón), trueques (Nativo). La barra de acción muestra el progreso.
3. Una ofrenda (consume solo si está todo) y una construcción (estanterías del Autodidacta o campamento del Caudillo).
4. Un jefe de rito: invocar agachado, que solo cuente para ti, que falle si no cumples la condición, que desaparezca si mueres o te alejas, y la espera de 1 minuto.
5. Completar un rito: sube el rango, se aplican los rasgos nuevos y el progreso se conserva al salir y entrar.

**F. Aliados** (dos jugadores, tecla J o `/aliados`): sin marcar, las áreas, trampas y conjuros de uno dañan al otro; tras marcar, no. Estandarte, Baluarte Sanador, curas de Iron's y Voz del Caudillo solo alcanzan a aliados. Las mascotas de un aliado no te atacan. La Nube de Humo solo oculta al Elfo y a sus aliados.

**G. Apuesta con la Muerte** (Bárbaro Indestructible): moneda girando sobre la cabeza (en primera persona, arriba en la pantalla), inmóvil e inmortal mientras gira, cara ganadora en grande; anj = 1 s inmortal; calavera = muere (o le salva el Orco o el Tótem).

**H. Clases sin probar de antes.** Cambios 2.7f y 2.7g, Estandarte, subclases del Pícaro, Ranger y Monje, compatibilidad con Epic Fight, Sigilo del Pícaro en PvP (ya no se revela por cercanía).

**I. Libro de Vínculos y menús.**
1. Maestro de Bestias: al coger la subclase aparece el libro en el kit (se retira al perderla). Receta libro + hueso + correa.
2. Vincular y romper a menos de 16 bloques; máximo 8; el libro muestra imagen, nombre y estadísticas; elegir destino al morir (3 puntos + aparición).
3. Menús de clase y raza: rejilla de tarjetas con retrato, cabecera con retrato grande (compacta a 24 px si la pantalla mide menos de 330 de alto), pestañas de subraza y botones de subclase con retrato; paneles, cabeceras y tarjetas con las texturas nuevas. Comprobar legibilidad en tamaños de interfaz 1 a 4.
4. Iconos de estados (efectos) en la barra y en el inventario.

**K. Aturdimiento de Epic Fight (diseño v2, rev. 2.12).** Con Epic Fight, Embestida (y Carga Demoledora), Puño de Inhabilitación y el efecto de Piedra del Monje ponen al objetivo en el aturdimiento real de Epic Fight (animación de golpe y sin poder actuar) durante el tiempo de la habilidad; sin Epic Fight, lentitud y debilidad fuertes. En `logs/rpgclasses-debug.log` sale "aturdimiento … aplicado" o "sin animación" (entonces usa el simulado).

**J. Monje rev. 2.9 (cargas de postura).** Protocolo de red 11.
1. Suelo (*Cuerpo Marcial*): con `/clases debug stats`, armadura 10 / dureza 4 y +4 de daño desarmado con 30 puntos de base (la mitad con 15); nada con armadura puesta.
2. Tecla de posturas: Agua → Piedra → Fuego, 2 s de espera. Fila de símbolos encima de la vida (y de la armadura), vacíos hasta el máximo.
3. Ganar cargas: Fuego golpeando; Piedra recibiendo golpes de 2+; Agua corriendo (cada 2 s), esquivando con Epic Fight (+3) o desviando (+3). Comprobar que se pierden al parar.
4. Efecto propio: al llegar al máximo o con Shift + golpe (o agachado + tecla de posturas) desde 10 cargas. Golpe Ígneo (más daño, rompe armadura, quema), aturdimiento (Piedra) y Contracorriente (lentitud y esquiva del siguiente golpe).
5. Descarga al cambiar: onda de fuego, absorción de Piedra (amarillo en la vida, se va a los 5 s), lentitud y empuje de Agua.
6. Puños: partículas de fuego, agua o piedra en las manos vacías, más fuertes cuantas más cargas; las ve también otro jugador.
7. Perks nuevos (Llama Constante, Ignición Temprana, Eco de Piedra, Postura Dominante, Flujo Continuo) y Meditación (4 cargas al terminar). Un Monje que tuviera Flujo, Onda Elemental, Híbrida o Calma Interior debe elegir perk de nuevo.

**L. Balance y visuales (diseño v2, rev. 2.13).**
1. Explorador Ágil +5 % de velocidad; mascotas del Maestro de Bestias +30 %; Puño de Inhabilitación 15 %; Escudo Arcano hasta 12 %.
2. Visuales: Provocación (anillos y mobs brillando), Baluarte (escudo girando), anillo dorado del Estandarte, aura roja de Furia Berserker, estela de Embestida, Sed de Sangre, gotas verdes de la Toxina, Piel Gruesa y Sed del Vampiro.
3. Mago: filas "Gratis N" con puntos de carga encima de la lista de habilidades (abajo a la derecha) al fijar conjuros gratuitos.
4. Infernal: con un Bárbaro o un Monje, sus conjuros raciales hacen menos daño por el muro de Magia.
5. Subrazas de oficio en Rango 2: experiencia extra (Autodidacta), dureza con armadura propia (Herrero), regeneración en bosque (Silvano), tridente (Pescador) y golpes que queman (Fundidor).

**M. Subclases nuevas (diseño v2, rev. 2.14).**
1. Mago de Batalla: lanza un conjuro y golpea en menos de 5 s (fuego quema, hielo ralentiza, rayo salta, sangre cura); tras 3 golpes seguidos sale el aviso "Eco Marcial" y el siguiente conjuro cuesta menos.
2. Palma Pesada: agachado con la mano vacía 1 s (suena), golpe fuerte; lanzar enemigos contra una pared u otro enemigo los aturde (explosión de partículas y yunque).
3. Juggernaut: estela de viento al embestir; atravesar hojas, cristal, puertas y vallas rompiéndolos (cada uno con su sonido) y recibir algo de daño. No debe romper nada en zonas protegidas ni en aventura.

**N. Las 10 escuelas (rev. 2.15).**
1. Elementalista: carga hasta detonar con conjuros de ender (el objetivo se teletransporta), invocación (anillo de colmillos), naturaleza (raíces y veneno), eldritch (debilidad, oscuridad y ceguera) y, con T.O Magic 'n Extras, agua (ola que empuja y moja). El aviso de cargas muestra el nombre de la escuela.
2. Mago de Batalla: conjuro + golpe con ender (apareces a la espalda), invocación (colmillos), eldritch y agua.
3. Arrancar sin T.O Magic 'n Extras: sin errores en el registro y las demás escuelas igual.
