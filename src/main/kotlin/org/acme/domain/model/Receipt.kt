/*
Receiptクラスがドメインモデルとして定義され、レシートに関連する情報を保持します。
将来的には、レシートに関連するビジネスロジック（例えば、計算や検証など）をこのクラスに追加できます。
*/

package org.acme.domain.model

data class Receipt(
    val storeName: String,
    val totalPrice: Double,
    val date: String
)
