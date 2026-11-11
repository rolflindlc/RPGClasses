# Documento de Diseño v2: Mod de Clases y Árboles de Habilidades para Minecraft Java

Versión 2 del diseño (revisión 2.7c, en desarrollo). Plataforma prevista: Forge 1.20.1 con Iron's Spells and Spellbooks, Epic Fight, Mowzie's Mobs y Cataclysm. Servidor principalmente PvE con algo de PvP. Objetivo del mod: sistema de clases balanceadas y programado de forma estructurada y orientada a la expansibilidad (clases futuras previstas: Alquimista, Nigromante, Druida).

Los valores marcados con **[MEDIR]** son objetivos de diseño que deben calibrarse en el juego real antes de darlos por cerrados.

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
    * Algunas clases tienen un modificador de atributos fijo, ligado a su identidad: **Mago −20% de vida máxima** (16 HP); **Paladín +20% de vida máxima** (24 HP) **y −10% de velocidad de movimiento**; **Pícaro −15% de vida máxima** (17 HP).
    * Se aplican **completos desde que se elige la clase** (a diferencia de los muros, que escalan con los puntos). Propuesta; ver pendientes.
    * Son aditivos con los demás bonos de vida de la clase, la subclase y los perks, y cuentan para el tope global de la regla 7.

13. **Ramas extra: 5 ramas por clase, máximo 3 usables.**
    * Cada clase tiene **5 ramas de pasivas** (30 ramas y 60 perks en total, cada rama con sus 2 perks de la regla 11). El jugador solo puede usar **3**: en cuanto pone **al menos 1 punto en tres ramas distintas**, las otras dos quedan **bloqueadas** hasta que reinicie la clase.
    * Llegar al nivel 30 sigue exigiendo llenar 3 ramas de 10 puntos (30 base). Los muros, los hitos de activas y el coste de XP no cambian.
    * **Interfaz:** las ramas bloqueadas se muestran **oscurecidas y con un candado**. Al reiniciar la clase se recuperan todos los puntos y se pueden volver a elegir las 3 ramas libremente.
    * Estado del diseño de las ramas 4.ª y 5.ª: **hecho para las seis clases** (ver cada clase).

14. **Escuela sagrada exclusiva del Paladín (rev. 2.7c).** Todas las clases menos el Paladín tienen un **muro de −100% de poder mágico en la escuela sagrada** de Iron's (incluidas las curaciones), **además** de su muro de Magia general. Es fijo desde que se elige la clase (no escala con los puntos), así que los conjuros sagrados no hacen nada fuera del Paladín, ni con libros, varitas o pergaminos. El muro de Magia del Paladín no se aplica a la sagrada.
    * **Curación en clases sin Paladín:** se cura **comiendo y con objetos** (pociones, manzanas doradas, etc.); no hay un sustituto sagrado.

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

**3. Mago de Batalla**
* **Concentración Inquebrantable:** los conjuros con tiempo de lanzamiento no se interrumpen ni sufren knockback al recibir daño.
* **Armonía Marcial:** reduce los muros de **Armas pesadas y ligeras** un 10% por nivel de subclase (se anulan al nivel 10). Es la pasiva que mitiga muros en esta subclase.
* **Escudo Arcano:** absorción igual al 3% (nivel 1) a 10% (nivel 10) del maná gastado, con **tope del 50% de la vida máxima**. Tras 6 s sin gastar maná, pierde el **10% del máximo del escudo por segundo**.

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
* Cada aliado recibe la cantidad entera (no se reparte), con un máximo de 6 aliados curados.

**2. Bastión Inflexible**
* **Firmeza Inquebrantable:** resistencia al empuje al **100%** (inmunidad).
* **Tenacidad:** tras recibir un golpe, los golpes recibidos en el siguiente segundo hacen **−50% de daño** (en lugar de modificar los ticks de invulnerabilidad).
* **Baluarte Incorruptible (hito de subclase 5):** *Baluarte* purga todos los debuffs e inmuniza contra nuevos efectos nocivos mientras dura.

**3. Juggernaut de Carga**
* **Vigor Colosal:** +6% de vida máxima por nivel de subclase (**+60% al nivel 10**). Sumado al +20% de vida de la clase, el total es **+80% (36 HP)**. Con el perk *Cuerpo Colosal* llegaría a +100% (40 HP), que es el tope global.
* **Baluarte de Embestida (hito de subclase 5):** *Velocidad III*, sprint forzado y daño de contacto con knockback de área.

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
* **Apuesta con la Muerte:** al recibir daño letal, hay una probabilidad de sobrevivir con **1 HP**. **No tiene cooldown.**
  * La probabilidad inicial es del **75%**. Cada vez que funciona, **baja 30 puntos**: 75% → 45% → 15% → 0%. Con 0% la habilidad no se activa.
  * La probabilidad **solo se restablece al 75% si el Bárbaro muere.**
  * Cada vez que se activa con éxito, emite un **grito** (sonido) y hace **1 de daño en área de 3 bloques** alrededor del Bárbaro, con **empuje de unos 5 bloques** a quien golpee.
* Valor de referencia: supervivientes esperados por vida = 0,75 + 0,75×0,45 + 0,75×0,45×0,15 ≈ **1,14**, comparable a un Tótem de la Inmortalidad por vida.

**2. Berserker Suicida**
* **Fuerza Desmedida:** +25% de daño base progresivo (nivel 10).
* **Última Furia Inmortal:** al recibir daño mortal, inmortalidad aleatoria de **7–17 s** con *Fuerza III* y *Velocidad III*. **Cooldown 5 min.**
  * Solo se activa si existe un agresor eliminable. Si no (entorno, creeper ya explotado, jefe inmune), no funciona y el Bárbaro muere.
  * Si elimina al agresor en ese tiempo, sobrevive con 1 HP y *Debilidad III* 10 s. Si no, muere al terminar.

**3. Corruptor de Plagas**
* **Sangre Resistente:** −100% de duración de *Hambre*, *Veneno* y *Wither* (nivel 10).
* **Golpe Aflictivo:** si el enemigo no tiene el efecto **Plaga**, el golpe se lo aplica. *Plaga* es un efecto propio: 2 HP/s durante 6 s, no se apila, **afecta también a no-muertos** y hace la mitad contra jefes. **Contagio:** si el portador muere con Plaga, se transmite al enemigo más cercano en 5 bloques con la duración restante.

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
| **Viento** | **Esquiva:** +15% de probabilidad de esquivar un ataque cuerpo a cuerpo o un proyectil (salta 1 bloque hacia atrás y no recibe daño). −10% de vida máxima. | **Ligereza:** +10% de velocidad de movimiento y +10% de velocidad de ataque. El sigilo se drena un 30% más rápido (5,2%/s). |
| **Toxinas** | **Dosis Concentrada:** la Toxina hace 1 HP cada 1 s durante 2 s (mismo daño total, más rápido). | **Dosis Prolongada:** la Toxina hace 0,5 HP cada 3 s durante 12 s (mismo daño total) y aplica *Lentitud I* mientras dure. |
| **Acrobacia** | **Contraataque:** tras esquivar con éxito, tu siguiente golpe hace +30% de daño durante 3 s. | **Voltereta Larga:** la esquiva recorre +50% de distancia. −15% de duración de la invulnerabilidad. |

Notas de los perks:
* **Sombra:** "sigilo máximo" se entiende como **duración** (la barra drena un % por segundo, así que su tamaño no cambia la duración). En PvP, "no te tiene como objetivo" significa "no te ha atacado en los últimos 5 s". Con *Provocación* del Paladín, Golpe a Traición da un bono casi permanente en grupo (sinergia buscada).
* **Filo A:** hay que medir los marcos de invulnerabilidad de Epic Fight, porque a más de ~2 golpes/s los golpes extra podrían perderse **[MEDIR]**.
* **Toxinas:** el daño de la Toxina se nerfeó a petición (de 6 a 2 HP por aplicación): la rama queda como control y utilidad más que daño. **Acrobacia:** depende de cómo exponga Epic Fight la estamina y la invulnerabilidad de la esquiva **[MEDIR]**; no se apila con la esquiva del perk Viento A (son sistemas distintos).
* **Viento A:** no esquiva daño de área ni del entorno. ICD 1 s para no esquivar varios golpes de un mismo combo. El salto se cancela si el destino no es seguro (lava, vacío).
* **Viento A** suma −10% de vida al rasgo base (−25% en total, 15 HP).

### Activa (Nivel Total 5): Sigilo por barra
* El Pícaro tiene una **barra propia de Sigilo**, separada de la estamina de Epic Fight.
* Agacharse activa la **invisibilidad verdadera**. Drenaje del **4%/s**; recuperación del **8%/s** fuera del sigilo.
* **Atacar o lanzar un hechizo cuesta el 40% del máximo de la barra.** Si la barra se agota, hay bloqueo hasta el 50%.
* **PvP:** la invisibilidad se revela a menos de 1 bloque o al recibir daño.

### Subclases

**1. Caminante de Sombras**
* **Agilidad de las Sombras:** la velocidad agachado sube hasta igualar la de caminar al nivel 10.
* **Paso de Sombra:** teletransporte de **8 bloques** hacia donde mira. **Cooldown 20 s.** No atraviesa bloques sólidos ni destinos ocupados.

**2. Ilusionista de Sombras**
* **Invisibilidad Absoluta:** el rango de detección de mobs baja hasta el −75% total (nivel 10).
* **Señuelo:** al hacerse invisible, crea un clon que absorbe el agro y huye. Desaparece en humo al terminar la invisibilidad. **Cooldown 25 s.**

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
* **Vínculo Salvaje:** +35% de vida máxima, +35% de daño y +25% de velocidad para mascotas domesticadas (lobos, gatos, loros, etc.). Las invocaciones no cuentan. El bonus aplica a un **máximo de 8 mascotas**.
* **Salvaguarda de Bestias:** si una mascota va a recibir daño letal, queda a 1 HP, se teletransporta al punto de reaparición del Ranger y se sienta. **Cooldown de 5 min por mascota**; permanece sin órdenes 60 s.

**2. Explorador Ágil**
* **Caminante de la Naturaleza:** **+10%** de velocidad adicional (sujeta al tope global). *(Antes +15% y bonus de Suerte; la Suerte se ha retirado.)*
* **Triple Salto y Escalada de Paredes:** tercer salto y escalada al agacharse junto a una pared. Si tensa un arco o ballesta estando adherido a la pared, se suelta.

**3. Francotirador**
* **Balística Extrema:** velocidad de proyectil hasta +300% (solo alcance) y daño **+75% en total** (incluye el +30% de la base). La velocidad de carga de la clase base se reduce **un 10% de su valor total por nivel de subclase** (a 0 en el nivel 10).
* **Disparo Letal de Francotirador:** tabla de Disparo Pesado a 1 / 2 / 3 / 6 s de tensado: +25% / +50% / +100% / +150%. Aditiva con el daño de clase (máximo ×3,25).

## 🥋 8. Monje

### Equipamiento inicial
* Sin loot inicial. Llevar cualquier pieza de armadura desactiva sus pasivas desarmadas.

### Ramas
| Rama | Pasiva y máximo |
| ----- | ----- |
| **Puño** | Daño desarmado. **[MEDIR]** En el nivel 30 el DPS desarmado equivale al de una espada de diamante en Epic Fight; daño por golpe = DPS objetivo ÷ golpes por segundo medidos con el moveset de puños |
| **Viento** | Velocidad de movimiento y salto hasta **+35%** sin armadura *(antes +40%)* |
| **Piel** | Armadura y dureza sin armadura, hasta **16 / 6** (por debajo de diamante completo, 20 / 8) |
| **Maestría de Postura** *(rama extra)* | Los bonus de cada postura suben un 20% (2% por punto); las penalizaciones no |
| **Meditación** *(rama extra)* | Agachado y quieto 2 s, curas 0,5 HP/s hasta el 60% de tu vida. Recibir daño la interrumpe |

### Perks de rama (1 de 2 al llenar la rama)
| Rama | Opción A | Opción B |
| ----- | ----- | ----- |
| **Puño** | **Flujo:** el cooldown de cambio de postura baja a 3 s y la postura anterior sigue 3 s a mitad de efecto. −5% de daño desarmado. | **Onda Elemental:** al cambiar de postura, onda de 3 bloques que hace el 75% de un golpe desarmado y aplica un efecto según la postura nueva (Fuego: incendia 3 s; Agua: empuje de 3 bloques y Lentitud I 2 s; Piedra: Debilidad I 2 s). El cooldown de cambio de postura sube a 7 s. |
| **Viento** | **Impulso de Carrera:** tras 2 s de sprint, el siguiente golpe desarmado hace +30% de daño y +50% de empuje. | **Pies Ligeros:** ignora telarañas, arena de almas, miel, nieve en polvo y la ralentización al comer o usar objetos. −10% de velocidad de movimiento. |
| **Piel** | **Piel Espiritual:** −2 de armadura y −1 de dureza, y −30% de daño mágico y elemental recibido (multiplicativo con otras fuentes, con tope del 85% de reducción combinada). | **Calma Interior:** reserva de absorción que acumula 0,5 HP por segundo sin recibir daño (máx. 5 HP a los 10 s). Al recibir un golpe absorbe hasta lo acumulado y gasta solo lo absorbido. |
| **Maestría de Postura** | **Postura Dominante:** eliges una postura favorita con +50% de bonus. Las otras dos tienen −25%. | **Postura Híbrida:** aplican a la vez las dos últimas posturas (la actual y la anterior), cada una al 50%. El cooldown de cambio de postura sube +2 s. |
| **Meditación** | **Trance Profundo:** curas 1 HP/s, pero tardas 4 s quieto en empezar. | **Mente Clara:** purgas un efecto negativo cada 5 s mientras meditas, pero curas la mitad. |

Notas de los perks:
* **Maestría de Postura + Flujo:** con *Postura Híbrida* y *Flujo* a la vez, durante los 3 s de efecto de Flujo tras un cambio aplican **las tres posturas** a la vez (sus bonus y penalizaciones se suman); pasado ese tiempo, quedan solo las dos últimas al 50%. Con las cifras actuales la suma es ≈ +25% de velocidad/salto, 0% de daño y −8% de armadura: ventana de transición, sin abuso. Sin Flujo, Híbrida mantiene las dos últimas al 50% de forma permanente. Con ambos el cooldown de cambio es de 5 s (4 − 1 de Flujo + 2 de Híbrida). Con *Onda Elemental* no hay conflicto (son opciones distintas de la misma rama). Con *Postura Dominante*, los bonus de la favorita (+50%) se suman al +20% de la pasiva.
* **Meditación:** vale sobre todo fuera de combate (el contenido es PvE); la curación se mide al 60% de vida máxima. Con *Mente Clara* purga también *Veneno* y *Wither*.
* **Viento:** ninguna opción sube velocidad ni salto, por lo que no chocan con los topes globales.
* **Piel Espiritual:** el equilibrio está en que ≈40% del daño recibido sea mágico o elemental; con Iron's y los jefes de Cataclysm se cumple en buena parte del contenido. Contra daño físico la vida efectiva baja ≈15%; contra magia sube ≈43%.
* **Calma Interior:** vale ≈+10% de vida efectiva en intercambios de ataca y huye y casi nada en combate continuo.

### Activas
* **Posturas de Combate Elemental (Nivel Total 5):** cambio libre entre posturas, con **4 s de cooldown** al cambiar (3 s con *Flujo*, 7 s con *Onda Elemental*). Los modificadores se aplican al **bonus de la pasiva**:
  * **Agua:** bonus de velocidad y salto ×2 (+100%); bonus de daño desarmado −50%.
  * **Piedra:** bonus de armadura **+25%** (hasta 20 / 7,5 con *Piel* al máximo); bonus de velocidad y salto −75%.
  * **Fuego:** bonus de daño desarmado +50%; bonus de armadura y dureza −33%.
* **Desvío y Reflejo de Proyectiles (Nivel Total 15):** cooldown 5 s. Reduce el daño de un proyectil en un valor igual al Nivel Total de Clase. Si lo reduce a 0, lo devuelve a su origen.
* **Paso Ligero y Marcha Elemental (Nivel Total 30):** inmunidad al daño por caída y caminar sobre el **agua** sin hundirse. Caminar sobre la **lava** sin daño solo es posible en **Postura del Fuego**.

### Subclases

**1. Maestro de la Brisa**
* **Cefiro Marcial:** bonus de velocidad hasta +80% y de salto hasta +120% (sujetos a los topes globales).
* **Impulso de Viento** (sustituye al doble salto): dash aéreo horizontal con **1 carga y 10 s de recarga**.

**2. Palma Pesada**
* **Fuerza de Choque:** empuje de los golpes desarmados hasta +100% y alcance de ataque hasta **+50%** al nivel 10.
* **Puño de Inhabilitación:** 10% por golpe, **ICD de 6 s por objetivo**. Aplica *Lentitud II* y *Ceguera* durante 1,5 s, más aturdimiento de Epic Fight.

**3. Guardián Trimundial**
* **Afinidad Corporal de Postura:** Fuego: hasta 75% de reducción de daño por fuego y lava. Agua: 33% de reducción de daño de proyectiles. Piedra: 20% de reducción de daño cuerpo a cuerpo.
* **Manifiesto de los Elementos:** Fuego: los golpes desarmados incendian al objetivo 6 s. Agua: nado +100% y *Respiración Acuática*. Piedra: inmunidad a *Veneno*, *Wither* y *Hambre*, y refleja el 15% del daño físico recibido.

## 📋 9. Pendiente de medición o decisión

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
  * Onda Elemental y Flujo (Monje): medir la frecuencia real de cambio de postura con los cooldowns de 7 s y 3 s.
* **Desbalances residuales (ver sección 10):** tras la 2.7c el Monje (+6%) es la clase más alta y el Mago (−5%) la más baja. Entre las subclases, Explorador Ágil (+10%) y Maestro de Bestias (+9%) arriba; Mago de Batalla (−9%) y Hoja Arcana (−6%) abajo. Rebalancear con datos medidos.
* **Ramas extra (regla 13):** medir en juego (Iron's: atributos de invocaciones y tiempo de lanzamiento; Epic Fight: estamina e invulnerabilidad de la esquiva, caída lenta); las 10 ramas nuevas no entran en la sección 10.
* **Escuela sagrada (regla 14):** confirmar que el −100% fijo (sin escalado por puntos) no afecta a efectos sagrados de objetos que no usan Spell Power.
* Clases futuras (diseño aplazado por decisión del usuario): Alquimista (efectos de poción), Nigromante (invocador) y Druida (transformación en bestias). Riesgos técnicos anotados: Druida frente a Epic Fight, pociones frente a la definición de la disciplina Arco, y solapamiento con el Mago.

## 📊 10. Estimación de balance (valores estimados, no medidos en el juego)

**Método.**
* **Movilidad** y **Utilidad:** valor estimado 0–10 por criterio de diseño (la utilidad de Paladín y Pícaro depende del equipo y del contenido).
* **Resistencia:** vida efectiva (HP) contra golpes de 15, sin encantamientos ni Resistencia, como media geométrica entre equipo de hierro (15/0) y de diamante (20/8) con los muros de armadura aplicados, multiplicada por el **factor de escudo** = 1 + 0,5 / ((1 + 2w)(1 + w)), donde w es el muro de Escudos (afín = 1,5; −100% = 1,08). Se pasa a 0–10 con 2×log₂(HP/10). Incluye Baluarte y Provocación en el Paladín.
* **DPS:** índice sostenido (1,00 = jugador sin clase con el mismo arma o los mismos hechizos), pasado a 0–10 con 3 + 4×(índice − 1).
* **Total:** media de las cuatro puntuaciones.
* **Supuestos:** 30% de críticos, Bárbaro al 60% de vida, Monje en postura neutral, un escudo bloquea ≈50% del daño cuando está disponible, ritmo de 387 XP/día.
* **Ajustes manuales acumulados sobre el modelo (sin recalcular todo; los perks y las ramas extra no se incluyen porque se diseñaron neutros):** 2.4: Monje con Piel 16/6 (resistencia 40,8) y Explorador Ágil con menos movilidad. 2.6: los críticos del Bárbaro (35%) y del Pícaro (25%) no están recalculados; las tablas usan el 30%. 2.7: vida Mago ×0,8, Paladín ×1,2 (Juggernaut ×1,125), Pícaro ×0,85; Paladín movilidad 2,5; Brutalidad (DPS Bárbaro ×0,9, de 1,95 a 1,75); Filo (DPS Pícaro ×0,78, de 1,91 a 1,49). 2.7b: Monje y subclases −0,5 de movilidad; Explorador Ágil movilidad 8 y utilidad 7; Maestro de Bestias DPS 2,14; Bárbaro y subclases +1 de utilidad; Corruptor DPS 2,00; Mago de Batalla resistencia 44,0; Hoja Arcana DPS 1,55. 2.7c (escuela sagrada): Paladín utilidad 7; Guardián Bendito utilidad 9; Mago y sus subclases −0,5 de utilidad.
* **Límite del método:** los muros de Magia, Arco y armas solo afectan a la puntuación a través de Armadura y Escudos; los demás muros impiden híbridos, pero este modelo no los ve. Las cifras de la 2.4 y la 2.7 son ajustes manuales sobre el modelo anterior.

### Clases (nivel 30)

| Clase | Movilidad | Resistencia (HP efectivos) | DPS (índice) | Utilidad | Total |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Mago | 3 | 26,8 (2,8) | 2,05 (7,2) | 6,5 | 4,88 |
| Paladín | 2,5 | 122,5 (7,2) | 1,15 (3,6) | 7 | 5,08 |
| Bárbaro | 5 | 56,5 (5,0) | 1,75 (6,0) | 4 | 5,00 |
| Pícaro | 6 | 23,5 (2,5) | 1,49 (5,0) | 7 | 5,11 |
| Ranger | 5 | 50,1 (4,7) | 1,61 (5,4) | 6 | 5,27 |
| Monje | 7 | 40,8 (4,1) | 1,17 (3,7) | 7 | 5,45 |

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
| Maestro de la Brisa | 8,5 | 44,9 (4,3) | 1,17 (3,7) | 7 | 5,88 |
| Palma Pesada | 7 | 46,9 (4,5) | 1,25 (4,0) | 7 | 5,63 |
| Guardián Trimundial | 7 | 51,0 (4,7) | 1,30 (4,2) | 8 | 5,98 |

### Lectura
* **Clases:** totales entre 4,88 y 5,45 (media 5,13), es decir, entre −5% y +6%. Mago (−5%) y Bárbaro (−3%) son los más bajos; Monje (+6%) el más alto; Paladín (−1%), Pícaro (0%) y Ranger (+3%) quedan cerca de la media. El muro de Magia sin sagrada sube al Paladín, y perder la sagrada baja al Mago.
* **Subclases:** totales entre 5,25 y 6,35 (media 5,75), entre −9% y +10%. Las más altas: Explorador Ágil (+10%) y Maestro de Bestias (+9%); las más bajas: Mago de Batalla (−9%) y Hoja Arcana (−6%).
* **Maestro de Bestias:** su DPS depende de hasta 8 mascotas y varía mucho entre mobs normales y jefes; el valor mostrado es una media aproximada.
* **Ramas extra y perks:** no entran en estas tablas. Los perks se diseñaron como laterales (≈0 en el total), con dos excepciones a vigilar al medir: Furia Desatada (Bárbaro) y Golpe a Traición (Pícaro) pueden subir el DPS en grupo.
