package me.lonaldeu.projectmace.mace.core

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class MaceKeys(plugin: JavaPlugin) {
    val maceTagKey: NamespacedKey = NamespacedKey(plugin, "legendary_mace_uuid")
    val maxDurabilityKey: NamespacedKey = NamespacedKey(plugin, "legendary_mace_max_durability")
    val currentDurabilityKey: NamespacedKey = NamespacedKey(plugin, "legendary_mace_current_durability")
}
