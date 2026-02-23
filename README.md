# Ender Canteen

A [NeoForge](https://neoforged.net/) mod for Minecraft **1.21.1** that adds a canteen item which can be linked to any fluid tank. If the linked tank contains water, the player can drink directly from it – across dimensions.

---

## Features

- 🪣 **Canteen item** – link it to any `IFluidHandler`-capable block by shift-right-clicking it
- 🌊 **Fluid Tap block** – a bridge block for tanks that don't directly expose a fluid handler (e.g. multiblock structures)
- 🌍 **Cross-dimension support** – the canteen works even if the linked tank is in a different dimension
- 💧 **[Thirst Was Taken](https://modrinth.com/mod/thirst-was-taken) integration** – restores thirst and quench points when drinking (required)
- 🍎 **[AppleSkin](https://modrinth.com/mod/appleskin) integration** – shows a thirst preview in the HUD (optional, client-only)
- ⚡ **RF/FE energy system** – drinking can optionally require RF energy stored in the canteen (configurable)
- 🎨 **Fully configurable** – drink amount, thirst/quench values, RF cost, and effect durations via the common config file

---

## Requirements

| Dependency                                                    | Version   | Side                         |
|---------------------------------------------------------------|-----------|------------------------------|
| NeoForge                                                      | 21.1.219+ | Client/Server                |
| Minecraft                                                     | 1.21.1    | Client/Server                |
| [Thirst Was Taken](https://modrinth.com/mod/thirst-was-taken) | 2.1.4+    | Client/Server                |
| [AppleSkin](https://modrinth.com/mod/appleskin)               | 3.0.8+    | Client and Server (optional) |

---

## Usage

1. **Link the canteen** – Shift + right-click any fluid tank or a Fluid Tap block placed next to one
2. **Drink** – Right-click while holding the linked canteen; the canteen will drain water from the tank and restore thirst
3. **Fluid Tap** – Place this block adjacent to any tank that isn't directly linkable, then link the canteen to the tap

---

## Configuration

The config file is located at `config/endercanteen-common.toml` and is generated on first launch.

| Option                  | Default  | Description                                     |
|-------------------------|----------|-------------------------------------------------|
| `drinkAmountMb`         | `500`    | mB of water consumed per drink                  |
| `thirstPer250mb`        | `2`      | Thirst points restored per 250 mB               |
| `quenchedPer250mb`      | `2`      | Quench points restored per 250 mB               |
| `nauseaDurationSeconds` | `8`      | Nausea duration for dirty water (purity 0/1)    |
| `hungerDurationSeconds` | `13`     | Hunger duration for very dirty water (purity 0) |
| `rfEnabled`             | `true`   | Whether drinking requires RF/FE energy          |
| `rfCapacity`            | `100000` | Maximum RF/FE the canteen can store             |
| `rfCostPerThirstPoint`  | `1000`   | RF/FE consumed per thirst+quench point restored |

---

## Building

```bash
./gradlew build
```

The compiled JAR will be placed in `build/libs/`.

To refresh dependencies if something goes wrong:

```bash
./gradlew --refresh-dependencies
```

---

## AI Disclosure
As I am new to minecraft modding and NeoForge, I used **GitHub Copilot** to assist with writing and "improving" parts of the code.
All AI-generated code has been reviewed and tested to ensure that it works and is safe to use.

---

## License

All Rights Reserved
