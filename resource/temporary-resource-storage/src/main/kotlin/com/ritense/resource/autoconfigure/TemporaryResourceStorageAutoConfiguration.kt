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

package com.ritense.resource.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.resource.security.config.TemporaryResourceStorageHttpSecurityConfigurer
import com.ritense.resource.service.TemporaryResourceStorageDeletionService
import com.ritense.resource.service.TemporaryResourceStorageService
import com.ritense.resource.web.rest.TemporaryResourceStorageResource
import com.ritense.valtimo.contract.upload.ValtimoUploadProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@AutoConfiguration
class TemporaryResourceStorageAutoConfiguration {

    @Qualifier("temporaryResourceStorageService")
    @Bean
    @ConditionalOnMissingBean(TemporaryResourceStorageService::class)
    fun temporaryResourceStorageService(
        @Value("\${valtimo.resource.temp.directory:}") valtimoResourceTempDirectory: String,
        uploadProperties: ValtimoUploadProperties,
        objectMapper: ObjectMapper,
    ): TemporaryResourceStorageService {
        return TemporaryResourceStorageService(
            valtimoResourceTempDirectory = valtimoResourceTempDirectory,
            uploadProperties = uploadProperties,
            objectMapper = objectMapper,
        )
    }

    @Bean
    @ConditionalOnMissingBean(TemporaryResourceStorageDeletionService::class)
    fun temporaryResourceStorageDeletionService(
        @Value("\${valtimo.temporaryResourceStorage.retentionInMinutes:60}") retentionInMinutes: Long,
        temporaryResourceStorageService: TemporaryResourceStorageService,
    ): TemporaryResourceStorageDeletionService {
        return TemporaryResourceStorageDeletionService(
            retentionInMinutes,
            temporaryResourceStorageService,
        )
    }

    @Bean
    @ConditionalOnMissingBean(TemporaryResourceStorageResource::class)
    fun temporaryResourceStorageResource(
        temporaryResourceStorageService: TemporaryResourceStorageService,
        applicationEventPublisher: ApplicationEventPublisher,
    ): TemporaryResourceStorageResource {
        return TemporaryResourceStorageResource(
            temporaryResourceStorageService,
            applicationEventPublisher
        )
    }

    @Order(490)
    @Bean
    @ConditionalOnMissingBean(TemporaryResourceStorageHttpSecurityConfigurer::class)
    fun temporaryResourceStorageHttpSecurityConfigurer(): TemporaryResourceStorageHttpSecurityConfigurer {
        return TemporaryResourceStorageHttpSecurityConfigurer()
    }

}
