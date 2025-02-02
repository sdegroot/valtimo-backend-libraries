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

package com.ritense.document.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritense.authorization.AuthorizationService;
import com.ritense.document.config.DocumentSpringContextHelper;
import com.ritense.document.domain.impl.JsonSchemaDocumentDefinition;
import com.ritense.document.domain.impl.listener.ApplicationReadyEventListenerImpl;
import com.ritense.document.domain.impl.listener.DocumentRelatedFileSubmittedEventListenerImpl;
import com.ritense.document.domain.impl.listener.RelatedJsonSchemaDocumentAvailableEventListenerImpl;
import com.ritense.document.domain.impl.sequence.JsonSchemaDocumentDefinitionSequenceRecord;
import com.ritense.document.exporter.JsonSchemaDocumentDefinitionExporter;
import com.ritense.document.importer.JsonSchemaDocumentDefinitionImporter;
import com.ritense.document.repository.DocumentDefinitionRepository;
import com.ritense.document.repository.DocumentDefinitionSequenceRepository;
import com.ritense.document.repository.impl.JsonSchemaDocumentRepository;
import com.ritense.document.service.DocumentDefinitionService;
import com.ritense.document.service.DocumentSearchService;
import com.ritense.document.service.DocumentSequenceGeneratorService;
import com.ritense.document.service.DocumentService;
import com.ritense.document.service.DocumentStatisticService;
import com.ritense.document.service.InternalCaseStatusService;
import com.ritense.document.service.SearchFieldService;
import com.ritense.document.service.UndeployDocumentDefinitionService;
import com.ritense.document.service.impl.JsonSchemaDocumentDefinitionSequenceGeneratorService;
import com.ritense.document.service.impl.JsonSchemaDocumentDefinitionService;
import com.ritense.document.service.impl.JsonSchemaDocumentSearchService;
import com.ritense.document.service.impl.JsonSchemaDocumentService;
import com.ritense.document.service.impl.UndeployJsonSchemaDocumentDefinitionService;
import com.ritense.document.web.rest.DocumentDefinitionResource;
import com.ritense.document.web.rest.DocumentResource;
import com.ritense.document.web.rest.DocumentSearchResource;
import com.ritense.document.web.rest.error.DocumentModuleExceptionTranslator;
import com.ritense.document.web.rest.impl.JsonSchemaDocumentDefinitionResource;
import com.ritense.document.web.rest.impl.JsonSchemaDocumentResource;
import com.ritense.document.web.rest.impl.JsonSchemaDocumentSearchResource;
import com.ritense.outbox.OutboxService;
import com.ritense.resource.service.ResourceService;
import com.ritense.valtimo.contract.authentication.UserManagementService;
import com.ritense.valtimo.contract.database.QueryDialectHelper;
import com.ritense.valtimo.contract.hardening.service.HardeningService;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EnableJpaRepositories(basePackages = "com.ritense.document.repository")
@EntityScan("com.ritense.document.domain")
public class DocumentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DocumentService.class)
    public JsonSchemaDocumentService documentService(
        final JsonSchemaDocumentRepository documentRepository,
        final JsonSchemaDocumentDefinitionService documentDefinitionService,
        final JsonSchemaDocumentDefinitionSequenceGeneratorService documentSequenceGeneratorService,
        @Nullable final ResourceService resourceService,
        final UserManagementService userManagementService,
        final AuthorizationService authorizationService,
        final ApplicationEventPublisher applicationEventPublisher,
        final OutboxService outboxService,
        final ObjectMapper objectMapper,
        final InternalCaseStatusService internalCaseStatusService
    ) {
        return new JsonSchemaDocumentService(
            documentRepository,
            documentDefinitionService,
            documentSequenceGeneratorService,
            resourceService,
            userManagementService,
            authorizationService,
            applicationEventPublisher,
            outboxService,
            objectMapper,
            internalCaseStatusService
        );
    }

    @Bean
    @ConditionalOnMissingBean(DocumentDefinitionService.class)
    public JsonSchemaDocumentDefinitionService documentDefinitionService(
        final ResourceLoader resourceLoader,
        final DocumentDefinitionRepository<JsonSchemaDocumentDefinition> documentDefinitionRepository,
        final AuthorizationService authorizationService
    ) {
        return new JsonSchemaDocumentDefinitionService(
            resourceLoader,
            documentDefinitionRepository,
            authorizationService
        );
    }

    @Bean
    @ConditionalOnMissingBean(JsonSchemaDocumentDefinitionExporter.class)
    public JsonSchemaDocumentDefinitionExporter documentDefinitionExporter(
        final JsonSchemaDocumentDefinitionService documentDefinitionService,
        ObjectMapper objectMapper
    ) {
        return new JsonSchemaDocumentDefinitionExporter(
            objectMapper,
            documentDefinitionService
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaDocumentDefinitionImporter documentDefinitionImporter(
        JsonSchemaDocumentDefinitionService jsonSchemaDocumentDefinitionService
    ) {
        return new JsonSchemaDocumentDefinitionImporter(
          jsonSchemaDocumentDefinitionService
        );
    }

    @Bean
    @ConditionalOnMissingBean(DocumentSequenceGeneratorService.class)
    public JsonSchemaDocumentDefinitionSequenceGeneratorService documentSequenceGeneratorService(
        final DocumentDefinitionSequenceRepository<JsonSchemaDocumentDefinitionSequenceRecord> documentDefinitionSequenceRepository
    ) {
        return new JsonSchemaDocumentDefinitionSequenceGeneratorService(documentDefinitionSequenceRepository);
    }

    @Bean
    @ConditionalOnMissingBean(UndeployDocumentDefinitionService.class)
    public UndeployJsonSchemaDocumentDefinitionService undeployDocumentDefinitionService(
        final JsonSchemaDocumentDefinitionService documentDefinitionService,
        final DocumentService documentService,
        final ApplicationEventPublisher applicationEventPublisher,
        final AuthorizationService authorizationService
    ) {
        return new UndeployJsonSchemaDocumentDefinitionService(
            documentDefinitionService,
            documentService,
            applicationEventPublisher,
            authorizationService
        );
    }

    @Bean
    @ConditionalOnMissingBean(DocumentSearchService.class)
    public JsonSchemaDocumentSearchService documentSearchService(
        final EntityManager entityManager,
        final QueryDialectHelper queryDialectHelper,
        final SearchFieldService searchFieldService,
        final UserManagementService userManagementService,
        final AuthorizationService authorizationService,
        final OutboxService outboxService,
        final ObjectMapper objectMapper
    ) {
        return new JsonSchemaDocumentSearchService(
            entityManager,
            queryDialectHelper,
            searchFieldService,
            userManagementService,
            authorizationService,
            outboxService,
            objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(ApplicationReadyEventListenerImpl.class)
    public ApplicationReadyEventListenerImpl applicationReadyEventListenerImpl(
        final DocumentDefinitionService documentDefinitionService
    ) {
        return new ApplicationReadyEventListenerImpl(documentDefinitionService);
    }

    @Bean
    @ConditionalOnMissingBean(RelatedJsonSchemaDocumentAvailableEventListenerImpl.class)
    public RelatedJsonSchemaDocumentAvailableEventListenerImpl relatedDocumentAvailableEventListener(
        final DocumentService documentService
    ) {
        return new RelatedJsonSchemaDocumentAvailableEventListenerImpl(documentService);
    }

    @Bean
    @ConditionalOnBean(ResourceService.class)
    @ConditionalOnMissingBean(DocumentRelatedFileSubmittedEventListenerImpl.class)
    public DocumentRelatedFileSubmittedEventListenerImpl documentRelatedFileSubmittedEventListener(
        final DocumentService documentService,
        final ResourceService resourceService
    ) {
        return new DocumentRelatedFileSubmittedEventListenerImpl(documentService, resourceService);
    }

    //API
    @Bean
    @ConditionalOnMissingBean(DocumentDefinitionResource.class)
    public JsonSchemaDocumentDefinitionResource documentDefinitionResource(
        final DocumentDefinitionService documentDefinitionService,
        final UndeployDocumentDefinitionService undeployDocumentDefinitionService,
        final DocumentStatisticService documentStatisticService
    ) {
        return new JsonSchemaDocumentDefinitionResource(
            documentDefinitionService,
            undeployDocumentDefinitionService,
            documentStatisticService
        );
    }

    @Bean
    @ConditionalOnMissingBean(DocumentResource.class)
    public JsonSchemaDocumentResource documentResource(
        DocumentService documentService
    ) {
        return new JsonSchemaDocumentResource(documentService);
    }

    @Bean
    @ConditionalOnMissingBean(DocumentSearchResource.class)
    public JsonSchemaDocumentSearchResource documentSearchResource(DocumentSearchService documentSearchService) {
        return new JsonSchemaDocumentSearchResource(documentSearchService);
    }

    @Bean("documentSpringContextHelper")
    @ConditionalOnMissingBean(DocumentSpringContextHelper.class)
    public DocumentSpringContextHelper documentSpringContextHelper() {
        return new DocumentSpringContextHelper();
    }

    @Bean
    @ConditionalOnMissingBean(DocumentModuleExceptionTranslator.class)
    public DocumentModuleExceptionTranslator documentModuleExceptionTranslator(
        Optional<HardeningService> hardeningServiceOptional
    ) {
        return new DocumentModuleExceptionTranslator(hardeningServiceOptional);
    }
}
