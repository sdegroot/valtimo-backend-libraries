/*
 * Copyright 2020 Dimpact.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.openzaak.service.impl

import com.ritense.openzaak.service.TokenGeneratorService
import com.ritense.valtimo.contract.utils.SecurityUtils
import io.jsonwebtoken.JwtBuilder
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.nio.charset.Charset
import java.util.Date
import mu.KLogger
import mu.KotlinLogging

class OpenZaakTokenGeneratorService : TokenGeneratorService {


    override fun generateToken(secretKey: String, clientId: String): String {
        if (secretKey.length < 32) {
            throw IllegalStateException("SecretKey needs to be at least 32 in length")
        }
        val signingKey = Keys.hmacShaKeyFor(secretKey.toByteArray(Charset.forName("UTF-8")))

        val jwtBuilder = Jwts.builder()
        jwtBuilder
            .setIssuer(clientId)
            .setIssuedAt(Date())
            .claim("client_id", clientId)

        appendUserInfo(jwtBuilder)
        copyClaimsFromAuthentication(jwtBuilder, "realm_access", "resource_access")
        return jwtBuilder
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact()
    }

    private fun appendUserInfo(jwtBuilder: JwtBuilder) {
        val userLogin = SecurityUtils.getCurrentUserLogin()
        val userId = userLogin ?: "Valtimo"

        jwtBuilder
            .claim("user_id", userId)
            .claim("user_representation", "")
    }

    private fun copyClaimsFromAuthentication(jwtBuilder: JwtBuilder, vararg claims: String) {
        val authentication = SecurityUtils.getCurrentUserAuthentication()

        val jwtToken: String = authentication?.credentials.toString()
        val unsignedToken = jwtToken.split("\\.", limit = 2).joinToString(".")

        val jwtParser = Jwts.parserBuilder().build()
        try {
            val jwtClaims = jwtParser.parseClaimsJwt(unsignedToken).body

            jwtBuilder.addClaims(jwtClaims.filterKeys {
                claims.contains(it)
            })
        } catch (ex: MalformedJwtException) {
            logger.error { ex }
        }
    }

    companion object {
        val logger: KLogger = KotlinLogging.logger {}
    }
}