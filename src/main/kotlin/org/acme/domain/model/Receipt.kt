package org.acme.domain.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Receipt(
    @JsonProperty("storeName") val storeName: String?,
    @JsonProperty("totalPrice") val totalPrice: Any?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("items") val items: List<Map<String, Any>>
) {
    init {
        // Store name validation
        storeName?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("Store name cannot be null or blank.")

        // Total price validation
        (totalPrice as? Int)?.takeIf { it >= 0 } ?: throw IllegalArgumentException("Total price must be a non-negative number.")

        // Date validation
        date?.takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } ?: throw IllegalArgumentException("Invalid date format: $date")
    }
}
