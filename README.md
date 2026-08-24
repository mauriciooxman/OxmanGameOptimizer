# Oxman Game Optimizer

Oxman Game Optimizer es una aplicación para Windows que aplica ajustes reversibles, mide su efecto y conserva únicamente los resultados útiles para cada equipo.

## Funciones principales

- Monitor de CPU, RAM y GPU, con detección de juegos.
- Competitive Mode reversible, rollback y recuperación después de un cierre inesperado.
- Performance Lab con PresentMon para medir FPS promedio, 1% Low y Frame Time.
- Benchmarks BEFORE/AFTER y clasificación de resultados `NO_CHANGE` y `CONFIGURATION_DRIFT`.
- Experimental Lab para validar ajustes fuera del BOOST normal.

La filosofía del proyecto es:

> Apply.  
> Measure.  
> Compare.  
> Keep only what works.

Oxman no garantiza aumentos de FPS. El beneficio depende del hardware, la configuración, el juego y la carga del sistema.

## Experimentos de v1.0

- **Process Priority:** experimental; puede presentar `CONFIGURATION_DRIFT` y no forma parte del BOOST normal.
- **HighQoS:** experimental; puede resultar `NO_CHANGE` si Windows o el juego ya lo utiliza.
- **Background Load Guard:** experimental; selecciona como máximo tres procesos seguros, no mata procesos, es reversible y no forma parte del BOOST normal.

Los experimentos se ejecutan solo cuando el usuario los inicia; Oxman no los promociona ni aplica automáticamente.

## Datos y seguridad

Benchmarks, snapshots experimentales y estado de recuperación se almacenan en `%LOCALAPPDATA%\OxmanGameOptimizer`. Los benchmarks guardan métricas agregadas y no almacenan credenciales, nombres de usuario, líneas de comando ni rutas de instalación.

Oxman v1.0 no desactiva Microsoft Defender ni Windows Update; no modifica HPET, pagefile o afinidad; no ejecuta ajustes con `bcdedit`; no aplica prioridades HIGH o REALTIME; no realiza ajustes agresivos del registro ni mata aplicaciones del usuario.

## Ejecución (distribución portable para clientes)

Extraiga la carpeta completa y haga doble clic en `Oxman Game Optimizer.exe`. Acepte la solicitud UAC de Windows. La distribución incluye su propio runtime Java y `tools/PresentMon.exe`; no necesita instalar Java, Maven, un JDK ni usar archivos `.jar` o `.bat`.

No mueva el ejecutable fuera de su carpeta ni separe las carpetas `app/`, `runtime/` y `tools/`.

`INICIAR-OXMAN.bat` se conserva solamente como alternativa de desarrollo y no forma parte de la distribución portable para clientes.

PresentMon es un componente de terceros. Consulte [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
