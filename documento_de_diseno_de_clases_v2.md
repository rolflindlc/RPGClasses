# Documento de Diseño v2: Mod de Clases y Árboles de Habilidades para Minecraft Java

Versión 2 del diseño (revisión 2.6, en desarrollo). Plataforma prevista: Forge 1.20.1 con Iron's Spells and Spellbooks, Epic Fight, Mowzie's Mobs y Cataclysm. Servidor principalmente PvE con algo de PvP. Objetivo del mod: sistema de clases balanceadas y programado de forma estructurada y orientada a la expansibilidad (clases futuras previstas: Alquimista, Nigromante, Druida).

Los valores marcados con **[MEDIR]** son objetivos de diseño que deben calibrarse en el juego real antes de darlos por cerrados.

**Cambios de la revisión 2.6 (decisiones tomadas al pasar a desarrollo):** en las ramas con dos pasivas, cada punto sube ambas y las dos llegan al máximo con 10 puntos (antes, máximo con 5); sistema de **críticos propio** (regla 9); las armas sin clasificar cuentan como ligeras; reiniciar permite elegir otra clase u otra subclase y retira el kit inicial; los topes globales solo cuentan los bonus del mod; las habilidades de supervivencia se gastan antes que el Tótem (regla 10); *Baluarte Sanador* cura la cantidad entera a cada aliado (máximo 6); el Nivel Total incluye los puntos de subclase. La sección 10 no se ha recalculado.

**Cambios de la revisión 2.5:** la subclase se puede elegir antes del nivel 30 (desde 15 puntos de clase base), con niveles de subclase limitados por el avance de la base (regla 2). Los muros escalan solo con puntos de la clase base, los hitos de subclase que modifican una activa base quedan latentes hasta tenerla, y el recorte del Francotirador pasa a ser proporcional. El estado final (nivel 40) y el coste de XP no cambian, por lo que la sección 10 sigue vigente.

**Cambios de la revisión 2.4:** nerfs al Monje (*Piel* baja de 20/8 a 16/6 de armadura/dureza, conservando el +25% de la Postura de Piedra; *Viento* baja del +50% al +40%) y al Explorador Ágil (velocidad adicional del +25% al +15% y Suerte máxima de +6 a +5).

**Cambios de la revisión 2.3:** nueva disciplina **Escudos** (afín del Paladín); el presupuesto de muro deja de ser fijo (más muro a las clases fuertes y más afinidades a las débiles); Bárbaro afín a Escudos; Ranger afín a Armadura; muros subidos a Pícaro y Monje; Baluarte Sanador cura también al propio Paladín. La revisión 2.2 había añadido: Mago reducido, Juggernaut a +60% de vida, Paladín afín a armas pesadas con muros de Magia y Arco al 75%, *Guardia Firme*, Paso de Sombra a 20 s, Impulso de Viento con 1 carga de 10 s y Francotirador con +75% de daño total.

## 🛠️ 1. Reglas Generales

1. **Loot inicial con durabilidad infinita.** Todo el equipamiento entregado al elegir clase tiene durabilidad infinita.

2. **Estructura de niveles (30 base + 10 subclase = 40).**
   * **Clase base:** 3 ramas de 10 puntos. **Un jugador no puede poner más de 10 puntos por rama, y al llenar una rama sus pasivas llegan a su valor máximo.** Llegar al nivel 30 exige llenar las 3 ramas. Cuando una rama contiene dos pasivas, cada punto de la rama sube ambas a la vez y las dos alcanzan su máximo con 10 puntos.
   * **Subclase:** se elige 1 de 3 subclases exclusivas, con 10 niveles propios. **Se desbloquea al tener 15 puntos en la clase base.**
     * **Tope ligado a la base:** los niveles de subclase no pueden superar ⌊(puntos de clase base − 10) / 2⌋. Con 15 puntos base se permiten 2, con 20 se permiten 5, con 25 se permiten 7 y con 30 se permiten 10.
     * El jugador reparte libremente sus puntos entre base y subclase dentro de ese tope. El estado final sigue siendo 30 base + 10 subclase = 40.
     * **Muros:** escalan **solo con los puntos de la clase base** (tope en 30); los puntos de subclase nunca los aumentan.
     * El respec de subclase es independiente y libre (el primer respec del jugador devuelve el XP, según la regla 6).

3. **Habilidades activas.** No pertenecen a una rama. Se desbloquean por hitos del **Nivel Total de Clase** (suma de puntos gastados). Los hitos marcados "de subclase" dependen del nivel de subclase. **Si un hito de subclase modifica una activa de la clase base que el jugador aún no tiene, queda latente hasta desbloquearla.** Afecta a Baluarte Sanador, Baluarte Incorruptible y Baluarte de Embestida (requieren *Baluarte*), a Disparo Letal de Francotirador (*Disparo Pesado*) y al Triple Salto del Explorador (*Doble Salto*).

4. **Una sola clase por jugador.**

5. **Coste en XP vanilla.** Los niveles se compran con puntos de XP (no con niveles vanilla, cuyo coste varía según los que se tengan).
   * Coste del nivel de clase *n* = **80 + 6n** puntos de XP.
   * Total para 40 niveles: **8120 XP** (5190 hasta el nivel 30 y 2930 para la subclase). Referencia: subir hasta el nivel 40 vanilla cuesta 2920 XP.
   * Objetivo de ritmo: ≈ 387 XP/día durante 21 días **[MEDIR]** (nivel 19 al final de la semana 1, nivel 30 en la semana 2 y nivel 40 en la semana 3). Debe existir un multiplicador de configuración del coste.

6. **Respec.** Sin precio en XP. Se puede reiniciar la clase o solo la subclase, fuera de combate.
   * Reiniciar la clase reinicia también la subclase y permite **elegir otra clase**; reiniciar la subclase permite **elegir otra subclase**.
   * Al reiniciar la clase se retiran los objetos del kit inicial que estén en el inventario (los que no estén, se dejan).
   * El **primer respec** de cada jugador devuelve el XP gastado en lo que se reinicia.
   * Los respecs posteriores **no devuelven XP**: hay que volver a pagar los niveles.

7. **Topes globales.** Bonus de velocidad de movimiento total ≤ **+100%** y de salto ≤ **+150%**, para cualquier combinación de clase, subclase y postura. Los topes cuentan **solo los bonus del mod** (no pociones ni equipo).

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
* **Paladín:** el muro de Magia afecta a todas las escuelas, incluida la sagrada. "No sagrado" en la v1 era un error de escritura.
* **Ajuste de balance:** el Paladín y el Bárbaro (débiles) ganan afinidad con Escudos; el Ranger gana afinidad con Armadura; el Pícaro y el Monje (fuertes) reciben muros más altos.
* Los muros regulan la elección de equipo. El precio del poder de cada clase está en su kit, no en el muro.

## 🧙‍♂️ 3. Mago

### Equipamiento inicial
* Libro básico de Iron's Spells (Proyectil Mágico) y armadura de cuero completa. Durabilidad infinita.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Poder** | Spell Power hasta +50% (5% por punto) |
| **Reserva** | Maná máximo hasta +300 (máximo con 10 puntos) y regeneración de maná hasta +40% (máximo con 10 puntos) |
| **Cadencia** | Reducción de cooldown hasta −25% (2,5% por punto) |

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
* **Escudo Arcano:** absorción igual al 3% (nivel 1) a 7% (nivel 10) del maná gastado, con **tope del 50% de la vida máxima**. Tras 6 s sin gastar maná, pierde el **10% del máximo del escudo por segundo**.

## 🛡️ 4. Paladín

### Equipamiento inicial
* Espada de piedra y armadura de malla completa. Durabilidad infinita.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Coraza** | Multiplicador de armadura de las piezas equipadas hasta +25% y de dureza hasta +40% (cada una con 10 puntos) |
| **Voluntad** | Resistencia a debuffs: −50% de duración de efectos negativos (5% por punto) |
| **Empuje** | Resistencia al retroceso hasta +50% y empuje infligido hasta +25% (cada uno con 10 puntos) |

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
* **Vigor Colosal:** +6% de vida máxima por nivel de subclase (**+60% al nivel 10, 32 HP**).
* **Baluarte de Embestida (hito de subclase 5):** *Velocidad III*, sprint forzado y daño de contacto con knockback de área.

## 🪓 5. Bárbaro

### Equipamiento inicial
* Hacha de hierro. Sin armadura. Durabilidad infinita.
* Llevar cualquier pieza de armadura desactiva Defensa y Velocidad Desarmada. Los escudos no cuentan como armadura.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Furia** | Furia de Heridas: +0,1% de daño por cada 1% de vida perdida (nivel 1), hasta +1% (nivel 10), con **tope de +75% de daño** |
| **Brutalidad** | Daño crítico hasta +100% (10% por punto) |
| **Piel de Hierro** | Defensa Desarmada hasta 16 de armadura y 6 de dureza, y Velocidad Desarmada hasta +25% (ambas con 10 puntos) |

### Activas
* **Sed de Sangre (Nivel Total 5):** al matar, cura el 10% de la vida máxima. ICD 1 s.
* **Embestida (Nivel Total 15):** carga de 6 bloques con aturdimiento de Epic Fight. Cooldown 20 s.
* **Furia Berserker (Nivel Total 30):** 10 s de +30% de velocidad de ataque y +25% de daño físico, con +20% de daño recibido. **Cooldown 60 s.**

### Subclases

**1. Indestructible**
* **Vitalidad Salvaje:** +1 HP de vida máxima por nivel de subclase (+10 HP, 5 corazones, al nivel 10).
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
* **Golpe Aflictivo:** si el enemigo no tiene el efecto **Plaga**, el golpe se lo aplica. *Plaga* es un efecto propio: 2 HP/s durante 6 s, no se apila, **afecta también a no-muertos** y hace la mitad contra jefes.

## 🗡️ 6. Pícaro

### Equipamiento inicial
* Espada de hierro y armadura de cuero completa. Durabilidad infinita.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Sombra** | Sigilo Físico (rango de detección `follow_range` hasta −50%) y Backstab (+100% de daño crítico desde invisibilidad), cada uno con 10 puntos |
| **Filo** | Velocidad de ataque hasta +60% y daño crítico hasta +75%, cada uno con 10 puntos |
| **Viento** | Velocidad de movimiento hasta +35% (10 puntos) |

### Activa (Nivel Total 5): Sigilo por barra
* El Pícaro tiene una **barra propia de Sigilo**, separada de la estamina de Epic Fight.
* Agacharse activa la **invisibilidad verdadera**. Drenaje del **4%/s**; recuperación del **8%/s** fuera del sigilo.
* **Atacar o lanzar un hechizo cuesta el 40% del máximo de la barra.** Si la barra se agota, hay bloqueo hasta el 50%.
* **PvP:** la invisibilidad se revela a menos de 2,5 bloques o al recibir daño.

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
* **Emboscada Arcana:** el primer hechizo lanzado desde invisibilidad hace **+40% de daño**. ICD 8 s. Cuesta el 40% de la barra de Sigilo.

## 🏹 7. Ranger

### Equipamiento inicial
* Arco de madera, carcaj y flechas básicas, armadura de cuero. Durabilidad infinita.

### Ramas
| Rama | Pasiva(s) y máximo |
| ----- | ----- |
| **Puntería** | Daño con arcos y ballestas hasta +30% y Visión de Halcón (zoom hasta 35%), cada uno con 10 puntos |
| **Cadencia** | Velocidad de carga del arco hasta +30% |
| **Alcance** | Velocidad de proyectiles hasta +100%, **solo alcance y trayectoria: no aumenta el daño** |

El daño de una flecha se calcula **sin** el efecto de la velocidad añadida por la clase.

### Activas
* **Conservación de Munición (Nivel Total 5):** 33% de no consumir munición.
* **Doble Salto (Nivel Total 15).**
* **Disparo Pesado (Nivel Total 30):** el contador empieza cuando el arco está **tensado al máximo**. A 2 / 4 / 6 s: +25% / +50% / +100% de daño. **Suma aditiva con el bonus de daño de la clase** (máximo ×2,3).

### Subclases

**1. Maestro de Bestias**
* **Vínculo Salvaje:** +50% de vida máxima, +50% de daño y +25% de velocidad para mascotas domesticadas (lobos, gatos, loros, etc.). Las invocaciones no cuentan. El bonus aplica a un **máximo de 8 mascotas**.
* **Salvaguarda de Bestias:** si una mascota va a recibir daño letal, queda a 1 HP, se teletransporta al punto de reaparición del Ranger y se sienta. **Cooldown de 5 min por mascota**; permanece sin órdenes 60 s.

**2. Explorador Ágil**
* **Caminante de la Naturaleza:** **+15%** de velocidad adicional (sujeta al tope global) y bonus de Suerte de **+3 en el nivel 1 hasta +5 en el nivel 10**.
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
| **Viento** | Velocidad de movimiento y salto hasta **+40%** sin armadura |
| **Piel** | Armadura y dureza sin armadura, hasta **16 / 6** (por debajo de diamante completo, 20 / 8) |

### Activas
* **Posturas de Combate Elemental (Nivel Total 5):** cambio libre entre posturas, con **4 s de cooldown** al cambiar. Los modificadores se aplican al **bonus de la pasiva**:
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

* Calibrar en el juego: DPS desarmado del Monje con los puños de Epic Fight, y el CDR total real (clase + equipo) frente a posibles topes de Iron's.
* Confirmar las listas de armas pesadas y ligeras con las categorías reales de Epic Fight y sus mods de armas.
* Escudos: definir con Epic Fight qué cuenta como "cooldown de recuperación" del escudo (desactivación por hacha, rotura de guardia) y medir su valor base.
* Con 10 puntos máximos por rama y 3 ramas, todos los jugadores de una clase tienen las mismas pasivas al nivel 30. La variedad queda en el orden de compra y en la subclase.
* Coraza del Paladín: con armadura de diamante o superior, los multiplicadores topan con el 80% de reducción de daño; revisar tras medir.
* Desbalances residuales (ver sección 10): tras los nerfs de la 2.4, el Explorador Ágil sigue por encima de la media (+14%); el Monje queda en torno a +6%. Revisar con datos medidos.
* Subclase temprana (rev. 2.5): medir el poder a mitad de camino (base 15–25 con subclase parcial); la sección 10 solo cubre el estado final. Decidido: el Nivel Total (hitos de activas) incluye los puntos de subclase.
* Críticos (rev. 2.6): medir la probabilidad base del 30% y los ajustes de Bárbaro (35%) y Pícaro (25% con crítico seguro desde invisibilidad).
* Clases futuras (diseño aplazado por decisión del usuario): Alquimista (efectos de poción), Nigromante (invocador) y Druida (transformación en bestias). Riesgos técnicos anotados: Druida frente a Epic Fight, pociones frente a la definición de la disciplina Arco, y solapamiento con el Mago.

## 📊 10. Estimación de balance (valores estimados, no medidos en el juego)

**Método.**
* **Movilidad** y **Utilidad:** valor estimado 0–10 por criterio de diseño (la utilidad de Paladín y Pícaro depende del equipo y del contenido).
* **Resistencia:** vida efectiva (HP) contra golpes de 15, sin encantamientos ni Resistencia, como media geométrica entre equipo de hierro (15/0) y de diamante (20/8) con los muros de armadura aplicados, multiplicada por el **factor de escudo** = 1 + 0,5 / ((1 + 2w)(1 + w)), donde w es el muro de Escudos (afín = 1,5; −100% = 1,08). Se pasa a 0–10 con 2×log₂(HP/10). Incluye Baluarte y Provocación en el Paladín.
* **DPS:** índice sostenido (1,00 = jugador sin clase con el mismo arma o los mismos hechizos), pasado a 0–10 con 3 + 4×(índice − 1).
* **Total:** media de las cuatro puntuaciones.
* **Supuestos:** 30% de críticos, Bárbaro al 60% de vida, Monje en postura neutral, un escudo bloquea ≈50% del daño cuando está disponible, ritmo de 387 XP/día.
* **Revisión 2.4:** Monje con *Piel* 16/6 (resistencia 61,9 → 40,8) y movilidad 7,5 (antes 8); Explorador Ágil con movilidad 8,5 (antes 9); Palma Pesada y Guardián Trimundial heredan el −0,5 de movilidad del Monje.
* **Revisión 2.6:** los ajustes de crítico (Bárbaro 35%, Pícaro 25% con crítico seguro desde invisibilidad) no están recalculados; las tablas siguen usando el 30% para todos.
* **Límite del método:** los muros de Magia, Arco y armas solo afectan a la puntuación a través de Armadura y Escudos; los demás muros impiden híbridos, pero este modelo no los ve. Las cifras de la 2.4 son un ajuste manual sobre el modelo anterior (sin recalcular todo el conjunto).

### Clases (nivel 30)

| Clase | Movilidad | Resistencia (HP efectivos) | DPS (índice) | Utilidad | Total |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Mago | 3 | 33,5 (3,5) | 2,05 (7,2) | 7 | 5,17 |
| Paladín | 3 | 102,1 (6,7) | 1,15 (3,6) | 6 | 4,83 |
| Bárbaro | 5 | 56,5 (5,0) | 1,95 (6,8) | 3 | 4,95 |
| Pícaro | 6 | 27,7 (2,9) | 1,91 (6,6) | 7 | 5,65 |
| Ranger | 5 | 50,1 (4,7) | 1,61 (5,4) | 6 | 5,27 |
| Monje | 7,5 | 40,8 (4,1) | 1,17 (3,7) | 7 | 5,56 |

### Subclases (nivel 40)

| Subclase | Movilidad | Resistencia (HP efectivos) | DPS (índice) | Utilidad | Total |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Evocador Caótico | 3 | 33,5 (3,5) | 2,30 (8,2) | 8 | 5,67 |
| Elementalista | 3 | 35,2 (3,6) | 2,40 (8,6) | 8 | 5,81 |
| Mago de Batalla | 3 | 50,2 (4,7) | 2,05 (7,2) | 7 | 5,46 |
| Guardián Bendito | 3 | 117,4 (7,1) | 1,15 (3,6) | 8 | 5,43 |
| Bastión Inflexible | 4 | 127,6 (7,3) | 1,15 (3,6) | 7 | 5,49 |
| Juggernaut de Carga | 5 | 163,4 (8,1) | 1,20 (3,8) | 6 | 5,72 |
| Indestructible | 5 | 110,1 (6,9) | 1,95 (6,8) | 5 | 5,93 |
| Berserker Suicida | 6 | 67,7 (5,5) | 2,44 (8,8) | 4 | 6,07 |
| Corruptor de Plagas | 5 | 62,1 (5,3) | 2,20 (7,8) | 4 | 5,52 |
| Caminante de Sombras | 8 | 33,2 (3,5) | 1,91 (6,6) | 8 | 6,53 |
| Ilusionista de Sombras | 7 | 41,6 (4,1) | 1,90 (6,6) | 8 | 6,43 |
| Hoja Arcana | 6 | 27,7 (2,9) | 1,90 (6,6) | 8 | 5,89 |
| Maestro de Bestias | 5 | 65,2 (5,4) | 2,20 (7,8) | 7 | 6,30 |
| Explorador Ágil | 8,5 | 57,6 (5,0) | 1,61 (5,4) | 8 | 6,74 |
| Francotirador | 5 | 50,1 (4,7) | 2,05 (7,2) | 5 | 5,46 |
| Maestro de la Brisa | 9 | 44,9 (4,3) | 1,17 (3,7) | 7 | 6,00 |
| Palma Pesada | 7,5 | 46,9 (4,5) | 1,25 (4,0) | 7 | 5,74 |
| Guardián Trimundial | 7,5 | 51,0 (4,7) | 1,30 (4,2) | 8 | 6,10 |

### Lectura
* **Clases:** totales entre 4,83 y 5,65 (media 5,24), es decir, entre −8% y +8%. El Pícaro queda arriba (+8%) y el Monje en +6%; el Paladín (−8%) y el Bárbaro (−5%) siguen por debajo.
* **Subclases:** totales entre 5,43 y 6,74 (media 5,91), entre −8% y +14%. La más alta sigue siendo Explorador Ágil (+14%), seguida de Caminante de Sombras (+11%) e Ilusionista de Sombras (+9%); las más bajas, Guardián Bendito, Francotirador y Mago de Batalla.
* **Maestro de Bestias:** su DPS depende de hasta 8 mascotas y varía mucho entre mobs normales y jefes; el valor mostrado es una media aproximada.
