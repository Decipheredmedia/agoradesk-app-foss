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
package com.agoradesk.app.backend.bitcoin

import android.util.Base64
import android.util.Log
import com.agoradesk.app.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * Bitcoin Core RPC client for wallet operations.
 *
 * Provides a Kotlin coroutine-based interface to Bitcoin Core's JSON-RPC API.
 * All monetary amounts use BigDecimal for precision. Network calls use structured
 * concurrency and have configured timeouts (connect: 10s, read: 30s, write: 30s).
 *
 * @property rpcUrl Bitcoin Core RPC endpoint URL
 * @property rpcUser RPC authentication username
 * @property rpcPassword RPC authentication password
 */
class BitcoinRpcClient(
    private val rpcUrl: String = BuildConfig.BITCOIN_RPC_URL,
    private val rpcUser: String,
    private val rpcPassword: String
) {
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Generates HTTP Basic Authentication header.
     *
     * @return Base64-encoded authorization header value
     */
    private fun getAuthHeader(): String {
        val credentials = "$rpcUser:$rpcPassword"
        val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    /**
     * Executes an RPC call to Bitcoin Core.
     *
     * @param T Expected response type
     * @param method RPC method name
     * @param params Method parameters
     * @param responseClass Class of the expected response
     * @return Deserialized response or null on error
     */
    private suspend fun <T> call(
        method: String,
        params: List<Any> = emptyList(),
        responseClass: Class<T>
    ): T? = withContext(Dispatchers.IO) {
        try {
            val request = BitcoinRpcRequest(method = method, params = params)
            val jsonBody = gson.toJson(request)

            if (BuildConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Request: $method - $jsonBody")
            }

            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val httpRequest = Request.Builder()
                .url(rpcUrl)
                .header("Authorization", getAuthHeader())
                .post(requestBody)
                .build()

            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (BuildConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Response: $responseBody")
            }

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP Error: ${response.code}")
                return@withContext null
            }

            val rpcResponse = gson.fromJson(responseBody, BitcoinRpcResponse::class.java)
            if (rpcResponse.error != null) {
                Log.e(TAG, "RPC Error: ${rpcResponse.error.message}")
                return@withContext null
            }

            gson.fromJson(gson.toJson(rpcResponse.result), responseClass)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            null
        }
    }

    /**
     * Retrieves the wallet balance.
     *
     * @return WalletBalance with confirmed, unconfirmed, and immature balances, or null on error
     */
    suspend fun getBalance(): WalletBalance? {
        return call("getbalances", emptyList(), WalletBalance::class.java)
    }

    /**
     * Generates a new Bitcoin address for receiving payments.
     *
     * @param label Optional label to associate with the address
     * @return New Bitcoin address or null on error
     */
    suspend fun getNewAddress(label: String = ""): String? {
        return call("getnewaddress", listOf(label), String::class.java)
    }

    /**
     * Sends Bitcoin to a specified address.
     *
     * @param address Destination Bitcoin address
     * @param amount Amount to send in BTC (BigDecimal for precision)
     * @return Transaction ID (txid) or null on error
     */
    suspend fun sendToAddress(address: String, amount: BigDecimal): String? {
        return call("sendtoaddress", listOf(address, amount), String::class.java)
    }

    /**
     * Lists recent transactions from the wallet.
     *
     * @param count Maximum number of transactions to return
     * @return List of transaction information or null on error
     */
    suspend fun listTransactions(count: Int = 10): List<TransactionInfo>? {
        return call("listtransactions", listOf("*", count), Array<TransactionInfo>::class.java)?.toList()
    }

    /**
     * Retrieves detailed information about a specific transaction.
     *
     * @param txid Transaction hash identifier
     * @return Transaction information or null on error
     */
    suspend fun getTransaction(txid: String): TransactionInfo? {
        return call("gettransaction", listOf(txid), TransactionInfo::class.java)
    }

    /**
     * Validates a Bitcoin address format.
     *
     * @param address Bitcoin address to validate
     * @return true if the address is valid, false otherwise
     */
    suspend fun validateAddress(address: String): Boolean {
        val result = call("validateaddress", listOf(address), Map::class.java)
        return result?.get("isvalid") as? Boolean ?: false
    }

    private companion object {
        private const val TAG = "BitcoinRPC"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
