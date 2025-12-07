<div align="center">

# ⚔️ ProjectMace

</div>

## 🎮 What is ProjectMace?

ProjectMace transforms the vanilla mace into a **legendary server-wide treasure**. With only a limited number of maces allowed to exist, wielders must prove their worth through combat — or risk losing their weapon forever.

> [!TIP]
> Perfect for **survival**, **factions**, **SMP**, or any server where rare items should create meaningful PvP encounters.

<br>

## ✨ Features

<table>
<tr>
<td width="50%">

### 🗡️ Legendary System
- Limited maces exist server-wide
- Bloodthirst timer mechanics
- Combat-based worthiness scoring
- Escalating difficulty over time

</td>
<td width="50%">

### 🛡️ Protection
- Inventory guard (frames, pots, etc.)
- Optional keep-on-death
- Void recovery system
- Full data persistence

</td>
</tr>
<tr>
<td width="50%">

### ⚡ Performance
- Native Folia support
- Paper optimized
- Async database operations
- Zero main-thread blocking

</td>
<td width="50%">

### 🔌 Integration
- 40+ MiniPlaceholders
- Full developer API
- YAML or SQLite storage
- Hot-reloadable config

</td>
</tr>
</table>

<br>

## 📥 Installation

```bash
# 1. Download the latest release
# 2. Drop into plugins/ folder
# 3. Restart server
# 4. Configure in plugins/ProjectMace/config.yml
```

### Requirements

| Requirement | Version |
|-------------|---------|
| Minecraft | 1.21+ |
| Server | Paper or Folia |
| Java | 21+ |

### Optional
- [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders) — Scoreboard & TAB placeholders

<br>

## ⚙️ Configuration

<details>
<summary><strong>📄 config.yml</strong> (click to expand)</summary>

```yaml
# ═══════════════════════════════════════════════════════════════
#                     ProjectMace Configuration
# ═══════════════════════════════════════════════════════════════

# Storage: "yaml" or "sqlite" (recommended for large servers)
storage: sqlite

# Max legendary maces on server (0 = unlimited)
max-legendary-maces: 3

# Hours before mace abandons wielder without worthy kill
bloodthirst-duration-hours: 24

# ─────────────────────────────────────────────────────────────────
#                          CRAFTING
# ─────────────────────────────────────────────────────────────────
crafting:
  enabled: true
  cooldown-seconds: 3.0
  durability: 500
  max-per-player: 1           # 0 = unlimited

# ─────────────────────────────────────────────────────────────────
#                        ENCHANTING
# ─────────────────────────────────────────────────────────────────
enchanting:
  anvil: true
  enchanting-table: true
  allowed-enchantments:
    - unbreaking
    - mending
    - density
    - breach
    - wind_burst
  blocked-enchantments: []

# ─────────────────────────────────────────────────────────────────
#                     COMBAT SCORING
# ─────────────────────────────────────────────────────────────────
features:
  combat:
    enabled: true
    scoring:
      worthy-kill-threshold: 10.0
      armor-multiplier: 2.0
      totem-bonus: 20.0

  inventory-guard:
    block-drop-on-death: false
    block-item-frames: true
    block-armor-stands: true
```

</details>

<br>

## 📜 Commands

| Command | Description |
|---------|-------------|
| `/mace help` | Show all commands |
| `/mace search` | Locate all legendary maces |
| `/mace timer [player]` | Check bloodthirst timers |
| `/mace bloodtimer <player> <add\|set> <time>` | Modify player timers |
| `/mace transfer <from> <to>` | Transfer mace ownership |
| `/mace unclaim <player>` | Force remove a mace |
| `/mace refund <player>` | Refund crafting materials |
| `/mace reload` | Reload configuration |

> [!NOTE]
> All commands require `mace.<command>` permission. Use `mace.*` for full access.

<br>

## 🏷️ Placeholders

> Requires [MiniPlaceholders](https://modrinth.com/plugin/miniplaceholders)

<details>
<summary><strong>🌐 Global Placeholders</strong></summary>

| Placeholder | Output |
|-------------|--------|
| `<projectmace_active_wielder_count>` | `3` |
| `<projectmace_total_mace_count>` | `5` |
| `<projectmace_max_mace_count>` | `10` |
| `<projectmace_available_mace_slots>` | `5` |
| `<projectmace_wielder_names>` | `Steve, Alex` |
| `<projectmace_loose_mace_count>` | `2` |
| `<projectmace_has_available_mace_slot>` | `true` |

</details>

<details>
<summary><strong>👤 Player Placeholders</strong></summary>

| Placeholder | Output |
|-------------|--------|
| `<projectmace_timer_seconds>` | `3600` |
| `<projectmace_timer_formatted>` | `01:00:00` |
| `<projectmace_timer_short>` | `1h 0m` |
| `<projectmace_timer_percent>` | `50.00` |
| `<projectmace_timer_state>` | `active` / `warning` / `critical` |
| `<projectmace_is_wielder>` | `true` |
| `<projectmace_last_kill_name>` | `Notch` |

</details>

<br>

## 🔧 Developer API

```kotlin
// Get the API instance
val api = LegendaryMaceApiProvider.get() ?: return

// Query state
val wielders = api.state.getActiveWielders()
val count = api.state.maceCount()
val isLegendary = api.state.isLegendaryMace(itemStack)

// Modify timers
api.control.extendBloodthirst(playerUuid, 3600.0)  // +1 hour
api.control.setBloodthirst(playerUuid, epochSeconds)

// Manage wielders
api.control.clearWielder(playerUuid, "admin_action")
api.control.giveTaggedMace(player, maceUuid)
```

<details>
<summary><strong>📦 Gradle / Maven</strong></summary>

**Gradle (Kotlin DSL)**
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.lonaldeu:ProjectMace:0.1.0")
}
```

**Maven**
```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.lonaldeu</groupId>
    <artifactId>ProjectMace</artifactId>
    <version>0.1.0</version>
    <scope>provided</scope>
</dependency>
```

</details>

<br>

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**Made with ❤️ by [Lonaldeu](https://github.com/lonaldeu)**

⭐ Star this repo if you find it useful!

</div>
