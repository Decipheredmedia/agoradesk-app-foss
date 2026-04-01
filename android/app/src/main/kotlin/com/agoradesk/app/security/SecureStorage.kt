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
package com.agoradesk.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for sensitive data using EncryptedSharedPreferences.
 *
 * Provides AES-256-GCM encrypted storage for tokens, credentials, and other
 * sensitive data. All data is encrypted at rest using Android Keystore.
 *
 * @property context Android application context
 */
class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Saves a string value securely.
     *
     * @param key Storage key
     * @param value String value to encrypt and store
     */
    fun saveString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    /**
     * Retrieves a securely stored string value.
     *
     * @param key Storage key
     * @param defaultValue Default value if key doesn't exist
     * @return Decrypted string value or default value
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    /**
     * Saves a boolean value securely.
     *
     * @param key Storage key
     * @param value Boolean value to encrypt and store
     */
    fun saveBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    /**
     * Retrieves a securely stored boolean value.
     *
     * @param key Storage key
     * @param defaultValue Default value if key doesn't exist
     * @return Decrypted boolean value or default value
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    /**
     * Saves a long value securely.
     *
     * @param key Storage key
     * @param value Long value to encrypt and store
     */
    fun saveLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).apply()
    }

    /**
     * Retrieves a securely stored long value.
     *
     * @param key Storage key
     * @param defaultValue Default value if key doesn't exist
     * @return Decrypted long value or default value
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    /**
     * Removes a specific key from secure storage.
     *
     * @param key Storage key to remove
     */
    fun remove(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    /**
     * Clears all data from secure storage.
     *
     * WARNING: This will delete all stored credentials and tokens.
     */
    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "secure_prefs"
        
        /** JWT access token storage key */
        const val KEY_JWT_TOKEN = "jwt_token"
        
        /** JWT refresh token storage key */
        const val KEY_REFRESH_TOKEN = "refresh_token"
        
        /** User ID storage key */
        const val KEY_USER_ID = "user_id"
        
        /** Bitcoin RPC username storage key */
        const val KEY_BTC_RPC_USER = "btc_rpc_user"
        
        /** Bitcoin RPC password storage key */
        const val KEY_BTC_RPC_PASSWORD = "btc_rpc_password"
        
        /** Monero RPC username storage key */
        const val KEY_XMR_RPC_USER = "xmr_rpc_user"
        
        /** Monero RPC password storage key */
        const val KEY_XMR_RPC_PASSWORD = "xmr_rpc_password"
    }
}
