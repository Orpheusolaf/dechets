package com.example.brusselscollectiondemo.data

import java.time.LocalDate

enum class WasteType {
    WHITE,
    BLUE,
    YELLOW,
    ORGANIC,
    BULKY,
    UNKNOWN
}

data class CollectionEvent(
    val date: LocalDate,
    val wasteType: WasteType,
    val label: String
)

data class CollectionSchedule(
    val query: AddressQuery,
    val events: List<CollectionEvent>,
    val source: String = "Bruxelles-Propreté"
)
