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

package com.ritense.catalogiapi

import com.fasterxml.jackson.databind.JsonNode
import com.ritense.authorization.AuthorizationContext
import com.ritense.catalogiapi.client.BesluittypeRequest
import com.ritense.catalogiapi.client.CatalogiApiClient
import com.ritense.catalogiapi.client.EigenschapRequest
import com.ritense.catalogiapi.client.ResultaattypeRequest
import com.ritense.catalogiapi.client.RoltypeRequest
import com.ritense.catalogiapi.client.StatustypeRequest
import com.ritense.catalogiapi.client.ZaaktypeInformatieobjecttypeRequest
import com.ritense.catalogiapi.client.ZaaktypeRequest
import com.ritense.catalogiapi.domain.Besluittype
import com.ritense.catalogiapi.domain.Eigenschap
import com.ritense.catalogiapi.domain.Informatieobjecttype
import com.ritense.catalogiapi.domain.Resultaattype
import com.ritense.catalogiapi.domain.Roltype
import com.ritense.catalogiapi.domain.Statustype
import com.ritense.catalogiapi.domain.Zaaktype
import com.ritense.catalogiapi.domain.ZaaktypeInformatieobjecttype
import com.ritense.catalogiapi.exception.ResultaattypeNotFoundException
import com.ritense.catalogiapi.exception.StatustypeNotFoundException
import com.ritense.catalogiapi.service.ZaaktypeUrlProvider
import com.ritense.document.service.DocumentService
import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginAction
import com.ritense.plugin.annotation.PluginActionProperty
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.processlink.domain.ActivityTypeWithEventName
import com.ritense.valtimo.contract.validation.Url
import com.ritense.zgw.Page
import mu.KotlinLogging
import org.camunda.bpm.engine.delegate.DelegateExecution
import java.net.URI

@Plugin(
    key = "catalogiapi",
    title = "Catalogi API",
    description = "Connects to the Catalogi API to retrieve zaak type information"
)
class CatalogiApiPlugin(
    val client: CatalogiApiClient,
    val zaaktypeUrlProvider: ZaaktypeUrlProvider,
    val documentService: DocumentService,
) {
    @Url
    @PluginProperty(key = "url", secret = false)
    lateinit var url: URI

    @PluginProperty(key = "authenticationPluginConfiguration", secret = false)
    lateinit var authenticationPluginConfiguration: CatalogiApiAuthentication

    @PluginAction(
        key = "get-statustype",
        title = "Get Statustype",
        description = "Retrieve the statustype and save it in a process variable",
        activityTypes = [ActivityTypeWithEventName.SERVICE_TASK_START, ActivityTypeWithEventName.CALL_ACTIVITY_START]
    )
    fun getStatustype(
        execution: DelegateExecution,
        @PluginActionProperty statustype: String,
        @PluginActionProperty processVariable: String,
    ) {
        val statustypeUrl = if (statustype.matches(HTTPS_REGEX)) {
            statustype
        } else {
            val document = AuthorizationContext.runWithoutAuthorization { documentService.get(execution.businessKey) }
            val zaaktypeUrl = zaaktypeUrlProvider.getZaaktypeUrl(document.definitionId().name())
            getStatustypeByOmschrijving(zaaktypeUrl, statustype).url!!.toASCIIString()
        }

        execution.setVariable(processVariable, statustypeUrl)
    }

    @PluginAction(
        key = "get-resultaattype",
        title = "Get Resultaattype",
        description = "Retrieve the resultaattype and save it in a process variable",
        activityTypes = [ActivityTypeWithEventName.SERVICE_TASK_START, ActivityTypeWithEventName.CALL_ACTIVITY_START]
    )
    fun getResultaattype(
        execution: DelegateExecution,
        @PluginActionProperty resultaattype: String,
        @PluginActionProperty processVariable: String,
    ) {
        val resultaattypeUrl = if (resultaattype.matches(HTTPS_REGEX)) {
            resultaattype
        } else {
            val document = AuthorizationContext.runWithoutAuthorization { documentService.get(execution.businessKey) }
            val zaaktypeUrl = zaaktypeUrlProvider.getZaaktypeUrl(document.definitionId().name())
            getResultaattypeByOmschrijving(zaaktypeUrl, resultaattype).url!!.toASCIIString()
        }

        execution.setVariable(processVariable, resultaattypeUrl)
    }

    @PluginAction(
        key = "get-besluittype",
        title = "Get Besluittype",
        description = "Retrieve the besluittype and save it in a process variable",
        activityTypes = [ActivityTypeWithEventName.SERVICE_TASK_START, ActivityTypeWithEventName.CALL_ACTIVITY_START]
    )
    fun getBesluittype(
        execution: DelegateExecution,
        @PluginActionProperty besluittype: String,
        @PluginActionProperty processVariable: String,
    ) {
        val besluittypeUrl = if (besluittype.matches(HTTPS_REGEX)) {
            besluittype
        } else {
            val document = AuthorizationContext.runWithoutAuthorization { documentService.get(execution.businessKey) }
            val zaaktypeUrl = zaaktypeUrlProvider.getZaaktypeUrl(document.definitionId().name())
            getBesluittypeByOmschrijving(zaaktypeUrl, besluittype).url!!.toASCIIString()
        }

        execution.setVariable(processVariable, besluittypeUrl)
    }

    fun getInformatieobjecttypes(
        zaakTypeUrl: URI,
    ): List<Informatieobjecttype> {
        var currentPage = 1
        var currentResults: Page<ZaaktypeInformatieobjecttype>?
        val results = mutableListOf<Informatieobjecttype>()

        do {
            logger.debug { "Getting page of ZaaktypeInformatieobjecttypes, page $currentPage for zaaktype $zaakTypeUrl" }
            currentResults = client.getZaaktypeInformatieobjecttypes(
                authenticationPluginConfiguration,
                url,
                ZaaktypeInformatieobjecttypeRequest(
                    zaaktype = zaakTypeUrl,
                    page = currentPage++
                )
            )
            currentResults.results.map {
                logger.trace { "Getting Informatieobjecttype ${it.informatieobjecttype}" }
                val informatieobjecttype = client.getInformatieobjecttype(
                    authenticationPluginConfiguration,
                    url,
                    it.informatieobjecttype
                )
                results.add(informatieobjecttype)
            }
        } while (currentResults?.next != null)

        return results
    }

    fun getInformatieobjecttype(
        typeUrl: URI,
    ): Informatieobjecttype {
        return client.getInformatieobjecttype(
            authenticationPluginConfiguration,
            url,
            typeUrl
        )
    }

    fun getRoltypes(zaakTypeUrl: URI): List<Roltype> {
        var currentPage = 1
        var currentResults: Page<Roltype>?
        val results = mutableListOf<Roltype>()

        do {
            logger.debug { "Getting page of Roltypes, page $currentPage for zaaktype $zaakTypeUrl" }
            currentResults = client.getRoltypen(
                authenticationPluginConfiguration,
                url,
                RoltypeRequest(
                    zaaktype = zaakTypeUrl,
                    page = currentPage++
                )
            )
            results.addAll(currentResults.results)
        } while (currentResults?.next != null)

        return results
    }

    fun getStatustypen(zaakTypeUrl: URI): List<Statustype> {
        var currentPage = 1
        var currentResults: Page<Statustype>?
        val results = mutableListOf<Statustype>()

        do {
            logger.debug { "Getting page of statustypen, page $currentPage for zaaktype $zaakTypeUrl" }
            currentResults = client.getStatustypen(
                authenticationPluginConfiguration,
                url,
                StatustypeRequest(
                    zaaktype = zaakTypeUrl,
                    page = currentPage++
                )
            )
            results.addAll(currentResults.results)
        } while (currentResults?.next != null)

        return results
    }

    fun getStatustype(statusTypeUrl: URI): Statustype {
        return client.getStatustype(authenticationPluginConfiguration, url, statusTypeUrl)
    }

    fun getStatustypeByOmschrijving(zaakTypeUrl: URI, omschrijving: String): Statustype {
        return getStatustypen(zaakTypeUrl)
            .singleOrNull { it.omschrijving.equals(omschrijving, ignoreCase = true) }
            ?: throw StatustypeNotFoundException("With 'omschrijving': '$omschrijving'")
    }

    fun getResultaattypen(zaakTypeUrl: URI): List<Resultaattype> {
        var currentPage = 1
        var currentResults: Page<Resultaattype>?
        val results = mutableListOf<Resultaattype>()

        do {
            logger.debug { "Getting page of resultaattypen, page $currentPage for zaaktype $zaakTypeUrl" }
            currentResults = client.getResultaattypen(
                authenticationPluginConfiguration,
                url,
                ResultaattypeRequest(
                    zaaktype = zaakTypeUrl,
                    page = currentPage++
                )
            )
            results.addAll(currentResults.results)
        } while (currentResults?.next != null)

        return results
    }

    fun getResultaattype(resultaatTypeUrl: URI): Resultaattype {
        return client.getResultaattype(authenticationPluginConfiguration, url, resultaatTypeUrl)
    }

    fun getResultaattypeByOmschrijving(zaakTypeUrl: URI, omschrijving: String): Resultaattype {
        return getResultaattypen(zaakTypeUrl)
            .singleOrNull { it.omschrijving.equals(omschrijving, ignoreCase = true) }
            ?: throw ResultaattypeNotFoundException("With 'omschrijving': '$omschrijving'")
    }

    fun getBesluittypen(zaakTypeUrl: URI): List<Besluittype> {
        var currentPage = 1
        var currentResults: Page<Besluittype>?
        val results = mutableListOf<Besluittype>()

        do {
            logger.debug { "Getting page of besluittypen, page $currentPage for zaaktype $zaakTypeUrl" }
            currentResults = client.getBesluittypen(
                authenticationPluginConfiguration,
                url,
                BesluittypeRequest(
                    zaaktypen = zaakTypeUrl,
                    page = currentPage++
                )
            )
            results.addAll(currentResults.results)
        } while (currentResults?.next != null)

        return results
    }

    fun getEigenschappen(zaakTypeUrl: URI): List<Eigenschap> {
        return Page.getAll { page ->
            logger.debug { "Getting page of eigenschappen, page $page for zaaktype $zaakTypeUrl" }
            client.getEigenschappen(
                authenticationPluginConfiguration,
                url,
                EigenschapRequest(
                    zaaktype = zaakTypeUrl,
                    page = page
                )
            )
        }
    }

    fun getBesluittypeByOmschrijving(zaakTypeUrl: URI, omschrijving: String): Besluittype {
        return getBesluittypen(zaakTypeUrl)
            .singleOrNull { it.omschrijving.equals(omschrijving, ignoreCase = true) }
            ?: throw StatustypeNotFoundException("With 'omschrijving': '$omschrijving'")
    }

    fun getZaaktypen(): List<Zaaktype> {
        return Page.getAll { page ->
            logger.debug { "Getting page of zaaktypen, page $page" }
            client.getZaaktypen(
                authenticationPluginConfiguration,
                url,
                ZaaktypeRequest(page = page)
            )
        }
    }

    fun getZaaktype(zaaktypeUrl: URI): Zaaktype {
        return client.getZaaktype(authenticationPluginConfiguration, url, zaaktypeUrl)
    }

    fun prefillCache() {
        client.prefillCache(authenticationPluginConfiguration, url)
    }

    companion object {
        val logger = KotlinLogging.logger {}
        const val PLUGIN_KEY = "catalogiapi"
        const val URL_PROPERTY = "url"
        private val HTTPS_REGEX = "https?://.+".toRegex()

        fun findConfigurationByUrl(url: URI) =
            { properties: JsonNode -> url.toString().startsWith(properties.get(URL_PROPERTY).textValue()) }
    }
}
