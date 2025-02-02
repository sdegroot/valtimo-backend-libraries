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

package com.ritense.portaaltaak

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath
import com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath
import com.ritense.BaseIntegrationTest
import com.ritense.authorization.AuthorizationContext.Companion.runWithoutAuthorization
import com.ritense.document.domain.impl.request.NewDocumentRequest
import com.ritense.notificatiesapi.NotificatiesApiAuthentication
import com.ritense.objectenapi.ObjectenApiAuthentication
import com.ritense.objectenapi.ObjectenApiPlugin
import com.ritense.objectenapi.client.ObjectRecord
import com.ritense.objectenapi.client.ObjectWrapper
import com.ritense.objectmanagement.domain.ObjectManagement
import com.ritense.objectmanagement.service.ObjectManagementService
import com.ritense.objecttypenapi.ObjecttypenApiAuthentication
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.domain.PluginProcessLink
import com.ritense.plugin.domain.PluginProcessLinkId
import com.ritense.plugin.repository.PluginProcessLinkRepository
import com.ritense.portaaltaak.exception.CompleteTaakProcessVariableNotFoundException
import com.ritense.processdocument.domain.impl.request.NewDocumentAndStartProcessRequest
import com.ritense.processdocument.service.ProcessDocumentService
import com.ritense.processlink.domain.ActivityTypeWithEventName
import com.ritense.valtimo.camunda.domain.CamundaTask
import com.ritense.valtimo.camunda.repository.CamundaTaskSpecificationHelper.Companion.byActive
import com.ritense.valtimo.camunda.repository.CamundaTaskSpecificationHelper.Companion.byId
import com.ritense.valtimo.camunda.repository.CamundaTaskSpecificationHelper.Companion.byProcessInstanceId
import com.ritense.valtimo.service.CamundaTaskService
import com.ritense.zakenapi.domain.ZaakInstanceLink
import com.ritense.zakenapi.domain.ZaakInstanceLinkId
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.camunda.bpm.engine.RepositoryService
import org.camunda.community.mockito.delegate.DelegateExecutionFake
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.endsWith
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Transactional
class PortaaltaakPluginIT : BaseIntegrationTest() {

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var processDocumentService: ProcessDocumentService

    @Autowired
    lateinit var pluginProcessLinkRepository: PluginProcessLinkRepository

    @Autowired
    lateinit var objectManagementService: ObjectManagementService

    @Autowired
    lateinit var procesDocumentService: ProcessDocumentService

    @Autowired
    lateinit var taskService: CamundaTaskService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    lateinit var server: MockWebServer

    lateinit var processDefinitionId: String

    lateinit var portaalTaakPluginDefinition: PluginConfiguration

    lateinit var notificatiesApiPlugin: PluginConfiguration

    lateinit var objectenPlugin: PluginConfiguration

    lateinit var objecttypenPlugin: PluginConfiguration

    lateinit var objectManagement: ObjectManagement

    protected var executedRequests: MutableList<RecordedRequest> = mutableListOf()

    @BeforeEach
    internal fun setUp() {
        server = MockWebServer()
        setupMockObjectenApiServer()
        server.start()

        // Since we do not have an actual authentication plugin in this context we will mock one
        val mockedId = PluginConfigurationId.existingId(UUID.fromString("27a399c7-9d70-4833-a651-57664e2e9e09"))
        doReturn(Optional.of(mock<PluginConfiguration>())).whenever(pluginConfigurationRepository).findById(mockedId)
        doReturn(TestAuthentication()).whenever(pluginService).createInstance(mockedId)
        doCallRealMethod().whenever(pluginService).createPluginConfiguration(any(), any(), any())

        notificatiesApiPlugin = createNotificatiesApiPlugin()
        objectenPlugin = createObjectenApiPlugin()
        objecttypenPlugin = createObjectTypenApiPlugin()
        objectManagement = createObjectManagement(objectenPlugin.id.id, objecttypenPlugin.id.id)
        portaalTaakPluginDefinition = createPortaalTaakPlugin(notificatiesApiPlugin, objectManagement)

        val zaakInstanceLink = ZaakInstanceLink(
            ZaakInstanceLinkId(UUID.randomUUID()),
            ZAAK_URL,
            UUID.randomUUID(),
            UUID.randomUUID(),
            URI.create("zaakTypeUrl"),
        )

        doReturn(zaakInstanceLink).whenever(zaakInstanceLinkService).getByDocumentId(any())
    }

    @Test
    fun `should create portaal taak`() {
        val actionPropertiesJson = """
            {
                "formType" : "${TaakFormType.ID.key}",
                "formTypeId": "some-form",
                "sendData": [
                    {
                        "key": "/lastname",
                        "value": "test"
                    }
                ],
                "receiveData": [],
                "receiver": "${TaakReceiver.OTHER.key}",
                "identificationKey": "${TaakIdentificatie.TYPE_KVK}",
                "identificationValue": "569312863",
                "verloopDurationInDays": 3
            }
        """.trimIndent()

        createProcessLink(actionPropertiesJson)

        val documentContent = """
            {
                "lastname": "test"
            }
        """.trimIndent()


        val task = startPortaalTaakProcess(documentContent)

        val recordedRequest = findRequest(HttpMethod.POST, "/objects")!!
        val body = recordedRequest.body.readUtf8()

        assertThat(body, hasJsonPath("$.type", endsWith("/objecttypes/object-type-id")))
        assertThat(body, jsonPathMissingOrNull("$.record.index"))
        assertThat(body, hasJsonPath("$.record.typeVersion", equalTo(1)))
        assertThat(body, hasJsonPath("$.record.data.identificatie.type", equalTo("kvk")))
        assertThat(body, hasJsonPath("$.record.data.identificatie.value", equalTo("569312863")))
        assertThat(body, hasJsonPath("$.record.data.data.lastname", equalTo("test")))
        assertThat(body, hasJsonPath("$.record.data.title", equalTo("user_task")))
        assertThat(body, hasJsonPath("$.record.data.status", equalTo("open")))
        assertThat(body, hasJsonPath("$.record.data.formulier.type", equalTo("id")))
        assertThat(body, hasJsonPath("$.record.data.formulier.value", equalTo("some-form")))
        assertThat(body, hasJsonPath("$.record.data.verwerker_taak_id", equalTo(task.id)))
        assertThat(body, hasJsonPath("$.record.data.zaak", equalTo(ZAAK_URL.toString())))
        assertThat(body, hasJsonPath("$.record.data.verloopdatum", startsWith(LocalDate.now().plusDays(3).toString())))
        assertThat(body, hasJsonPath("$.record.startAt", equalTo(LocalDate.now().toString())))
        assertThat(body, jsonPathMissingOrNull("$.record.endAt"))
        assertThat(body, jsonPathMissingOrNull("$.record.registrationAt"))
        assertThat(body, jsonPathMissingOrNull("$.record.correctionFor"))
        assertThat(body, jsonPathMissingOrNull("$.record.correctedBy"))
    }

    @Test
    fun `should create portaal taak without verloopdatum`() {
        val actionPropertiesJson = """
            {
                "formType" : "${TaakFormType.ID.key}",
                "formTypeId": "some-form",
                "sendData": [
                    {
                        "key": "/lastname",
                        "value": "test"
                    }
                ],
                "receiveData": [],
                "receiver": "${TaakReceiver.OTHER.key}",
                "identificationKey": "${TaakIdentificatie.TYPE_KVK}",
                "identificationValue": "569312863",
                "verloopDurationInDays": null
            }
        """.trimIndent()

        createProcessLink(actionPropertiesJson)

        val documentContent = """
            {
                "lastname": "test"
            }
        """.trimIndent()


        val task = startPortaalTaakProcess(documentContent)

        val recordedRequest = findRequest(HttpMethod.POST, "/objects")!!
        val body = recordedRequest.body.readUtf8()

        assertThat(body, hasJsonPath("$.type", endsWith("/objecttypes/object-type-id")))
        assertThat(body, jsonPathMissingOrNull("$.record.index"))
        assertThat(body, hasJsonPath("$.record.typeVersion", equalTo(1)))
        assertThat(body, hasJsonPath("$.record.data.identificatie.type", equalTo("kvk")))
        assertThat(body, hasJsonPath("$.record.data.identificatie.value", equalTo("569312863")))
        assertThat(body, hasJsonPath("$.record.data.data.lastname", equalTo("test")))
        assertThat(body, hasJsonPath("$.record.data.title", equalTo("user_task")))
        assertThat(body, hasJsonPath("$.record.data.status", equalTo("open")))
        assertThat(body, hasJsonPath("$.record.data.formulier.type", equalTo("id")))
        assertThat(body, hasJsonPath("$.record.data.formulier.value", equalTo("some-form")))
        assertThat(body, hasJsonPath("$.record.data.verwerker_taak_id", equalTo(task.id)))
        assertThat(body, hasJsonPath("$.record.data.zaak", equalTo(ZAAK_URL.toString())))
        assertThat(body, hasJsonPath("$.record.data.verloopdatum", nullValue()))
        assertThat(body, hasJsonPath("$.record.startAt", equalTo(LocalDate.now().toString())))
        assertThat(body, jsonPathMissingOrNull("$.record.endAt"))
        assertThat(body, jsonPathMissingOrNull("$.record.registrationAt"))
        assertThat(body, jsonPathMissingOrNull("$.record.correctionFor"))
        assertThat(body, jsonPathMissingOrNull("$.record.correctedBy"))
    }

    @Test
    fun `should create portaal taak with verloopdatum from BPMN user task due-date`() {
        val actionPropertiesJson = """
            {
                "formType" : "${TaakFormType.ID.key}",
                "formTypeId": "some-form",
                "sendData": [
                    {
                        "key": "/lastname",
                        "value": "test"
                    }
                ],
                "receiveData": [],
                "receiver": "${TaakReceiver.OTHER.key}",
                "identificationKey": "${TaakIdentificatie.TYPE_KVK}",
                "identificationValue": "569312863",
                "verloopDurationInDays": null
            }
        """.trimIndent()

        createProcessLink(actionPropertiesJson, "portaaltaak-due-date-process")
        val task = startPortaalTaakProcess("{}", "portaaltaak-due-date-process")

        val recordedRequest = findRequest(HttpMethod.POST, "/objects")!!
        val body = recordedRequest.body.readUtf8()

        assertThat(body, hasJsonPath("$.type", endsWith("/objecttypes/object-type-id")))
        assertThat(body, jsonPathMissingOrNull("$.record.index"))
        assertThat(body, hasJsonPath("$.record.typeVersion", equalTo(1)))
        assertThat(body, hasJsonPath("$.record.data.identificatie.type", equalTo("kvk")))
        assertThat(body, hasJsonPath("$.record.data.identificatie.value", equalTo("569312863")))
        assertThat(body, hasJsonPath("$.record.data.data.lastname", equalTo("test")))
        assertThat(body, hasJsonPath("$.record.data.title", equalTo("user_task")))
        assertThat(body, hasJsonPath("$.record.data.status", equalTo("open")))
        assertThat(body, hasJsonPath("$.record.data.formulier.type", equalTo("id")))
        assertThat(body, hasJsonPath("$.record.data.formulier.value", equalTo("some-form")))
        assertThat(body, hasJsonPath("$.record.data.verwerker_taak_id", equalTo(task.id)))
        assertThat(body, hasJsonPath("$.record.data.zaak", equalTo(ZAAK_URL.toString())))
        assertThat(body, hasJsonPath("$.record.data.verloopdatum", equalTo("2040-02-03T12:34:56.000Z")))
        assertThat(body, hasJsonPath("$.record.startAt", equalTo(LocalDate.now().toString())))
        assertThat(body, jsonPathMissingOrNull("$.record.endAt"))
        assertThat(body, jsonPathMissingOrNull("$.record.registrationAt"))
        assertThat(body, jsonPathMissingOrNull("$.record.correctionFor"))
        assertThat(body, jsonPathMissingOrNull("$.record.correctedBy"))
    }

    @Test
    fun `should complete Camunda Task`() {
        val task = startPortaalTaakProcess("""
            {
                "lastname": "test"
            }
        """.trimIndent())
        assertNotNull( runWithoutAuthorization { taskService.findTaskById(task.id) })

        val portaaltaakPlugin = spy(pluginService.createInstance(portaalTaakPluginDefinition.id) as PortaaltaakPlugin)
        val delegateExecution = DelegateExecutionFake()
        delegateExecution.setVariable("verwerkerTaakId",task.id)
        delegateExecution.setVariable("objectenApiPluginConfigurationId",objectenPlugin.id.id.toString())
        delegateExecution.setVariable("portaalTaakObjectUrl","http://some.resource/url")
        val objectenApiPlugin: ObjectenApiPlugin = mock()
        val objectWrapperCaptor = argumentCaptor<ObjectWrapper>()
        val jsonNodeCaptor = argumentCaptor<JsonNode>()
        val objectWrapper = getObjectWrapper()

        doReturn(objectenApiPlugin).whenever(pluginService).createInstance(any<PluginConfigurationId>())
        whenever(objectenApiPlugin.getObject(any())).thenReturn(objectWrapper)
        whenever(objectenApiPlugin.objectPatch(any(), any())).thenReturn(null)

        portaaltaakPlugin.completePortaalTaak(delegateExecution)

        verify(portaaltaakPlugin).changeDataInPortalTaakObject(objectWrapperCaptor.capture(),jsonNodeCaptor.capture())

        val sentTaakObject: TaakObject = objectMapper.treeToValue(jsonNodeCaptor.firstValue,TaakObject::class.java)
        assertEquals(TaakStatus.VERWERKT, sentTaakObject.status)
        assertEquals(0, taskService.countTasks(byId(task.id)))
    }

    private fun getObjectWrapper(): ObjectWrapper {
        return ObjectWrapper(
            URI.create("http://objects/"),
            UUID.randomUUID(),
            URI.create("http://objectType/aType"),
            ObjectRecord(
                typeVersion = 1,
                data = objectMapper.valueToTree(getTaakObject()),
                startAt = LocalDate.now()
            )
        )

    }

    @Test
    fun `should throw exception due to missing verwerkerTaakId`() {
        val portaaltaakPlugin = pluginService.createInstance(portaalTaakPluginDefinition.id) as PortaaltaakPlugin
        val delegateExecution = DelegateExecutionFake()
        val result =
            assertThrows<CompleteTaakProcessVariableNotFoundException>{ portaaltaakPlugin.completePortaalTaak(delegateExecution) }
        assertEquals("verwerkerTaakId is required but was not provided",result.message)
    }

    @Test
    fun `should throw exception due to missing objectenApiPluginConfigurationId`() {
        val task = startPortaalTaakProcess("""
            {
                "lastname": "test"
            }
        """.trimIndent())
        val portaaltaakPlugin = pluginService.createInstance(portaalTaakPluginDefinition.id) as PortaaltaakPlugin
        val delegateExecution = DelegateExecutionFake()
        delegateExecution.setVariable("verwerkerTaakId",task.id)
        val result =
            assertThrows<CompleteTaakProcessVariableNotFoundException>{ portaaltaakPlugin.completePortaalTaak(delegateExecution) }
        assertEquals("objectenApiPluginConfigurationId is required but was not provided",result.message)
    }

    @Test
    fun `should throw exception due to missing portaalTaakObjectUrl`() {
        val task = startPortaalTaakProcess("""
            {
                "lastname": "test"
            }
        """.trimIndent())
        val portaaltaakPlugin = pluginService.createInstance(portaalTaakPluginDefinition.id) as PortaaltaakPlugin
        val delegateExecution = DelegateExecutionFake()
        delegateExecution.setVariable("verwerkerTaakId",task.id)
        delegateExecution.setVariable("objectenApiPluginConfigurationId",objectenPlugin.id.id.toString())

        val result =
            assertThrows<CompleteTaakProcessVariableNotFoundException>{ portaaltaakPlugin.completePortaalTaak(delegateExecution) }
        assertEquals("portaalTaakObjectUrl is required but was not provided",result.message)
    }


    fun <T> jsonPathMissingOrNull(jsonPath: String): Matcher<T> {
        return anyOf(
            hasNoJsonPath(jsonPath),
            hasJsonPath(jsonPath, nullValue())
        )
    }

    private fun startPortaalTaakProcess(content: String, processDefinitionKey: String = PROCESS_DEFINITION_KEY): CamundaTask {
        return runWithoutAuthorization {
            val newDocumentRequest =
                NewDocumentRequest(DOCUMENT_DEFINITION_KEY, objectMapper.readTree(content))
            val request = NewDocumentAndStartProcessRequest(processDefinitionKey, newDocumentRequest)
            val processResult = procesDocumentService.newDocumentAndStartProcess(request)
            taskService.findTask(
                byActive().and(
                    byProcessInstanceId(
                        processResult.resultingProcessInstanceId().get().toString()
                    )
                )
            )
        }
    }

    private fun createObjectManagement(
        objectenApiPluginConfigurationId: UUID,
        objecttypenApiPluginConfigurationId: UUID
    ): ObjectManagement {
        val objectManagement = ObjectManagement(
            title = "Henk",
            objectenApiPluginConfigurationId = objectenApiPluginConfigurationId,
            objecttypenApiPluginConfigurationId = objecttypenApiPluginConfigurationId,
            objecttypeId = "object-type-id"
        )
        return objectManagementService.create(objectManagement)
    }

    private fun createNotificatiesApiPlugin(): PluginConfiguration {
        val pluginPropertiesJson = """
            {
              "url": "${server.url("/")}",
              "callbackUrl": "http://host.docker.internal:8080/api/v1/notificatiesapi/callback",
              "authenticationPluginConfiguration": "27a399c7-9d70-4833-a651-57664e2e9e09"
            }
        """.trimIndent()

        val configuration = pluginService.createPluginConfiguration(
            "Notificaties API plugin configuration",
            objectMapper.readTree(
                pluginPropertiesJson
            ) as ObjectNode,
            "notificatiesapi"
        )
        return configuration
    }


    private fun createPortaalTaakPlugin(
        notificatiesApiPlugin: PluginConfiguration,
        objectManagement: ObjectManagement
    ): PluginConfiguration {
        val pluginPropertiesJson = """
            {
              "notificatiesApiPluginConfiguration": "${notificatiesApiPlugin.id.id}",
              "objectManagementConfigurationId": "${objectManagement.id}",
              "completeTaakProcess": "process-completed-portaaltaak"
            }
        """.trimIndent()

        val configuration = pluginService.createPluginConfiguration(
            "Portaaltaak plugin configuration",
            objectMapper.readTree(
                pluginPropertiesJson
            ) as ObjectNode,
            "portaaltaak"
        )
        return configuration
    }

    private fun createObjectTypenApiPlugin(): PluginConfiguration {
        val pluginPropertiesJson = """
            {
              "url": "${server.url("/")}",
              "authenticationPluginConfiguration": "27a399c7-9d70-4833-a651-57664e2e9e09"
            }
        """.trimIndent()

        val configuration = pluginService.createPluginConfiguration(
            "Objecten plugin configuration",
            objectMapper.readTree(
                pluginPropertiesJson
            ) as ObjectNode,
            "objecttypenapi"
        )
        return configuration
    }

    private fun createObjectenApiPlugin(): PluginConfiguration {
        val pluginPropertiesJson = """
            {
              "url": "${server.url("/")}",
              "authenticationPluginConfiguration": "27a399c7-9d70-4833-a651-57664e2e9e09"
            }
        """.trimIndent()

        val configuration = pluginService.createPluginConfiguration(
            "Objecttype plugin configuration",
            objectMapper.readTree(
                pluginPropertiesJson
            ) as ObjectNode,
            "objectenapi"
        )
        return configuration
    }

    private fun createProcessLink(propertiesConfig: String, processDefinitionKey: String = PROCESS_DEFINITION_KEY) {
        processDefinitionId = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(processDefinitionKey)
            .latestVersion()
            .singleResult()
            .id

        pluginProcessLinkRepository.save(
            PluginProcessLink(
                PluginProcessLinkId(UUID.randomUUID()),
                processDefinitionId,
                "user_task",
                objectMapper.readTree(propertiesConfig) as ObjectNode,
                portaalTaakPluginDefinition.id,
                "create-portaaltaak",
                activityType = ActivityTypeWithEventName.USER_TASK_CREATE
            )
        )
    }


    private fun setupMockObjectenApiServer() {
        val dispatcher: Dispatcher = object : Dispatcher() {
            @Throws(InterruptedException::class)
            override fun dispatch(request: RecordedRequest): MockResponse {
                executedRequests.add(request)
                val path = request.path?.substringBefore('?')
                val response = when (path) {
                    "/kanaal" -> getKanaalResponse()
                    "/abonnement" -> createAbonnementResponse()
                    "/objects" -> createObjectResponse()
                    else -> MockResponse().setResponseCode(404)
                }
                return response
            }
        }

        server.dispatcher = dispatcher
    }

    private fun getKanaalResponse(): MockResponse {
        val body = """
            [
                {
                  "naam": "objecten"
                }
            ]
        """.trimIndent()
        return mockJsonResponse(body)
    }

    private fun createAbonnementResponse(): MockResponse {
        val body = """
            {
              "url": "http://localhost",
              "auth": "test123",
              "callbackUrl": "http://localhost"
            }
        """.trimIndent()
        return mockJsonResponse(body)
    }

    private fun createObjectResponse(): MockResponse {
        val body = """
            {
              "url": "http://example.com",
              "uuid": "095be615-a8ad-4c33-8e9c-c7612fbf6c9f",
              "type": "http://example.com",
              "record": {
                "index": 0,
                "typeVersion": 32767,
                "data": {
                  "property1": null,
                  "property2": null
                },
                "geometry": {
                  "type": "string",
                  "coordinates": [
                    0,
                    0
                  ]
                },
                "startAt": "2019-08-24",
                "endAt": "2019-08-24",
                "registrationAt": "2019-08-24",
                "correctionFor": "string",
                "correctedBy": "string"
              }
            }
        """.trimIndent()
        return mockJsonResponse(body)
    }

    private fun mockJsonResponse(body: String): MockResponse {
        return MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(body)
    }

    fun findRequest(method: HttpMethod, path: String): RecordedRequest? {
        return executedRequests
            .filter { method.matches(it.method!!) }
            .firstOrNull { it.path?.substringBefore('?').equals(path) }
    }

    private fun getTaakObject(): TaakObject {
        return TaakObject(
            identificatie = TaakIdentificatie("aType", "aValue"),
            data = emptyMap(),
            title = "aTitle",
            status = TaakStatus.INGEDIEND,
            formulier = TaakForm(TaakFormType.ID, "anId"),
            verwerkerTaakId = UUID.randomUUID().toString(),
            URI.create("aZaakInstanceUrl"),
            LocalDateTime.now(),
            verzondenData = mapOf(
                "documenten" to listOf(
                    URI.create("/name"), URI.create("/phone"),
                    "name" to "Luis",
                    "phone" to "999999999"
                )

            )
        )

    }

    class TestAuthentication : ObjectenApiAuthentication, ObjecttypenApiAuthentication, NotificatiesApiAuthentication {
        override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
            return next.exchange(request)
        }
    }

    companion object {
        private const val PROCESS_DEFINITION_KEY = "portaaltaak-process"
        private const val DOCUMENT_DEFINITION_KEY = "profile"
        private val ZAAK_URL = URI("http://zaak.url")
    }

}
