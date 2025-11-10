package com.agoradesk.app.backend.repository

import android.content.Context
import com.agoradesk.app.backend.api.CoinGeckoApi
import com.agoradesk.app.backend.bitcoin.BitcoinRpcClient
import com.agoradesk.app.backend.bitcoin.TransactionInfo
import com.agoradesk.app.backend.bitcoin.WalletBalance
import com.agoradesk.app.backend.monero.MoneroRpcClient
import com.agoradesk.app.backend.monero.MoneroBalance
import com.agoradesk.app.backend.monero.MoneroTransfer
import com.agoradesk.app.security.SecureStorage
import com.agoradesk.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Repository pattern for managing crypto operations
 * Combines Bitcoin, Monero, and price data sources
 */
class CryptoRepository(context: Context) {
    
    private val secureStorage = SecureStorage(context)
    
    private val bitcoinClient by lazy {
        val user = secureStorage.getString(SecureStorage.KEY_BTC_RPC_USER) ?: "bitcoinrpc"
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
        Retrofit.Builder()
            .baseUrl(BuildConfig.COINGECKO_API_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }
    
    // Bitcoin Operations
    
    suspend fun getBitcoinBalance(): Result<WalletBalance> {
        return try {
            val balance = bitcoinClient.getBalance()
            if (balance != null) {
                Result.success(balance)
            } else {
                Result.failure(Exception("Failed to fetch Bitcoin balance"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getBitcoinAddress(label: String = ""): Result<String> {
        return try {
            val address = bitcoinClient.getNewAddress(label)
            if (address != null) {
                Result.success(address)
            } else {
                Result.failure(Exception("Failed to generate Bitcoin address"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendBitcoin(address: String, amount: Double): Result<String> {
        return try {
            // Validate address first
            if (!bitcoinClient.validateAddress(address)) {
                return Result.failure(Exception("Invalid Bitcoin address"))
            }
            
            val txid = bitcoinClient.sendToAddress(address, amount)
            if (txid != null) {
                Result.success(txid)
            } else {
                Result.failure(Exception("Failed to send Bitcoin"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getBitcoinTransactions(count: Int = 10): Result<List<TransactionInfo>> {
        return try {
            val transactions = bitcoinClient.listTransactions(count)
            if (transactions != null) {
                Result.success(transactions)
            } else {
                Result.failure(Exception("Failed to fetch transactions"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Monero Operations
    
    suspend fun getMoneroBalance(): Result<MoneroBalance> {
        return try {
            val balance = moneroClient.getBalance()
            if (balance != null) {
                Result.success(balance)
            } else {
                Result.failure(Exception("Failed to fetch Monero balance"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMoneroAddress(): Result<String> {
        return try {
            val addressInfo = moneroClient.getAddress()
            if (addressInfo != null) {
                Result.success(addressInfo.address)
            } else {
                Result.failure(Exception("Failed to get Monero address"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendMonero(address: String, amount: Long): Result<String> {
        return try {
            // Validate address first
            if (!moneroClient.validateAddress(address)) {
                return Result.failure(Exception("Invalid Monero address"))
            }
            
            val result = moneroClient.transfer(address, amount)
            if (result != null) {
                Result.success(result.txHash)
            } else {
                Result.failure(Exception("Failed to send Monero"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMoneroTransfers(): Result<List<MoneroTransfer>> {
        return try {
            val transfers = moneroClient.getTransfers()
            if (transfers != null) {
                Result.success(transfers)
            } else {
                Result.failure(Exception("Failed to fetch transfers"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Price Data
    
    suspend fun getCryptoPrices(
        ids: String = "bitcoin,monero",
        vsCurrencies: String = "usd,eur"
    ): Result<Map<String, com.agoradesk.app.backend.api.CoinPrice>> {
        return try {
            val response = coinGeckoApi.getPrice(
                ids = ids,
                vsCurrencies = vsCurrencies,
                include24hrChange = true,
                includeMarketCap = true
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch prices: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Flow-based operations for real-time updates
    
    fun observeBitcoinBalance(): Flow<WalletBalance?> = flow {
        while (true) {
            val balance = bitcoinClient.getBalance()
            emit(balance)
            kotlinx.coroutines.delay(30000) // Update every 30 seconds
        }
    }
    
    fun observeMoneroBalance(): Flow<MoneroBalance?> = flow {
        while (true) {
            val balance = moneroClient.getBalance()
            emit(balance)
            kotlinx.coroutines.delay(30000) // Update every 30 seconds
        }
    }
    
    fun observePrices(): Flow<Map<String, com.agoradesk.app.backend.api.CoinPrice>?> = flow {
        while (true) {
            val response = coinGeckoApi.getPrice(
                ids = "bitcoin,monero",
                vsCurrencies = "usd,eur",
                include24hrChange = true
            )
            if (response.isSuccessful) {
                emit(response.body())
            }
            kotlinx.coroutines.delay(60000) // Update every minute
        }
    }
}
