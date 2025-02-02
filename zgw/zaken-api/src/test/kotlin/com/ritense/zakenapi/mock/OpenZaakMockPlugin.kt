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

package com.ritense.zakenapi.mock

import com.ritense.catalogiapi.CatalogiApiAuthentication
import com.ritense.plugin.annotation.Plugin
import com.ritense.zakenapi.ZakenApiAuthentication
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

@Plugin(
    key = "openzaak",
    title = "OpenZaak mock plugin",
    description = "OpenZaak mock plugin"
)
class OpenZaakMockPlugin : ZakenApiAuthentication, CatalogiApiAuthentication {
    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
        return next.exchange(ClientRequest.from(request).build())
    }
}