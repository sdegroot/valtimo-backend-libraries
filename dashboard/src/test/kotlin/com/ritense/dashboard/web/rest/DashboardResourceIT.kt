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

package com.ritense.dashboard.web.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.dashboard.BaseIntegrationTest
import com.ritense.dashboard.TestDataSourceProperties
import com.ritense.dashboard.domain.WidgetConfiguration
import com.ritense.dashboard.repository.DashboardRepository
import com.ritense.dashboard.repository.WidgetConfigurationRepository
import com.ritense.dashboard.service.DashboardService
import com.ritense.valtimo.contract.authentication.model.ValtimoUser
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

class DashboardResourceIT @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val dashboardService: DashboardService,
    private val dashboardRepository: DashboardRepository,
    private val widgetConfigurationRepository: WidgetConfigurationRepository,
    private val objectMapper: ObjectMapper
): BaseIntegrationTest() {

    lateinit var mockMvc: MockMvc

    @BeforeEach
    fun beforeEach() {
        clean()

        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build()
        val testUser = ValtimoUser()
        testUser.firstName = "John"
        testUser.lastName = "Joe"
        whenever(userManagementService.currentUser).thenReturn(testUser)
    }

    @AfterEach
    fun afterEach() {
        clean()
    }

    private fun clean() {
        widgetConfigurationRepository.deleteAll()
        dashboardRepository.deleteAll()
    }

    @Test
    fun `should get dashboards`() {
        val dashboard = dashboardService.createDashboard("Test dashboard", "Test description")
        widgetConfigurationRepository.save(
            WidgetConfiguration(
                key = "doorlooptijd",
                title = "Doorlooptijd",
                dashboard = dashboard,
                dataSourceKey = "doorlooptijd",
                dataSourceProperties = objectMapper.readTree("""{ "threshold": 50 }""") as ObjectNode,
                displayTypeProperties = objectMapper.readTree("""{ "useKpi": true }""") as ObjectNode,
                displayType = "number",
                order = 1
            )
        )

        mockMvc.perform(
            get("/api/v1/dashboard")
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].key").value("test_dashboard"))
            .andExpect(jsonPath("$[0].title").value("Test dashboard"))
            .andExpect(jsonPath("$[0].widgets", hasSize<Int>(1)))
            .andExpect(jsonPath("$[0].widgets[0].key").value("doorlooptijd"))
            .andExpect(jsonPath("$[0].widgets[0].title").value("Doorlooptijd"))
            .andExpect(jsonPath("$[0].widgets[0].displayType").value("number"))
            .andExpect(jsonPath("$[0].widgets[0].displayTypeProperties.useKpi").value(true))
    }

    @Test
    @Transactional
    fun `should get dashboards widget data`() {
        val dashboard = dashboardService.createDashboard("Widget dashboard", "Test description")
        widgetConfigurationRepository.save(
            WidgetConfiguration(
                key = "single-test",
                title = "Single",
                dashboard = dashboard,
                dataSourceKey = "number-data",
                dataSourceProperties = objectMapper.valueToTree(TestDataSourceProperties("x")),
                displayType = "x",
                order = 0,
                displayTypeProperties = objectMapper.createObjectNode()
            )
        )
        widgetConfigurationRepository.save(
            WidgetConfiguration(
                key = "multi-test",
                title = "Multi",
                dashboard = dashboard,
                dataSourceKey = "numbers-data",
                dataSourceProperties = objectMapper.valueToTree(TestDataSourceProperties("x")),
                displayType = "x",
                order = 1,
                displayTypeProperties = objectMapper.createObjectNode()
            )
        )


        mockMvc.perform(
            get("/api/v1/dashboard/{dashboard-key}/data", dashboard.key)
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].key").value("single-test"))
            .andExpect(jsonPath("$[0].data.value").value("1"))
            .andExpect(jsonPath("$[0].data.total").value("0"))
            .andExpect(jsonPath("$[1].key").value("multi-test"))
            .andExpect(jsonPath("$[1].data.values").isArray)
            .andExpect(jsonPath("$[1].data.values").isEmpty)
            .andExpect(jsonPath("$[1].data.total").value("0"))
    }
}