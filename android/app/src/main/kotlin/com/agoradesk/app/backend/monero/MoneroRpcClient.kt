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

class MoneroRpcClient(
    private val rpcUrl: String = BuildConfig.MONERO_RPC_URL,
    private val rpcUser: String? = null,
    private val rpcPassword: String? = null
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getAuthHeader(): String? {
        return if (rpcUser != null && rpcPassword != null) {
            val credentials = "$rpcUser:$rpcPassword"
            "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        } else null
    }

    private suspend fun <T> call(method: String, params: Map<String, Any> = emptyMap(), responseClass: Class<T>): T? {
        return withContext(Dispatchers.IO) {
            try {
                val request = MoneroRpcRequest(method = method, params = params)
                val jsonBody = gson.toJson(request)
                
                if (BuildConfig.DEBUG_LOGGING) {
                    Log.d("MoneroRPC", "Request: $method - $jsonBody")
                }

                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val httpRequestBuilder = Request.Builder()
                    .url("$rpcUrl/json_rpc")
                    .post(requestBody)

                getAuthHeader()?.let { httpRequestBuilder.header("Authorization", it) }

                val response = client.newCall(httpRequestBuilder.build()).execute()
                val responseBody = response.body?.string()

                if (BuildConfig.DEBUG_LOGGING) {
                    Log.d("MoneroRPC", "Response: $responseBody")
                }

                if (!response.isSuccessful) {
                    Log.e("MoneroRPC", "HTTP Error: ${response.code}")
                    return@withContext null
                }

                val rpcResponse = gson.fromJson(responseBody, MoneroRpcResponse::class.java)
                gson.fromJson(gson.toJson(rpcResponse.result), responseClass)
            } catch (e: Exception) {
                Log.e("MoneroRPC", "Exception: ${e.message}", e)
                null
            }
        }
    }

    suspend fun getBalance(): MoneroBalance? {
        return call("get_balance", emptyMap(), MoneroBalance::class.java)
    }

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

    suspend fun getTransfers(filterByHeight: Boolean = false, minHeight: Long = 0): List<MoneroTransfer>? {
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

        val result = call("get_transfers", params, Map::class.java)
        return result?.let {
            val allTransfers = mutableListOf<MoneroTransfer>()
            
            (it["in"] as? List<*>)?.forEach { transfer ->
                gson.fromJson(gson.toJson(transfer), MoneroTransfer::class.java)?.let { t ->
                    allTransfers.add(t)
                }
            }
            
            (it["out"] as? List<*>)?.forEach { transfer ->
                gson.fromJson(gson.toJson(transfer), MoneroTransfer::class.java)?.let { t ->
                    allTransfers.add(t)
                }
            }
            
            allTransfers
        }
    }

    suspend fun validateAddress(address: String): Boolean {
        val params = mapOf("address" to address)
        val result = call("validate_address", params, Map::class.java)
        return result?.get("valid") as? Boolean ?: false
    }
}
