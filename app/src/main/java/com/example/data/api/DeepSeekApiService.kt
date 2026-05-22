package com.example.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface DeepSeekApiService {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorizationHeader: String,
        @Body request: DeepSeekChatRequest
    ): DeepSeekChatResponse

    companion object {
        fun create(baseUrl: String = "https://api.deepseek.com/"): DeepSeekApiService {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val validUrl = if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
                baseUrl
            } else {
                "https://api.deepseek.com/"
            }

            return Retrofit.Builder()
                .baseUrl(validUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(DeepSeekApiService::class.java)
        }
    }
}
