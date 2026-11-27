# Plan técnico: Ingeniero

Tamaño: grande (una clase completa + integración con un mod externo sin API pública estable). Se hace por fases; cada
fase compila en GitHub Actions y se prueba en el juego antes de la siguiente.

| Fase | Qué | Depende de NTGL | Riesgo |
| --- | --- | --- | --- |
| 1 | Disciplina Pólvora en todas las clases; daño de NTGL por el pipeline (balas, explosiones, fuego, golpes con el arma); excepción de la lanza de balista; martillo neumático pesado | Solo detectar clases/tipos (reflexión) | Bajo |
| 2 | Clase Ingeniero en datos: muros, kit, rasgo Armero (+50 % de munición a mano), ramas y perks como datos, textos y traducciones | No | Bajo |
| 3 | Prototipo de recarga activa: engancharse a `GunReloadEvent`, leer/escribir la munición del arma (NBT), HUD de la barra | Sí | **Alto** |
| 4 | Mecánicas de las ramas: Munición Improvisada (balas especiales + icono), Racha Perfecta, Recarga en Marcha, Cartuchera, Puntería; activas del tronco | Sí | Medio |
| 5 | Pistolero (Mano Rápida, Duelista, Abanico, Ojo Muerto) | Sí (disparar desde código) | Alto |
| 6 | Artillero (Cañón Caliente, Brazo de Hierro, Bombardeo con entidades propias, Modo Asedio) | Parcial | Medio |
| 7 | Artificiero (entidad torreta, minas, bloque de trinchera, cargas, 3 teclas) | Sí (la torreta dispara el arma) | Alto |

## Decisiones técnicas

* **Sin dependencia de compilación con NTGL.** `compat/ntgl/NtglBridge` detecta por nombre de clase
  (`com.nukateam.ntgl.common.foundation.item.WeaponItem`, `...entity.ProjectileEntity`) y registra los eventos de NTGL con
  `IEventBus.addListener(..., Class.forName(...), ...)` solo si `ModList.isLoaded("ntgl")`. Si NTGL cambia, el puente se
  apaga sin romper el mod.
* **Detección del daño de armas de fuego:** tipo de daño con espacio de nombres `ntgl` (balas, fuego, energía) **o**
  entidad directa que es un proyectil de NTGL (explosiones).
* **Munición disparada (lanza de balista):** `GunProjectileHitEvent` se lanza justo antes del daño en el mismo hilo; el
  puente guarda el proyectil en curso y el pipeline lo consulta (válido solo en ese tick y para ese tirador).
* **Alternativas descartadas:** compilar contra NTGL desde Modrinth (versión y mapeos inciertos; un cambio de NTGL
  rompería la compilación); aplicar el muro en `GunProjectileSpawnEvent` (no cubre las explosiones).
