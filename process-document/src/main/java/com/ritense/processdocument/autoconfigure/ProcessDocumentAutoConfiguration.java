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

package com.ritense.processdocument.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ritense.authorization.AuthorizationService;
import com.ritense.document.repository.DocumentDefinitionRepository;
import com.ritense.document.service.DocumentDefinitionService;
import com.ritense.document.service.DocumentService;
import com.ritense.document.service.impl.JsonSchemaDocumentDefinitionService;
import com.ritense.document.service.impl.JsonSchemaDocumentService;
import com.ritense.processdocument.domain.delegate.ProcessDocumentStartEventMessageDelegate;
import com.ritense.processdocument.domain.impl.delegate.ProcessDocumentStartEventMessageDelegateImpl;
import com.ritense.processdocument.domain.impl.listener.StartEventFromCallActivityListenerImpl;
import com.ritense.processdocument.domain.impl.listener.StartEventListenerImpl;
import com.ritense.processdocument.domain.impl.listener.UndeployDocumentDefinitionEventListener;
import com.ritense.processdocument.domain.listener.StartEventFromCallActivityListener;
import com.ritense.processdocument.domain.listener.StartEventListener;
import com.ritense.processdocument.repository.DocumentDefinitionProcessLinkRepository;
import com.ritense.processdocument.repository.ProcessDocumentDefinitionRepository;
import com.ritense.processdocument.repository.ProcessDocumentInstanceRepository;
import com.ritense.processdocument.resolver.DocumentJsonValueResolverFactory;
import com.ritense.processdocument.resolver.DocumentTableValueResolver;
import com.ritense.processdocument.service.DocumentDefinitionProcessLinkService;
import com.ritense.processdocument.service.ProcessDocumentAssociationService;
import com.ritense.processdocument.service.ProcessDocumentDeploymentService;
import com.ritense.processdocument.service.ProcessDocumentService;
import com.ritense.processdocument.service.impl.CamundaProcessJsonSchemaDocumentAssociationService;
import com.ritense.processdocument.service.impl.CamundaProcessJsonSchemaDocumentDeploymentService;
import com.ritense.processdocument.service.impl.CamundaProcessJsonSchemaDocumentService;
import com.ritense.processdocument.service.impl.DocumentDefinitionProcessLinkServiceImpl;
import com.ritense.processdocument.web.rest.ProcessDocumentResource;
import com.ritense.valtimo.camunda.service.CamundaRepositoryService;
import com.ritense.valtimo.contract.authentication.UserManagementService;
import com.ritense.valtimo.service.CamundaProcessService;
import com.ritense.valtimo.service.CamundaTaskService;
import com.ritense.valueresolver.ValueResolverFactory;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.extension.reactor.spring.EnableCamundaEventBus;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@EnableJpaRepositories(basePackages = "com.ritense.processdocument.repository")
@EntityScan("com.ritense.processdocument.domain")
@EnableCamundaEventBus
public class ProcessDocumentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProcessDocumentService.class)
    public CamundaProcessJsonSchemaDocumentService processDocumentService(
        JsonSchemaDocumentService documentService,
        CamundaTaskService camundaTaskService,
        CamundaProcessService camundaProcessService,
        ProcessDocumentAssociationService processDocumentAssociationService,
        AuthorizationService authorizationService
    ) {
        return new CamundaProcessJsonSchemaDocumentService(
            documentService,
            camundaTaskService,
            camundaProcessService,
            processDocumentAssociationService,
            authorizationService
        );
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDocumentAssociationService.class)
    public CamundaProcessJsonSchemaDocumentAssociationService processDocumentAssociationService(
        ProcessDocumentDefinitionRepository processDocumentDefinitionRepository,
        ProcessDocumentInstanceRepository processDocumentInstanceRepository,
        DocumentDefinitionRepository documentDefinitionRepository,
        DocumentDefinitionService documentDefinitionService,
        CamundaRepositoryService repositoryService,
        RuntimeService runtimeService,
        HistoryService historyService,
        AuthorizationService authorizationService,
        DocumentService documentService,
        UserManagementService userManagementService
    ) {
        return new CamundaProcessJsonSchemaDocumentAssociationService(
            processDocumentDefinitionRepository,
            processDocumentInstanceRepository,
            documentDefinitionRepository,
            documentDefinitionService,
            repositoryService,
            runtimeService,
            historyService,
            authorizationService,
            documentService,
            userManagementService
        );
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDocumentStartEventMessageDelegate.class)
    public ProcessDocumentStartEventMessageDelegateImpl processDocumentStartEventMessageDelegate(
        ProcessDocumentAssociationService processDocumentAssociationService,
        DocumentService documentService,
        RuntimeService runtimeService
    ) {
        return new ProcessDocumentStartEventMessageDelegateImpl(
            processDocumentAssociationService,
            documentService,
            runtimeService
        );
    }

    @Bean
    @ConditionalOnMissingBean(StartEventFromCallActivityListener.class)
    public StartEventFromCallActivityListenerImpl startEventFromCallActivityListener(
        ProcessDocumentAssociationService processDocumentAssociationService
    ) {
        return new StartEventFromCallActivityListenerImpl(processDocumentAssociationService);
    }

    @Bean
    @ConditionalOnMissingBean(StartEventListener.class)
    public StartEventListenerImpl startEventListener(
        ProcessDocumentService processDocumentService,
        ProcessDocumentAssociationService processDocumentAssociationService,
        ApplicationEventPublisher applicationEventPublisher,
        ObjectMapper objectMapper
    ) {
        return new StartEventListenerImpl(
            processDocumentService,
            processDocumentAssociationService,
            applicationEventPublisher,
            objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(UndeployDocumentDefinitionEventListener.class)
    public UndeployDocumentDefinitionEventListener undeployDocumentDefinitionEventListener(
        ProcessDocumentAssociationService processDocumentAssociationService,
        CamundaProcessService camundaProcessService
    ) {
        return new UndeployDocumentDefinitionEventListener(
            processDocumentAssociationService,
            camundaProcessService
        );
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDocumentResource.class)
    public ProcessDocumentResource processDocumentResource(
        ProcessDocumentService processDocumentService,
        ProcessDocumentAssociationService processDocumentAssociationService,
        DocumentDefinitionProcessLinkService documentDefinitionProcessLinkService
    ) {
        return new ProcessDocumentResource(processDocumentService, processDocumentAssociationService, documentDefinitionProcessLinkService);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDocumentDeploymentService.class)
    public ProcessDocumentDeploymentService processDocumentDeploymentService(
            ResourceLoader resourceLoader,
            ProcessDocumentAssociationService processDocumentAssociationService,
            DocumentDefinitionService documentDefinitionService,
            ObjectMapper objectMapper
    ) {
        return new CamundaProcessJsonSchemaDocumentDeploymentService(
            resourceLoader,
            processDocumentAssociationService,
            documentDefinitionService,
            objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(DocumentJsonValueResolverFactory.class)
    public ValueResolverFactory documentJsonValueResolver(
        ProcessDocumentService processDocumentService,
        DocumentService documentService,
        JsonSchemaDocumentDefinitionService documentDefinitionService,
        ObjectMapper objectMapper
    ) {
        return new DocumentJsonValueResolverFactory(
            processDocumentService,
            documentService,
            documentDefinitionService,
            objectMapper
        );
    }

    @Bean
    @ConditionalOnMissingBean(DocumentTableValueResolver.class)
    public ValueResolverFactory documentTableValueResolver(
        ProcessDocumentService processDocumentService,
        DocumentService documentService
    ) {
        return new DocumentTableValueResolver(processDocumentService, documentService);
    }

    @Bean
    @ConditionalOnMissingBean(DocumentDefinitionProcessLinkService.class)
    public DocumentDefinitionProcessLinkService documentDefinitionProcessLinkService(
        DocumentDefinitionProcessLinkRepository documentDefinitionProcessLinkRepository,
        CamundaRepositoryService repositoryService
    ) {
        return new DocumentDefinitionProcessLinkServiceImpl(documentDefinitionProcessLinkRepository, repositoryService);
    }


}
