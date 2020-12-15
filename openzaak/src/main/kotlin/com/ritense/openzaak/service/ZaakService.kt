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

package com.ritense.openzaak.service

import com.ritense.openzaak.service.impl.model.zaak.Eigenschap
import com.ritense.openzaak.service.impl.model.zaak.Zaak
import org.camunda.bpm.engine.delegate.DelegateExecution
import java.net.URI
import java.time.LocalDateTime
import java.util.UUID

interface ZaakService {

    fun createZaakWithLink(delegateExecution: DelegateExecution)

    fun createZaak(
        zaaktype: URI,
        startdatum: LocalDateTime,
        bronorganisatie: String,
        verantwoordelijkeOrganisatie: String
    ): Zaak

    fun getZaak(id: UUID): Zaak

    fun getZaakEigenschappen(id: UUID): Collection<Eigenschap>

    fun setZaakStatus(zaak: URI, statusType: URI, datumStatusGezet: LocalDateTime)

    fun setZaakResultaat(zaak: URI, resultaatType: URI)

    fun modifyEigenschap(zaakUrl: URI, zaakId: UUID, eigenschappen: Map<URI, String>)
}