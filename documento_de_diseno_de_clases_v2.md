# Documento de Diseño v2: Mod de Clases y Árboles de Habilidades para Minecraft Java

Versión 2 del diseño (revisión 2.14, en desarrollo). Plataforma prevista: Forge 1.20.1 con Iron's Spells and Spellbooks, Epic Fight, Mowzie's Mobs y Cataclysm. Servidor principalmente PvE con algo de PvP. Objetivo del mod: sistema de clases balanceadas y programado de forma estructurada y orientada a la expansibilidad (clases futuras previstas: Alquimista, Nigromante, Druida).

Los valores marcados con **[MEDIR]** son objetivos de diseño que deben calibrarse en el juego real antes de darlos por cerrados.

**Cambios de las revisiones 2.9 a 2.14:**
* **Mecánicas nuevas (2.14):** *Mago de Batalla* gana **Filo Imbuido** y **Eco Marcial**; *Palma Pesada* gana **Golpe Cargado** e **Impacto** (el Puño de Inhabilitación sale con cada Impacto en vez de por probabilidad); *Baluarte de Embestida* deja una **estela de viento** y **rompe bloques débiles** con daño al Paladín según su dureza (ver cada subclase).
* **Balance y visuales (2.13):** Explorador Ágil +10% → **+5%** de velocidad; Maestro de Bestias +35% → **+30%** de vida y daño de mascotas; Palma Pesada: Puño de Inhabilitación 10% → **15%**; Mago de Batalla: Escudo Arcano 10% → **12%** del maná gastado. Efectos visuales nuevos: Provocación (onda y brillo en los mobs provocados), Baluarte (escudo de partículas mientras dura), Estandarte (anillo dorado en el borde del aura), Furia Berserker (aura roja), Embestida (estela y golpe), Sed de Sangre (sangre hacia el Bárbaro), Toxina (gotas verdes) y barra de cargas de **Conjuros Gratuitos** del Mago en la interfaz. La sección 10 no está recalculada con estos ajustes.
* **Aturdimiento de Epic Fight (2.12):** todos los aturdimientos de las clases (*Embestida* y *Carga Demoledora* del Bárbaro, *Puño de Inhabilitación* de Palma Pesada y el efecto propio de Piedra del Monje) aplican el **estado de aturdimiento real de Epic Fight** (aturdimiento corto, alargado a la duración de la habilidad). Sin Epic Fight, o en criaturas sin animación de aturdimiento, se usa el simulado (lentitud y debilidad fuertes). El tag `rpgclasses:stun_immune` sigue haciendo inmune. No cambian la congelación del Elementalista ni el Cepo del Goblin (no son aturdimientos).
* **Rework del Monje por cargas de postura (2.9):** el crecimiento plano de daño y armadura del Monje se sustituye por **cargas de postura** (sección 8). El nivel da un **suelo** (*Cuerpo Marcial*, ≈ ⅔ del daño y la armadura anteriores) y el resto se gana en combate acumulando cargas en la postura activa. Las ramas Puño, Piel y Viento **potencian las cargas** de Fuego, Piedra y Agua. Perks nuevos: Llama Constante e Ignición Temprana (Puño), Eco de Piedra (Piel), Postura Dominante y Flujo Continuo (Maestría); desaparecen Flujo, Onda Elemental, Postura Híbrida y Calma Interior. Sustituye al rework de la 2.8 (*Cuerpo Marcial* a medias con las ramas), que no llegó a programarse. **Programado en la 2.11.**
* **Aliados (regla 15, 2.9):** cada jugador elige a sus aliados; el resto son enemigos.
* **Pícaro (2.8):** la invisibilidad ya no se revela por cercanía en PvP.
* **Apuesta con la Muerte (2.10):** moneda con anj y calavera, sin cooldown (sección 5).
* **Libro de Vínculos (2.11):** objeto del Maestro de Bestias (sección 7).
* **Interfaz del Monje (2.11):** cargas como símbolos encima de la barra de vida y efecto del elemento en los puños.

**Historial resumido (revisiones 2.2 a 2.6):** 2.2–2.3: Mago reducido, Juggernaut a +60% de vida, nueva disciplina Escudos (afín del Paladín y del Bárbaro), presupuesto de muro variable, Ranger afín a Armadura. 2.4: nerfs al Monje (Piel 16/6, Viento +40%) y al Explorador Ágil. 2.5: subclase desde 15 puntos de base (tope ⌊(base−10)/2⌋), muros solo con puntos de base, hitos de subclase latentes. 2.6: pasivas dobles al máximo con 10 puntos, críticos propios (regla 9), armas sin clasificar = ligeras, supervivencia antes del Tótem (regla 10), Nivel Total incluye subclase, reinicios ilimitados con −33% de XP (regla 6).

**Cambios de la revisión 2.7:**
* **Perks de rama (regla 11):** al llenar una rama (10 puntos) se elige 1 de 2 perks. Eran 36 perks (18 ramas × 2); con la regla 13 pasan a ser **60** (30 ramas × 2). Son laterales: cada uno cambia la forma de jugar con una ventaja y un coste, y no pretende subir el poder.
* **Rasgos base de clase (regla 12):** Mago −20% de vida máxima; Paladín +20% de vida máxima y −10% de velocidad; Pícaro −15% de vida máxima.
* **Tope global de vida (regla 7):** los bonos porcentuales de vida máxima suman como máximo +100%. Juggernaut de Carga queda en +80% total (+20% de la clase y +60% de la subclase, sumados).
* **Dos nerfs de base por pruebas:** Brutalidad (Bárbaro) de +100% a +50% de daño crítico; Filo (Pícaro) de +60% a +25% de velocidad de ataque.
* **Ajustes de balance tras el análisis (rev. 2.7b):** Monje: Viento +40% → +35%. Explorador Ágil: velocidad +15% → +10% y sin Suerte. Maestro de Bestias: bonus de mascotas +50% → +35% (vida y daño). Bárbaro: *Furia Berserker* aplica *Debilidad I* 6 s a los enemigos a 6 bloques. Corruptor de Plagas: *Plaga* se contagia al morir el portador. Mago de Batalla: Escudo Arcano 7% → 10%. Hoja Arcana: Emboscada Arcana +40% → +60%. Sección 10 reajustada a mano.
* **Regla 14 (escuela sagrada, rev. 2.7c):** muro de −100% en la escuela sagrada para todas las clases salvo el Paladín, aparte del muro de Magia general; el muro de Magia del Paladín no la afecta. El Mago pierde así la sagrada (y con ella la curación de Iron's). Sección 10 ajustada a mano.
* **Regla 13 (ramas extra):** 5 ramas por clase, máximo 3 usables; las otras dos se bloquean al invertir en tres. Todas las clases tienen ya sus ramas 4.ª y 5.ª: Mago (*Convocación*, *Fluidez*), Paladín (*Estandarte*, *Castigo*), Bárbaro (*Estampida*, *Curtido*), Pícaro (*Toxinas*, *Acrobacia*), Ranger (*Flechas Especiales*, *Retirada*) y Monje (*Maestría de Postura*, *Meditación*).
* **Pícaro, invisibilidad en PvP:** el radio de revelado baja de 2,5 a **1 bloque** de un jugador.

**Cambios de la revisión 2.7f (tras la primera ronda de pruebas):**
* **Mago:** *Reserva* compensa que Iron's regenera un % del maná máximo (la regeneración real sube +40 % con 10 puntos; Ráfaga +60 %, Pozo −40 %, en maná por segundo). *Elementalista*: cada carga nueva reinicia el tiempo en todos los niveles y avisa de las cargas; la detonación de **Hielo** encierra al objetivo en una **Tumba de Hielo** de Iron's (los jefes solo se congelan). *Magia Salvaje*: probabilidad mínima del 10 %; si no hay otro conjuro ofensivo equipado, replica el mismo. *Magia de Sangre* también paga con vida los conjuros continuos.
* **Paladín:** *Baluarte Incorruptible* trata como negativos los efectos dañinos y los del tag `rpgclasses:negative` (efectos de otros mods registrados como neutrales) y purga cada 0,5 s. *Baluarte de Embestida*: +0,5 de altura de paso mientras corre.
* **Bárbaro:** *Embestida* base se frena contra el primer enemigo y rebota (solo *Carga Imparable* atraviesa y golpea a varios). *Carnívoro*: solo se puede comer carne (la carne sacia el doble).
* **Pícaro:** *Señuelo* lleva el equipo del Pícaro (manos y armadura) y su cooldown baja a **10 s**. El sigilo no deja partículas ni muestra equipo; cualquier ataque cuerpo a cuerpo desde el sigilo gasta barra.
* **Ranger:** *Flecha Explosiva* mide los 2 bloques hasta el cuerpo del enemigo y empuja. *Maestro de Bestias*: al domesticar un animal se pregunta si vincularse; solo las mascotas **vinculadas** reciben Vínculo Salvaje y Salvaguarda (agachado + mano vacía para vincular o romper). La Salvaguarda busca un hueco donde quepa la mascota.
* **Monje:** *Trance Profundo* cura hasta el **80 %**; *Mente Clara* ya no reduce la curación.

## 🛠️ 1. Reglas Generales

1. **Loot inicial con durabilidad infinita.** Todo el equipamiento entregado al elegir clase tiene durabilidad infinita.

2. **Estructura de niveles (30 base + 10 subclase = 40).**
   * **Clase base:** 3 ramas de 10 puntos (elegidas entre las 5 de la clase, regla 13). **Un jugador no puede poner más de 10 puntos por rama, y al llenar una rama sus pasivas llegan a su valor máximo.** Llegar al nivel 30 exige llenar las 3 ramas. Cuando una rama contiene dos pasivas, cada punto de la rama sube ambas a la vez y las dos alcanzan su máximo con 10 puntos.
   * **Subclase:** se elige 1 de 3 subclases exclusivas, con 10 niveles propios. **Se desbloquea al tener 15 puntos en la clase base.**
     * **Tope ligado a la base:** los niveles de subclase no pueden superar ⌊(puntos de clase base − 10) / 2⌋. Con 15 puntos base se permiten 2, con 20 se permiten 5, con 25 se permiten 7 y con 30 se permiten 10.
     * El jugador reparte libremente sus puntos entre base y subclase dentro de ese tope. El estado final sigue siendo 30 base + 10 subclase = 40.
     * **Muros:** escalan **solo con los puntos de la clase base** (tope en 30); los puntos de subclase nunca los aumentan.
     * El respec de subclase es independiente (devuelve el 67% de la XP, según la regla 6).

3. **Habilidades activas.** No pertenecen a una rama. Se desbloquean por hitos del **Nivel Total de Clase** (suma de puntos gastados). Los hitos marcados "de subclase" dependen del nivel de subclase. **Si un hito de subclase modifica una activa de la clase base que el jugador aún no tiene, queda latente hasta desbloquearla.** Afecta a Baluarte Sanador, Baluarte Incorruptible y Baluarte de Embestida (requieren *Baluarte*), a Disparo Letal de Francotirador (*Disparo Pesado*) y al Triple Salto del Explorador (*Doble Salto*).

4. **Una sola clase por jugador.**

5. **Coste en XP vanilla.** Los niveles se compran con puntos de XP (no con niveles vanilla, cuyo coste varía según los que se tengan).
   * Coste del nivel de clase *n* = **80 + 6n** puntos de XP.
   * Total para 40 niveles: **8120 XP** (5190 hasta el nivel 30 y 2930 para la subclase). Referencia: subir hasta el nivel 40 vanilla cuesta 2920 XP.
   * Objetivo de ritmo: ≈ 387 XP/día durante 21 días **[MEDIR]** (nivel 19 al final de la semana 1, nivel 30 en la semana 2 y nivel 40 en la semana 3). Debe existir un multiplicador de configuración del coste.

6. **Respec.** Sin precio en XP. Se puede reiniciar la clase o solo la subclase, fuera de combate.
   * Reiniciar la clase reinicia también la subclase y permite **elegir otra clase**; reiniciar la subclase permite **elegir otra subclase**.
   * Al reiniciar la clase se retiran los objetos del kit inicial que estén en el inventario (los que no estén, se dejan).
   * **Reinicios ilimitados.** Cada reinicio devuelve el **67%** de la XP de lo que se reinicia: se pierde un **33%** (configurable).
   * La XP de un reinicio de subclase se calcula sobre el coste actual de los niveles que se pierden (los más altos del Nivel Total), no sobre lo que costaron en su momento.
   * Al reiniciar la clase se pierden los perks de rama elegidos (regla 11); al volver a llenar las ramas se eligen de nuevo.
   * **Se devuelven todos los puntos** y el jugador los reparte de nuevo con total libertad: puede elegir otras ramas (las 3 de las 5, regla 13), otra subclase o incluso otra clase.

7. **Topes globales.**
   * Bonus de velocidad de movimiento total ≤ **+100%** y de salto ≤ **+150%**, para cualquier combinación de clase, subclase, perk y postura. Los topes cuentan **solo los bonus del mod** (no pociones ni equipo).
   * **Vida máxima porcentual:** la suma de los bonos porcentuales de vida máxima de rasgos de clase, subclase y perks es ≤ **+100%** (aditivos entre sí). Los bonos planos (p. ej., +10 HP de *Vitalidad Salvaje*) no cuentan para el tope. Los penalizadores se suman a los bonos.

8. **Definición de categorías (por categorías de arma de Epic Fight, ajustables por datapack).**
   * **Armas pesadas:** mandobles, espadones largos, tachi, hachas, martillos.
   * **Armas ligeras:** espadas, dagas, katanas ligeras, lanzas.
   * **Arco:** arcos, ballestas y armas arrojadizas.
   * **Escudos:** escudos vanilla y de otros mods, usados en la mano secundaria.
   * **Sin clasificar:** cualquier objeto sin categoría usado para golpear cuerpo a cuerpo cuenta como **arma ligera**. La mano vacía sigue siendo "desarmado".

9. **Críticos.** El mod usa una probabilidad de crítico propia y desactiva el crítico vanilla por salto (no existe en el modo batalla de Epic Fight). Valores **[MEDIR]**:
   * Probabilidad base: **30%** para todas las clases. Daño crítico base: **×1,5**; los bonus de daño crítico de las ramas se suman a ese multiplicador.
   * **Bárbaro: 35%** (+5 puntos). **Pícaro: 25%** (−5 puntos), pero todo golpe **desde invisibilidad es crítico seguro**.
   * Pueden ser críticos los golpes cuerpo a cuerpo y las flechas con el arco tensado al máximo. Los hechizos no usan este sistema.

10. **Supervivencia y Tótem.** Las habilidades que evitan la muerte (*Apuesta con la Muerte*, *Última Furia Inmortal*) se resuelven **antes** que el Tótem de la Inmortalidad.

11. **Perks de rama.**
   * Al llegar a **10 puntos en una rama** (rama llena), el jugador elige **1 de 2 perks** (opción A u opción B) propios de esa rama. No cuesta XP ni puntos y no cuenta para el Nivel Total.
   * Los perks son **laterales**: cada uno da una ventaja y un coste (o un estilo de juego distinto), de modo que ninguna opción sea estrictamente mejor. Ninguno pretende subir el poder de la clase; los valores están pensados para ser neutros y se calibran con **[MEDIR]**.
   * El perk elegido se mantiene hasta que se reinicie la clase (propuesta; ver pendientes). Los perks se definen en datos (JSON/datapack) como el resto de la clase.
   * Los perks cuentan para los topes globales (regla 7).

12. **Rasgos base de clase.**
    * Algunas clases tienen un modificador de atributos fijo, ligado a su identidad: **Mago −20% de vida máxima** (16 HP); **Paladín +20% de vida máxima** (24 HP) **y −10% de velocidad de movimiento**; **Pícaro −15% de vida máxima** (17 HP). El **Monje** tiene el rasgo *Cuerpo Marcial* (sección 8), que a diferencia de los demás **escala con los puntos de clase base**.
    * Se aplican **completos desde que se elige la clase** (a diferencia de los muros, que escalan con los puntos). Propuesta; ver pendientes.
    * Son aditivos con los demás bonos de vida de la clase, la subclase y los perks, y cuentan para el tope global de la regla 7.

13. **Ramas extra: 5 ramas por clase, máximo 3 usables.**
    * Cada clase tiene **5 ramas de pasivas** (30 ramas y 60 perks en total, cada rama con sus 2 perks de la regla 11). El jugador solo puede usar **3**: en cuanto pone **al menos 1 punto en tres ramas distintas**, las otras dos quedan **bloqueadas** hasta que reinicie la clase.
    * Llegar al nivel 30 sigue exigiendo llenar 3 ramas de 10 puntos (30 base). Los muros, los hitos de activas y el coste de XP no cambian.
    * **Interfaz:** las ramas bloqueadas se muestran **oscurecidas y con un candado**. Al reiniciar la clase se recuperan todos los puntos y se pueden volver a elegir las 3 ramas libremente.
    * Estado del diseño de las ramas 4.ª y 5.ª: **hecho para las seis clases** (ver cada clase).

14. **Escuela sagrada exclusiva del Paladín (rev. 2.7c).** Todas las clases menos el Paladín tienen un **muro de −100% de poder mágico en la escuela sagrada** de Iron's (incluidas las curaciones), **además** de su muro de Magia general. Es fijo desde que se elige la clase (no escala con los puntos), así que los conjuros sagrados no hacen nada fuera del Paladín, ni con libros, varitas o pergaminos. El muro de Magia del Paladín no se aplica a la sagrada.
    * **Curación en clases sin Paladín:** se cura **comiendo y con objetos** (pociones, manzanas doradas, etc.); no hay un sustituto sagrado.

15. **Aliados y enemigos (rev. 2.9).** Cada jugador elige a quién considera **aliado** (tecla **J**, botón *Aliados* del libro o `/aliados agregar|quitar <jugador>`). **Todo el que no es aliado cuenta como enemigo.**
    * La relación es **unilateral** y se mira desde quien produce el efecto: tus apoyos (Estandarte, Baluarte Sanador, Voz del Caudillo, Nube de Humo, curas y mejoras de Iron's) solo alcanzan a tus aliados, y tus efectos ofensivos (áreas, ondas, trampas, debilitaciones, conjuros de Iron's, ataques de Epic Fight) no les afectan. Al marcar a alguien, se le avisa con un enlace para marcarte también.
    * **Dueños:** las mascotas, invocaciones, señuelos y proyectiles cuentan como su dueño. Tus mascotas y las de tus aliados son aliadas tuyas.
    * **Fuego amigo:** no puedes dañar a quien tienes como aliado (configurable: `allies.friendlyFire`).
    * Un mismo equipo de scoreboard (`/team`) también cuenta como aliado (configurable). Los mobs sin dueño jugador siguen las reglas de cada efecto (hostiles, aldeanos, etc.).
    * Ningún mod de la lista trae un sistema de aliados propio (Iron's y Epic Fight usan los equipos de scoreboard), y depender de FTB Teams añadiría tres mods y un modelo por equipos; por eso es propio, pero conectado a `isAlliedTo` para que Iron's, Epic Fight y vanilla lo respeten.

## 🧱 2. Sistema de Muros de Disciplina (sustituye a los "Debuffs de Clase" de la v1)

**Objetivo:** impedir que un jugador use su clase y además el estilo de otras clases.

**Cómo funciona:**
* Existen 6 **disciplinas**: **Magia** (poder de hechizo, todas las escuelas), **Arco**, **Armas pesadas**, **Armas ligeras**, **Armadura** (armadura y dureza de las piezas equipadas) y **Escudos**.
* Cada clase es **afín** a algunas disciplinas (sin penalización) y recibe un **muro** en las demás.
* El muro crece linealmente con los **puntos de la clase base**: **penalización = máximo × (puntos base / 30)**, con tope en 30. Los puntos de subclase no la aumentan. Ningún muro supera el 100%.
* **Presupuesto variable:** ya no todas las clases suman lo mismo. Para equilibrar, a las clases fuertes se les sube el porcentaje de muro y a las débiles se les añaden afinidades.
* Mitigar o anular un muro **no es una regla general**: es una pasiva específica de ciertas subclases (Mago de Batalla, Hoja Arcana).
* Los muros también se aplican en PvP.

**Efecto de cada muro:**
* **Magia:** reduce el Spell Power. **Arco, Armas pesadas, Armas ligeras:** reducen el daño final con ese tipo de arma. **Armadura:** reduce la armadura y la dureza de las piezas equipadas.
* **Escudos:** un muro de X% aumenta el **cooldown de recuperación del escudo** un **2X%** (tiempo que el escudo queda inutilizable al ser desactivado o romperse la guardia) y el **gasto de durabilidad** un **X%**. Ejemplo: −30% de afinidad con escudos = +60% de cooldown de recuperación y +30% de gasto de durabilidad. Con −100%: +200% y +100%.

| Clase | Afín (sin muro) | Magia | Arco | Pesadas | Ligeras | Armadura | Escudos | Total |
| ----- | ----- | ----- | ----- | ----- | ----- | ----- | ----- | ----- |
| **Mago** | Magia | afín | −25% | −50% | −50% | −25% | −50% | 200 |
| **Paladín** | Armadura, Ligeras, Pesadas, Escudos | −75% | −75% | afín | afín | afín | afín | 150 |
| **Bárbaro** | Pesadas, Escudos | −60% | −45% | afín | −45% | n/a | afín | 150 |
| **Pícaro** | Ligeras | −45% | −35% | −55% | afín | −40% | −70% | 245 |
| **Ranger** | Arco, Armadura | −40% | afín | −45% | −35% | afín | −30% | 150 |
| **Monje** | Desarmado | −70% | −30% | −35% | −40% | n/a | −100% | 275 |

Notas:
* **Bárbaro y Monje:** la disciplina Armadura no cuenta, porque llevar cualquier pieza de armadura **desactiva** sus pasivas "desarmadas". Es un muro más fuerte que el porcentaje.
* **Paladín:** el muro de Magia afecta a todas las escuelas **excepto la sagrada** (rev. 2.7c). Las demás clases tienen −100% en la sagrada (regla 14).
* Los muros regulan la elección de equipo. El precio del poder de cada clase está en su kit, no en el muro. Desde la 2.7, los rasgos base de clase (regla 12) y los perks de rama (regla 11) aportan un segundo nivel de identidad.

## 🧙‍♂️ 3. Mago

### Equipamiento inicial
* Libro básico de Iron's Spells (Proyectil Mágico) y armadura de cuero completa. Durabilidad infinita.

### Rasgo base de clase
* **−20% de vida máxima** (16 HP).

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Poder** | Spell Power hasta +50% (5% por punto) |
| **Reserva** | Maná máximo hasta +300 (máximo con 10 puntos) y regeneración de maná hasta +40% (máximo con 10 puntos) |
| **Cadencia** | Reducción de cooldown hasta −25% (2,5% por punto) |
| **Convocación** *(rama extra)* | +40% de vida y daño de las invocaciones (4% por punto) |
| **Fluidez** *(rama extra)* | Velocidad de movimiento hasta +15% y sin ralentización al lanzar conjuros (ambas con 10 puntos) |

### Perks de rama (1 de 2 al llenar la rama)
| Rama | Opción A | Opción B |
| ----- | ----- | ----- |
| **Poder** | **Poder a Distancia:** +35% de daño a ≥15 bloques y −20% a ≤6 bloques. | **Poder a Quemarropa:** +40% de daño a ≤6 bloques y −20% a ≥15 bloques. |
| **Reserva** | **Ráfaga de Maná:** −150 de maná máximo y +60% de regeneración de maná. | **Pozo de Maná:** +200 de maná máximo y −40% de regeneración de maná. |
| **Cadencia** | **Cadencia Arcana:** −25% de cooldown adicional (−50% con la rama) y −10% de Spell Power. | **Magia de Sangre:** si falta maná, el conjuro se paga con vida (1 HP = 15 de maná); nunca baja del 25% de la vida máxima. Ese maná no cuenta como gastado para Magia Salvaje ni Escudo Arcano. |
| **Convocación** | **Legión:** cada conjuro de invocar crea **+1 invocación**; cada una tiene −25% de vida y daño. | **Élite:** cada conjuro de invocar crea **−1 invocación (mínimo 1)**; cada una tiene +30% de vida y daño extra. Los conjuros de invocar cuestan +15% de maná. |
| **Fluidez** | **Paso Arcano:** tras lanzar un conjuro, +30% de velocidad de movimiento durante 2 s. ICD 4 s. | **Caída de Pluma:** sin daño por caída y caída lenta al mantener el salto. −10% de velocidad de movimiento base. |

Notas de los perks:
* **Poder:** la distancia se mide hasta el objetivo en el momento del impacto. Entre 6 y 15 bloques el modificador se interpola linealmente entre los dos extremos (sin escalón).
* **Reserva:** los números dependen de la regeneración base de Iron's **[MEDIR]**. Objetivo: que A y B den el mismo maná total en combates de 45–60 s. El mínimo de maná máximo con A debe permitir lanzar los conjuros equipados.
* **Convocación:** en Iron's, relanzar un conjuro de invocar despawnea las invocaciones anteriores, por eso el perk cambia el **número por lanzamiento** y no hay tope simultáneo. El modificador se aplica sobre el número que dé el conjuro (que escala con su nivel). En Élite, los conjuros de 1 invocación no pierden nada con el −1 (mínimo 1), de ahí el +15% de maná; en Legión duplican de 1 a 2, de ahí el −25%. Medir ambos **[MEDIR]**.
* **Fluidez:** Paso Arcano y la velocidad de la rama cuentan para los topes globales (regla 7). Comprobar que la caída lenta de Caída de Pluma es compatible con Epic Fight **[MEDIR]**.
* **Cadencia A:** con Elementalista (+10% de CDR) la reducción total llegaría a −60%. Comprobar el tope real de CDR de Iron's **[MEDIR]**.

### Activa: Conjuros Gratuitos por Cargas
* Al alcanzar el hito, el jugador elige 1 conjuro, que queda fijado permanentemente en esa ranura.
* Si hay cargas y el coste original del conjuro es ≤ el límite de la ranura, el lanzamiento **consume 1 carga en lugar de maná**. Sin cargas se lanza con maná normal.
* **Recarga:** 120 s por carga. **ICD** de 3 s entre usos. La CDR **no** acelera la recarga.
* Un lanzamiento con carga cuenta como **0 de maná gastado**: no activa Magia Salvaje ni Escudo Arcano.

| Hito (Nivel Total) | Ranura | Cargas | Coste máximo |
| ----- | ----- | ----- | ----- |
| 5 | 1 | 1 | ≤ 75 |
| 15 | 2 | 2 | ≤ 125 |
| 30 | 3 | 3 | ≤ 200 |

### Subclases

**1. Evocador Caótico**
* **Ampliación Mágica:** +30% de tamaño de proyectiles y área al nivel 10.
* **Erupción de Magia Salvaje:** probabilidad = **maná consumido × 0,33%, con tope del 50%**.
  * La réplica lanza otro conjuro ofensivo equipado en **8 direcciones horizontales**, hace el 33% del daño y **no cuesta maná**.
  * Una réplica **no puede activar otra** Magia Salvaje. **ICD global de 1,5 s.**
  * Los conjuros legendarios **pueden activar** Magia Salvaje al lanzarse, pero **no pueden ser la réplica**.

**2. Elementalista**
* **Sintonización Elemental:** +1% de CDR por nivel de subclase (+10% al nivel 10).
* **Debuffs Elementales:** los conjuros que dañan aplican cargas con probabilidad del 40–75%.
  * Niveles 1–3: máx. 3 cargas, 4 s. Niveles 4–7: máx. 4 cargas, 6 s. Niveles 8–10: máx. 5 cargas, 8 s y refresco del temporizador.
  * Cada elemento va ligado a una escuela de Iron's: Fuego (explosión de área), Hielo (congelación), Rayo (arcos eléctricos), Sangre (robo de vida). Funciona con jefes.
  * Detonaciones (rev. 2.7e): **Fuego** explosión de 3 bloques al 60% del golpe + fuego 4 s. **Hielo** Lentitud VII + Fatiga 3 s (jefes 1,5 s). **Rayo** arcos a 3 enemigos en 6 bloques al 50%. **Sangre** +40% de daño que cura al lanzador. El daño secundario no encadena cargas.

**3. Mago de Batalla**
* **Concentración Inquebrantable:** los conjuros con tiempo de lanzamiento no se interrumpen ni sufren knockback al recibir daño.
* **Armonía Marcial:** reduce los muros de **Armas pesadas y ligeras** un 10% por nivel de subclase (se anulan al nivel 10). Es la pasiva que mitiga muros en esta subclase.
* **Escudo Arcano:** absorción igual al 3% (nivel 1) a 12% (nivel 10, rev. 2.13; antes 10%) del maná gastado, con **tope del 50% de la vida máxima**. Tras 6 s sin gastar maná, pierde el **10% del máximo del escudo por segundo**.
* **Filo Imbuido (rev. 2.14):** cada conjuro imbuye el arma con su escuela durante **5 s**. El siguiente golpe cuerpo a cuerpo hace **+15% (nivel 1) a +35% (nivel 10)** de daño y aplica un efecto: **fuego** quema 3 s (se suma a la quemadura), **hielo** *Lentitud II* 2 s y congela, **rayo** salta al enemigo más cercano en 4 bloques con el 50% del golpe, **sangre** cura el 25% del daño extra, **naturaleza** *Veneno I* 3 s; las demás escuelas solo suman el daño. **[MEDIR]**
* **Eco Marcial (rev. 2.14):** cada **3 golpes cuerpo a cuerpo seguidos** (sin pasar más de 4 s entre ellos), el siguiente conjuro cuesta **−30% de maná**. No se acumula.

## 🛡️ 4. Paladín

### Equipamiento inicial
* Espada de piedra y armadura de malla completa. Durabilidad infinita.

### Rasgo base de clase
* **+20% de vida máxima** (24 HP) y **−10% de velocidad de movimiento**.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Coraza** | Multiplicador de armadura de las piezas equipadas hasta +25% y de dureza hasta +40% (cada una con 10 puntos) |
| **Voluntad** | Resistencia a debuffs: −50% de duración de efectos negativos (5% por punto) |
| **Empuje** | Resistencia al retroceso hasta +50% y empuje infligido hasta +25% (cada uno con 10 puntos) |
| **Estandarte** *(rama extra)* | Aura de 8 bloques: +10% de armadura y −10% de duración de efectos negativos para ti y tus aliados (1% por punto) |
| **Castigo** *(rama extra)* | +25% de daño contra no-muertos y mobs del Nether, y +10% de daño general sin escudo (ambas con 10 puntos) |

### Perks de rama (1 de 2 al llenar la rama)
| Rama | Opción A | Opción B |
| ----- | ----- | ----- |
| **Coraza** | **Coraza Pesada:** +20% de armadura y dureza de las piezas, −5% de velocidad de movimiento. | **Cuerpo Colosal:** +20% de vida máxima, −5% de velocidad de movimiento. |
| **Voluntad** | **Voluntad contra el Daño:** inmunidad a efectos que hacen daño (Veneno, Wither, quemadura, sangrado de Iron's). | **Voluntad contra el Control:** inmunidad a Lentitud, Debilidad, Ceguera, Fatiga, Náusea y Oscuridad. |
| **Empuje** | **Contragolpe de Escudo:** al bloquear con escudo un golpe cuerpo a cuerpo, el atacante sale despedido 3 bloques. ICD 3 s. | **Onda de Choque:** cada 5.º golpe lanza una onda de 3 bloques con empuje y el 30% del daño. |
| **Estandarte** | **Estandarte de Guerra:** el aura también da +8% de daño a los aliados. Tú tienes −15% de armadura. | **Estandarte de Vida:** el aura regenera 0,5 HP/s a los aliados (no a ti). |
| **Castigo** | **Juicio:** cada 6.º golpe hace +40% de daño sagrado en un área de 2 bloques. −10% de daño base. | **Condena:** marcas al objetivo 6 s: recibe +15% de daño tuyo. No puedes marcar a otro durante ese tiempo. |

Notas de los perks:
* **Coraza A:** por el tope del 80% de reducción de daño, ayuda sobre todo contra golpes grandes (jefes). **Coraza B** cuenta para el tope global de vida (regla 7).
* **Estandarte:** el aura no se apila con la de otro Paladín (cuenta la más alta). Estandarte de Guerra cuenta como bonus de daño de aliados; revisar en grupo **[MEDIR]**.
* **Castigo:** el daño sagrado de Juicio y Condena cuenta como daño del Paladín (no del muro de Magia). "Mobs del Nether" se define por datapack.
* **Voluntad:** en ambas opciones, los efectos negativos de los que **no** es inmune duran un **+50%**. Sumado al −50% de la rama, la duración neta es la normal: el perk cambia la reducción general por una inmunidad. Congelación, enraizado, silencio y aturdimiento siguen afectándole en la opción B. Las listas de efectos se ajustan por datapack.

### Activas y pasivas de hito
* **Provocación (Nivel Total 10):** fuerza a los mobs en 20 bloques a fijar al Paladín como objetivo. Lentitud I a los afectados durante 3 s. Dos añadidos: **cooldown de 30 s** y **−20% de daño recibido durante 4 s**, para que sirva también en solitario.
* **Guardia Firme (pasiva, Nivel Total 10):** +15% de daño contra enemigos que tengan al Paladín como objetivo.
* **Baluarte (Nivel Total 30):** 40–50% de reducción de todo el daño recibido durante 6–8 s. Cooldown 60 s.

### Subclases

**1. Guardián Bendito**
* **Bendición de Vida:** +5% de curación recibida por nivel de subclase (+50%).
* **Baluarte Sanador (hito de subclase 5):** mientras *Baluarte* está activo, **cuando el Paladín recibe daño**, un 30–50% del daño mitigado **cura a los aliados en 12 bloques y también al propio Paladín**. **No devuelve daño.**
* Cada aliado recibe la cantidad entera (no se reparte), con un máximo de 6 aliados curados (los más cercanos que no estén a vida completa).

**2. Bastión Inflexible**
* **Firmeza Inquebrantable:** resistencia al empuje al **100%** (inmunidad).
* **Tenacidad:** tras recibir un golpe, los golpes recibidos en el siguiente segundo hacen **−50% de daño** (en lugar de modificar los ticks de invulnerabilidad). Los golpes reducidos no alargan la ventana (rev. 2.7e), para que un daño continuo no quede reducido siempre.
* **Baluarte Incorruptible (hito de subclase 5):** *Baluarte* purga todos los debuffs e inmuniza contra nuevos efectos nocivos mientras dura.

**3. Juggernaut de Carga**
* **Vigor Colosal:** +6% de vida máxima por nivel de subclase (**+60% al nivel 10**). Sumado al +20% de vida de la clase, el total es **+80% (36 HP)**. Con el perk *Cuerpo Colosal* llegaría a +100% (40 HP), que es el tope global.
* **Baluarte de Embestida (hito de subclase 5):** *Velocidad III*, sprint forzado y daño de contacto con knockback de área.
  * Rev. 2.7e: el Paladín corre hacia delante sin parar (gira con la cámara). Al tocar a un enemigo, **3 de daño** y empuje a todos los enemigos en **2,5 bloques**; cada enemigo, como mucho una vez cada 0,5 s. **[MEDIR]**
  * Rev. 2.14: deja una **estela de viento** detrás mientras dura. Al chocar contra **bloques débiles** (tag `rpgclasses:charge_breakable`: hojas, cristal y paneles, puertas, vallas, puertas de valla y trampillas de madera, macetas, telarañas, heno, andamios, escaleras de mano, bambú, caña) los **rompe** (sueltan su objeto) con el sonido propio de cada bloque, hasta 6 por tic. Cada bloque le hace **0,5 + 0,5 × dureza** de daño (hojas ≈ 0,6; cristal ≈ 0,65; valla ≈ 1,5; puerta de madera ≈ 2). Respeta las protecciones de terreno y el modo aventura. **[MEDIR]**

## 🪓 5. Bárbaro

### Equipamiento inicial
* Hacha de hierro. Sin armadura. Durabilidad infinita.
* Llevar cualquier pieza de armadura desactiva Defensa y Velocidad Desarmada. Los escudos no cuentan como armadura.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Furia** | Furia de Heridas: +0,1% de daño por cada 1% de vida perdida (nivel 1), hasta +1% (nivel 10), con **tope de +75% de daño** |
| **Brutalidad** | Daño crítico hasta **+50%** (5% por punto), que se suma al multiplicador base ×1,5 (máximo ×2,0). *(Antes +100%; bajado tras pruebas.)* |
| **Piel de Hierro** | Defensa Desarmada hasta 16 de armadura y 6 de dureza, y Velocidad Desarmada hasta +25% (ambas con 10 puntos) |
| **Estampida** *(rama extra)* | *Embestida*: +50% de distancia (de 6 a 9 bloques) y −25% de cooldown (de 20 a 15 s). Queda latente hasta tener *Embestida* |
| **Curtido** *(rama extra)* | −40% de daño de caída, fuego y explosiones, y −40% de consumo de hambre (ambas con 10 puntos) |

Críticos: ver regla 9 (probabilidad del Bárbaro: **35%**; multiplicador ×1,5 base).

### Perks de rama (1 de 2 al llenar la rama)
| Rama | Opción A | Opción B |
| ----- | ----- | ----- |
| **Furia** | **Furia Desatada:** +0,5% de daño por cada 1% de vida perdida, sin tope y sumado a la pasiva. −50% de curación recibida de todas las fuentes. | **Furia Templada:** −0,15% de daño por cada 1% de vida perdida (resta a la pasiva). +0,5% de curación recibida por cada 1% de vida perdida, de todas las fuentes. |
| **Brutalidad** | **Golpe Demoledor:** +75% de daño crítico adicional y −15 puntos de probabilidad de crítico. | **Golpe Constante:** −35% de daño crítico y +20 puntos de probabilidad de crítico. |
| **Piel de Hierro** | **Piel Reforzada:** +2 de armadura y +1 de dureza desarmado, −15% de velocidad de movimiento. | **Piel Ligera:** −2 de armadura y −1 de dureza desarmado, +15% de velocidad de movimiento. |
| **Estampida** | **Carga Imparable:** *Embestida* atraviesa enemigos y golpea a todos con el 60% del daño, sin aturdir. | **Carga Demoledora:** *Embestida* aturde el doble y termina con una onda de daño, pero +30% de cooldown. |
| **Curtido** | **Carnívoro:** la carne da el doble de saturación. Los demás alimentos dan −30%. | **Aguante:** al bajar del 30% de vida, −20% de daño recibido durante 5 s (ICD 30 s). −10% de curación recibida. |

Notas de los perks:
* **Furia:** la parte de la pasiva base conserva su tope de +75%. "Curación" incluye la regeneración natural por comida, pociones, manzanas, *Sed de Sangre* y curas de aliados (se implementa escalando la cantidad curada, no el ritmo). Con A el máximo natural del perk es +50%, porque la vida no baja del 0%.
* **Brutalidad:** los puntos de probabilidad son absolutos. Con el 35% de probabilidad del Bárbaro y el multiplicador crítico (×1,5 base + 0,5 de la rama = ×2,0), A (20% de probabilidad, ×2,75) y B (55%, ×1,65) dan el mismo daño esperado que sin perk (≈×1,35): cambia la varianza **[MEDIR]**.
* **Piel de Hierro A** deja al Bárbaro en 18/7 y B en 14/5.
* **Estampida:** con Carga Demoledora el cooldown de *Embestida* pasa de 15 a ≈19,5 s. **Curtido:** Carnívoro y Furia Desatada (−50% de curación) se complementan; Aguante usa la curación escalada de Furia (ver pendientes).

### Activas
* **Sed de Sangre (Nivel Total 5):** al matar, cura el 10% de la vida máxima. ICD 1 s.
* **Embestida (Nivel Total 15):** carga de 6 bloques con aturdimiento de Epic Fight. Cooldown 20 s.
* **Furia Berserker (Nivel Total 30):** 10 s de +30% de velocidad de ataque y +25% de daño físico, con +20% de daño recibido. Al activarla, los enemigos a 6 bloques reciben *Debilidad I* durante 6 s. **Cooldown 60 s.**

### Subclases

**1. Indestructible**
* **Vitalidad Salvaje:** +1 HP de vida máxima por nivel de subclase (+10 HP, 5 corazones, al nivel 10). Bonus plano: no cuenta para el tope global de vida.
* **Apuesta con la Muerte (rev. 2.10):** al recibir daño letal se lanza una **moneda** con un **anj** y una **calavera**. **No tiene cooldown ni límite de usos.**
  * Mientras la moneda gira (unos **1,6 s**), el Bárbaro queda a **1 HP**, **inmortal** y **sin poder hacer nada** (ni moverse, atacar, usar objetos, habilidades o conjuros). La moneda se ve girando sobre su cabeza (en primera persona, arriba en la pantalla).
  * Probabilidad del anj: **75%** la primera vez de cada vida y **50%** las siguientes. Se restablece al 75% **solo si el Bárbaro muere**.
  * Al caer la moneda, la cara ganadora aparece **en grande sobre la cabeza** y se aplica el resultado:
    * **Anj:** sobrevive con 1 HP, **1 s de inmortalidad**, **grito** con **1 de daño en área de 3 bloques** y **empuje de unos 5 bloques** a los enemigos.
    * **Calavera:** recibe de nuevo el golpe letal; aún pueden salvarle la Última Resistencia del Orco o el Tótem.
  * Si se desconecta con la moneda en el aire, el resultado se aplica al momento.
* Valor de referencia: supervivientes esperados por vida = 0,75 × (1 + 1) = **1,5** (antes 1,14) **[MEDIR]**.

**2. Berserker Suicida**
* **Fuerza Desmedida:** +25% de daño base progresivo (nivel 10).
* **Última Furia Inmortal:** al recibir daño mortal, inmortalidad aleatoria de **7–17 s** con *Fuerza III* y *Velocidad III*. **Cooldown 5 min.**
  * Solo se activa si existe un agresor eliminable. Si no (entorno, creeper ya explotado, jefe inmune), no funciona y el Bárbaro muere.
  * Si elimina al agresor en ese tiempo, sobrevive con 1 HP y *Debilidad III* 10 s. Si no, muere al terminar.
  * Rev. 2.7e: el agresor que hay que matar queda **brillando**. La muerte cuenta si el golpe final es del Bárbaro. Desconectarse durante la Furia hace que muera al volver.

**3. Corruptor de Plagas**
* **Sangre Resistente:** −100% de duración de *Hambre*, *Veneno* y *Wither* (nivel 10).
* **Golpe Aflictivo:** si el enemigo no tiene el efecto **Plaga**, el golpe se lo aplica. *Plaga* es un efecto propio: 2 HP/s durante 6 s, no se apila, **afecta también a no-muertos** y hace la mitad contra jefes. **Contagio:** si el portador muere con Plaga, se transmite al enemigo más cercano en 5 bloques con la duración restante.
  * Rev. 2.7e: solo cuerpo a cuerpo; la leche cura la Plaga. **Sangre Resistente** escala −10% por nivel (inmune al nivel 10).

## 🗡️ 6. Pícaro

### Equipamiento inicial
* Espada de hierro y armadura de cuero completa. Durabilidad infinita.

### Rasgo base de clase
* **−15% de vida máxima** (17 HP).

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Sombra** | Sigilo Físico (rango de detección `follow_range` hasta −50%) y Backstab (+100% de daño crítico desde invisibilidad), cada uno con 10 puntos |
| **Filo** | Velocidad de ataque hasta **+25%** y daño crítico hasta +75%, cada uno con 10 puntos. *(Antes la velocidad de ataque era +60%; bajada tras pruebas.)* |
| **Viento** | Velocidad de movimiento hasta +35% (10 puntos) |
| **Toxinas** *(rama extra)* | 30% de probabilidad de aplicar *Toxina* al golpear (3% por punto): 1 HP cada 3 s durante 6 s (2 HP en total), no se apila |
| **Acrobacia** *(rama extra)* | −30% de coste de estamina al esquivar y +25% de duración de la invulnerabilidad de la esquiva de Epic Fight (ambas con 10 puntos) |

### Perks de rama (1 de 2 al llenar la rama)
| Rama | Opción A | Opción B |
| ----- | ----- | ----- |
| **Sombra** | **Golpe a Traición:** +25% de daño contra enemigos que no te tengan como objetivo. −20% de duración del sigilo. | **Sigilo Profundo:** +40% de duración del sigilo y +20% de detección (`follow_range` de los mobs). |
| **Filo** | **Ráfaga:** +25% de velocidad de ataque, −12% de daño. | **Estocada:** ignora el 30% de la armadura del objetivo, −15% de velocidad de ataque. |
| **Viento** | **Esquiva:** +15% de probabilidad de esquivar un ataque cuerpo a cuerpo o un proyectil (salta 1 bloque hacia atrás y no recibe daño). −10% de vida máxima. | **Ligereza:** +10% de velocidad de movimiento y +10% de velocidad de ataque. El sigilo se drena un 30% más rápido (13%/s). |
| **Toxinas** | **Dosis Concentrada:** la Toxina hace 1 HP cada 1 s durante 2 s (mismo daño total, más rápido). | **Dosis Prolongada:** la Toxina hace 0,5 HP cada 3 s durante 12 s (mismo daño total) y aplica *Lentitud I* mientras dure. |
| **Acrobacia** | **Contraataque:** tras esquivar con éxito, tu siguiente golpe hace +30% de daño durante 3 s. | **Voltereta Larga:** la esquiva recorre +50% de distancia. −15% de duración de la invulnerabilidad. |

Notas de los perks:
* **Sombra:** "sigilo máximo" se entiende como **duración** (la barra drena un % por segundo, así que su tamaño no cambia la duración). En PvP, "no te tiene como objetivo" significa "no te ha atacado en los últimos 5 s". Con *Provocación* del Paladín, Golpe a Traición da un bono casi permanente en grupo (sinergia buscada).
* **Filo A:** hay que medir los marcos de invulnerabilidad de Epic Fight, porque a más de ~2 golpes/s los golpes extra podrían perderse **[MEDIR]**.
* **Toxinas:** el daño de la Toxina se nerfeó a petición (de 6 a 2 HP por aplicación): la rama queda como control y utilidad más que daño. **Acrobacia:** depende de cómo exponga Epic Fight la estamina y la invulnerabilidad de la esquiva **[MEDIR]**; no se apila con la esquiva del perk Viento A (son sistemas distintos).
* **Viento A:** no esquiva daño de área ni del entorno. ICD 1 s para no esquivar varios golpes de un mismo combo. El salto se cancela si el destino no es seguro (lava, vacío).
* **Viento A** suma −10% de vida al rasgo base (−25% en total, 15 HP).

### Activa (Nivel Total 5): Sigilo por barra
* **Acrobacia con Epic Fight (rev. 2.7g).** En Epic Fight 20.x la esquiva es invulnerable durante toda su animación (no hay marcos aparte). Por eso: *+invulnerabilidad* = ventana extra tras la animación en la que los golpes esquivables fallan y cuentan como esquiva con éxito; *−invulnerabilidad* (Voltereta Larga) = en el último tramo de la animación los golpes entran. Con Acrobacia al máximo y Voltereta Larga, el factor es 1,25 × 0,85 ≈ 1,06. La distancia de la esquiva escala con la velocidad de movimiento, así que Voltereta Larga suma +50 % de velocidad solo mientras dura la esquiva. Contraataque se gasta en el primer golpe (cuerpo a cuerpo o flecha) dentro de los 3 s. **[MEDIR]** en el juego.
* El Pícaro tiene una **barra propia de Sigilo**, separada de la estamina de Epic Fight.
* Agacharse activa la **invisibilidad verdadera**. Drenaje del **10%/s** (10 s de invisibilidad; antes 4%/s, rev. 2.7d); recuperación del **8%/s** fuera del sigilo.
* **Atacar o lanzar un hechizo cuesta el 40% del máximo de la barra.** Si la barra se agota, hay bloqueo hasta el 50%.
* **PvP:** la invisibilidad **ya no se revela por cercanía** a otro jugador (rev. 2.8); solo se pierde al agotarse la barra o al soltar agacharse.

### Subclases

**1. Caminante de Sombras**
* **Agilidad de las Sombras:** la velocidad agachado sube hasta igualar la de caminar al nivel 10.
* **Paso de Sombra:** teletransporte de **8 bloques** hacia donde mira. **Cooldown 20 s.** No atraviesa bloques sólidos ni destinos ocupados.

**2. Ilusionista de Sombras**
* **Invisibilidad Absoluta:** el rango de detección de mobs baja hasta el −75% total (nivel 10).
* **Señuelo:** al hacerse invisible, crea un clon que absorbe el agro y huye. Desaparece en humo al terminar la invisibilidad. **Cooldown 10 s** (rev. 2.7f).

**3. Hoja Arcana**
* **Pasivas mágicas:** +15% CDR, +15% Spell Power, +15% de regeneración de maná y +150 de maná máximo.
* **Armonía Arcana:** reduce el muro de **Magia** un 10% por nivel de subclase (anulado al nivel 10).
* **Emboscada Arcana:** el primer hechizo lanzado desde invisibilidad hace **+60% de daño**. ICD 8 s. Cuesta el 40% de la barra de Sigilo.

## 🏹 7. Ranger

### Equipamiento inicial
* Arco de madera, carcaj y flechas básicas, armadura de cuero. Durabilidad infinita.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Puntería** | Daño con arcos y ballestas hasta +30% y Visión de Halcón (zoom hasta 35%), cada uno con 10 puntos |
| **Cadencia** | Velocidad de carga del arco hasta +30% |
| **Alcance** | Velocidad de proyectiles hasta +100%, **solo alcance y trayectoria: no aumenta el daño** |
| **Flechas Especiales** *(rama extra)* | +25% de duración y potencia de los efectos de las flechas con poción (2,5% por punto) |
| **Retirada** *(rama extra)* | Tras recibir un golpe cuerpo a cuerpo, +25% de velocidad de movimiento durante 2 s. ICD 5 s (con 10 puntos) |

El daño de una flecha se calcula **sin** el efecto de la velocidad añadida por la clase.

### Perks de rama (1 de 2 al llenar la rama)
| Rama | Opción A | Opción B |
| ----- | ----- | ----- |
| **Puntería** | **Tirador Firme:** +25% de daño si llevas ≥1 s sin moverte; −15% si disparas en sprint o en el aire. | **Tirador Móvil:** +20% de daño mientras te mueves; −15% si estás quieto. |
| **Cadencia** | **Tirador Ágil:** tensar el arco no te ralentiza. −15% de velocidad de carga. | **Contraataque Veloz:** tras recibir un golpe cuerpo a cuerpo, tu siguiente flecha en 3 s sale con carga instantánea y el 70% del daño. ICD 8 s. |
| **Alcance** | **Perforante:** la flecha atraviesa a un enemigo adicional (el segundo recibe el 60% del daño). −10% de daño al primer impacto. | **Marca de Cazador:** el impacto marca al objetivo 6 s (resplandor, ICD 10 s por objetivo). El marcado recibe +12% de daño de todas las fuentes, incluido tú. |
| **Flechas Especiales** | **Flecha Explosiva:** las flechas normales explotan al impactar (2 bloques, 30% del daño, sin romper bloques). −10% de daño directo. | **Flecha Pegajosa:** el impacto aplica *Lentitud I* 2 s. −10% de velocidad de proyectil. |
| **Retirada** | **Salto Evasivo:** el primer golpe cuerpo a cuerpo cada 12 s te hace saltar 4 bloques hacia atrás sin recibir daño. | **Trampa de Cazador:** tras recibir un golpe cuerpo a cuerpo dejas una trampa bajo tus pies durante 3 s que aplica *Lentitud II* a quien la pise (ICD 12 s). |

Notas de los perks:
* **Tirador Ágil:** hay que comprobar cómo trata Epic Fight el movimiento al apuntar **[MEDIR]**. Con Francotirador, que ya recorta la velocidad de carga de la base, revisar la interacción (ver pendientes).
* **Flechas Especiales:** riesgo de solaparse con el futuro Alquimista (efectos de poción); la rama solo mejora flechas con poción y las normales con sus perks. **Retirada:** el Salto Evasivo no se activa con daño de área ni del entorno; revisar la interacción con Contraataque Veloz (Cadencia B), que se dispara con el mismo golpe **[MEDIR]**.
* **Marca de Cazador:** su valor personal es bajo (≈+7% de media); el de grupo es alto, y también ayuda a las mascotas del Maestro de Bestias.

### Activas
* **Conservación de Munición (Nivel Total 5):** 33% de no consumir munición.
* **Doble Salto (Nivel Total 15).**
* **Disparo Pesado (Nivel Total 30):** el contador empieza cuando el arco está **tensado al máximo**. A 2 / 4 / 6 s: +25% / +50% / +100% de daño. **Suma aditiva con el bonus de daño de la clase** (máximo ×2,3).

### Subclases

**1. Maestro de Bestias**
* **Vínculo Salvaje:** +30% de vida máxima, +30% de daño (rev. 2.13; antes +35%) y +25% de velocidad para mascotas domesticadas (lobos, gatos, loros, etc.). Las invocaciones no cuentan. El bonus aplica a un **máximo de 8 mascotas**.
* **Salvaguarda de Bestias:** si una mascota va a recibir daño letal, queda a 1 HP, se teletransporta al punto de reaparición del Ranger y se sienta. **Cooldown de 5 min por mascota**; permanece sin órdenes 60 s.
  * Rev. 2.7e: **Vínculo Salvaje** cuenta las 8 mascotas más cercanas (64 bloques); el bonus se quita al alejarse o perder la subclase. Sin cama/ancla, la mascota va al spawn del mundo. "Sin órdenes" = no se puede levantar ni montar durante 60 s. Si el Ranger está desconectado, la Salvaguarda no actúa.
  * Rev. 2.11: **Libro de Vínculos.** Objeto propio del Maestro de Bestias (receta libro + hueso + correa), que forma parte de su kit al coger la subclase y se retira al perderla. Muestra cada mascota vinculada con imagen (huevo de aparición de su especie), nombre y estadísticas. **Máximo 8 vínculos**; vincular o romper exige estar a **16 bloques o menos**; las mascotas no cargadas no se pueden romper desde el libro. Permite elegir adónde se teletransporta la mascota al morir (la Salvaguarda lo usa): **3 puntos guardados + el punto de aparición**.

**2. Explorador Ágil**
* **Caminante de la Naturaleza:** **+5%** de velocidad adicional (sujeta al tope global). *(Antes +15% con Suerte y +10% hasta la rev. 2.13.)*
* **Triple Salto y Escalada de Paredes:** tercer salto y escalada al agacharse junto a una pared. Si tensa un arco o ballesta estando adherido a la pared, se suelta.
  * Rev. 2.7e: adherido no cae; subes con **saltar** o **avanzar**. Agacharse en el suelo frente a la pared y avanzar también trepa.

**3. Francotirador**
* **Balística Extrema:** velocidad de proyectil hasta +300% (solo alcance) y daño **+75% en total** (incluye el +30% de la base). La velocidad de carga de la clase base se reduce **un 10% de su valor total por nivel de subclase** (a 0 en el nivel 10).
* **Disparo Letal de Francotirador:** tabla de Disparo Pesado a 1 / 2 / 3 / 6 s de tensado: +25% / +50% / +100% / +150%. Aditiva con el daño de clase (máximo ×3,25).

## 🥋 8. Monje

### Equipamiento inicial
* Sin loot inicial. Llevar cualquier pieza de armadura desactiva sus pasivas desarmadas (también *Cuerpo Marcial*) y **borra todas sus cargas**; mientras la lleve no acumula cargas.

### Rasgo base de clase: Cuerpo Marcial (rev. 2.9)
* Da un **suelo** que crece de forma lineal con los puntos de **clase base** (los de subclase no cuentan) y llega al máximo con 30: **armadura 10 / dureza 4** y un bonus de daño desarmado equivalente a **⅔ del que daba el rasgo con las ramas llenas hasta la rev. 2.8** (≈ 72% del DPS objetivo) **[MEDIR]**.
* El resto del poder del Monje se gana con las **cargas de postura** (ver abajo). Las ramas Puño, Piel y Viento **ya no dan stats planos**: potencian las cargas de Fuego, Piedra y Agua.
* **Objetivo de diseño [MEDIR]:** con Fuego a 10 cargas y la rama Puño llena, el DPS desarmado equivale al de una espada de diamante en Epic Fight; daño por golpe = DPS objetivo ÷ golpes por segundo medidos con el moveset de puños.

### Cargas de postura (rev. 2.9)
**Posturas de Combate Elemental (Nivel Total 5).** Siempre hay una postura activa: al desbloquearlas se elige una. Cambio libre con **2 s de cooldown** (3 s con *Flujo Continuo*).

**Reglas generales:**
1. **Contador de cargas.** Solo se acumulan cargas de la postura activa. Máximo **10** (+2 con 5 puntos de Maestría de Postura y +4 con 10; *Postura Dominante* lo modifica).
2. **Efecto propio.** Se dispara en el siguiente golpe desarmado en dos casos: al llegar al **máximo** de cargas, o antes con **Shift + golpe desarmado** teniendo **≥10 cargas** (también con una tecla de habilidad, y sobre agua o lava el golpe no hunde al Monje). Gasta **todas** las cargas y su fuerza es **cargas gastadas ÷ 10**.
3. **Descarga.** Al cambiar de postura se gastan todas las cargas de la que se deja, con un efecto de esa postura proporcional a las cargas. Con 0 cargas no pasa nada.
4. **Decaimiento.** Cada postura pierde cargas de forma progresiva cuando deja de recibirlas (ver tabla).
5. **Desvío de proyectiles (Nivel Total 15)** da cargas: **+1 en Fuego, +3 en Agua y +1 en Piedra**.
6. **Aturdimientos compartidos.** Un aturdimiento del Monje (Piedra o *Puño de Inhabilitación* de Palma Pesada) solo se aplica si han pasado al menos **su propio ICD** (8 s Piedra, 6 s Palma Pesada) desde el último aturdimiento del Monje al mismo objetivo, sea cual sea su origen.
7. **Quemaduras acumulables.** Las quemaduras del Monje (*Golpe Ígneo*, *Manifiesto* de Guardián Trimundial) **suman su duración** a la que el objetivo ya tenga, con un tope de **15 s [MEDIR]**.
8. **Umbrales de daño.** Los umbrales de daño recibido (≥2 HP en Piedra, ≥4 HP con *Eco de Piedra*) se miden **antes de la armadura**.

| | **Fuego: Combo** | **Piedra: Temple** | **Agua: Fluir** |
| ----- | ----- | ----- | ----- |
| **Ganas carga con** | Cada golpe desarmado encadenado | Cada golpe recibido de ≥2 HP (ICD 0,5 s) | Esquivar o desviar un proyectil (+3) y cada 2 s de sprint (+1) |
| **Cada carga da** | +3% de daño (máx. +30% con 10) | +0,75 de armadura y +0,25 de dureza | +2,5% de velocidad y salto (máx. +25% con 10, sujeto a los topes globales) |
| **Coste fijo** | Armadura y dureza del suelo −25% | −15% de velocidad de movimiento y −30% de salto **[MEDIR]** | Daño del suelo −25% |
| **Decaimiento** | Tras 1,5 s sin golpear, pierdes 1 carga cada 0,5 s | Tras 3 s sin recibir golpes, pierdes 1 carga cada 2 s | Pierdes 1 carga por segundo sin moverte |
| **Efecto propio** | **Golpe Ígneo** (un objetivo): +5% de daño extra y 5% de armadura del objetivo ignorada por carga gastada (10 cargas: +50% y 50%; tope del 80% de armadura ignorada) e incendia 4 s | **Aturdimiento** de 0,15 s por carga (1,5 s con 10; la mitad en jefes; ICD 8 s por objetivo) | **Contracorriente:** tu siguiente golpe aplica *Lentitud II* durante 0,2 s por carga y te da una **esquiva garantizada** del siguiente golpe recibido durante 0,3 s por carga (sin empuje) |
| **Descarga al cambiar** | Onda de fuego en 3 bloques: 5% de un golpe por carga | Absorción de 0,5 HP por carga durante 5 s (sin tope) | Área de 3 bloques: *Lentitud I* durante cargas ÷ 2 s y empuje de cargas ÷ 2 bloques |

* **Tope de armadura de Piedra:** la armadura total no pasa de **20** ni la dureza de **8**. Las cargas que ya no puedan sumar armadura por el tope dan **+10% de resistencia al empuje** cada una (máx. 100%).
* **Fuego:** el Golpe Ígneo (un objetivo, rompe armadura) y la descarga (área, a cualquier número de cargas) están pensados para no pisarse; con 10 cargas se decide entre rematar a un objetivo o soltar la onda al cambiar.
* **Piedra:** el aturdimiento y la absorción no dependen de la armadura, así que las cargas por encima del tope siguen siendo útiles.
* **Interfaz (rev. 2.11):** las cargas se ven como una fila de símbolos de **fuego, agua o piedra encima de la barra de vida** (llenos hasta las cargas actuales, vacíos hasta el máximo; laten al llegar al umbral y brillan con el efecto preparado). Los **puños** del Monje muestran partículas del elemento, más densas y vivas cuantas más cargas tiene; los demás jugadores también las ven.

### Ramas
| Rama | Pasiva y máximo |
| ----- | ----- |
| **Puño** | Potencia las cargas de **Fuego**: +33% al buff de daño por carga (de 3% a 4%) y +33% a la potencia de la onda de descarga |
| **Viento** | Potencia las cargas de **Agua**: +33% al buff de velocidad y salto por carga (de 2,5% a 3,33%) y +33% a la *Lentitud* y al empuje de la descarga. Ya no da un bonus plano de velocidad |
| **Piel** | Potencia las cargas de **Piedra**: +33% al buff de armadura y dureza por carga (de 0,75 / 0,25 a 1 / 0,33) y +33% a la absorción de la descarga |
| **Maestría de Postura** *(rama extra)* | Cargas máximas de todas las posturas: +2 con 5 puntos y +4 con 10 |
| **Meditación** *(rama extra)* | Agachado y quieto 2 s, curas 0,5 HP/s hasta el 60% de tu vida. Recibir daño la interrumpe. Al dejar de meditar, recibes **4 cargas** de la postura actual que no decaen durante 10 s |

### Perks de rama (1 de 2 al llenar la rama)
| Rama | Opción A | Opción B |
| ----- | ----- | ----- |
| **Puño** | **Llama Constante:** el decaimiento de las cargas de Fuego es un 50% más lento, pero el buff de daño por carga baja un 20%. | **Ignición Temprana:** el umbral del Shift + golpe en Fuego baja de 10 a **6 cargas**, pero el daño extra del *Golpe Ígneo* es de +4% por carga (en vez de +5%). Sigue disparándose solo al llegar al máximo. |
| **Viento** | **Impulso de Carrera:** tras 2 s de sprint, el siguiente golpe desarmado hace +30% de daño y +50% de empuje. | **Pies Ligeros:** ignora telarañas, arena de almas, miel, nieve en polvo y la ralentización al comer o usar objetos. −10% de velocidad de movimiento. |
| **Piel** | **Piel Espiritual:** −2 de armadura y −1 de dureza, y −30% de daño mágico y elemental recibido (multiplicativo con otras fuentes, con tope del 85% de reducción combinada). | **Eco de Piedra:** la absorción de la descarga de Temple se duplica, pero Temple solo gana carga con golpes recibidos de ≥4 HP (antes de armadura). |
| **Maestría de Postura** | **Postura Dominante:** eliges una postura favorita con **+3 cargas máximas**; las otras dos tienen **−3**. Con la rama llena: favorita 17, otras 11. | **Flujo Continuo:** al cambiar de postura conservas el 30% de las cargas (redondeo hacia abajo) como cargas de la nueva postura, y la descarga gasta solo el resto. El cooldown de cambio sube +1 s. |
| **Meditación** | **Trance Profundo:** curas 1 HP/s hasta el **80%** de tu vida, pero tardas 4 s quieto en empezar. | **Mente Clara:** purgas un efecto negativo cada 5 s mientras meditas (la curación no se reduce). |

Notas de los perks:
* **Postura Dominante:** el umbral de 10 cargas del efecto propio es fijo, así que las tres posturas lo alcanzan (las no favoritas tienen máximo 11) y la favorita pega más fuerte, porque la fuerza es cargas ÷ 10 (con 17 cargas, 170%). Con *Eco de Piedra*, una favorita de 17 cargas podría absorber ≈ 17 HP **[MEDIR]**.
* **Ignición Temprana:** con 6 cargas el *Golpe Ígneo* hace +24% de daño extra, así que da un finalizador más frecuente pero más flojo. No es compatible con *Llama Constante* (misma rama).
* **Flujo Continuo:** las cargas conservadas cuentan como cargas de la nueva postura y están sujetas a su decaimiento y a su máximo.
* **Meditación:** vale sobre todo fuera de combate (el contenido es PvE). Con *Mente Clara* purga también *Veneno* y *Wither*.
* **Viento:** *Impulso de Carrera* y *Pies Ligeros* no suben velocidad ni salto, por lo que no chocan con los topes globales.
* **Piel Espiritual:** el equilibrio está en que ≈40% del daño recibido sea mágico o elemental; con Iron's y los jefes de Cataclysm se cumple en buena parte del contenido. Contra daño físico la vida efectiva baja ≈15%; contra magia sube ≈43%.

### Implementación (rev. 2.11, decisiones pendientes de confirmar)
* **Tecla del efecto propio:** agachado + tecla de posturas prepara el efecto para el siguiente golpe si tienes el umbral; Shift + golpe también funciona. Sin umbral y con *Postura Dominante*, agachado + tecla fija la postura actual como favorita.
* **Ciclo de posturas:** Agua → Piedra → Fuego. Hasta la primera pulsación no hay postura (solo el suelo).
* **Llama Constante:** "50% más lento" = los tiempos de espera y de pérdida de Fuego se duplican.
* **Descarga de Piedra:** la absorción que quede a los 5 s se retira. **Contracorriente:** la esquiva garantizada solo evita golpes de una criatura (no el entorno).
* **Hundirse en agua o lava con Shift** (Paso Ligero): sin cambios todavía; pendiente de probar con Epic Fight.
* Los perks quitados (*Flujo*, *Onda Elemental*, *Postura Híbrida*, *Calma Interior*) dejan la rama llena sin perk: se vuelve a elegir uno nuevo.

### Activas
* **Posturas de Combate Elemental (Nivel Total 5):** ver *Cargas de postura*.
* **Desvío y Reflejo de Proyectiles (Nivel Total 15):** cooldown 5 s. Reduce el daño de un proyectil en un valor igual al Nivel Total de Clase. Si lo reduce a 0, lo devuelve a su origen. Da cargas de postura (regla 5).
* **Paso Ligero y Marcha Elemental (Nivel Total 30):** inmunidad al daño por caída y caminar sobre el **agua** sin hundirse. Caminar sobre la **lava** sin daño solo es posible en **Postura del Fuego**.
  * Rev. 2.7e: agacharse permite hundirse en el agua; sobre la lava (en Postura del Fuego) se anulan el daño de lava y la quemadura. Rev. 2.9: el golpe con Shift del efecto propio no hunde al Monje.

### Subclases

**1. Maestro de la Brisa**
* **Cefiro Marcial:** bonus de velocidad hasta +80% y de salto hasta +120% (sujetos a los topes globales). Como Viento ya no da bonus plano, el tope global se alcanza menos que antes.
* **Impulso de Viento** (sustituye al doble salto): dash aéreo horizontal con **1 carga y 10 s de recarga**.
  * Rev. 2.7e: es una **activa** (tecla de habilidad); solo funciona en el aire. Impulso ≈ 6 bloques **[MEDIR]**.

**2. Palma Pesada**
* **Fuerza de Choque:** empuje de los golpes desarmados hasta +100% y alcance de ataque hasta **+50%** al nivel 10.
* **Golpe Cargado (rev. 2.14):** agachado y con la mano vacía, la palma se carga hasta **1 s** (suena al completarse). El golpe agachado hace **+20% (nivel 1) a +50% (nivel 10)** de daño y **hasta +100% de empuje** según la carga; con la carga completa **rompe la guardia** (desactiva el escudo de un jugador). Compatible con el Shift + golpe de las cargas del Monje.
* **Impacto (rev. 2.14):** durante **1 s** tras un golpe, si el enemigo lanzado **choca contra un bloque o contra otro enemigo**, recibe **2 + 20–40% del golpe** de daño (el otro enemigo también) y queda aturdido. No afecta a jefes.
* **Puño de Inhabilitación:** desde la rev. 2.14 se aplica **con cada Impacto** (antes 15% por golpe), **ICD de 6 s por objetivo**. Aplica *Lentitud II* y *Ceguera* durante 1,5 s, más aturdimiento de Epic Fight. Comparte el registro de aturdimientos del Monje (regla 6).
  * Rev. 2.7e / 2.12: con Epic Fight el aturdimiento es el suyo; sin él, el del mod (lentitud y debilidad fuertes). La rama **Fuerza de Choque** ya multiplica el empuje de los golpes desarmados.

**3. Guardián Trimundial**
* **Afinidad Corporal de Postura:** Fuego: hasta 75% de reducción de daño por fuego y lava. Agua: 33% de reducción de daño de proyectiles. Piedra: 20% de reducción de daño cuerpo a cuerpo.
* **Manifiesto de los Elementos:** Fuego: los golpes desarmados incendian al objetivo 6 s (la quemadura se suma a la del *Golpe Ígneo*, regla 7). Agua: nado +100% y *Respiración Acuática*. Piedra: inmunidad a *Veneno*, *Wither* y *Hambre*, y refleja el 15% del daño físico recibido.
  * Rev. 2.7e: todo depende de la **postura actual**. El reflejo es daño de espinas y solo se aplica a golpes cuerpo a cuerpo y proyectiles.

## 📋 9. Pendiente de medición o decisión

* **Rework del Monje (2.9):**
  * Medir los **golpes por segundo** de los puños de Epic Fight y, con ellos, los tiempos de decaimiento y el tiempo hasta 10 cargas (objetivo: ≈ 4 s en Fuego).
  * Comprobar el **suelo** (daño ≈ 72% del DPS objetivo, armadura 10 / dureza 4) y que un Monje sin cargas es jugable.
  * **Fuego con Maestría o Dominante:** con 14–17 cargas el buff llegaría a +56–68% de daño con Puño llena; valorar un tope.
  * **Velocidad:** sin cargas de Agua el Monje ya no tiene bonus de velocidad (−35% frente a la 2.8). Piedra queda con −15% de velocidad y −30% de salto **[MEDIR]**.
  * **Farmeo de cargas fuera de combate (decisión del usuario: sin restricción):** correr da cargas de Agua y golpear a cualquier entidad da cargas de Fuego. Vigilar si se abusa.
  * **Absorción de Piedra sin tope:** hasta 8,5 HP con 17 cargas y ≈ 17 HP con *Eco de Piedra*.
  * **Shift + golpe:** comprobar el uso de Shift en Epic Fight y el hundimiento en agua y lava.
  * **Aturdimiento compartido** y **tope de quemadura de 15 s**.
  * La sección 10 usa cargas medias y máximas como estimación; recalcular con datos medidos.

* Calibrar en el juego: DPS desarmado del Monje con los puños de Epic Fight, y el CDR total real (clase + equipo + perks) frente a posibles topes de Iron's (el Mago con Cadencia Arcana y Elementalista llegaría a −60%).
* Confirmar las listas de armas pesadas y ligeras con las categorías reales de Epic Fight y sus mods de armas.
* Escudos: definir con Epic Fight qué cuenta como "cooldown de recuperación" del escudo (desactivación por hacha, rotura de guardia) y medir su valor base.
* Subclase temprana (rev. 2.5): medir el poder a mitad de camino (base 15–25 con subclase parcial); la sección 10 solo cubre el estado final. Decidido: el Nivel Total (hitos de activas) incluye los puntos de subclase.
* Críticos (rev. 2.6): medir la probabilidad base del 30% y los ajustes de Bárbaro (35%) y Pícaro (25% con crítico seguro desde invisibilidad). Los perks de Brutalidad se han calculado con esos valores y con el multiplicador ×1,5: revisarlos si cambian.
* **Rasgos base de clase (2.7):** confirmar que se aplican completos desde la elección de clase (propuesta) o si deben escalar con los puntos como los muros.
* **Perks de rama (2.7):**
  * Confirmar que el perk solo cambia al reiniciar la clase (propuesta) o si se permite cambiarlo libremente.
  * Poder del Mago: medir la distancia de impacto con proyectiles, rayos y conjuros de área. Reserva: medir la regeneración base de Iron's para igualar A y B en 45–60 s de combate.
  * Furia del Bárbaro: implementar "curación" escalando la cantidad curada (incluye regeneración natural, que consume el doble de comida por vida recuperada).
  * Filo y Viento del Pícaro: medir los marcos de invulnerabilidad de Epic Fight y comprobar la compatibilidad del esquive del perk con el esquive propio de Epic Fight.
  * Tirador Ágil (Ranger): medir cómo trata Epic Fight el movimiento al apuntar, y revisar la interacción con la reducción de velocidad de carga del Francotirador. Contraataque Veloz: decidir si la flecha instantánea cuenta como arco tensado al máximo a efectos de crítico.
* **Desbalances residuales (ver sección 10):** tras la 2.9 el Monje (−1% con cargas medias, +13% con cargas máximas) queda cerca de la media; Ranger (+4%) es la clase más alta y Mago (−3%) la más baja. Entre las subclases, Explorador Ágil (+12%) y Maestro de Bestias (+10%) arriba; Palma Pesada (−9%) y Mago de Batalla (−8%) abajo. Rebalancear con datos medidos.
* **Ramas extra (regla 13):** medir en juego (Iron's: atributos de invocaciones y tiempo de lanzamiento; Epic Fight: estamina e invulnerabilidad de la esquiva, caída lenta); las 10 ramas nuevas no entran en la sección 10.
* **Escuela sagrada (regla 14):** confirmar que el −100% fijo (sin escalado por puntos) no afecta a efectos sagrados de objetos que no usan Spell Power.
* Clases futuras (diseño aplazado por decisión del usuario): Alquimista (efectos de poción), Nigromante (invocador) y Druida (transformación en bestias). Riesgos técnicos anotados: Druida frente a Epic Fight, pociones frente a la definición de la disciplina Arco, y solapamiento con el Mago.

## 📊 10. Estimación de balance (valores estimados, no medidos en el juego)

**Método.**
* **Movilidad** y **Utilidad:** valor estimado 0–10 por criterio de diseño (la utilidad de Paladín y Pícaro depende del equipo y del contenido).
* **Resistencia:** vida efectiva (HP) contra golpes de 15, sin encantamientos ni Resistencia, como media geométrica entre equipo de hierro (15/0) y de diamante (20/8) con los muros de armadura aplicados, multiplicada por el **factor de escudo** = 1 + 0,5 / ((1 + 2w)(1 + w)), donde w es el muro de Escudos (afín = 1,5; −100% = 1,08). Se pasa a 0–10 con 2×log₂(HP/10). Incluye Baluarte y Provocación en el Paladín.
* **DPS:** índice sostenido (1,00 = jugador sin clase con el mismo arma o los mismos hechizos), pasado a 0–10 con 3 + 4×(índice − 1).
* **Total:** media de las cuatro puntuaciones.
* **Supuestos:** 30% de críticos, Bárbaro al 60% de vida, un escudo bloquea ≈50% del daño cuando está disponible, ritmo de 387 XP/día. **Monje (rev. 2.9):** con Puño, Piel y Viento llenas, usando en cada criterio la postura que lo favorece (Agua para movilidad, Piedra para resistencia, Fuego para DPS) y con **cargas medias** (5 de 10) en las tablas; con cargas máximas (10) se da el resultado en la lectura.
* **Modelo del Monje:** suelo de daño = 72% del daño de la 2.8 (15% base + 85% de bonus × ⅔). Fuego con 5 cargas: +20% de daño y +5% por el finalizador, índice 1,05 (con 10 cargas: +40%, índice 1,17). Piedra con 5 cargas (Piel llena): 15 de armadura y 5,7 de dureza, 37,5 HP efectivos (con 10 cargas: 20 / 7,3 y 60,6 HP). Movilidad: 5,5 con 5 cargas de Agua (+17% de velocidad) y 6,5 con 10 (+33%). Utilidad +0,5 por el aturdimiento, la esquiva garantizada y las cargas por desvío.
* **Ajustes manuales acumulados sobre el modelo (sin recalcular todo; los perks y las ramas extra no se incluyen porque se diseñaron neutros):** 2.4: Monje con Piel 16/6 (resistencia 40,8) y Explorador Ágil con menos movilidad. 2.6: los críticos del Bárbaro (35%) y del Pícaro (25%) no están recalculados; las tablas usan el 30%. 2.7: vida Mago ×0,8, Paladín ×1,2 (Juggernaut ×1,125), Pícaro ×0,85; Paladín movilidad 2,5; Brutalidad (DPS Bárbaro ×0,9, de 1,95 a 1,75); Filo (DPS Pícaro ×0,78, de 1,91 a 1,49). 2.7b: Monje y subclases −0,5 de movilidad; Explorador Ágil movilidad 8 y utilidad 7; Maestro de Bestias DPS 2,14; Bárbaro y subclases +1 de utilidad; Corruptor DPS 2,00; Mago de Batalla resistencia 44,0; Hoja Arcana DPS 1,55. 2.7c (escuela sagrada): Paladín utilidad 7; Guardián Bendito utilidad 9; Mago y sus subclases −0,5 de utilidad. **2.9:** Monje y sus tres subclases recalculados con cargas de postura (ver modelo del Monje); las subclases del Monje aplican el mismo cambio de resistencia (×0,92) y de DPS (−0,12) que la clase, y la movilidad de Palma Pesada y Guardián Trimundial baja a 5,5.
* **Límite del método:** los muros de Magia, Arco y armas solo afectan a la puntuación a través de Armadura y Escudos; los demás muros impiden híbridos, pero este modelo no los ve. Las cifras de la 2.4 y la 2.7 son ajustes manuales sobre el modelo anterior.

### Clases (nivel 30)

| Clase | Movilidad | Resistencia (HP efectivos) | DPS (índice) | Utilidad | Total |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Mago | 3 | 26,8 (2,8) | 2,05 (7,2) | 6,5 | 4,88 |
| Paladín | 2,5 | 122,5 (7,2) | 1,15 (3,6) | 7 | 5,08 |
| Bárbaro | 5 | 56,5 (5,0) | 1,75 (6,0) | 4 | 5,00 |
| Pícaro | 6 | 23,5 (2,5) | 1,49 (5,0) | 7 | 5,11 |
| Ranger | 5 | 50,1 (4,7) | 1,61 (5,4) | 6 | 5,27 |
| Monje (cargas medias) | 5,5 | 37,5 (3,8) | 1,05 (3,2) | 7,5 | 5,00 |

### Subclases (nivel 40)

| Subclase | Movilidad | Resistencia (HP efectivos) | DPS (índice) | Utilidad | Total |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Evocador Caótico | 3 | 26,8 (2,8) | 2,30 (8,2) | 7,5 | 5,38 |
| Elementalista | 3 | 28,2 (3,0) | 2,40 (8,6) | 7,5 | 5,53 |
| Mago de Batalla | 3 | 44,0 (4,3) | 2,05 (7,2) | 6,5 | 5,25 |
| Guardián Bendito | 2,5 | 140,9 (7,6) | 1,15 (3,6) | 9 | 5,68 |
| Bastión Inflexible | 3,5 | 153,1 (7,9) | 1,15 (3,6) | 7 | 5,49 |
| Juggernaut de Carga | 4,5 | 183,8 (8,4) | 1,20 (3,8) | 6 | 5,68 |
| Indestructible | 5 | 110,1 (6,9) | 1,75 (6,0) | 6 | 5,98 |
| Berserker Suicida | 6 | 67,7 (5,5) | 2,16 (7,6) | 5 | 6,03 |
| Corruptor de Plagas | 5 | 62,1 (5,3) | 2,00 (7,0) | 5 | 5,58 |
| Caminante de Sombras | 8 | 28,2 (3,0) | 1,49 (5,0) | 8 | 5,99 |
| Ilusionista de Sombras | 7 | 35,4 (3,7) | 1,48 (4,9) | 8 | 5,89 |
| Hoja Arcana | 6 | 23,5 (2,5) | 1,55 (5,2) | 8 | 5,43 |
| Maestro de Bestias | 5 | 65,2 (5,4) | 2,14 (7,6) | 7 | 6,25 |
| Explorador Ágil | 8 | 57,6 (5,0) | 1,61 (5,4) | 7 | 6,35 |
| Francotirador | 5 | 50,1 (4,7) | 2,05 (7,2) | 5 | 5,46 |
| Maestro de la Brisa (cargas medias) | 8 | 41,5 (4,1) | 1,05 (3,2) | 7,5 | 5,70 |
| Palma Pesada (cargas medias) | 5,5 | 43,3 (4,2) | 1,13 (3,5) | 7,5 | 5,19 |
| Guardián Trimundial (cargas medias) | 5,5 | 47,1 (4,5) | 1,18 (3,7) | 8,5 | 5,55 |

### Lectura
* **Clases:** totales entre 4,88 y 5,27 (media 5,06), es decir, entre −3% y +4%. Mago (−3%) y Bárbaro (−1%) quedan en la parte baja; Ranger (+4%) es el más alto; Paladín (0%), Pícaro (+1%) y Monje (−1%) quedan cerca de la media. El muro de Magia sin sagrada sube al Paladín, y perder la sagrada baja al Mago.
* **Monje:** depende de las cargas, así que su puntuación es un rango. Con **cargas medias** (5) el total es **5,00**; con **cargas máximas** (10) sube a **5,72** (movilidad 6,5, resistencia 60,6 HP, DPS 1,17 y utilidad 7,5), que lo sitúa en +13% de la media. Con cargas máximas, las subclases salen a 6,3 (Maestro de la Brisa), 5,9 (Palma Pesada) y 6,3 (Guardián Trimundial). Dominante y Maestría pueden empujarlo por encima de eso (ver pendientes).
* **Subclases:** totales entre 5,19 y 6,35 (media 5,69), entre −9% y +12%. Las más altas: Explorador Ágil (+12%) y Maestro de Bestias (+10%); las más bajas: Palma Pesada (−9%) y Mago de Batalla (−8%).
* **Maestro de Bestias:** su DPS depende de hasta 8 mascotas y varía mucho entre mobs normales y jefes; el valor mostrado es una media aproximada.
* **Ramas extra y perks:** no entran en estas tablas. Los perks se diseñaron como laterales (≈0 en el total), con dos excepciones a vigilar al medir: Furia Desatada (Bárbaro) y Golpe a Traición (Pícaro) pueden subir el DPS en grupo.
