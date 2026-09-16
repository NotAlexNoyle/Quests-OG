package de.eisi05.npc.api.utils

import java.util.concurrent.Callable
import org.bukkit.plugin.Plugin

/**
 * No-op replacement for NpcApi's bundled bStats `Metrics`. The real class is excluded from the shaded jar (see
 * `relocateNpcApi` in build.gradle.kts); this keeps `NpcApi`'s constructor linking without phoning home.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Metrics(plugin: Plugin, serviceId: Int) {
    fun addCustomChart(chart: CustomChart) {}

    open class CustomChart(val chartId: String)

    class SingleLineChart(chartId: String, callable: Callable<Int>) : CustomChart(chartId)
}
