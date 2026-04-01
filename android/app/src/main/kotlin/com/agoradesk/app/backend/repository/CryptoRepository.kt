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
package com.agoradesk.app.backend.repository

import android.content.Context
import com.agoradesk.app.BuildConfig
import com.agoradesk.app.backend.api.CoinGeckoApi
import com.agoradesk.app.backend.api.CoinPrice
import com.agoradesk.app.backend.bitcoin.BitcoinRpcClient
import com.agoradesk.app.backend.bitcoin.TransactionInfo
import com.agoradesk.app.backend.bitcoin.WalletBalance
import com.agoradesk.app.backend.monero.MoneroBalance
import com.agoradesk.app.backend.monero.MoneroRpcClient
import com.agoradesk.app.backend.monero.MoneroTransfer
import com.agoradesk.app.security.SecureStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Repository for managing cryptocurrency operations.
 *
 * Combines Bitcoin, Monero, and price data sources using the Repository pattern.
 * Provides a clean interface for data access with proper error handling.
 * All Bitcoin monetary amounts use BigDecimal for precision.
 *
 * @property context Android application context
 */
class CryptoRepository(context: Context) {

    private val secureStorage = SecureStorage(context)

    private val bitcoinClient by lazy {
        val user = secureStorage.getString(SecureStorage.KEY_BTC_RPC_USER) ?: DEFAULT_BTC_USER
        val password = secureStorage.getString(SecureStorage.KEY_BTC_RPC_PASSWORD) ?: ""
        BitcoinRpcClient(
            rpcUrl = BuildConfig.BITCOIN_RPC_URL,
            rpcUser = user,
            rpcPassword = password
        )
    }

    private val moneroClient by lazy {
        val user = secureStorage.getString(SecureStorage.KEY_XMR_RPC_USER)
        val password = secureStorage.getString(SecureStorage.KEY_XMR_RPC_PASSWORD)
        MoneroRpcClient(
            rpcUrl = BuildConfig.MONERO_RPC_URL,
            rpcUser = user,
            rpcPassword = password
        )
    }

    private val coinGeckoApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.COINGECKO_API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    // Bitcoin Operations

    /**
     * Retrieves the Bitcoin wallet balance.
     *
     * @return Result containing WalletBalance or error
     */
    suspend fun getBitcoinBalance(): Result<WalletBalance> = executeWithResult {
        bitcoinClient.getBalance() ?: throw Exception("Failed to fetch Bitcoin balance")
    }

    /**
     * Generates a new Bitcoin receiving address.
     *
     * @param label Optional label for the address
     * @return Result containing the new address or error
     */
    suspend fun getBitcoinAddress(label: String = ""): Result<String> = executeWithResult {
        bitcoinClient.getNewAddress(label) ?: throw Exception("Failed to generate Bitcoin address")
    }

    /**
     * Sends Bitcoin to a specified address.
     *
     * @param address Destination Bitcoin address
     * @param amount Amount to send in BTC (BigDecimal for precision)
     * @return Result containing transaction ID or error
     */
    suspend fun sendBitcoin(address: String, amount: BigDecimal): Result<String> = executeWithResult {
        if (!bitcoinClient.validateAddress(address)) {
            throw Exception("Invalid Bitcoin address")
        }
        bitcoinClient.sendToAddress(address, amount) ?: throw Exception("Failed to send Bitcoin")
    }

    /**
     * Retrieves recent Bitcoin transactions.
     *
     * @param count Maximum number of transactions to fetch
     * @return Result containing list of transactions or error
     */
    suspend fun getBitcoinTransactions(count: Int = 10): Result<List<TransactionInfo>> = executeWithResult {
        bitcoinClient.listTransactions(count) ?: throw Exception("Failed to fetch transactions")
    }

    // Monero Operations

    /**
     * Retrieves the Monero wallet balance.
     *
     * @return Result containing MoneroBalance or error
     */
    suspend fun getMoneroBalance(): Result<MoneroBalance> = executeWithResult {
        moneroClient.getBalance() ?: throw Exception("Failed to fetch Monero balance")
    }

    /**
     * Retrieves the primary Monero wallet address.
     *
     * @return Result containing the address or error
     */
    suspend fun getMoneroAddress(): Result<String> = executeWithResult {
        val addressInfo = moneroClient.getAddress()
        addressInfo?.address ?: throw Exception("Failed to get Monero address")
    }

    /**
     * Sends Monero to a specified address.
     *
     * @param address Destination Monero address
     * @param amount Amount in atomic units (1 XMR = 1e12 atomic units)
     * @return Result containing transaction hash or error
     */
    suspend fun sendMonero(address: String, amount: Long): Result<String> = executeWithResult {
        if (!moneroClient.validateAddress(address)) {
            throw Exception("Invalid Monero address")
        }
        val result = moneroClient.transfer(address, amount)
        result?.txHash ?: throw Exception("Failed to send Monero")
    }

    /**
     * Retrieves Monero transaction history.
     *
     * @return Result containing list of transfers or error
     */
    suspend fun getMoneroTransfers(): Result<List<MoneroTransfer>> = executeWithResult {
        moneroClient.getTransfers() ?: throw Exception("Failed to fetch transfers")
    }

    // Price Data

    /**
     * Fetches current cryptocurrency prices.
     *
     * @param ids Comma-separated cryptocurrency IDs
     * @param vsCurrencies Comma-separated fiat currencies
     * @return Result containing price map or error
     */
    suspend fun getCryptoPrices(
        ids: String = "bitcoin,monero",
        vsCurrencies: String = "usd,eur"
    ): Result<Map<String, CoinPrice>> = executeWithResult {
        val response = coinGeckoApi.getPrice(
            ids = ids,
            vsCurrencies = vsCurrencies,
            include24hrChange = true,
            includeMarketCap = true
        )

        if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            throw Exception("Failed to fetch prices: ${response.code()}")
        }
    }

    // Flow-based operations for real-time updates

    /**
     * Observes Bitcoin wallet balance with periodic updates.
     *
     * Emits balance every 30 seconds.
     *
     * @return Flow of WalletBalance or null on error
     */
    fun observeBitcoinBalance(): Flow<WalletBalance?> = flow {
        while (true) {
            emit(bitcoinClient.getBalance())
            delay(BALANCE_UPDATE_INTERVAL_MS)
        }
    }

    /**
     * Observes Monero wallet balance with periodic updates.
     *
     * Emits balance every 30 seconds.
     *
     * @return Flow of MoneroBalance or null on error
     */
    fun observeMoneroBalance(): Flow<MoneroBalance?> = flow {
        while (true) {
            emit(moneroClient.getBalance())
            delay(BALANCE_UPDATE_INTERVAL_MS)
        }
    }

    /**
     * Observes cryptocurrency prices with periodic updates.
     *
     * Emits prices every minute.
     *
     * @return Flow of price map or null on error
     */
    fun observePrices(): Flow<Map<String, CoinPrice>?> = flow {
        while (true) {
            val response = coinGeckoApi.getPrice(
                ids = "bitcoin,monero",
                vsCurrencies = "usd,eur",
                include24hrChange = true
            )
            if (response.isSuccessful) {
                emit(response.body())
            }
            delay(PRICE_UPDATE_INTERVAL_MS)
        }
    }

    /**
     * Executes a suspend function and wraps result in Result type.
     */
    private suspend inline fun <T> executeWithResult(
        crossinline block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        private const val DEFAULT_BTC_USER = "bitcoinrpc"
        private const val BALANCE_UPDATE_INTERVAL_MS = 30000L
        private const val PRICE_UPDATE_INTERVAL_MS = 60000L
    }
}
