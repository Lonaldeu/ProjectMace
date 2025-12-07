package me.lonaldeu.projectmace.mace.api

/**
 * Static accessor for the Legendary Mace API.
 *
 * Other plugins should call [get] during or after their `onEnable`.
 */
object LegendaryMaceApiProvider {
    @Volatile
    private var api: LegendaryMaceApi? = null

    @JvmStatic
    fun get(): LegendaryMaceApi? = api

    @JvmStatic
    fun require(): LegendaryMaceApi = api
        ?: error("LegendaryMaceApi is not available. Ensure ProjectMace is enabled before requesting the API.")

    internal fun register(api: LegendaryMaceApi) {
        this.api = api
    }

    internal fun unregister(current: LegendaryMaceApi?) {
        if (this.api == current) {
            this.api = null
        }
    }
}
