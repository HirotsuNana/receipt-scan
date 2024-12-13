package org.acme.domain.model

import io.quarkus.logging.Log

data class Receipt(
    val storeName: String?,
    val totalPrice: Double?,
    val date: String?
) {
    init {
        Log.info("Initializing Receipt: storeName=$storeName, totalPrice=$totalPrice, date=$date")

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
