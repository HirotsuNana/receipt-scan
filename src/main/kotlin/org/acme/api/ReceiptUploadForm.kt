package org.acme.api

import jakarta.ws.rs.FormParam
import java.io.InputStream

data class ReceiptUploadForm(
    @FormParam("file") val image: InputStream,
    @FormParam("fileName") val fileName: String?
) {
    // デフォルトコンストラクタでInputStreamをnullで初期化
    constructor() : this(InputStream.nullInputStream(), null)

    // もしファイル名がnullの場合はタイムスタンプでファイル名を生成するメソッドを追加
    fun generateFileNameIfNull(): String {
        return fileName ?: "receipt-${System.currentTimeMillis()}.jpg"
    }
}
