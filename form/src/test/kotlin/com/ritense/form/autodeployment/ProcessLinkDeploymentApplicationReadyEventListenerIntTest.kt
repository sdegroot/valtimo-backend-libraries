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

package com.ritense.form.autodeployment

import com.ritense.authorization.AuthorizationContext
import com.ritense.form.BaseIntegrationTest
import com.ritense.form.domain.FormProcessLink
import com.ritense.processlink.autodeployment.ProcessLinkDeploymentApplicationReadyEventListener
import com.ritense.processlink.repository.ProcessLinkRepository
import com.ritense.valtimo.camunda.domain.CamundaProcessDefinition
import com.ritense.valtimo.camunda.service.CamundaRepositoryService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.isA
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional


@Transactional
class ProcessLinkDeploymentApplicationReadyEventListenerIntTest @Autowired constructor(
    private val repositoryService: CamundaRepositoryService,
    private val processLinkRepository: ProcessLinkRepository,
    private val listener: ProcessLinkDeploymentApplicationReadyEventListener
): BaseIntegrationTest() {

    @Test
    fun `should find 1 deployed process link on user task`() {
        listener.deployProcessLinks()

        val processDefinition = getLatestProcessDefinition()
        val processLinks =
            processLinkRepository.findByProcessDefinitionIdAndActivityId(processDefinition.id, "do-something")

        assertThat(processLinks, hasSize(1))
        val processLink = processLinks.first()
        assertThat(processLink, isA(FormProcessLink::class.java))
        processLink as FormProcessLink
        assertThat(processLink.formDefinitionId, notNullValue())
    }

    private fun getLatestProcessDefinition(): CamundaProcessDefinition {
        return AuthorizationContext.runWithoutAuthorization {
            repositoryService.findLatestProcessDefinition("form-one-task-process")!!
        }
    }
}