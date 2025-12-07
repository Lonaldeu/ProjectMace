package me.lonaldeu.projectmace.mace.core

import org.bukkit.Location
import kotlin.math.floor

fun clamp01(value: Double): Double = when {
    value < 0.0 -> 0.0
    value > 1.0 -> 1.0
    else -> value
}

fun Location.blockXFast(): Int = floor(x).toInt()
fun Location.blockYFast(): Int = floor(y).toInt()
fun Location.blockZFast(): Int = floor(z).toInt()
