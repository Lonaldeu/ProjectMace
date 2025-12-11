package me.lonaldeu.projectmace.mace.events

import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Crafter
import org.bukkit.entity.Player
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.block.CrafterCraftEvent
import java.util.UUID
import kotlin.math.ceil

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

import me.lonaldeu.projectmace.mace.core.MaceContext

internal class CraftEvents(
    private val context: MaceContext
) : Listener {

    private val state get() = context.state
    private val lifecycle get() = context.lifecycle
    private val effects get() = context.effects
    private val items get() = context.items
    private val messaging get() = context.messaging
    private val saveData get() = context.saveData
    private fun nowSeconds() = context.nowSeconds()

    private fun maxLegendaryMaces() = context.config.maxLegendaryMaces
    private fun maxMacesPerPlayer() = context.config.crafting.maxPerPlayer
    private fun isCraftingEnabled() = context.config.crafting.enabled
    private fun craftCooldownSeconds() = context.config.crafting.cooldownSeconds
    private fun bloodthirstDurationSeconds() = context.config.bloodthirstDurationHours * 3600L

    private fun logVerboseEvent(
        eventType: String,
        playerName: String?,
        playerUuid: UUID?,
        maceUuid: UUID?,
        location: org.bukkit.Location?,
        containerContext: String?,
        outcome: String?,
        reason: String?,
        additionalContext: Map<String, Any?>,
        timerEnd: Double?,
        timeLeft: Double?
    ) {
        context.eventLogger.logVerboseEvent(
            eventType, playerName, playerUuid, maceUuid, location, containerContext, outcome, reason, additionalContext, timerEnd, timeLeft
        )
    }

    private val craftCooldowns: MutableMap<UUID, Double> = state.craftCooldowns

    @EventHandler(ignoreCancelled = true)
    fun onCraftItem(event: CraftItemEvent) {
        if (!isCraftingEnabled()) {
            return
        }

        val recipe = event.recipe
        val resultType = recipe.result.type
        if (resultType != Material.MACE) {
            return
        }

        val crafter = event.whoClicked as? Player ?: return
        val crafterUuid = crafter.uniqueId

        val now = nowSeconds()

        if (state.isWielder(crafterUuid)) {
            messaging.sendLegacyMessage(crafter, "&cYou already wield a Legendary Mace.")
            event.isCancelled = true
            return
        }

        if (lifecycle.playerCarriesLegendaryMace(crafter)) {
            messaging.sendLegacyMessage(crafter, "&cYou are already carrying a Legendary Mace.")
            event.isCancelled = true
            return
        }

        // Check per-player limit
        val perPlayerLimit = maxMacesPerPlayer()
        if (perPlayerLimit > 0) {
            val playerMaceCount = countPlayerMaces(crafterUuid)
            if (playerMaceCount >= perPlayerLimit) {
                val msg = if (perPlayerLimit == 1) {
                    "&cYou can only own one Legendary Mace."
                } else {
                    "&cYou can only own $perPlayerLimit Legendary Maces."
                }
                messaging.sendLegacyMessage(crafter, msg)
                event.isCancelled = true
                return
            }
        }

        val limit = maxLegendaryMaces()
        if (state.maceCount() >= limit) {
            messaging.sendLegacyMessage(crafter, "&cAll ${limit} Legendary Maces already exist.")
            event.isCancelled = true
            return
        }

        val cooldownExpiration = craftCooldowns[crafterUuid]
        if (cooldownExpiration != null && now < cooldownExpiration) {
            val secondsLeft = ceil(cooldownExpiration - now).toInt().coerceAtLeast(1)
            messaging.sendLegacyMessage(
                crafter,
                "&cThe forging altar is still recharging. Try again in ${secondsLeft}s."
            )
            event.isCancelled = true
            return
        }

        craftCooldowns[crafterUuid] = now + craftCooldownSeconds()

        val newMaceUuid = UUID.randomUUID()
        val producedStack = event.currentItem?.clone() ?: return
        items.ensureTaggedMace(producedStack, newMaceUuid)
        event.currentItem = producedStack
        event.inventory.result = producedStack

        val timerEnd = nowSeconds() + bloodthirstDurationSeconds()
        lifecycle.registerWielder(crafter, newMaceUuid, timerEnd)
        saveData()

        logVerboseEvent(
            "CRAFT",
            crafter.name,
            crafterUuid,
            newMaceUuid,
            crafter.location,
            null,
            null,
            null,
            emptyMap(),
            timerEnd,
            null
        )

        messaging.broadcastAnnouncement(messaging.announcementRelicForged(crafter.name))

        if (event.isShiftClick) {
            event.isCancelled = true
            val leftovers = crafter.inventory.addItem(producedStack.clone())
            leftovers.values.forEach { leftover ->
                crafter.world.dropItemNaturally(crafter.location, leftover)
            }
            consumeCraftIngredients(event)
            event.currentItem = null
            event.inventory.result = null
            crafter.updateInventory()
        }

        effects.startWielderEffects(crafter)
    }

    @EventHandler(ignoreCancelled = true)
    fun onCrafterCraft(event: CrafterCraftEvent) {
        val recipe = event.recipe
        if (recipe.result.type != Material.MACE) {
            return
        }

        // Block automated mace creation
        event.isCancelled = true

        val crafter = event.block.state as? Crafter ?: return
        val location = crafter.location

        // Play denial sound effect
        location.world.playSound(location, Sound.BLOCK_CRAFTER_FAIL, 1.0f, 1.0f)

        // Eject the heavy core back out
        val crafterInventory = crafter.inventory
        for (item in crafterInventory.storageContents) {
            if (item?.type == Material.HEAVY_CORE) {
                location.world.dropItemNaturally(location.add(0.5, 1.0, 0.5), item.clone())
                item.amount = 0
            }
        }
    }

    private fun consumeCraftIngredients(event: CraftItemEvent) {
        val craftingInventory = event.inventory
        val matrix = craftingInventory.matrix
        var changed = false

        for (index in matrix.indices) {
            val stack = matrix[index] ?: continue
            if (stack.type == Material.AIR) continue
            stack.amount = stack.amount - 1
            if (stack.amount <= 0) {
                matrix[index] = null
            }
            changed = true
        }

        if (changed) {
            craftingInventory.matrix = matrix
        }
    }

    private fun countPlayerMaces(playerUuid: UUID): Int {
        return state.maceWielders.count { it.key == playerUuid }
    }
}
