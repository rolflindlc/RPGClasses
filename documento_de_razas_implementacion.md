# Razas: estado de implementación (fases R1, R2 y R3)

Acompaña a *Documento de Diseño: Razas* (anexo, rev. 0.6, y adenda rev. 0.7). Describe qué hay programado en el mod, cómo probarlo y qué falta. Fecha: 2026-11-13 (10.º relevo, con los ajustes de balance del diseño v2 rev. 2.13); la sección 5 (razas de la adenda 0.7, fases R3a–R3c) es del 2026-11-16.

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
| **Tritón** | Respira y pica bajo el agua, +50 % nado, visión subacuática, +30 % daño de fuego, *Debilidad I* en el Nether | Pescador (`fisher`), Moldeador (`shaper`: V, B y agachado + V), Abisal (`abyssal`) | Moldeador: sustituir sus habilidades por conjuros de *T.O Magic 'n Extras* (R0 Hydroshot nv1, R1 Overflow nv3, R2 Jetstream nv1, R3 Tidal Grasp nv1); faltan los ids |
| **Infernal** | Inmune al fuego; +30 % daño de frío y de agua; el agua le quema (1 HP cada 2 s); conjuros raciales con muro de Magia (con Iron's: Fire Bolt, **Magma Bomb** y **Raise Hell**), golems hostiles, aldeanos +50 % | Nativo (`native`, V en R3), Fundidor (`smelter`, V en R3), Pirómano (`pyromaniac`, con Iron's) | — |
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

**N. Las escuelas (rev. 2.15–2.16).** La sagrada no pone cargas ni imbuye.
1. Elementalista: carga hasta detonar con conjuros de ender (el objetivo se teletransporta), invocación (anillo de colmillos), naturaleza (raíces y veneno), eldritch (Marca del Vacío: anillo de almas 4 s; pegarle entre varios y ver que la implosión atrae y hace más daño cuanto más recibió; también si muere antes) y, con T.O Magic 'n Extras, agua (ola que empuja y moja). El aviso de cargas muestra el nombre de la escuela.
2. Mago de Batalla: conjuro + golpe con ender (apareces a la espalda), invocación (colmillos), eldritch (onda sónica en línea que atraviesa a varios enemigos alineados) y agua.
3. Arrancar sin T.O Magic 'n Extras: sin errores en el registro y las demás escuelas igual.

**O. Debilidades raciales (rev. 2.17).**
1. Infernal: meterse en agua (o columnas de burbujas) quema 1 HP cada 2 s, con vapor y siseo; la lluvia no. Mensaje de muerte propio.
2. Infernal: congelación (nieve polvo) y conjuros de hielo hacen +30 %; con T.O Magic 'n Extras, también los de agua.
3. Tritón: al entrar al Nether recibe *Debilidad I* (sin icono), que se quita a los 3 s de salir.

## 5. Fase R3: razas de la adenda 0.7 (Slime, Coloso, Dracónido y Centauro)

Fecha: 2026-11-16 (R3a y R3b; R3c el mismo día). Protocolo de red **13** (servidor y cliente con el mismo jar). Ids: `rpgclasses:slime`, `colossus`, `draconid`, `centaur`. **Las 15 subrazas están disponibles** (39 en total).

### 5.1 Qué hay hecho

* **Reglas:** sin tope de vida máxima para nadie (solo el suelo de 10 HP); una raza tiene de 2 a 6 subrazas.
* **Datos:** las 4 razas, sus 15 subrazas y los **45 ritos** están en el datapack (`races/*.json`). Los ritos usan un objetivo nuevo, `counter` (lanzamientos, fusiones, bloques rotos…), el filtro `kill.by` (derrota con el aliento, la embestida, el enjambre…) y condiciones nuevas del jefe (`source_share`, `counts`, `min_slimes`) y del altar (`snowy`, `storm`, `deep_water`, `high`).
* **Retratos:** plantillas grises en blanco de 236×236 en `textures/gui/portrait/race/<raza>.png` y `subrace/<raza>/<subraza>.png`, junto a las demás, para sustituirlas por los tuyos.
* **Troncos (las 4 razas):**
  * **Slime:** sin daño de caída, rebote al caer (agachado no), +100 % de salto, −15 % de vida, +50 % de retroceso.
  * **Coloso:** tamaño ×2, +100 % de vida, rotura 2×2×2 (agachado 1; tarda lo del bloque más lento; nunca contenedores ni bloques con datos; respeta zonas protegidas), alcance +1,5, paso +0,6, salto +100 %, caída segura 6, detección +50 %, +50 % de hambre, se hunde y camina por el fondo (sale saltando del agua poco profunda), pisa cultivos (agachado no), no monta animales ni barcas. En tercera persona la cámara se aleja según el tamaño.
  * **Dracónido:** resistencia a su elemento (tope 80 %), +25 % de hambre, neutralidad de los dragones salvajes de Ice and Fire de su elemento (se rompe al dañarlos, abrir un contenedor a 32 bloques de ellos o romper montones de tesoro).
  * **Centauro:** hitbox de caballo (1,4 × 1,6; ojos a 1,5) y **cuerpo de caballo dibujado** (pelaje de caballo vanilla según el jugador; el torso humano va encima de la parte delantera, sin piernas, y las grebas y botas no se ven), aceleración al correr en línea recta (un giro brusco la reduce), embestida desde el 40 % (pierde el 75 % de la velocidad), salto cargado como el del caballo con barra (un toque es un salto normal), caída segura 6, aliados montados (clic derecho con la mano vacía; 1 o 2 por Rango), no monta animales ni barcas.
* **Subrazas (las 15):**
  * **Dracónido (las 6):** aliento con la tecla racial 1 (volver a pulsar lo corta). Fuego prende; Hielo ralentiza; Eléctrico salta a enemigos cercanos; Venenoso envenena (no-muertos: Lentitud) e inmune a Veneno en R2; Ácido aplica **Corrosión** (efecto nuevo, −5 % de armadura y dureza por nivel) e inmune a Hambre y a la comida mala; Agua empuja, apaga el fuego, aire ×3, respira bajo el agua en R2 y nada más rápido.
  * **Slime Enjambre (R3c):** tecla racial 1: te divides en un slime pequeño por corazón (Rango 3, por medio corazón; máx. 25). Tú eres uno de ellos (te encoges, se te ve como un slime y tu vida máxima es la de un slime) y los demás te siguen a saltos y se lanzan contra los enemigos cercanos. Solo daño por contacto (ignora la invulnerabilidad del objetivo; 1 s por objetivo y slime; sufre su armadura). Un golpe mata a un slime sin daño sobrante; si muere el que controlas, pasas al más cercano; si mueren todos, mueres. Dividido no usas objetos, bloques, ataques, habilidades, conjuros ni armadura, ni recoges objetos. Las pociones y conjuros de curación crean slimes nuevos (sin pasar de tu vida máxima ni de 25). Volver a pulsar (o acabar el tiempo) os une con la vida de los que quedan.
  * **Slime Resorte:** agachado en el suelo carga (barra verde); saltar con la carga te lanza con el giro del Impulso; rebota en paredes y suelo según la potencia (3 rebotes; agachado los corta).
  * **Slime Mitótico (R3c):** el golpe letal de un enemigo (tras las habilidades de clase, como el Orco) te divide en dos copias de la mitad de tamaño y de vida máxima, con la vida llena; la otra copia (con tu aspecto y tu nombre) huye de los enemigos y se acerca a ti. Si muere la que controlas pasas a la otra; si mueren las dos, mueres. Agachado a 2 bloques de tu copia, con una barra, os fusionáis (3 s; 2 s en Rangos 2–3) con la vida del Rango. Enfriamiento de la división de 5 a 2 min.
  * **Slime Viscoso:** agachado contra una pared o una entidad te pegas (barra naranja; saltar sube, soltar el agachado baja); en el aire te deslizas por las paredes; los golpes dan Lentitud.
  * **Coloso Porteador (R3c):** el **cinturón** (tecla racial 1): un menú con 3–6 huecos donde pones bloques funcionales (cofre y cofre atrapado —dos en un hueco son un cofre grande y ocupan 2—, barril, cofre de Ender, horno, alto horno, ahumador, mesa de crafteo, mesa de encantamientos, yunque, telar, cortapiedras, afiladora, mesa de herrería y de cartografía) y un botón **Abrir** debajo de cada uno. Los cofres y barriles son contenedores propios; los hornos cocinan mientras caminas; las mesas abren su menú vanilla. Tus aliados lo abren **agachados con clic derecho sobre ti** (solo usan los bloques). Al morir caen los bloques y lo que contienen. −10 % de velocidad y −5 % por pasajero (sin esto en Rango 3).
  * **Coloso Titán (R3c):** *Crecer* (tecla racial 1; volver a pulsarla la termina): tamaño ×4 con alcance 7,5/9, paso 2,4, salto 5 y caída segura 12; segunda barra de vida (corazones amarillos de absorción) que se pierde al terminar; rotura 3×3×3; hambre y detección +100 %; rompe hojas y pisa el césped hasta dejar tierra (fuera de zonas protegidas); pisotones que suenan muy fuerte, hacen vibrar sensores y Warden y **sacuden la pantalla** a quien esté a 24 bloques. No crece si no cabe (hojas y cultivos no cuentan). El enfriamiento empieza al terminar.
  * **Centauro Pegaso, Unicornio y Guerrero:** planeo con caída lenta (control en el aire en R2, aleteo con saltar en R3); aura de maná para aliados, +50 % de curación de pociones y conjuros y cuerno; daño cuerpo a cuerpo según la velocidad, y **al galope (≥40 %) atacar no le frena** (R3c): ni el frenazo vanilla de golpear corriendo ni el bloqueo de movimiento de las animaciones de Epic Fight.

### 5.2 Decisiones que he tomado (pendientes de tu confirmación)

* **Aliento:** cono de 30° de medio ángulo, golpea cada 0,5 s, **10 de maná por segundo** (sin Iron's no gasta maná); el enfriamiento empieza al acabar la canalización máxima. Daño por tipos propios (`breath_*`); el de fuego cuenta como fuego (el Infernal es inmune).
* **Corrosión** en jefes: la mitad. **Agua:** sin T.O Magic 'n Extras aplica Lentitud I 2 s (el efecto Mojado queda para cuando sepamos su id).
* **Centauro:** la caída segura de 6 bloques (no estaba en la adenda) para que el salto cargado de 5,3 bloques no haga daño. Su velocidad racial va como multiplicador total aparte, limitada a 3 veces la carrera vanilla. Si el Centauro embiste a la vez que la *Embestida* del Bárbaro o el Juggernaut, por ahora cuentan las dos.
* **Pegaso:** el planeo es la caída lenta de vanilla (oculta), activada al caer desde la altura del Rango.
* **Unicornio:** el aura suma +50 % al atributo de regeneración de maná de Iron's de los aliados (jugadores) en el radio. La Regeneración de manzanas doradas y faros también cuenta como "poción".
* **Viscoso:** te sueltas al perder 6 HP o más de golpe; la barra se recarga en el suelo en 2 s.
* **Titán (R3c):** volver a pulsar la tecla termina la forma antes. Un pisotón cada ~2,6 bloques caminados; el temblor baja con la distancia (el Titán lo nota al 30 %) y cada jugador lo puede reducir o quitar con la opción `titanShake` de `config/rpgclasses-client.toml`. Sonido a volumen 4 (se oye a ~64 bloques). Para que las vibraciones lleguen más lejos, cada pisotón se emite también a 8 bloques en las 4 direcciones; agachado no vibra. La barra extra es absorción (corazones amarillos).
* **Porteador (R3c):** el cofre de Ender del cinturón abre el cofre de Ender de quien lo usa. Las mesas se abren donde está el Porteador (la de encantamientos cuenta las estanterías de alrededor) y el yunque del cinturón no se desgasta. La experiencia de los hornos se da al dueño al cocinar. Quitar un bloque del cinturón devuelve su contenido a tu inventario. El −10 % de velocidad es un rasgo de datos (con los suelos de velocidad); el −5 % por pasajero va aparte.
* **Enjambre (R3c):** el número de slimes redondea hacia arriba (con 5,5 HP, 3 slimes) y la vida se reparte por igual. El enfriamiento empieza al uniros. Los slimes atacan a mobs hostiles, jefes, jugadores enemigos y a quien os ataque a ti o a tus aliados (a 8 bloques del slime y 16 de ti). La regeneración natural cura 1 HP a un slime dañado cada 4 s con la comida casi llena. Al salir o cambiar de dimensión os unís. Dividido tampoco haces daño de otras formas (Epic Fight, proyectiles ya lanzados, efectos).
* **Mitótico (R3c):** "enemigo" = mob hostil, jefe, jugador enemigo o un mob que te tenía como objetivo. Si tu copia muere (o se queda atrás al cambiar de dimensión), recuperas tu tamaño al acabar el enfriamiento de la división, sin curarte. Vida tras fusionar en Rango 0: 1 HP.
* **Guerrero (R3c):** con Epic Fight se restauran las teclas de movimiento si una animación de ataque las bloquea estando al ≥40 % [MEDIR: depende de cómo bloquee Epic Fight el movimiento].
* **Correcciones (R3c):** el galope del Centauro no medía bien el movimiento en el servidor (no habría acelerado nunca); ahora compara con la posición anterior. El planeo del Pegaso ya no depende de la velocidad vertical del servidor (no es fiable), solo de la distancia de caída. Un Coloso Juggernaut que atraviesa hojas o cristal con la Embestida ya no rompe además un cubo de 2×2×2 alrededor de cada bloque (lo mismo con los pasos del Titán).

### 5.3 Qué falta

1. **Porteador:** ver los bloques del cinturón sobre su cuerpo (hoy solo están en su menú).
2. **Centauro:** ajustar en el juego las medidas del cuerpo de caballo, del torso y de los pasajeros sobre el lomo **[MEDIR]**.
3. Que la embestida del Centauro no se sume a la *Embestida* del Bárbaro o el Juggernaut (contar solo la más fuerte).
4. Comprobar los ids de los dragones de Ice and Fire y del efecto Mojado de T.O Magic 'n Extras, y calibrar todo lo marcado **[MEDIR]** en la adenda.
5. Integrar la adenda 0.7 en el anexo de razas y en el documento v2 (reglas 7, 8 y 10 y sección 10).

### 5.4 Qué probar

0. **Arranque:** sin errores de Mixin (17: 15 comunes y 2 de cliente, `CameraZoomMixin` y `ArmorLegsMixin`). Servidor y cliente con el mismo jar (protocolo 13). Con `/clases raza set <jugador> <raza> <subraza> [rango]` se prueba cada subraza.
1. **Elección:** las 4 razas nuevas en la pantalla, con 6 pestañas en el Dracónido (el nombre completo sale al pasar el ratón). Ninguna subraza en gris.
2. **Slime:** caer desde lo alto sin daño y rebotar; agachado no rebota; salto de 2,5 bloques. **Resorte** y **Viscoso** como arriba.
3. **Enjambre** (`slime swarm`): V con poca y mucha vida (número de slimes, 25 como mucho); que te sigan y ataquen a un zombi; que un golpe fuerte mate a un solo slime; que al morir el tuyo pases a otro; que no puedas usar objetos, comer, abrir puertas ni lanzar conjuros; una poción de curación crea slimes; V de nuevo os une con la vida sumada; en Rango 3, un slime por medio corazón. Otro jugador te ve como un slime. Ritos: derrotas por contacto y el jefe con 6 slimes vivos.
4. **Mitótico** (`slime mitotic`): que un zombi te "mate" y te dividas (mitad de tamaño, vida llena, copia con tu aspecto que huye); que el vacío o `/kill` no te dividan; que al morir la que controlas pases a la copia; fusionarte agachado junto a ella (barra) con la vida del Rango; el enfriamiento (no se divide dos veces seguidas). Rito Dos en Uno.
5. **Coloso** (`colossus porter`): tamaño, romper 2×2×2 de pie y 1 agachado, cofres que no se rompen, agua (se hunde), cultivos pisados, no poder montar barcas ni caballos, caer 6 bloques sin daño, cámara en tercera persona.
6. **Porteador:** V abre el cinturón; poner cofres (dos en un hueco = cofre grande), un horno con algo que cocinar y caminar (sigue cocinando), una mesa de crafteo y un yunque; Abrir cada uno; que un aliado agachado con clic derecho lo abra y uno que no es aliado no; morir y ver que cae todo; salir y entrar y que se conserve; con pasajeros, más lento.
7. **Titán** (`colossus titan`): V para crecer (no deja si hay techo), la barra amarilla extra que se pierde al terminar, romper 3×3×3, alcance largo, subir escalones de 2 bloques, caer 12 sin daño, hojas que se rompen y césped que se vuelve tierra (no en el spawn protegido), el temblor en otro jugador cercano y la opción `titanShake`, y que el enfriamiento empiece al acabar (V de nuevo lo termina antes).
8. **Dracónido:** cada aliento con la tecla V (y cortarlo con V), el maná que baja, el muro de Magia con un Bárbaro, la Corrosión en el inventario del objetivo, resistencias (fuego, nieve polvo, rayo, veneno, ahogarse).
9. **Centauro:** el cuerpo de caballo (patas al andar, cola, pelaje distinto en otro jugador, sin grebas ni botas a la vista, con y sin Epic Fight); correr en recto y ver que acelera (antes de esta fase no lo hacía); embestir a un zombi; salto cargado con barra; que un aliado se monte (clic derecho) y que con 2 pasajeros vayan uno detrás de otro; huecos de 1 bloque. **Guerrero:** golpear corriendo al galope sin perder la carrera, con y sin Epic Fight.
10. **Ritos:** `/raza ritos` en cada subraza nueva; una ofrenda y una hazaña con contador (p. ej. Resorte: lanzamientos; Titán: bloques en área).
