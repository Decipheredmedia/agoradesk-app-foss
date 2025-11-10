package com.agoradesk.app.backend.monero

import com.google.gson.annotations.SerializedName

data class MoneroRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: String = "0",
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: Map<String, Any> = emptyMap()
)

data class MoneroRpcResponse<T>(
    @SerializedName("id") val id: String,
    @SerializedName("jsonrpc") val jsonrpc: String,
    @SerializedName("result") val result: T?
)

data class MoneroBalance(
    @SerializedName("balance") val balance: Long,
    @SerializedName("unlocked_balance") val unlockedBalance: Long,
    @SerializedName("multisig_import_needed") val multisigImportNeeded: Boolean = false
)

data class MoneroAddress(
    @SerializedName("address") val address: String,
    @SerializedName("address_index") val addressIndex: Int,
    @SerializedName("label") val label: String?,
    @SerializedName("used") val used: Boolean
)

data class MoneroTransfer(
    @SerializedName("txid") val txid: String,
    @SerializedName("payment_id") val paymentId: String,
    @SerializedName("height") val height: Long,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("amount") val amount: Long,
    @SerializedName("fee") val fee: Long,
    @SerializedName("note") val note: String,
    @SerializedName("destinations") val destinations: List<Destination>,
    @SerializedName("type") val type: String,
    @SerializedName("unlock_time") val unlockTime: Long,
    @SerializedName("subaddr_index") val subaddrIndex: SubaddressIndex,
    @SerializedName("address") val address: String,
    @SerializedName("confirmations") val confirmations: Long
)

data class Destination(
    @SerializedName("amount") val amount: Long,
    @SerializedName("address") val address: String
)

data class SubaddressIndex(
    @SerializedName("major") val major: Int,
    @SerializedName("minor") val minor: Int
)

data class TransferResult(
    @SerializedName("tx_hash") val txHash: String,
    @SerializedName("tx_key") val txKey: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("fee") val fee: Long,
    @SerializedName("tx_blob") val txBlob: String,
    @SerializedName("tx_metadata") val txMetadata: String,
    @SerializedName("multisig_txset") val multisigTxset: String
)
