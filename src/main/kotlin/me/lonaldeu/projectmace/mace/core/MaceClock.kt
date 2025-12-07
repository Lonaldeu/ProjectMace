package me.lonaldeu.projectmace.mace.core

import java.time.Instant

fun nowSeconds(): Double = Instant.now().epochSecond.toDouble()
