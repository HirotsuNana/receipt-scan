package org.acme.domain.repository

import org.acme.domain.model.Receipt

interface ReceiptRepository {
    fun save(receipt: Receipt)
}
