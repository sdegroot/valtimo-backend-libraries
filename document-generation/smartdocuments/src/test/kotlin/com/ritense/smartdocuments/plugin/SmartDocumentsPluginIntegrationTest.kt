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

package com.ritense.smartdocuments.plugin

import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.authorization.AuthorizationContext.Companion.runWithoutAuthorization
import com.ritense.document.domain.impl.request.NewDocumentRequest
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginProcessLink
import com.ritense.plugin.domain.PluginProcessLinkId
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.plugin.service.PluginService
import com.ritense.processdocument.domain.impl.request.NewDocumentAndStartProcessRequest
import com.ritense.processdocument.service.ProcessDocumentService
import com.ritense.processlink.domain.ActivityTypeWithEventName
import com.ritense.resource.domain.MetadataType
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.smartdocuments.BaseSmartDocumentsIntegrationTest
import com.ritense.smartdocuments.domain.SmartDocumentsRequest
import com.ritense.valtimo.camunda.domain.CamundaProcessDefinition
import com.ritense.valtimo.camunda.service.CamundaRepositoryService
import org.assertj.core.api.Assertions.assertThat
import org.camunda.bpm.engine.RuntimeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.HttpMethod
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Transactional
@AutoConfigureWebTestClient(timeout = "36000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmartDocumentsPluginIntegrationTest @Autowired constructor(
    private val processDocumentService: ProcessDocumentService,
    private val pluginService: PluginService,
    private val smartDocumentsPluginFactory: SmartDocumentsPluginFactory,
    private val pluginProcessLinkRepository: PluginProcessLinkRepository,
    private val camundaRepositoryService: CamundaRepositoryService,
    private val runtimeService: RuntimeService,
    private val temporaryResourceStorageService: TemporaryResourceStorageService,
): BaseSmartDocumentsIntegrationTest() {

    lateinit var smartDocumentsPlugin: SmartDocumentsPlugin
    lateinit var pluginConfiguration: PluginConfiguration
    lateinit var processDefinition: CamundaProcessDefinition

    @BeforeEach
    internal fun beforeEach() {
        startMockServer()
        pluginConfiguration = pluginService.createPluginConfiguration(
            "Smart documents plugin configuration",
            objectMapper.readTree(
                "{\"url\":\"${server.url("/")}\",\"username\":\"test-username\",\"password\":\"test-password\"}"
            ) as ObjectNode,
            "smartdocuments"
        )
        val generateDocumentActionProperties = """
            {
                "templateGroup": "test-template-group",
                "templateName": "test-template-name",
                "format": "XML",
                "templateData": [
                    {
                        "key": "achternaam",
                        "value": "doc:/lastname"
                    },
                    {
                        "key": "leeftijd",
                        "value": "pv:age"
                    },
                    {
                        "key": "nonExistingDocumentVar",
                        "value": "doc:/nonExisting"
                    },
                    {
                        "key": "nonExistingProcessVar",
                        "value": "pv:/nonExisting"
                    },
                    {
                        "key": "fixedValue",
                        "value": "My fixed value"
                    }
                ],
                "resultingDocumentProcessVariableName": "my-generated-document"
            }
        """.trimIndent()

        smartDocumentsPlugin = smartDocumentsPluginFactory.create(pluginConfiguration)
        processDefinition = runWithoutAuthorization {
            camundaRepositoryService.findLatestProcessDefinition("document-generation-plugin")!!
        }

        saveProcessLink(generateDocumentActionProperties)
    }

    @Test
    fun `should generate document`() {
        // given
        val documentContent = objectMapper.readTree("{\"lastname\": \"Klaveren\"}")
        val newDocumentRequest = NewDocumentRequest(DOCUMENT_DEFINITION_KEY, documentContent)
        val request = NewDocumentAndStartProcessRequest(PROCESS_DEFINITION_KEY, newDocumentRequest)
            .withProcessVars(mapOf("age" to 138))

        // when
        runWithoutAuthorization {
            processDocumentService.newDocumentAndStartProcess(request)
        }

        // then
        val requestBody =
            findRequestBody(HttpMethod.POST, "/wsxmldeposit/deposit/unattended", SmartDocumentsRequest::class.java)
        assertThat(requestBody.smartDocument.selection.templateGroup).isNotEqualTo("test-template-group")
        assertThat(requestBody.smartDocument.selection.template).isEqualTo("test-template-name")
        assertThat(requestBody.customerData).isEqualTo(mapOf(
            "achternaam" to "Klaveren",
            "leeftijd" to 138,
            "nonExistingDocumentVar" to null,
            "nonExistingProcessVar" to null,
            "fixedValue" to "My fixed value",
        ))
    }

    @Test
    fun `should create temp file when generating document`() {
        // given
        val newDocumentRequest = NewDocumentRequest(DOCUMENT_DEFINITION_KEY, objectMapper.createObjectNode())
        val request = NewDocumentAndStartProcessRequest(PROCESS_DEFINITION_KEY, newDocumentRequest)

        // when
        runWithoutAuthorization {
            processDocumentService.newDocumentAndStartProcess(request)
        }

        // then
        val resourceId = runtimeService.createVariableInstanceQuery()
            .variableName("my-generated-document")
            .singleResult()
            .value as String
        val metadata = temporaryResourceStorageService.getResourceMetadata(resourceId)
        val content = temporaryResourceStorageService.getResourceContentAsInputStream(resourceId).reader().readText()
        assertThat(metadata[MetadataType.FILE_NAME.key]).isEqualTo("integration-test_answer.xml")
        assertThat(content).isEqualToIgnoringWhitespace(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
               <SmartDocument Version="2.0"><Selection><TypistID>6EB1F851D3704EA3B459908655031D2B</TypistID><UserGroupID>4CDE31EF4D6C45F1AC8535283331E911</UserGroupID><AuthorID>6EB1F851D3704EA3B459908655031D2B</AuthorID><TemplateGroupID>30A0CD0E17774B2E999EE7C55F875A1E</TemplateGroupID><Template>integration-test</Template><TemplateID VersionID="BE12AB4E08AA4FEE80D1968234604BED">AF9E82AE27DA439EB1C12A4ADC145D6E</TemplateID><FixedValues/></Selection><Variables/><QuestionAnswers><Question ID="3731C9BA59124A7E8F05AFFF77DB15A5" Description="Sex"><Answer ID="6A055F5C58A141F39C2B398ABE6A31B9" Description="Mr" IsFormatted="true"/></Question><Question ID="C7643C8546984BE3B83B853812937213" Description="Last name"><![CDATA[Smith]]></Question></QuestionAnswers><DocumentProperties><Guid>AF9E82AE27DA439EB1C12A4ADC145D6E</Guid><BuiltIn><creator>Arnold Pi</creator></BuiltIn><Extended/><Custom><Author name="Author" type="lpwstr" property="true" variable="false" xml="false">development-user</Author><Typist name="Typist" type="lpwstr" property="true" variable="false" xml="false">development-user</Typist><TemplateId name="TemplateId" type="lpwstr" property="true" variable="false" xml="false">AF9E82AE27DA439EB1C12A4ADC145D6E</TemplateId><Template name="Template" type="lpwstr" property="true" variable="false" xml="false">integration-test</Template></Custom></DocumentProperties></SmartDocument>
               <customerData>
                  <sex>M</sex>
                  <lastname>Smith</lastname>
               </customerData>
            </root>
            """
        )
    }

    @Test
    fun `should resolve template-name value from process-variable`() {
        // given
        saveProcessLink(
            """
            {
                "templateGroup": "test-template-group",
                "templateName": "pv:my-template-name-variable",
                "format": "XML",
                "templateData": [],
                "resultingDocumentProcessVariableName": "my-generated-document"
            }
        """.trimIndent()
        )
        val newDocumentRequest = NewDocumentRequest(DOCUMENT_DEFINITION_KEY, objectMapper.createObjectNode())
        val request = NewDocumentAndStartProcessRequest(PROCESS_DEFINITION_KEY, newDocumentRequest)
            .withProcessVars(mapOf("my-template-name-variable" to "my-custom-template-name"))

        // when
        runWithoutAuthorization {
            processDocumentService.newDocumentAndStartProcess(request)
        }

        // then
        val requestBody =
            findRequestBody(HttpMethod.POST, "/wsxmldeposit/deposit/unattended", SmartDocumentsRequest::class.java)
        assertThat(requestBody.smartDocument.selection.template).isEqualTo("my-custom-template-name")
    }

    @Test
    fun `should throw error when template-name contains process-variable that doesn't exist`() {
        // given
        saveProcessLink(
            """
            {
                "templateGroup": "test-template-group",
                "templateName": "pv:non-existing-process-variable",
                "format": "XML",
                "templateData": [],
                "resultingDocumentProcessVariableName": "my-generated-document"
            }
        """.trimIndent()
        )
        val newDocumentRequest = NewDocumentRequest(DOCUMENT_DEFINITION_KEY, objectMapper.createObjectNode())
        val request = NewDocumentAndStartProcessRequest(PROCESS_DEFINITION_KEY, newDocumentRequest)

        // when
        val result = runWithoutAuthorization {
            processDocumentService.newDocumentAndStartProcess(request)
        }

        // then
        assertThat(result.errors()).hasSize(1)
        assertThat(result.errors()[0].asString()).startsWith("Unexpected error occurred, please contact support")
    }

    private fun saveProcessLink(generateDocumentActionProperties: String) {
        pluginProcessLinkRepository.save(
            PluginProcessLink(
                PluginProcessLinkId(UUID.fromString("aad69a1b-0325-40ff-91df-27762305dcc1")),
                processDefinition.id,
                "GenerateDocument",
                objectMapper.readTree(generateDocumentActionProperties) as ObjectNode,
                pluginConfiguration.id,
                "generate-document",
                ActivityTypeWithEventName.SERVICE_TASK_START
            )
        )
    }

    companion object {
        private const val PROCESS_DEFINITION_KEY = "document-generation-plugin"
        private const val DOCUMENT_DEFINITION_KEY = "profile"
    }

}
