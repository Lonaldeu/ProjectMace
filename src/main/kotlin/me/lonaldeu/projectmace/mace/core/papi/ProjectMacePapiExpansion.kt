package me.lonaldeu.projectmace.mace.core.papi

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

/**
 * PlaceholderAPI expansion for ProjectMace.
 *
 * This class is in a separate package to isolate the PlaceholderAPI dependency.
 * It will only be loaded when PlaceholderAPI is confirmed present on the server.
 *
 * ## Supported Placeholders
 *
 * All placeholders use the format: `%projectmace_<placeholder>%`
 *
 * @see me.lonaldeu.projectmace.mace.core.MacePlaceholderBridge
 */
class ProjectMacePapiExpansion(
    private val expansionIdentifier: String,
    private val expansionAuthor: String,
    private val expansionVersion: String,
    private val requestHandler: (OfflinePlayer?, String) -> String?
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = expansionIdentifier

    override fun getAuthor(): String = expansionAuthor

    override fun getVersion(): String = expansionVersion

    /**
     * Persist through reloads - required for internal expansions.
     */
    override fun persist(): Boolean = true

    /**
     * Handles placeholder requests.
     *
     * @param player The player viewing the placeholder (may be null for global placeholders)
     * @param params The placeholder parameter (e.g., "timer_seconds" for %projectmace_timer_seconds%)
     * @return The placeholder value, or null if unknown
     */
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        return requestHandler(player, params)
    }
}
