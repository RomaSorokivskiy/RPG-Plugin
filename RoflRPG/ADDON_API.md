# RoflRPG Addon API (Custom Skills)

## Idea
You can write a separate plugin (addon) and register a custom skill handler.
Then in `skills.yml` you set:

```yml
skills:
  my_blink:
    name: "@defs.skills.my_blink.name"
    icon: ENDER_PEARL
    trigger: Z
    requiredLevel: 5
    cooldownTicks: 160
    gcdTicks: 20
    cost: { type: MANA, amount: 6 }
    target: { type: SELF, range: 0 }
    handler: "example:blink"
    data:
      distance: 6
```

`handler` is a string key. Recommended format: `pluginId:skillName`.

## Addon side
In your addon plugin:

```java
RoflRPGApi api = Bukkit.getServicesManager().load(RoflRPGApi.class);
api.registerSkillHandler("example:blink", ctx -> {
    ctx.dash(1.6, 0.05);
    ctx.particle("PORTAL", 30);
    ctx.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
});
```

You can read your params from `ctx.data()` (values from `skills.yml -> data:`).

## Notes
- Server cannot detect real "Z" key directly. Use `PlayerSwapHandItemsEvent`.
  In Minecraft controls, bind **Swap Hands** to Z.
- Default data-driven handler is `roflskills:effects` (addon). If the addon is missing,
  RoflRPG will fall back to built-in effect execution using the `effects:` list.
