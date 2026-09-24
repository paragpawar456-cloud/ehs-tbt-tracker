package com.ehs.tbttracker.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Google Apps Script Web App. The deployment URL is passed with @Url so it can come from config.
 * Note: Apps Script answers POST with a 302 to script.googleusercontent.com; OkHttp follows it
 * as a GET, which is exactly what Apps Script expects.
 */
interface SheetsWebAppApi {
    @GET
    suspend fun list(
        @Url url: String,
        @Query("token") token: String,
        @Query("action") action: String = "list",
    ): ListResponse

    @POST
    suspend fun create(@Url url: String, @Body body: CreateRequest): CreateResponse

    @GET
    suspend fun thumbnail(
        @Url url: String,
        @Query("token") token: String,
        @Query("id") fileId: String,
        @Query("size") size: Int,
        @Query("action") action: String = "thumb",
    ): ThumbResponse
}
