/*
 * Copyright 2015-2024 Ritense BV, the Netherlands.
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

package com.ritense.objecttypenapi

import com.fasterxml.jackson.databind.JsonNode
import com.ritense.objecttypenapi.client.Objecttype
import com.ritense.objecttypenapi.client.ObjecttypenApiClient
import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.valtimo.contract.validation.Url
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Plugin(
   key = "objecttypenapi",
   title = "Objecttypen API",
   description = "Connects to the Objecttypen API"
)
class ObjecttypenApiPlugin(
    private val objecttypenApiClient: ObjecttypenApiClient
) {
    @Url
    @PluginProperty(key = "url", secret = false)
    lateinit var url: URI

    @PluginProperty(key = "authenticationPluginConfiguration", secret = false)
    lateinit var authenticationPluginConfiguration: ObjecttypenApiAuthentication

    fun getObjecttype(typeUrl: URI): Objecttype {
        return objecttypenApiClient.getObjecttype(authenticationPluginConfiguration, typeUrl)
    }

    fun getObjectTypeUrlById(id:String): URI {
        return UriComponentsBuilder.fromUri(url)
            .pathSegment("objecttypes")
            .pathSegment(id)
            .build()
            .toUri()
    }

    companion object {
        const val URL_PROPERTY = "url"

        fun findConfigurationByUrl(url: URI) =
            { properties: JsonNode -> url.toString().startsWith(properties.get(URL_PROPERTY).textValue()) }
    }
}