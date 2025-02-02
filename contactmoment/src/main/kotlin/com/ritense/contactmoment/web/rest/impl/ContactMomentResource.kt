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

package com.ritense.contactmoment.web.rest.impl

import com.ritense.connector.service.ConnectorService
import com.ritense.contactmoment.connector.ContactMomentConnector
import com.ritense.contactmoment.domain.ContactMoment
import com.ritense.contactmoment.domain.Kanaal
import com.ritense.contactmoment.web.rest.ContactMomentResource
import com.ritense.contactmoment.web.rest.request.CreateContactMomentRequest
import org.springframework.http.ResponseEntity

@Deprecated("Since 12.0.0. No replacement available.")
class ContactMomentResource(
    private val connectorService: ConnectorService
) : ContactMomentResource {

    override fun getContactMomenten(): ResponseEntity<List<ContactMoment>> {
        return ResponseEntity.ok(getContactMomentConnector().getContactMomenten(1))
    }

    override fun createContactMomenten(request: CreateContactMomentRequest): ResponseEntity<ContactMoment> {
        return ResponseEntity.ok(getContactMomentConnector().createContactMoment(
            request.kanaal,
            request.tekst
        ))
    }

    override fun getKanalen(): ResponseEntity<Array<Kanaal>> {
        return ResponseEntity.ok(Kanaal.values())
    }

    private fun getContactMomentConnector(): ContactMomentConnector {
        return connectorService.loadByClassName(ContactMomentConnector::class.java)
    }
}