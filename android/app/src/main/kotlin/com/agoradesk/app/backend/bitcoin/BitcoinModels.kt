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

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/**
 * Bitcoin Core RPC request payload.
 *
 * @property jsonrpc JSON-RPC version (always "2.0")
 * @property id Request identifier
 * @property method RPC method name to invoke
 * @property params Method parameters as a list
 */
data class BitcoinRpcRequest(
    @SerializedName("jsonrpc") val jsonrpc: String = "2.0",
    @SerializedName("id") val id: String = "agoradesk",
    @SerializedName("method") val method: String,
    @SerializedName("params") val params: List<Any> = emptyList()
)

/**
 * Bitcoin Core RPC response wrapper.
 *
 * @param T Type of the result data
 * @property result Successful response data (null on error)
 * @property error Error details if the RPC call failed
 * @property id Request identifier matching the request
 */
data class BitcoinRpcResponse<T>(
    @SerializedName("result") val result: T?,
    @SerializedName("error") val error: RpcError?,
    @SerializedName("id") val id: String
)

/**
 * RPC error details.
 *
 * @property code Error code from Bitcoin Core
 * @property message Human-readable error message
 */
data class RpcError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String
)

/**
 * Bitcoin wallet balance information.
 *
 * All amounts are in BTC using BigDecimal for precision.
 *
 * @property balance Confirmed balance in BTC
 * @property unconfirmedBalance Unconfirmed balance in BTC
 * @property immatureBalance Immature (coinbase) balance in BTC
 */
data class WalletBalance(
    @SerializedName("balance") val balance: BigDecimal,
    @SerializedName("unconfirmed_balance") val unconfirmedBalance: BigDecimal,
    @SerializedName("immature_balance") val immatureBalance: BigDecimal
)

/**
 * Bitcoin transaction information.
 *
 * @property txid Transaction hash identifier
 * @property amount Transaction amount in BTC (BigDecimal for precision)
 * @property confirmations Number of confirmations
 * @property time Transaction timestamp (Unix epoch)
 * @property timeReceived Time the transaction was received (Unix epoch)
 * @property address Associated address (may be null)
 * @property category Transaction category (send, receive, etc.)
 * @property label Address label if set
 * @property vout Output index
 */
data class TransactionInfo(
    @SerializedName("txid") val txid: String,
    @SerializedName("amount") val amount: BigDecimal,
    @SerializedName("confirmations") val confirmations: Int,
    @SerializedName("time") val time: Long,
    @SerializedName("timereceived") val timeReceived: Long,
    @SerializedName("address") val address: String?,
    @SerializedName("category") val category: String,
    @SerializedName("label") val label: String?,
    @SerializedName("vout") val vout: Int
)

/**
 * Response from sending a Bitcoin transaction.
 *
 * @property txid Transaction hash identifier
 */
data class SendTransactionResponse(
    @SerializedName("txid") val txid: String
)
