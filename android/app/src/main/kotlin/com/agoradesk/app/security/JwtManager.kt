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

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date
import javax.crypto.SecretKey

/**
 * JWT (JSON Web Token) manager for authentication and authorization.
 *
 * Handles generation, validation, and parsing of JWT tokens using HMAC-SHA256.
 *
 * @property secretKey Secret key for signing tokens (must be at least 32 characters)
 */
class JwtManager(private val secretKey: String) {

    private val key: SecretKey = Keys.hmacShaKeyFor(secretKey.toByteArray())

    /**
     * Generates a new JWT token for a user.
     *
     * @param userId User identifier to embed in the token
     * @param expirationMs Token expiration time in milliseconds (default: 24 hours)
     * @return Signed JWT token string
     */
    fun generateToken(userId: String, expirationMs: Long = DEFAULT_EXPIRATION_MS): String {
        val now = Date()
        val expiration = Date(now.time + expirationMs)

        return Jwts.builder()
            .subject(userId)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact()
    }

    /**
     * Validates a JWT token's signature and expiration.
     *
     * @param token JWT token to validate
     * @return true if token is valid and not expired, false otherwise
     */
    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts the user ID from a JWT token.
     *
     * @param token JWT token to parse
     * @return User ID from the token's subject claim, or null if invalid
     */
    fun getUserId(token: String): String? {
        return try {
            getClaims(token).subject
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a JWT token has expired.
     *
     * @param token JWT token to check
     * @return true if token is expired, false if still valid
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            getClaims(token).expiration.before(Date())
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Parses and validates claims from a JWT token.
     *
     * @param token JWT token to parse
     * @return Claims object containing token data
     * @throws io.jsonwebtoken.JwtException if token is invalid
     */
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    private companion object {
        /** Default token expiration: 24 hours in milliseconds */
        private const val DEFAULT_EXPIRATION_MS = 86400000L
    }
}
