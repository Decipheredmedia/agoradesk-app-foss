package com.agoradesk.app.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date
import javax.crypto.SecretKey

class JwtManager(private val secretKey: String) {
    
    private val key: SecretKey = Keys.hmacShaKeyFor(secretKey.toByteArray())

    fun generateToken(userId: String, expirationMs: Long = 86400000): String {
        val now = Date()
        val expiration = Date(now.time + expirationMs)

        return Jwts.builder()
            .subject(userId)
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            false
        }
    }

    fun getUserId(token: String): String? {
        return try {
            getClaims(token).subject
        } catch (e: Exception) {
            null
        }
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun isTokenExpired(token: String): Boolean {
        return try {
            getClaims(token).expiration.before(Date())
        } catch (e: Exception) {
            true
        }
    }
}
