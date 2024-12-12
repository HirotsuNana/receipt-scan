/*
Receiptクラスがドメインモデルとして定義され、レシートに関連する情報を保持します。
将来的には、レシートに関連するビジネスロジック（例えば、計算や検証など）をこのクラスに追加できます。
*/

package org.acme.domain.model

import io.quarkus.logging.Log

data class Receipt(
    val storeName: String,
    val totalPrice: Double,
    val date: String
) {
    init {
        Log.info("Initializing Receipt: storeName=$storeName, totalPrice=$totalPrice, date=$date")
        require(storeName.isNotBlank()) { "Store name cannot be blank." }
        require(totalPrice >= 0) { "Total price cannot be negative." }
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) { "Invalid date format: $date" }
    }
}
