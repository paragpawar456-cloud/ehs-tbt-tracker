package com.ehs.tbttracker.data.remote

import kotlinx.serialization.Serializable

/** Raw sheet row; every value is the cell's *display* string so the app owns all parsing. */
@Serializable
data class SheetRowDto(
    val row: Int = 0,
    val timestamp: String = "",
    val date: String = "",
    val contractor: String = "",
    val manpower: String = "",
    val location: String = "",
    val photo: String = "",
    val notes: String = "",
    val clientRef: String = "",
)

@Serializable
data class ListResponse(
    val ok: Boolean,
    val code: Int? = null,
    val error: String? = null,
    val serverTime: String? = null,
    val rows: List<SheetRowDto> = emptyList(),
    /** Names from the optional "Master Contractors" tab. */
    val masters: List<String> = emptyList(),
)

@Serializable
data class CreateRequest(
    val action: String = "create",
    val token: String,
    val clientRef: String,
    val date: String,
    val contractor: String,
    val manpower: Int,
    val location: String,
    val notes: String,
    val photoBase64: String?,
    val photoMime: String = "image/jpeg",
    val photoName: String,
)

@Serializable
data class CreateResponse(
    val ok: Boolean,
    val code: Int? = null,
    val error: String? = null,
    val row: Int? = null,
    val photoUrl: String? = null,
    val timestamp: String? = null,
    val duplicate: Boolean = false,
)

@Serializable
data class ThumbResponse(
    val ok: Boolean,
    val code: Int? = null,
    val error: String? = null,
    val mime: String? = null,
    val data: String? = null,
)

/** Apps Script always answers HTTP 200; the logical status lives in the body. */
class BackendException(val code: Int, message: String) : Exception(message) {
    /** 4xx (except 408/429) will fail again if retried unchanged. */
    val isPermanent: Boolean get() = code in 400..499 && code != 408 && code != 429
}
