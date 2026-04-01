/*
 * Copyright 2025 AgoraDesk/LocalMonero
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.agoradesk.app.backend.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.math.BigDecimal

/**
 * CoinGecko API interface for cryptocurrency price data.
 *
 * Provides real-time and historical price information for cryptocurrencies.
 * All prices use BigDecimal for precision.
 */
interface CoinGeckoApi {

    /**
     * Fetches current price data for specified cryptocurrencies.
     *
     * @param ids Comma-separated cryptocurrency IDs (e.g., "bitcoin,monero")
     * @param vsCurrencies Comma-separated fiat currencies (e.g., "usd,eur")
     * @param include24hrChange Whether to include 24-hour price change
     * @param includeMarketCap Whether to include market capitalization
     * @return Response containing price data mapped by cryptocurrency ID
     */
    @GET("simple/price")
    suspend fun getPrice(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String,
        @Query("include_24hr_change") include24hrChange: Boolean = true,
        @Query("include_market_cap") includeMarketCap: Boolean = true
    ): Response<Map<String, CoinPrice>>

    /**
     * Fetches market data for specified cryptocurrencies.
     *
     * @param vsCurrency Fiat currency for pricing (e.g., "usd")
     * @param ids Comma-separated cryptocurrency IDs
     * @param order Sort order (default: market cap descending)
     * @param perPage Number of results per page
     * @param page Page number
     * @param sparkline Whether to include sparkline data
     * @return Response containing list of market data
     */
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

/**
 * Cryptocurrency price information.
 *
 * All monetary values use BigDecimal for precision.
 *
 * @property usd Price in US dollars
 * @property eur Price in euros
 * @property usd24hChange 24-hour price change in USD
 * @property usdMarketCap Market capitalization in USD
 */
data class CoinPrice(
    @SerializedName("usd") val usd: BigDecimal?,
    @SerializedName("eur") val eur: BigDecimal?,
    @SerializedName("usd_24h_change") val usd24hChange: BigDecimal?,
    @SerializedName("usd_market_cap") val usdMarketCap: BigDecimal?
)

/**
 * Cryptocurrency market information.
 *
 * Contains comprehensive market data including price, volume, and statistics.
 *
 * @property id Cryptocurrency identifier
 * @property symbol Cryptocurrency symbol (e.g., BTC, XMR)
 * @property name Cryptocurrency full name
 * @property image URL to cryptocurrency logo image
 * @property currentPrice Current price (BigDecimal for precision)
 * @property marketCap Market capitalization
 * @property marketCapRank Market cap ranking position
 * @property totalVolume 24-hour trading volume
 * @property high24h 24-hour high price
 * @property low24h 24-hour low price
 * @property priceChange24h 24-hour absolute price change
 * @property priceChangePercentage24h 24-hour percentage price change
 */
data class CoinMarket(
    @SerializedName("id") val id: String,
    @SerializedName("symbol") val symbol: String,
    @SerializedName("name") val name: String,
    @SerializedName("image") val image: String,
    @SerializedName("current_price") val currentPrice: BigDecimal,
    @SerializedName("market_cap") val marketCap: Long,
    @SerializedName("market_cap_rank") val marketCapRank: Int,
    @SerializedName("total_volume") val totalVolume: Long,
    @SerializedName("high_24h") val high24h: BigDecimal,
    @SerializedName("low_24h") val low24h: BigDecimal,
    @SerializedName("price_change_24h") val priceChange24h: BigDecimal,
    @SerializedName("price_change_percentage_24h") val priceChangePercentage24h: BigDecimal
)
