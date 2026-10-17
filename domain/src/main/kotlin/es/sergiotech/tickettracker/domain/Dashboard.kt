package es.sergiotech.tickettracker.domain

import java.time.LocalDate

enum class ProductFilter {
    ALL,
    WARRANTY,
    CRITICAL,
    RETURNS,
}

fun Product.matches(filter: ProductFilter, today: LocalDate): Boolean =
    when (filter) {
        ProductFilter.ALL -> true
        ProductFilter.WARRANTY -> warrantyEnd?.let { it >= today } == true
        ProductFilter.CRITICAL ->
            warrantyEnd?.let { it >= today && it <= today.plusDays(30) } == true
        ProductFilter.RETURNS -> returnEnd?.let { it >= today } == true
    }

fun TrackedContract.needsRenewal(today: LocalDate): Boolean =
    active && nextDate <= today.plusDays(30)
