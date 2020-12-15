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

package com.ritense.openzaak.form

import com.ritense.openzaak.service.impl.ZaakService
import com.ritense.openzaak.service.impl.ZaakTypeLinkService
import com.ritense.valtimo.contract.form.ExternalFormFieldType
import com.ritense.valtimo.contract.form.FormFieldDataResolver
import java.util.UUID

class OpenZaakFormFieldDataResolver(
    private val zaakService: ZaakService,
    private val zaakTypeLinkService: ZaakTypeLinkService
) : FormFieldDataResolver {

    override fun supports(externalFormFieldType: ExternalFormFieldType): Boolean {
        return externalFormFieldType == ExternalFormFieldType.OZ
    }

    override fun get(documentDefinitionName: String, documentId: UUID, vararg varNames: String): Map<String, Any> {
        val zaakTypeLink = zaakTypeLinkService.findBy(documentDefinitionName)
        val zaakInstanceLink = zaakTypeLink.getZaakInstanceLink(documentId)
        val eigenschappen = zaakService.getZaakEigenschappen(zaakInstanceLink.zaakInstanceId)

        val result = mutableMapOf<String, String>()

        if (!eigenschappen.isEmpty()) {
            for (varName in varNames) {
                val waarde = eigenschappen.first { it.naam == varName }.waarde
                result[varName] = waarde
            }
        }

        return result
    }

}