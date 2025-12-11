package me.lonaldeu.projectmace.mace.core

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class MaceKeys(plugin: JavaPlugin) {
    val maceTagKey: NamespacedKey = NamespacedKey(plugin, me.lonaldeu.projectmace.license.StringVault.get("NBT_MACE_UUID"))
    val maxDurabilityKey: NamespacedKey = NamespacedKey(plugin, me.lonaldeu.projectmace.license.StringVault.get("NBT_MAX_DURABILITY"))
    val currentDurabilityKey: NamespacedKey = NamespacedKey(plugin, me.lonaldeu.projectmace.license.StringVault.get("NBT_LORE_DURABILITY"))
}
