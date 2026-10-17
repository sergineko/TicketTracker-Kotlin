package es.sergiotech.tickettracker.data

import es.sergiotech.tickettracker.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal fun Purchase.json(): JSONObject =
    JSONObject().apply {
        put("schema_version", 1)
        put("id", id)
        put("owner_id", ownerId)
        put("merchant", merchant)
        put("purchased_on", purchasedOn)
        put("delivered_on", deliveredOn ?: JSONObject.NULL)
        put("total_minor", total.minorUnits)
        put("currency", total.currency)
        put("channel", channel.name)
        put("order_number", orderNumber)
        put("notes", notes)
        put("revision", revision)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted_at", deletedAt ?: JSONObject.NULL)
        put(
            "lines",
            JSONArray().apply {
                lines.forEach { line ->
                    put(
                        JSONObject().apply {
                            put("id", line.id)
                            put("quantity", line.quantity)
                            put("unit_price_minor", line.unitPrice?.minorUnits ?: JSONObject.NULL)
                            put(
                                "product",
                                JSONObject().apply {
                                    put("id", line.product.id)
                                    put("name", line.product.name)
                                    put("warranty_end", line.product.warrantyEnd ?: JSONObject.NULL)
                                    put("return_end", line.product.returnEnd ?: JSONObject.NULL)
                                    put("deadline_source", line.product.deadlineSource)
                                },
                            )
                        }
                    )
                }
            },
        )
    }

internal fun ReceiptDocument.json(): JSONObject =
    JSONObject().apply {
        put("schema_version", 1)
        put("id", id)
        put("owner_id", ownerId)
        put("purchase_id", purchaseId ?: JSONObject.NULL)
        put("display_name", displayName)
        put("state", state.name)
        put("created_at", createdAt)
        put("extracted_text", extractedText)
        put("retention_policy", "KEEP_ORIGINAL")
        put(
            "variants",
            JSONArray().apply {
                variants.forEach { variant ->
                    put(
                        JSONObject().apply {
                            put("id", variant.id)
                            put("kind", variant.kind.name)
                            put("path", "documents/${variant.relativePath}")
                            put("mime_type", variant.mimeType)
                            put("byte_size", variant.byteSize)
                            put("sha256", variant.sha256)
                        }
                    )
                }
            },
        )
    }
