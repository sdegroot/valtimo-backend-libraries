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

package com.ritense.formflow.domain

import com.ritense.formflow.BaseIntegrationTest
import com.ritense.formflow.domain.instance.FormFlowInstance
import com.ritense.formflow.repository.FormFlowDefinitionRepository
import com.ritense.formflow.repository.FormFlowInstanceRepository
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@Transactional
internal class FormFlowInstanceIT : BaseIntegrationTest() {

    @Autowired
    lateinit var formFlowInstanceRepository: FormFlowInstanceRepository

    @Autowired
    lateinit var formFlowDefinitionRepository: FormFlowDefinitionRepository

    @Test
    fun `create form flow instance successfully`() {
        val formFlowDefinition =
            formFlowDefinitionRepository.findFirstByIdKeyOrderByIdVersionDesc("inkomens_loket")

        val formFlowInstance = FormFlowInstance(
            formFlowDefinition = formFlowDefinition!!
        )
        formFlowInstanceRepository.saveAndFlush(formFlowInstance)

        val storedInstance = formFlowInstanceRepository.findById(formFlowInstance.id).get()

        assertTrue(storedInstance == formFlowInstance)
    }

    @Test
    fun `update form flow instance successfully`() {
        val formFlowDefinition =
            formFlowDefinitionRepository.findFirstByIdKeyOrderByIdVersionDesc("inkomens_loket")

        val formFlowInstance = FormFlowInstance(
            formFlowDefinition = formFlowDefinition!!
        )
        formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        formFlowInstance.complete(formFlowInstance.currentFormFlowStepInstanceId!!, JSONObject("""{"woonplaats":{"inUtrecht":true}}"""))
        formFlowInstanceRepository.saveAndFlush(formFlowInstance)

        val storedInstance = formFlowInstanceRepository.findById(formFlowInstance.id).get()
        assertEquals(2, storedInstance.getHistory().size)
        val firstStep = storedInstance.getHistory()[0]
        assertEquals(firstStep.submissionData, """{"woonplaats":{"inUtrecht":true}}""")
        val secondStep = storedInstance.getHistory()[1]
        assertNull(secondStep.submissionData)
    }

    @Test
    fun `complete goes through the entire flow`() {
        val formFlowDefinition =
            formFlowDefinitionRepository.findFirstByIdKeyOrderByIdVersionDesc("inkomens_loket")
        val submissionData = """
            {
                "inkomen": {
                    "value": 1500
                },
                "leeftijd": {
                    "isJongerDanAOW":true,
                    "isOuderDan21":true
                },
                "gezinssituatie": {
                    "vermogenGrens": 2000
                },
                "woonplaats": {
                    "inUtrecht":true
                }
            }"""
        val formFlowInstance = FormFlowInstance(
            formFlowDefinition = formFlowDefinition!!
        )
        formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        while (formFlowInstance.currentFormFlowStepInstanceId != null) {
            formFlowInstance.getCurrentStep().open()
            formFlowInstance.complete(formFlowInstance.currentFormFlowStepInstanceId!!, JSONObject(submissionData))
            formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        }

        val storedInstance = formFlowInstanceRepository.findById(formFlowInstance.id).get()
        assertEquals(7, storedInstance.getHistory().size)
        storedInstance.getHistory().forEach{
            assertEquals(it.submissionData, submissionData.replace("[ \\n]".toRegex(), ""))
        }
    }

    @Test
    fun `complete goes through the entire flow, back and then through again`() {
        val formFlowDefinition =
            formFlowDefinitionRepository.findFirstByIdKeyOrderByIdVersionDesc("inkomens_loket")
        val submissionData = """
            {
                "inkomen": {
                    "value": 1500
                },
                "leeftijd": {
                    "isJongerDanAOW":true,
                    "isOuderDan21":true
                },
                "gezinssituatie": {
                    "vermogenGrens": 2000
                },
                "woonplaats": {
                    "inUtrecht":true
                }
            }"""
        val formFlowInstance = FormFlowInstance(
            formFlowDefinition = formFlowDefinition!!
        )

        while (formFlowInstance.getCurrentStep().stepKey != "end") {
            formFlowInstance.getCurrentStep().open()
            formFlowInstance.complete(formFlowInstance.currentFormFlowStepInstanceId!!, JSONObject(submissionData))
            formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        }

        while (formFlowInstance.getCurrentStep().stepKey != "woonplaats") {
            formFlowInstance.back()
            formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        }

        val storedInstance = formFlowInstanceRepository.findById(formFlowInstance.id).get()
        assertEquals(7, storedInstance.getHistory().size)

        formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        while (formFlowInstance.currentFormFlowStepInstanceId != null) {
            formFlowInstance.getCurrentStep().open()
            formFlowInstance.complete(formFlowInstance.currentFormFlowStepInstanceId!!, JSONObject(submissionData))
            formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        }

        val storedInstance2 = formFlowInstanceRepository.findById(formFlowInstance.id).get()
        assertEquals(7, storedInstance2.getHistory().size)
        storedInstance2.getHistory().forEach{
            assertEquals(it.submissionData, submissionData.replace("[ \\n]".toRegex(), ""))
        }
    }

    @Test
    fun `navigate to next step removes previous steps`() {
        val formFlowDefinition =
            formFlowDefinitionRepository.findFirstByIdKeyOrderByIdVersionDesc("inkomens_loket")
        val submissionData = """
            {
                "inkomen": {
                    "value": 1500
                },
                "leeftijd": {
                    "isJongerDanAOW":true,
                    "isOuderDan21":true
                },
                "gezinssituatie": {
                    "vermogenGrens": 2000
                },
                "woonplaats": {
                    "inUtrecht":true
                }
            }"""
        val formFlowInstance = FormFlowInstance(
            formFlowDefinition = formFlowDefinition!!
        )

        val submissionData2 = """
            {
                "inkomen": {
                    "value": 1500
                },
                "leeftijd": {
                    "isJongerDanAOW":true,
                    "isOuderDan21":true
                },
                "gezinssituatie": {
                    "vermogenGrens": 2000
                },
                "woonplaats": {
                    "inUtrecht":false
                }
            }"""

        formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        while (formFlowInstance.getCurrentStep().stepKey != "end") {
            formFlowInstance.getCurrentStep().open()
            formFlowInstance.complete(formFlowInstance.currentFormFlowStepInstanceId!!, JSONObject(submissionData))
            formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        }

        val storedInstance = formFlowInstanceRepository.findById(formFlowInstance.id).get()
        assertEquals(7, storedInstance.getHistory().size)

        while (formFlowInstance.getCurrentStep().stepKey != "woonplaats") {
            formFlowInstance.back()
            formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        }

        while (formFlowInstance.currentFormFlowStepInstanceId != null) {
            formFlowInstance.getCurrentStep().open()
            formFlowInstance.complete(formFlowInstance.currentFormFlowStepInstanceId!!, JSONObject(submissionData2))
            formFlowInstanceRepository.saveAndFlush(formFlowInstance)
        }

        val storedInstance2 = formFlowInstanceRepository.findById(formFlowInstance.id).get()
        assertEquals(2, storedInstance2.getHistory().size)
        storedInstance2.getHistory().forEach{
            assertEquals(it.submissionData, submissionData2.replace("[ \\n]".toRegex(), ""))
        }
    }
}
