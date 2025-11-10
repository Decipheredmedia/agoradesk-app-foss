package com.agoradesk.app.backend.bitcoin

import com.google.gson.annotations.SerializedName

data class BitcoinRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: String = "agoradesk",
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: List<Any> = emptyList()
)

data class BitcoinRpcResponse<T>(
    @SerializedName("result") val result: T?,
    @SerializedName("error") val error: RpcError?,
    @SerializedName("id") val id: String
)

data class RpcError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String
)

data class WalletBalance(
    @SerializedName("balance") val balance: Double,
    @SerializedName("unconfirmed_balance") val unconfirmedBalance: Double,
    @SerializedName("immature_balance") val immatureBalance: Double
)

data class TransactionInfo(
    @SerializedName("txid") val txid: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("confirmations") val confirmations: Int,
    @SerializedName("time") val time: Long,
    @SerializedName("timereceived") val timeReceived: Long,
    @SerializedName("address") val address: String?,
    @SerializedName("category") val category: String,
    @SerializedName("label") val label: String?,
    @SerializedName("vout") val vout: Int
)

data class SendTransactionResponse(
    @SerializedName("txid") val txid: String
)
