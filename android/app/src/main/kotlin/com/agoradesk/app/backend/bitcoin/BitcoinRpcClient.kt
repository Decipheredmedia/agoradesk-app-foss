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
import java.util.concurrent.TimeUnit

class BitcoinRpcClient(
    private val rpcUrl: String = BuildConfig.BITCOIN_RPC_URL,
    private val rpcUser: String,
    private val rpcPassword: String
) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getAuthHeader(): String {
        val credentials = "$rpcUser:$rpcPassword"
        return "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun <T> call(method: String, params: List<Any> = emptyList(), responseClass: Class<T>): T? {
        return withContext(Dispatchers.IO) {
            try {
                val request = BitcoinRpcRequest(method = method, params = params)
                val jsonBody = gson.toJson(request)
                
                if (BuildConfig.DEBUG_LOGGING) {
                    Log.d("BitcoinRPC", "Request: $method - $jsonBody")
                }

                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(rpcUrl)
                    .header("Authorization", getAuthHeader())
                    .post(requestBody)
                    .build()

                val response = client.newCall(httpRequest).execute()
                val responseBody = response.body?.string()

                if (BuildConfig.DEBUG_LOGGING) {
                    Log.d("BitcoinRPC", "Response: $responseBody")
                }

                if (!response.isSuccessful) {
                    Log.e("BitcoinRPC", "HTTP Error: ${response.code}")
                    return@withContext null
                }

                val rpcResponse = gson.fromJson(responseBody, BitcoinRpcResponse::class.java)
                if (rpcResponse.error != null) {
                    Log.e("BitcoinRPC", "RPC Error: ${rpcResponse.error.message}")
                    return@withContext null
                }

                gson.fromJson(gson.toJson(rpcResponse.result), responseClass)
            } catch (e: Exception) {
                Log.e("BitcoinRPC", "Exception: ${e.message}", e)
                null
            }
        }
    }

    suspend fun getBalance(): WalletBalance? {
        return call("getbalances", emptyList(), WalletBalance::class.java)
    }

    suspend fun getNewAddress(label: String = ""): String? {
        return call("getnewaddress", listOf(label), String::class.java)
    }

    suspend fun sendToAddress(address: String, amount: Double): String? {
        val response = call("sendtoaddress", listOf(address, amount), String::class.java)
        return response
    }

    suspend fun listTransactions(count: Int = 10): List<TransactionInfo>? {
        return call("listtransactions", listOf("*", count), Array<TransactionInfo>::class.java)?.toList()
    }

    suspend fun getTransaction(txid: String): TransactionInfo? {
        return call("gettransaction", listOf(txid), TransactionInfo::class.java)
    }

    suspend fun validateAddress(address: String): Boolean {
        val result = call("validateaddress", listOf(address), Map::class.java)
        return result?.get("isvalid") as? Boolean ?: false
    }
}
