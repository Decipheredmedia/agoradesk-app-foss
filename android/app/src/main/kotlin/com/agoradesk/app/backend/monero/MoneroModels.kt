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

import com.google.gson.annotations.SerializedName

/**
 * Monero Wallet RPC request payload.
 *
 * @property jsonrpc JSON-RPC version (always "2.0")
 * @property id Request identifier
 * @property method RPC method name to invoke
 * @property params Method parameters as a map
 */
data class MoneroRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: String = "0",
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: Map<String, Any> = emptyMap()
)

/**
 * Monero Wallet RPC response wrapper.
 *
 * @param T Type of the result data
 * @property id Request identifier matching the request
 * @property jsonrpc JSON-RPC version
 * @property result Successful response data (null on error)
 */
data class MoneroRpcResponse<T>(
    @SerializedName("id") val id: String,
    @SerializedName("jsonrpc") val jsonrpc: String,
    @SerializedName("result") val result: T?
)

/**
 * Monero wallet balance information.
 *
 * All amounts are in atomic units (1 XMR = 1e12 atomic units).
 *
 * @property balance Total balance in atomic units
 * @property unlockedBalance Unlocked (spendable) balance in atomic units
 * @property multisigImportNeeded Whether multisig import is required
 */
data class MoneroBalance(
    @SerializedName("balance") val balance: Long,
    @SerializedName("unlocked_balance") val unlockedBalance: Long,
    @SerializedName("multisig_import_needed") val multisigImportNeeded: Boolean = false
)

/**
 * Monero wallet address information.
 *
 * @property address The Monero address string
 * @property addressIndex Index of the address in the wallet
 * @property label Optional label associated with the address
 * @property used Whether the address has been used
 */
data class MoneroAddress(
    @SerializedName("address") val address: String,
    @SerializedName("address_index") val addressIndex: Int,
    @SerializedName("label") val label: String?,
    @SerializedName("used") val used: Boolean
)

/**
 * Monero transaction transfer information.
 *
 * @property txid Transaction hash identifier
 * @property paymentId Payment ID associated with the transfer
 * @property height Block height of the transaction
 * @property timestamp Transaction timestamp (Unix epoch)
 * @property amount Transfer amount in atomic units
 * @property fee Transaction fee in atomic units
 * @property note Note/memo associated with the transfer
 * @property destinations List of destination addresses
 * @property type Transfer type (in, out, pending, etc.)
 * @property unlockTime Block height when funds become spendable
 * @property subaddrIndex Subaddress index information
 * @property address Associated address
 * @property confirmations Number of confirmations
 */
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

/**
 * Transfer destination information.
 *
 * @property amount Amount sent to this destination in atomic units
 * @property address Destination Monero address
 */
data class Destination(
    @SerializedName("amount") val amount: Long,
    @SerializedName("address") val address: String
)

/**
 * Subaddress index information.
 *
 * @property major Major index (account)
 * @property minor Minor index (subaddress within account)
 */
data class SubaddressIndex(
    @SerializedName("major") val major: Int,
    @SerializedName("minor") val minor: Int
)

/**
 * Result of a Monero transfer operation.
 *
 * @property txHash Transaction hash identifier
 * @property txKey Transaction private key
 * @property amount Amount transferred in atomic units
 * @property fee Transaction fee in atomic units
 * @property txBlob Raw transaction blob
 * @property txMetadata Transaction metadata
 * @property multisigTxset Multisig transaction set (if applicable)
 */
data class TransferResult(
    @SerializedName("tx_hash") val txHash: String,
    @SerializedName("tx_key") val txKey: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("fee") val fee: Long,
    @SerializedName("tx_blob") val txBlob: String,
    @SerializedName("tx_metadata") val txMetadata: String,
    @SerializedName("multisig_txset") val multisigTxset: String
)
