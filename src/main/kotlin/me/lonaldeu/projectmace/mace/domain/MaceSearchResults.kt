package me.lonaldeu.projectmace.mace.domain

internal data class MaceSearchResults(
    val realLocations: List<String>,
    val illegalLocations: List<String>
)
