package org.acme.domain.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.quarkus.logging.Log

data class Receipt(
    @JsonProperty("storeName") val storeName: String?,
    @JsonProperty("totalPrice") val totalPrice: Int?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("items") val items: List<Map<String, Any>>
) {
    init {
        Log.info("Initializing Receipt: storeName=$storeName, totalPrice=$totalPrice, date=$date, item=$items")

        // storeName が null でない、かつ空白でないことを確認
        storeName?.let { require(it.isNotBlank()) { "Store name cannot be blank." } } ?: run {
            throw IllegalArgumentException("Store name cannot be null.")
        }

        // totalPrice が null でない、かつ0以上であることを確認
        totalPrice?.let {
            require(it >= 0) { "Total price cannot be negative." }
        } ?: run {
            throw IllegalArgumentException("Total price cannot be null.")
        }

        // date が null でなく、かつ正しい日付形式（yyyy-MM-dd）であることを確認
        date?.let {
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(it)) { "Invalid date format: $it" }
        } ?: run {
            throw IllegalArgumentException("Date cannot be null.")
        }
    }
}
