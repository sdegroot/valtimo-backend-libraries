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

package com.ritense.documentenapi.service

import com.ritense.documentenapi.domain.ZgwDocumentTrefwoord
import com.ritense.documentenapi.repository.ZgwDocumentTrefwoordRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional

@Transactional
class ZgwDocumentTrefwoordService(
    private val zgwDocumentTrefwoordRepository: ZgwDocumentTrefwoordRepository
) {
    fun getTrefwoorden(caseDefinitionName: String): List<ZgwDocumentTrefwoord> {
        return zgwDocumentTrefwoordRepository.findAllByCaseDefinitionName(caseDefinitionName)
    }

    fun getTrefwoorden(caseDefinitionName: String, pageable: Pageable): Page<ZgwDocumentTrefwoord> {
        return zgwDocumentTrefwoordRepository.findAllByCaseDefinitionName(caseDefinitionName, pageable)
    }

    fun getTrefwoorden(caseDefinitionName: String, search: String?, pageable: Pageable): Page<ZgwDocumentTrefwoord> {
        return if (!search.isNullOrBlank()) {
            zgwDocumentTrefwoordRepository.findAllByCaseDefinitionNameAndValueContaining(caseDefinitionName, search, pageable)
        } else {
            zgwDocumentTrefwoordRepository.findAllByCaseDefinitionName(caseDefinitionName, pageable)
        }
    }

    fun createTrefwoord(caseDefinitionName: String, trefwoord: String) {
        val existingTrefwoord = zgwDocumentTrefwoordRepository.findAllByCaseDefinitionNameAndValue(caseDefinitionName, trefwoord)
        require(existingTrefwoord == null) {
            "Trefwoord $trefwoord already exists for case definition $caseDefinitionName"
        }
        zgwDocumentTrefwoordRepository.save(ZgwDocumentTrefwoord(caseDefinitionName, trefwoord))
    }

    fun deleteTrefwoord(caseDefinitionName: String, trefwoord: String) {
        return zgwDocumentTrefwoordRepository.deleteByCaseDefinitionNameAndValue(caseDefinitionName, trefwoord)
    }

    fun deleteTrefwoorden(caseDefinitionName: String, trefwoorden: List<String>) {
        return zgwDocumentTrefwoordRepository.deleteByCaseDefinitionNameAndValueIn(caseDefinitionName, trefwoorden)
    }
}
