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
package com.agoradesk.app.backend.monero

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
import java.util.concurrent.TimeUnit

/**
 * Monero Wallet RPC client for wallet operations.
 *
 * Provides a Kotlin coroutine-based interface to Monero Wallet RPC API.
 * All monetary amounts use Long (atomic units). Network calls use structured
 * concurrency and have configured timeouts (connect: 10s, read: 30s, write: 30s).
 *
 * @property rpcUrl Monero Wallet RPC endpoint URL
 * @property rpcUser Optional RPC authentication username
 * @property rpcPassword Optional RPC authentication password
 */
class MoneroRpcClient(
    private val rpcUrl: String = BuildConfig.MONERO_RPC_URL,
    private val rpcUser: String? = null,
    private val rpcPassword: String? = null
) {
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Generates HTTP Basic Authentication header if credentials are provided.
     *
     * @return Base64-encoded authorization header value or null
     */
    private fun getAuthHeader(): String? {
        return if (rpcUser != null && rpcPassword != null) {
            val credentials = "$rpcUser:$rpcPassword"
            val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            "Basic $encoded"
        } else null
    }

    /**
     * Executes an RPC call to Monero Wallet.
     *
     * @param T Expected response type
     * @param method RPC method name
     * @param params Method parameters as a map
     * @param responseClass Class of the expected response
     * @return Deserialized response or null on error
     */
    private suspend fun <T> call(
        method: String,
        params: Map<String, Any> = emptyMap(),
        responseClass: Class<T>
    ): T? = withContext(Dispatchers.IO) {
        try {
            val request = MoneroRpcRequest(method = method, params = params)
            val jsonBody = gson.toJson(request)

            if (BuildConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Request: $method - $jsonBody")
            }

            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val httpRequestBuilder = Request.Builder()
                .url("$rpcUrl/json_rpc")
                .post(requestBody)

            getAuthHeader()?.let { httpRequestBuilder.header("Authorization", it) }

            val response = client.newCall(httpRequestBuilder.build()).execute()
            val responseBody = response.body?.string()

            if (BuildConfig.DEBUG_LOGGING) {
                Log.d(TAG, "Response: $responseBody")
            }

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP Error: ${response.code}")
                return@withContext null
            }

            val rpcResponse = gson.fromJson(responseBody, MoneroRpcResponse::class.java)
            gson.fromJson(gson.toJson(rpcResponse.result), responseClass)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            null
        }
    }

    /**
     * Retrieves the wallet balance.
     *
     * @return MoneroBalance with balance and unlocked balance in atomic units, or null on error
     */
    suspend fun getBalance(): MoneroBalance? {
        return call("get_balance", emptyMap(), MoneroBalance::class.java)
    }

    /**
     * Retrieves the primary wallet address.
     *
     * @return MoneroAddress information or null on error
     */
    suspend fun getAddress(): MoneroAddress? {
        val result = call("get_address", emptyMap(), Map::class.java)
        return result?.let {
            val address = it["address"] as? String
            val addressIndex = ((it["address_index"] as? Double) ?: 0.0).toInt()
            address?.let { addr ->
                MoneroAddress(
                    address = addr,
                    addressIndex = addressIndex,
                    label = null,
                    used = false
                )
            }
        }
    }

    /**
     * Sends Monero to a specified address.
     *
     * @param address Destination Monero address
     * @param amount Amount to send in atomic units (1 XMR = 1e12 atomic units)
     * @return TransferResult with transaction details or null on error
     */
    suspend fun transfer(address: String, amount: Long): TransferResult? {
        val params = mapOf(
            "destinations" to listOf(
                mapOf(
                    "amount" to amount,
                    "address" to address
                )
            ),
            "priority" to 0,
            "get_tx_key" to true
        )
        return call("transfer", params, TransferResult::class.java)
    }

    /**
     * Retrieves transaction history from the wallet.
     *
     * @param filterByHeight Whether to filter by block height
     * @param minHeight Minimum block height for filtering
     * @return List of MoneroTransfer objects or null on error
     */
    suspend fun getTransfers(
        filterByHeight: Boolean = false,
        minHeight: Long = 0
    ): List<MoneroTransfer>? {
        val params = buildTransferParams(filterByHeight, minHeight)
        val result = call("get_transfers", params, Map::class.java)
        return result?.let { parseTransfers(it) }
    }

    /**
     * Builds parameters map for getTransfers call.
     */
    private fun buildTransferParams(
        filterByHeight: Boolean,
        minHeight: Long
    ): Map<String, Any> {
        val params = mutableMapOf<String, Any>(
            "in" to true,
            "out" to true,
            "pending" to true,
            "failed" to false,
            "pool" to true
        )
        if (filterByHeight) {
            params["min_height"] = minHeight
        }
        return params
    }

    /**
     * Parses transfer result map into list of MoneroTransfer objects.
     */
    private fun parseTransfers(result: Map<*, *>): List<MoneroTransfer> {
        val allTransfers = mutableListOf<MoneroTransfer>()

        (result["in"] as? List<*>)?.forEach { transfer ->
            gson.fromJson(gson.toJson(transfer), MoneroTransfer::class.java)?.let {
                allTransfers.add(it)
            }
        }

        (result["out"] as? List<*>)?.forEach { transfer ->
            gson.fromJson(gson.toJson(transfer), MoneroTransfer::class.java)?.let {
                allTransfers.add(it)
            }
        }

        return allTransfers
    }

    /**
     * Validates a Monero address format.
     *
     * @param address Monero address to validate
     * @return true if the address is valid, false otherwise
     */
    suspend fun validateAddress(address: String): Boolean {
        val params = mapOf("address" to address)
        val result = call("validate_address", params, Map::class.java)
        return result?.get("valid") as? Boolean ?: false
    }

    private companion object {
        private const val TAG = "MoneroRPC"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
