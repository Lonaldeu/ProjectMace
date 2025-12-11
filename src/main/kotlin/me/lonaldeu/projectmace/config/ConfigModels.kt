package me.lonaldeu.projectmace.config

/**
 * Typed configuration data classes.
 * These provide compile-time safety and reduce boilerplate in ConfigService.
 */

// ═══════════════════════════════════════════════════════════════
//                     COMBAT CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class CombatScoringConfig(
    val baseDamage: Double = 5.0,
    val damageMultiplier: Double = 1.0,
    val healthMultiplier: Double = 0.5,
    val armorMultiplier: Double = 2.0,
    val totemBonus: Double = 20.0,
    val worthyKillThreshold: Double = 10.0,
    val easyKillThreshold: Double = 5.0
)

data class CombatConfig(
    val enabled: Boolean = true,
    val trackDamage: Boolean = true,
    val awardWorthyKills: Boolean = true,
    val sendMessages: Boolean = true,
    val sendVoicelines: Boolean = true,
    val scoring: CombatScoringConfig = CombatScoringConfig()
)

// ═══════════════════════════════════════════════════════════════
//                     EFFECTS CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class ParticleConfig(
    val enabled: Boolean = true,
    val intervalSeconds: Int = 1,
    val count: Int = 10
) {
    val intervalTicks: Long get() = intervalSeconds * 20L
}

data class SoundConfig(
    val enabled: Boolean = true,
    val heartbeatEnabled: Boolean = true,
    val heartbeatThresholdSeconds: Int = 60,
    val volume: Float = 0.5f,
    val pitchMin: Float = 0.5f,
    val pitchMax: Float = 1.5f
)

data class EffectsConfig(
    val particles: ParticleConfig = ParticleConfig(),
    val sounds: SoundConfig = SoundConfig()
)

// ═══════════════════════════════════════════════════════════════
//                     TIMERS CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class TimersConfig(
    val warningThresholdSeconds: Int = 300,
    val criticalThresholdSeconds: Int = 60,
    val lastChanceThresholdSeconds: Int = 60,
    val idleWhisperIntervalSeconds: Int = 150,
    val statusCheckIntervalSeconds: Int = 10
) {
    val idleWhisperIntervalTicks: Long get() = idleWhisperIntervalSeconds * 20L
}

// ═══════════════════════════════════════════════════════════════
//                     CRAFTING CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class CraftingConfig(
    val enabled: Boolean = true,
    val cooldownSeconds: Double = 3.0,
    val durability: Int = 500,
    val maxPerPlayer: Int = 1
)

// ═══════════════════════════════════════════════════════════════
//                   INVENTORY GUARD CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class InventoryGuardConfig(
    val enabled: Boolean = true,
    val blockDecoratedPots: Boolean = true,
    val blockItemFrames: Boolean = true,
    val blockArmorStands: Boolean = true,
    val blockAllays: Boolean = true,
    val blockDropOnDeath: Boolean = false
)

// ═══════════════════════════════════════════════════════════════
//                     LOOSE MACE CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class LooseMaceConfig(
    val despawnDelaySeconds: Int = 600,
    val voidRecoveryEnabled: Boolean = true,
    val announcementEnabled: Boolean = true
) {
    val despawnDelayTicks: Long get() = despawnDelaySeconds * 20L
}

// ═══════════════════════════════════════════════════════════════
//                     HOLD TIME CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class HoldTimeConfig(
    val baseDamageRequirement: Double = 10.0,
    val escalationRate: Double = 0.5,
    val trackingEnabled: Boolean = true
)

// ═══════════════════════════════════════════════════════════════
//                   ANNOUNCEMENTS CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class AnnouncementsConfig(
    val cooldownMillis: Long = 2000,
    val maceLostEnabled: Boolean = true,
    val bloodthirstUnmetEnabled: Boolean = true,
    val divineInterventionEnabled: Boolean = true
)

// ═══════════════════════════════════════════════════════════════
//                   PERFORMANCE CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class PerformanceConfig(
    val combatLogMaxHistory: Int = 1000,
    val combatLogPruningIntervalSeconds: Int = 60,
    val chunkForceLoadEnabled: Boolean = true,
    val chunkUnloadDelaySeconds: Int = 5
) {
    val chunkUnloadDelayTicks: Long get() = chunkUnloadDelaySeconds * 20L
}

// ═══════════════════════════════════════════════════════════════
//                     LOGGING CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class LoggingConfig(
    val verboseEvents: Boolean = false,
    val logDirectory: String = "mace_logs",
    val includeLocationData: Boolean = true,
    val includeTimerData: Boolean = true
)

// ═══════════════════════════════════════════════════════════════
//                   PLACEHOLDER CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class PlaceholderFormatConfig(
    val emptyValue: String = "None",
    val booleanTrue: String = "true",
    val booleanFalse: String = "false",
    val listSeparator: String = ", "
)

data class PlaceholderConfig(
    val enabled: Boolean = true,
    val format: PlaceholderFormatConfig = PlaceholderFormatConfig()
)

// ═══════════════════════════════════════════════════════════════
//                   ENCHANTING CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class EnchantingConfig(
    val anvil: Boolean = true,
    val enchantingTable: Boolean = true,
    val allowedEnchantments: List<String> = listOf(
        "unbreaking", "mending", "sharpness", "smite", "bane_of_arthropods",
        "fire_aspect", "looting", "knockback", "sweeping_edge",
        "density", "breach", "wind_burst", "curse_of_vanishing"
    ),
    val blockedEnchantments: List<String> = emptyList()
)

// ═══════════════════════════════════════════════════════════════
//                     ROOT CONFIGURATION
// ═══════════════════════════════════════════════════════════════

data class MaceConfig(
    val storage: String = "yaml",
    val debug: Boolean = false,
    val maxLegendaryMaces: Int = 3,
    val autoSaveIntervalSeconds: Int = 300,
    val bloodthirstDurationHours: Int = 24,
    val crafting: CraftingConfig = CraftingConfig(),
    val enchanting: EnchantingConfig = EnchantingConfig(),
    val inventoryGuard: InventoryGuardConfig = InventoryGuardConfig(),
    val combat: CombatConfig = CombatConfig(),
    val effects: EffectsConfig = EffectsConfig(),
    val timers: TimersConfig = TimersConfig(),
    val looseMace: LooseMaceConfig = LooseMaceConfig(),
    val holdTime: HoldTimeConfig = HoldTimeConfig(),
    val announcements: AnnouncementsConfig = AnnouncementsConfig(),
    val performance: PerformanceConfig = PerformanceConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val placeholders: PlaceholderConfig = PlaceholderConfig()
) {
    val autoSaveIntervalTicks: Long get() = autoSaveIntervalSeconds * 20L
    val bloodthirstDurationSeconds: Long get() = if (bloodthirstDurationHours == 0) Long.MAX_VALUE else bloodthirstDurationHours * 3600L
}
