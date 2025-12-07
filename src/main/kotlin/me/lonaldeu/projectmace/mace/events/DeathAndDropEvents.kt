package me.lonaldeu.projectmace.mace.events

import me.lonaldeu.projectmace.mace.core.MaceItems
import me.lonaldeu.projectmace.mace.core.MaceMessaging
import me.lonaldeu.projectmace.mace.core.nowSeconds
import me.lonaldeu.projectmace.mace.domain.MaceCombatService
import me.lonaldeu.projectmace.mace.domain.MaceEffects
import me.lonaldeu.projectmace.mace.domain.MaceLifecycle
import me.lonaldeu.projectmace.mace.domain.MaceState
import me.lonaldeu.projectmace.mace.domain.model.LooseMace
import me.lonaldeu.projectmace.mace.domain.model.MaceConstants
import me.lonaldeu.projectmace.mace.tasks.MaceDespawnTasks
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

internal class DeathAndDropEvents(
    private val state: MaceState,
    private val lifecycle: MaceLifecycle,
    private val effects: MaceEffects,
    private val items: MaceItems,
    private val messaging: MaceMessaging,
    private val despawnTasks: MaceDespawnTasks,
    private val combatService: MaceCombatService,
    private val saveData: () -> Unit,
    private val logVerboseEvent: (
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
    ) -> Unit,
    private val bloodthirstDurationSeconds: () -> Long,
    private val blockDropOnDeath: Boolean,
    private val nowSeconds: () -> Double = ::nowSeconds
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        event.player.killer?.takeIf { state.isWielder(it.uniqueId) }?.let { killer ->
            combatService.handlePlayerKill(killer, player)
        }

        val data = state.maceWielders[player.uniqueId] ?: run {
            combatService.clearCombatData(player.uniqueId)
            return
        }

        val lastDamage = player.lastDamageCause
        val killer = player.killer
        val damageContext = mutableMapOf<String, Any>()
        lastDamage?.cause?.name?.let { damageContext["death_cause"] = it }
        lastDamage?.finalDamage?.let { damageContext["final_damage"] = it }
        killer?.let { killerPlayer ->
            damageContext["killer_name"] = killerPlayer.name
            damageContext["killer_uuid"] = killerPlayer.uniqueId.toString()
            val weaponInHand = killerPlayer.inventory.itemInMainHand
            if (!weaponInHand.type.isAir) {
                damageContext["killer_weapon"] = weaponInHand.type.name
            }
        } ?: run {
            val damager = (lastDamage as? EntityDamageByEntityEvent)?.damager
            when (damager) {
                is LivingEntity -> {
                    damageContext["damager_type"] = damager.type.name
                    damageContext["damager_uuid"] = damager.uniqueId.toString()
                }
                is Projectile -> {
                    damageContext["projectile_type"] = damager.type.name
                    val shooter = damager.shooter
                    when (shooter) {
                        is LivingEntity -> {
                            damageContext["projectile_shooter_type"] = shooter.type.name
                            damageContext["projectile_shooter_uuid"] = shooter.uniqueId.toString()
                        }
                    }
                }
            }
        }

        effects.stopWielderEffects(player)
        state.maceWielders.remove(player.uniqueId)

        // If block-drop-on-death is enabled, keep mace in inventory instead of dropping
        if (blockDropOnDeath) {
            val maceInInventory = player.inventory.contents.firstOrNull { stack ->
                stack != null && items.getMaceUuid(stack) == data.maceUuid
            }
            if (maceInInventory != null) {
                // Mace stays in inventory, don't drop it
                combatService.clearCombatData(player.uniqueId)
                return
            }
        }

        var dropStack: ItemStack? = null
        val iterator = event.drops.iterator()
        while (iterator.hasNext()) {
            val stack = iterator.next()
            val maceUuid = items.getMaceUuid(stack) ?: continue
            if (maceUuid == data.maceUuid) {
                dropStack = stack.clone()
                iterator.remove()
                break
            }
        }

        val finalDrop = dropStack ?: ItemStack(Material.MACE)
        items.ensureTaggedMace(finalDrop, data.maceUuid)

        val dropEntity = player.world.dropItem(player.location, finalDrop)
        dropEntity.owner = null
        dropEntity.pickupDelay = 0

        state.looseMaces[data.maceUuid] = LooseMace(
            maceUuid = data.maceUuid,
            location = dropEntity.location,
            timerEndEpochSeconds = nowSeconds() + 300.0,
            originalOwnerUuid = player.uniqueId
        )
        despawnTasks.startMaceDespawnSequence(data.maceUuid)
        saveData()

        messaging.broadcastAnnouncement(messaging.announcementMaceLoosened(player.name))

        logVerboseEvent(
            "DEATH",
            player.name,
            player.uniqueId,
            data.maceUuid,
            player.location,
            null,
            "LOST_ON_DEATH",
            null,
            damageContext,
            null,
            null
        )

        combatService.clearCombatData(player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val maceUuid = items.getMaceUuid(event.item.itemStack) ?: return

        if (state.isWielder(player.uniqueId)) {
            event.isCancelled = true
            return
        }

        state.looseMaces.remove(maceUuid)?.let { loose ->
            despawnTasks.stopMaceDespawnSequence(maceUuid)
            despawnTasks.releaseChunk(loose.location)
        }

        val timerEnd = nowSeconds() + bloodthirstDurationSeconds()
        lifecycle.registerWielder(player, maceUuid, timerEnd)
        effects.startWielderEffects(player)
        saveData()

        messaging.sendLegacyMessage(player, "&6You feel the Legendary Mace's hunger stirring within you.")
        logVerboseEvent(
            "PICKUP",
            player.name,
            player.uniqueId,
            maceUuid,
            event.item.location,
            null,
            null,
            null,
            emptyMap(),
            timerEnd,
            null
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val maceUuid = items.getMaceUuid(event.itemDrop.itemStack) ?: return
        val player = event.player

        event.isCancelled = true
        player.updateInventory()
        messaging.sendLegacyMessage(player, "&cThe Legendary Mace refuses to be discarded.")
        logVerboseEvent(
            "DROP_CANCELLED",
            player.name,
            player.uniqueId,
            maceUuid,
            player.location,
            null,
            "DENIED",
            null,
            emptyMap(),
            null,
            null
        )
    }
}
