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

package com.ritense.objectenapi.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.objectenapi.ObjectenApiAuthentication
import com.ritense.objectenapi.event.ObjectCreated
import com.ritense.objectenapi.event.ObjectDeleted
import com.ritense.objectenapi.event.ObjectPatched
import com.ritense.objectenapi.event.ObjectUpdated
import com.ritense.objectenapi.event.ObjectViewed
import com.ritense.objectenapi.event.ObjectsListed
import com.ritense.outbox.OutboxService
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

class ObjectenApiClient(
    private val webclientBuilder: WebClient.Builder,
    private val outboxService: OutboxService,
    private val objectMapper: ObjectMapper

) {

    fun getObject(
        authentication: ObjectenApiAuthentication,
        objectUrl: URI
    ): ObjectWrapper {
        val result = webclientBuilder
            .clone()
            .filter(authentication)
            .build()
            .get()
            .uri(objectUrl)
            .retrieve()
            .toEntity(ObjectWrapper::class.java)
            .block()

        val responseBody = result?.body!!

        val response = if (responseBody.type.host == HOST_DOCKER_INTERNAL)
            responseBody.copy(
                type = URI.create(
                    responseBody.type.toString().replace(HOST_DOCKER_INTERNAL, "localhost")
                )
            ) else responseBody

        if (result.hasBody()) {
            outboxService.send {
                ObjectViewed(
                    response.url.toString(),
                    objectMapper.valueToTree(response)
                )
            }
        }

        return response
    }

    fun getObjectsByObjecttypeUrl(
        authentication: ObjectenApiAuthentication,
        objecttypesApiUrl: URI,
        objectsApiUrl: URI,
        objectypeId: String,
        pageable: Pageable
    ): ObjectsList {
        val host = if (objecttypesApiUrl.host == "localhost") {
            HOST_DOCKER_INTERNAL
        } else {
            objecttypesApiUrl.host
        }
        val objectTypeUrl = UriComponentsBuilder.newInstance()
            .uri(objecttypesApiUrl)
            .host(host)
            .pathSegment("objecttypes")
            .pathSegment(objectypeId)
            .toUriString()

        val result = webclientBuilder
            .clone()
            .filter(authentication)
            .baseUrl(objectsApiUrl.toASCIIString())
            .build()
            .get()
            .uri { builder ->
                builder.path("objects")
                    .queryParam("type", objectTypeUrl)
                    .queryParam("pageSize", pageable.pageSize)
                    .queryParam("page", pageable.pageNumber + 1) //objects api pagination starts at 1 instead of 0
                    .build()
            }
            .header(ACCEPT_CRS, EPSG_4326)
            .retrieve()
            .toEntity(ObjectsList::class.java)
            .block()

        if (result.hasBody()) {
            outboxService.send {
                ObjectsListed(
                    objectMapper.valueToTree(result.body.results)
                )
            }
        }

        return result?.body!!
    }

    fun getObjectsByObjecttypeUrlWithSearchParams(
        authentication: ObjectenApiAuthentication,
        objecttypesApiUrl: URI,
        objectsApiUrl: URI,
        objectypeId: String,
        searchString: String,
        pageable: Pageable
    ): ObjectsList {
        val host = if (objecttypesApiUrl.host == "localhost") {
            HOST_DOCKER_INTERNAL
        } else {
            objecttypesApiUrl.host
        }
        val objectTypeUrl = UriComponentsBuilder.newInstance()
            .uri(objecttypesApiUrl)
            .host(host)
            .pathSegment("objecttypes")
            .pathSegment(objectypeId)
            .toUriString()

        val result = webclientBuilder
            .clone()
            .filter(authentication)
            .baseUrl(objectsApiUrl.toASCIIString())
            .build()
            .get()
            .uri { builder ->
                builder.path("objects")
                    .queryParam("type", objectTypeUrl)
                    .queryParam("pageSize", pageable.pageSize)
                    .queryParam("page", pageable.pageNumber + 1) //objects api pagination starts at 1 instead of 0
                    .queryParam("data_attrs", searchString)
                    .build()
            }
            .header(ACCEPT_CRS, EPSG_4326)
            .retrieve()
            .toEntity(ObjectsList::class.java)
            .block()

        if (result.hasBody()) {
            outboxService.send {
                ObjectsListed(
                    objectMapper.valueToTree(result.body.results)
                )
            }
        }

        return result?.body!!
    }

    fun createObject(
        authentication: ObjectenApiAuthentication,
        objectsApiUrl: URI,
        objectRequest: ObjectRequest
    ): ObjectWrapper {
        val objectRequestCorrectedHost = if (objectRequest.type.host == "localhost") {
            objectRequest.copy(
                type = UriComponentsBuilder
                    .fromUri(objectRequest.type)
                    .host(HOST_DOCKER_INTERNAL)
                    .build()
                    .toUri()
            )
        } else {
            objectRequest
        }

        val result = webclientBuilder
            .clone()
            .filter(authentication)
            .baseUrl(objectsApiUrl.toASCIIString())
            .build()
            .post()
            .uri("objects")
            .header(ACCEPT_CRS, EPSG_4326)
            .header(CONTENT_CRS, EPSG_4326)
            .bodyValue(objectRequestCorrectedHost)
            .retrieve()
            .toEntity(ObjectWrapper::class.java)
            .block()

        if (result.hasBody()) {
            outboxService.send {
                ObjectCreated(
                    result.body.url.toString(),
                    objectMapper.valueToTree(result.body)
                )
            }
        }

        return result?.body!!
    }

    fun objectPatch(
        authentication: ObjectenApiAuthentication,
        objectUrl: URI,
        objectRequest: ObjectRequest
    ): ObjectWrapper {
        val objectRequestCorrectedHost = if (objectRequest.type.host == "localhost") {
            objectRequest.copy(
                type = UriComponentsBuilder
                    .fromUri(objectRequest.type)
                    .host(HOST_DOCKER_INTERNAL)
                    .build()
                    .toUri()
            )
        } else {
            objectRequest
        }
        val result = webclientBuilder
            .clone()
            .filter(authentication)
            .build()
            .patch()
            .uri(objectUrl)
            .header(CONTENT_CRS, EPSG_4326)
            .bodyValue(objectRequestCorrectedHost)
            .retrieve()
            .toEntity(ObjectWrapper::class.java)
            .block()


        if (result.hasBody()) {
            outboxService.send {
                ObjectPatched(
                    result.body.url.toString(),
                    objectMapper.valueToTree(result.body)
                )
            }
        }

        return result?.body!!
    }

    fun objectUpdate(
        authentication: ObjectenApiAuthentication,
        objectUrl: URI,
        objectRequest: ObjectRequest
    ): ObjectWrapper {
        val objectRequestCorrectedHost = if (objectRequest.type.host == "localhost") {
            objectRequest.copy(
                type = UriComponentsBuilder
                    .fromUri(objectRequest.type)
                    .host(HOST_DOCKER_INTERNAL)
                    .build()
                    .toUri()
            )
        } else {
            objectRequest
        }
        val result = webclientBuilder
            .clone()
            .filter(authentication)
            .build()
            .put()
            .uri(objectUrl)
            .header(CONTENT_CRS, EPSG_4326)
            .bodyValue(objectRequestCorrectedHost)
            .retrieve()
            .toEntity(ObjectWrapper::class.java)
            .block()

        if (result.hasBody()) {
            outboxService.send {
                ObjectUpdated(
                    result.body.url.toString(),
                    objectMapper.valueToTree(result.body)
                )
            }
        }

        return result?.body!!
    }

    fun deleteObject(authentication: ObjectenApiAuthentication, objectUrl: URI): HttpStatus {
        val result = webclientBuilder
            .clone()
            .filter(authentication)
            .build()
            .delete()
            .uri(objectUrl)
            .header(CONTENT_CRS, EPSG_4326)
            .retrieve()
            .toBodilessEntity()
            .block()

        if (result?.statusCode?.is2xxSuccessful == true) {
            outboxService.send {
                ObjectDeleted(
                    objectUrl.toString()
                )
            }
        }

        return HttpStatus.valueOf(result?.statusCode!!.value())
    }

    companion object {
        private const val HOST_DOCKER_INTERNAL = "host.docker.internal"
        private const val CONTENT_CRS = "Content-Crs"
        private const val ACCEPT_CRS = "Accept-Crs"
        private const val EPSG_4326 = "EPSG:4326"
    }
}
