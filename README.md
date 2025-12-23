# RoflRPG (Core) + RoflRPGSkillsAddon (Example Addon)

This repository contains two plugins:

- **RoflRPG** – the core RPG framework (profiles, classes, talents, HUD, skill triggers, cooldowns, timeline engine).
- **RoflRPGSkillsAddon** – an example addon that implements a **data-driven** skill handler (`roflskills:effects`).

The core idea: **RoflRPG executes the flow** (costs, cooldowns, targeting, timelines) and then **delegates the actual skill logic** to a handler. Handlers can live in separate plugins (addons).

## How to run on a server

1. Build both modules with Maven (`mvn package`).
2. Put both JARs into your server `plugins/` folder.
3. Start the server.

On first start, configs will be copied into `plugins/RoflRPG/`:
- `config.yml`
- `classes.yml`
- `races.yml`
- `talents.yml`
- `skills.yml`
- `cosmetics.yml`

## Skills configuration

All skills are defined in `skills.yml` under `skills:`.

Key fields:
- `trigger`: `RIGHT_CLICK`, `Z`, `X`, ... (see `SkillTrigger` enum)
- `cooldownTicks`, `gcdTicks`
- `cost` (`MANA` or `STAMINA`)
- `target` (`SELF`, `RAY`, `CONE`, `AREA`)
- `handler`: handler id, e.g. **`roflskills:effects`**

### Timeline (cinematics)

A skill can define `data.timeline` with steps split into phases:
- `cast`
- `impact`
- `expire`

Each step has:
- `phase`: cast|impact|expire
- `at`: delay in ticks from phase start
- `type`: e.g. `SOUND`, `RING`, `SPIRAL`, `BEAM`, `PULSE`, ...
- `target`: `CASTER`, `TARGET`, `LOOK_POS`

See `CinematicsService` for the full list of supported step types.

## Writing your own addon (custom handler)

1. Depend on `RoflRPG` and declare `depend: [RoflRPG]` in your addon `plugin.yml`.
2. On enable, get the API and register a handler:

```java
RoflRPGApi api = getServer().getServicesManager().load(RoflRPGApi.class);
api.registerSkillHandler("myaddon:fire", ctx -> {
    // ctx.skill() contains the loaded SkillDef
    // ctx.caster() is the player
    // ctx.target() is the ray target (may be null)
});
```

`RoflRPGSkillsAddon` provides `roflskills:effects` as an example handler and `EffectsEngine` as a reference implementation.
