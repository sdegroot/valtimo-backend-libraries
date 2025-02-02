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

package com.ritense.contactmoment.autoconfigure

import com.ritense.connector.domain.Connector
import com.ritense.connector.service.ConnectorService
import com.ritense.contactmoment.client.ContactMomentClient
import com.ritense.contactmoment.client.ContactMomentTokenGenerator
import com.ritense.contactmoment.connector.ContactMomentConnector
import com.ritense.contactmoment.connector.ContactMomentProperties
import com.ritense.contactmoment.listener.MailSendListener
import com.ritense.contactmoment.service.KlantcontactService
import com.ritense.contactmoment.web.rest.ContactMomentResource
import com.ritense.contactmoment.web.rest.MessageResource
import com.ritense.klant.service.KlantService
import com.ritense.valtimo.contract.authentication.CurrentUserService
import com.ritense.valtimo.contract.authentication.UserManagementService
import com.ritense.valtimo.contract.mail.MailSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope
import org.springframework.web.reactive.function.client.WebClient

@Deprecated("Since 12.0.0. No replacement available.")
@SpringBootConfiguration
@AutoConfiguration
class ContactMomentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContactMomentClient::class)
    fun contactMomentClient(
        webclientBuilder: WebClient.Builder,
        contactMomentTokenGenerator: ContactMomentTokenGenerator,
    ): ContactMomentClient {
        return ContactMomentClient(webclientBuilder, contactMomentTokenGenerator)
    }

    @Bean
    @ConditionalOnMissingBean(ContactMomentTokenGenerator::class)
    fun contactMomentTokenGenerator(): ContactMomentTokenGenerator {
        return ContactMomentTokenGenerator()
    }

    @Bean
    @ConditionalOnMissingBean(ContactMomentConnector::class)
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    fun contactMomentConnector(
        contactMomentProperties: ContactMomentProperties,
        contactMomentClient: ContactMomentClient,
        currentUserService: CurrentUserService,
        userManagementService: UserManagementService,
    ): Connector {
        return ContactMomentConnector(contactMomentProperties, contactMomentClient, currentUserService, userManagementService)
    }

    @Bean
    @ConditionalOnMissingBean(ContactMomentProperties::class)
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    fun contactMomentProperties(
    ): ContactMomentProperties {
        return ContactMomentProperties()
    }

    @Bean
    @ConditionalOnMissingBean(ContactMomentResource::class)
    fun contactMomentResource(connectorService: ConnectorService): ContactMomentResource {
        return com.ritense.contactmoment.web.rest.impl.ContactMomentResource(connectorService)
    }

    @Bean
    @ConditionalOnMissingBean(MailSendListener::class)
    fun mailSendListener(connectorService: ConnectorService): MailSendListener {
        return MailSendListener(connectorService)
    }

    @Bean
    @ConditionalOnMissingBean(KlantcontactService::class)
    fun klantcontactService(
        sender: MailSender,
        klantService: KlantService,
        @Value("\${valtimo.genericTemplateName:default-template}") templateName: String
    ): KlantcontactService {
        return KlantcontactService(sender, klantService, templateName)
    }

    @Bean
    @ConditionalOnMissingBean(MessageResource::class)
    fun messageResource(klantcontactService: KlantcontactService): MessageResource {
        return com.ritense.contactmoment.web.rest.impl.MessageResource(klantcontactService)
    }
}
