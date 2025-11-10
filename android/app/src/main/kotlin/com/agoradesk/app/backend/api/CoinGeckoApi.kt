package com.agoradesk.app.backend.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoApi {
    
    @GET("simple/price")
    suspend fun getPrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String,
        @Query("include_24hr_change") include24hrChange: Boolean = true,
        @Query("include_market_cap") includeMarketCap: Boolean = true
    ): Response<Map<String, CoinPrice>>
    
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String,
        @Query("ids") ids: String,
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 10,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = false
    ): Response<List<CoinMarket>>
}

data class CoinPrice(
    @SerializedName("usd") val usd: Double?,
    @SerializedName("eur") val eur: Double?,
    @SerializedName("usd_24h_change") val usd24hChange: Double?,
    @SerializedName("usd_market_cap") val usdMarketCap: Double?
)

data class CoinMarket(
    @SerializedName("id") val id: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("name") val name: String,
    @SerializedName("image") val image: String,
    @SerializedName("current_price") val currentPrice: Double,
    @SerializedName("market_cap") val marketCap: Long,
    @SerializedName("market_cap_rank") val marketCapRank: Int,
    @SerializedName("total_volume") val totalVolume: Long,
    @SerializedName("high_24h") val high24h: Double,
    @SerializedName("low_24h") val low24h: Double,
    @SerializedName("price_change_24h") val priceChange24h: Double,
    @SerializedName("price_change_percentage_24h") val priceChangePercentage24h: Double
)
