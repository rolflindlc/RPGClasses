# Adenda: Ingeniero (Armero, armas de fuego de Create: Gunsmithing)

Adenda del *Documento de Diseño v2*. **Estado (27-11-2026): diseño cerrado (tronco, perks y 3 subclases). En implementación.**

**Convenciones:** sin marca = decidido por el usuario. **(propuesta)** = decisión de Claude, pendiente de confirmar.
**[MEDIR]** = calibrar en el juego. **[TÉCNICO]** = depende de engancharse a NTGL; se prototipa antes.

## 0. Contexto

* Las armas son las de **Create: Gunsmithing** (`cgs`, 1.4.4) sobre la librería **NTGL** (`ntgl`, 3.1.2). NTGL no trae
  recetas: solo se fabrican las armas de Gunsmithing.
* Cómo funciona el daño en NTGL (comprobado en el jar): el daño de un disparo **se reparte entre los perdigones**; con
  `damageReduceOverLife` el daño **baja con la distancia**; NTGL **reinicia la invulnerabilidad** del objetivo (todos los
  perdigones impactan). La bala usa un `DamageSource` de tipo `ntgl:*` cuyo atacante **y** entidad directa son el tirador;
  las explosiones usan el tipo vanilla de explosión con la entidad directa = el proyectil.
* Antes de esta adenda las armas de fuego **se saltaban todo el mod** (ni muros, ni críticos, ni bonus de clase).

## 1. Balance de Create: Gunsmithing (datapack `rp_gunsmithing_balance`)

Referencia: espada de hierro ≈ 9,6 DPS, netherita ≈ 12,8 (cadencia vanilla). DPS sostenido con recarga; `rate` se toma
como ticks entre disparos (como en el mod de MrCrayfish en el que se basa NTGL).

| Arma (nivel de Create) | Cambio | DPS sostenido aprox. |
| --- | --- | --- |
| Trabuco (andesita) | cartucho 10 → 12; recarga 70 → 50 ticks | ~4 (perdigones ~6) |
| Clavadora (latón) | clavo 5 → 2, acero 8 → 2,5; cadencia 2 → 3 ticks; recarga 15 → 30 | ~11 (acero ~13) |
| Lanzallamas (latón/Nether) | llama 2 → 1,5 | ~10 + fuego |
| Revólver (acero) | bala 8 → 10, perforante 12 → 12,5; recarga 60 → 40 | ~12 (perf. ~15) |
| Escopeta (acero) | cartucho 20 → 14, flechette 25 → 14, flechette de acero 30 → 17, incendiario 20 → 14 | ~13 a quemarropa |
| Lanzacohetes (acero + TNT) | cohete 5 + 20 → 4 + 14 **sin romper bloques**; pequeño 1 + 8 → 1 + 5; **lanza de balista 20 → 15** | ~17 en área |
| Gatling (cumbre de Create) | bala 8 → **6**, perforante 12 → **9** (decisión del usuario) | ~22 (perf. ~33) |

* **Disparo a la cabeza ×1,5** (antes ×1,25): `headShotDamageMultiplier` en `config/ntgl-common.toml`.
* **Crítico propio de NTGL desactivado** (los críticos son del mod) **(propuesta)**.

## 2. Disciplina «Pólvora» (`rites_and_paths:firearm`)

* Cubre: las armas de NTGL en la mano (también sus golpes cuerpo a cuerpo: bayonetas, culata), sus balas, sus explosiones
  y su fuego. El **martillo neumático** cuenta como **Pesadas**.
* **Muros:** Ingeniero 0 % (afín) · Ranger −40 % · Pícaro −50 % · Bárbaro, Paladín, Monje y Bardo −60 % · Mago, Clérigo
  y Druida −75 % · Maestro del Giro −60 % **(propuesta)**.
* **Lanza de balista:** ni el Ranger ni el Ingeniero tienen muro con ella (excepción por **munición**, porque la dispara el
  mismo lanzacohetes que los cohetes).

## 3. Tronco del Ingeniero

**Identidad:** DPS a distancia con picos, que depende de munición fabricada y abastece de munición al servidor.
Frente al Ranger (constancia, movilidad, recurso infinito) el Ingeniero son picos, recargas y un recurso que cuesta.

* **Muros (propuesta):** Pólvora 0 %, Ligeras −20 %, Armadura −20 %, Pesadas −40 % (el **martillo neumático sin muro**,
  como la guitarra-hacha del Bardo), Arco −50 %, Escudos −50 %, Magia −50 %.
* **Kit inicial (propuesta):** trabuco con durabilidad infinita y 24 cartuchos de papel.
* **Rasgo base, Armero:** **+50 % de munición** al fabricarla **a mano** (las líneas de Create no tienen jugador asociado).
* **Recarga activa (mecánica central) [TÉCNICO]:** al recargar, un cursor recorre una barra en el centro de la pantalla.
  Se vuelve a pulsar recargar: **zona perfecta** (estrecha) = recarga al instante y **+15 % de daño 4 s**; **zona buena**
  = recarga al instante; **fuera** = el arma se encasquilla y la recarga va un 50 % más lenta; **sin pulsar** = normal.

### 3.1 Ramas (5, máximo 3 usables; valores a 10 puntos)

| Rama | Efecto |
| --- | --- |
| **Munición Improvisada** | **25 %** (2,5 % por punto) de que la primera bala tras recargar sea especial; en armas automáticas, las 5 primeras. Tipos base: **incendiaria** (fuego 4 s), **aturdidora** (0,5 s; jefes solo ralentizados), **congelante** (Lentitud II 3 s, escarcha visual sin daño de congelación). El **tipo de la siguiente especial se decide antes y se ve con un icono** junto a la mira (se ilumina cuando la cargada es especial). |
| **Recarga en Marcha** | Recargar no ralentiza (−10 % de la penalización por punto); recarga perfecta = +20 % de velocidad 2 s. |
| **Racha Perfecta** | +5 % de daño por recarga perfecta seguida (0,5 % por punto y carga), **tope 6 cargas** (+30 %). Se pierde al encasquillar o tras 15 s sin disparar **(propuesta)**. |
| **Cartuchera** | Las armas de fuego de la barra rápida que no llevas en la mano se recargan solas al 50 % (5 % por punto). |
| **Puntería** | −40 % de dispersión y +10 % de daño a más de 15 bloques. |

### 3.2 Perks (1 de 2 al llenar la rama)

| Rama | A | B |
| --- | --- | --- |
| Munición Improvisada | **Munición Oxidada:** añade *Veneno I 6 s* y *Wither II 3 s*. | **Munición Arcana:** añade *Levitación 3 s* y *atracción* (teletransporta al objetivo un 25 % de la distancia hacia ti). Jefes inmunes a ambas: Lentitud II en su lugar **(propuesta)**. |
| Recarga en Marcha | **Huida Táctica:** recargando con un enemigo a menos de 5 bloques, +30 % de velocidad durante la recarga; el bonus de la perfecta baja a +10 %. | **Impulso de Recarga:** una recarga perfecta te lanza **4 bloques hacia delante** (dirección horizontal de la mirada), ICD 6 s; las no perfectas tardan un 10 % más. |
| Racha Perfecta | **Racha Frágil:** +7 % por carga (hasta +42 %), recibir daño quita 2 cargas. | **Racha Sostenida:** encasquillar solo quita 2 cargas, pero +4 % por carga (hasta +24 %). |
| Cartuchera | **Bandolera Giratoria:** al vaciarse el arma cambias al instante a la siguiente cargada de la barra; la cartuchera recarga al 35 %. | **Arma Arrojadiza:** con el arma vacía, clic izquierdo la lanza (daño de culata + aturdir 0,5 s); vuelve recargada a la cartuchera a los 4 s; la recarga pasiva solo funciona con armas lanzadas. |
| Puntería | **Cazacabezas:** cabeza ×1,75 en vez de ×1,5; −15 % al resto de impactos. | **Supresión:** los impactos que no son en la cabeza dan Lentitud I 1 s; la cabeza baja a ×1,25. |

### 3.3 Activas del tronco (propuesta)

* **Nivel 5, Culatazo:** golpe con la culata, empuja 4 bloques y aturde 0,5 s. Recarga 12 s.
* **Nivel 15, Fuego Supresor:** 6 s de +25 % de cadencia; tus balas dan Lentitud I. Recarga 40 s.
* **Nivel 30, Último Cargador:** 8 s sin gastar munición y con todas las recargas perfectas. Recarga 120 s.

## 4. Subclases

### 4.1 Pistolero (armas ligeras: revólver, trabuco, escopeta; etiqueta `firearms/light`)

* **Mano Rápida (1–10):** +2 % de cadencia por nivel con armas ligeras; cambiar entre armas de fuego es instantáneo.
* **Duelista (1–10):** el primer enemigo al que disparas queda **retado** (icono). Contra él +2 % de daño por nivel y te
  hace un 10 % menos. Un solo retado; al matarlo, +20 % de velocidad 3 s y el reto pasa al siguiente al que dispares.
* **Nivel 5, Abanico [TÉCNICO]:** con el revólver vacías el tambor en 0,5 s; dispersión ×3 y −30 % por bala; después
  recarga obligatoria. Recarga 12 s.
* **Nivel 10, Ojo Muerto [TÉCNICO]:** 3 s marcando enemigos al pasar la mira (máx. 6); al terminar, una bala a la cabeza
  de cada marcado, gastando del cargador. Recarga 60 s.

### 4.2 Artillero (armas pesadas: gatling, lanzacohetes, clavadora, lanzallamas, granadas; etiqueta `firearms/heavy`)

* **Cañón Caliente (1–10):** cada segundo de fuego continuo +2 % de cadencia, hasta +2 % por nivel; 2 s sin disparar
  lo enfría.
* **Brazo de Hierro (1–10):** −5 % por nivel de la ralentización de las armas pesadas y +5 % por nivel de resistencia al
  empuje.
* **Nivel 5, Bombardeo:** marcas un punto a ≤30 bloques; a 1,5 s caen 6 obuses en radio 4 (8 de daño en 2 bloques cada
  uno, sin romper bloques ni dañar aliados). Gasta 1 cohete pequeño o 3 de pólvora. Recarga 25 s.
* **Nivel 10, Modo Asedio:** anclado 10 s: +40 % de cadencia, +25 % de daño en área, −30 % de daño recibido, sin empuje y
  **sin gastar munición**. Recarga 75 s.

### 4.3 Artificiero (torretas y artilugios)

* **Cargas compartidas** por torretas, minas y trinchera: **1 (nivel 1) → 2 (nivel 4) → 3 (nivel 8)**. Cada carga tarda
  **15 s**; se recargan de una en una. **3 teclas propias** (torreta, mina, trinchera), con contexto de clase.
* **Torreta:** el arma de fuego de tu mano se coloca como torreta (sale del inventario). **Con la mano vacía** recoges todas
  las torretas y recuperas sus cargas al instante. Con otra cosa en la mano: aviso, no hace nada **(propuesta)**.
  * Dispara a mobs y jugadores **no aliados** con el **70 % de las estadísticas del arma**, sin tus ramas ni recarga activa.
  * Munición de **tu inventario** si estás a ≤32 bloques; alcance 24, giro completo **(propuesta)**. Mientras estás vivo
    rellena su cargador desde tu inventario **(propuesta)**: esa es su **reserva propia**.
  * **Muerto:** sigue disparando con su reserva; al vaciarse, para. **Desconectado:** dispara **sin gastar munición**, pero
    lo que mata **no suelta objetos ni experiencia**. Nada se recoge al morir o desconectarte.
  * **Se rompe de un golpe** y vuelve sola a tu inventario; su carga empieza los 15 s. Los mobs que la tengan más cerca
    que a ti pueden atacarla. Los golpes de aliados no la rompen **(propuesta)**. Solo donde podrías poner un bloque.
  * Límite técnico: sin jugadores cerca, la zona no se ejecuta y la torreta se «congela».
* **Mina:** se **lanza** hasta 6 bloques; a 1 s se vuelve invisible para los enemigos (tú y aliados la veis con contorno
  **(propuesta)**). Explota al pisarla un enemigo: radio 2, sin romper bloques, **6 + 1 por nivel** de daño **(propuesta)**.
  Máximo: 3 × cargas (3/6/9); la más antigua desaparece al pasarse.
* **Trinchera:** semicírculo de **radio 3** hacia donde miras, **1 de alto en el centro y 2 en los extremos**, solo en aire.
  Frena como una telaraña: enemigos a velocidad de telaraña y **1 de daño cada 0,5 s si se mueven**; tú y tus aliados os
  frenáis **la mitad** y sin daño. Se rompe como una telaraña (misma dureza y herramientas) y no suelta nada. Una sola: la
  nueva borra la anterior; dura **60 s como máximo (propuesta)**.
* La subraza del Goblin **Artificiero** pasa a llamarse **Chatarrero**.

## 5. Ideas futuras

* **Recarga Acrobática:** esquivar en la zona perfecta cuenta como recarga perfecta (descartada por ahora: sin ventaja).

## 6. Riesgos técnicos

1. **Recarga activa:** la recarga es de NTGL (`GunReloadEvent.Pre/Post`); hay que poder acortarla, alargarla y conocer su
   duración. Prototipo antes de las ramas que dependen de ella.
2. **Disparar desde el código** (Abanico, Ojo Muerto, torretas): crear proyectiles de NTGL con el arma y la munición
   correctas. Prototipo.
3. **Cadencia, dispersión y munición gratis:** NTGL tiene `GunFireEvent.Pre/Post` y `GunProjectileSpawnEvent` (multiplicador
   de daño y de crítico); la cadencia y la dispersión pueden necesitar mixins.
4. NTGL no es una dependencia de compilación: el puente usa reflexión y se desactiva solo si NTGL no está instalado.
