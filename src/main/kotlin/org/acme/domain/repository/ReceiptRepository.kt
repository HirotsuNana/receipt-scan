/*
ReceiptRepositoryインターフェースを使って、Firestoreなどのストレージに関する詳細を隠蔽しています。
これにより、ストレージの実装を変更する際にも、他のコードへの影響を最小限に抑えることができます。
（例えば、FirestoreからBigQueryに変更する場合でも、インターフェースは変わりません）
*/

package org.acme.domain.repository

import org.acme.domain.model.Receipt

interface ReceiptRepository {
    fun save(receipt: Receipt)
    // 他に必要なメソッドを追加（例えば、検索や削除など）
}
