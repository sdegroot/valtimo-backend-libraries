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

package com.ritense.valtimo.autoconfigure;

import com.ritense.valtimo.CamundaBeansPlugin;
import com.ritense.valtimo.camunda.ProcessDefinitionDeployedEventPublisher;
import com.ritense.valtimo.camunda.command.ValtimoSchemaOperationsCommand;
import com.ritense.valtimo.camunda.processaudit.HistoryEventAuditProcessEnginePlugin;
import com.ritense.valtimo.camunda.processaudit.TaskEventHandler;
import com.ritense.valtimo.camunda.repository.CustomRepositoryServiceImpl;
import com.ritense.valtimo.camunda.task.service.NotificationService;
import com.ritense.valtimo.camunda.task.service.ReminderService;
import com.ritense.valtimo.camunda.task.service.impl.NotificationServiceImpl;
import com.ritense.valtimo.camunda.task.service.impl.ReminderServiceImpl;
import com.ritense.valtimo.config.CamundaConfiguration;
import com.ritense.valtimo.config.CustomFormTypesProcessEnginePlugin;
import com.ritense.valtimo.contract.annotation.ProcessBean;
import com.ritense.valtimo.contract.authentication.UserManagementService;
import com.ritense.valtimo.contract.config.LiquibaseRunner;
import com.ritense.valtimo.contract.config.ValtimoProperties;
import com.ritense.valtimo.contract.mail.MailSender;
import com.ritense.valtimo.emailnotificationsettings.service.EmailNotificationSettingsService;
import com.ritense.valtimo.helper.CamundaCollectionHelper;
import com.ritense.valtimo.helper.DelegateTaskHelper;
import com.ritense.valtimo.service.CamundaTaskService;
import com.ritense.valtimo.web.rest.error.CamundaExceptionTranslator;
import org.camunda.bpm.application.impl.event.ProcessApplicationEventListenerPlugin;
import org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration;
import org.camunda.bpm.spring.boot.starter.configuration.Ordering;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

@AutoConfiguration
@AutoConfigureAfter(CamundaBpmAutoConfiguration.class)
public class CamundaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CustomFormTypesProcessEnginePlugin.class)
    public CustomFormTypesProcessEnginePlugin customFormTypesProcessEnginePlugin() {
        return new CustomFormTypesProcessEnginePlugin();
    }

    @Bean
    @ConditionalOnMissingBean(HistoryEventAuditProcessEnginePlugin.class)
    public HistoryEventAuditProcessEnginePlugin historyEventAuditProcessEnginePlugin(final ApplicationEventPublisher applicationEventPublisher) {
        return new HistoryEventAuditProcessEnginePlugin(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(CamundaCollectionHelper.class)
    public CamundaCollectionHelper camundaCollectionHelper() {
        return new CamundaCollectionHelper();
    }

    @Bean
    @ConditionalOnMissingBean(ProcessApplicationEventListenerPlugin.class)
    public ProcessApplicationEventListenerPlugin processApplicationEventListenerPlugin() {
        return new ProcessApplicationEventListenerPlugin();
    }

    @Bean
    @ConditionalOnMissingBean(CamundaConfiguration.class)
    public CamundaConfiguration camundaConfiguration(
        final ValtimoSchemaOperationsCommand valtimoSchemaOperationsCommand,
        final CustomRepositoryServiceImpl repositoryService,
        final ProcessDefinitionDeployedEventPublisher processDefinitionDeployedEventPublisher
    ) {
        return new CamundaConfiguration(valtimoSchemaOperationsCommand, repositoryService, processDefinitionDeployedEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(ValtimoSchemaOperationsCommand.class)
    public ValtimoSchemaOperationsCommand valtimoSchemaOperationsCommand(final LiquibaseRunner liquibaseRunner) {
        return new ValtimoSchemaOperationsCommand(liquibaseRunner);
    }

    @Primary
    @Bean
    @ConditionalOnMissingBean(CustomRepositoryServiceImpl.class)
    public CustomRepositoryServiceImpl customRepositoryServiceImpl(final ApplicationEventPublisher applicationEventPublisher) {
        return new CustomRepositoryServiceImpl(applicationEventPublisher);
    }

    @ProcessBean
    @Bean
    @ConditionalOnMissingBean(NotificationService.class)
    public NotificationService notificationService(
        final EmailNotificationSettingsService emailNotificationService,
        final MailSender mailSender,
        final ValtimoProperties valtimoProperties,
        final DelegateTaskHelper delegateTaskHelper,
        final UserManagementService userManagementService
    ) {
        return new NotificationServiceImpl(
            emailNotificationService,
            mailSender,
            valtimoProperties,
            delegateTaskHelper,
            userManagementService
        );
    }

    @Bean
    @ConditionalOnMissingBean(ReminderService.class)
    @ConditionalOnProperty(prefix = "scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ReminderService reminderService(
        final CamundaTaskService taskService,
        final EmailNotificationSettingsService emailNotificationService,
        final MailSender mailSender,
        final UserManagementService userManagementService,
        final ValtimoProperties valtimoProperties
    ) {
        return new ReminderServiceImpl(
            taskService,
            emailNotificationService,
            mailSender,
            userManagementService,
            valtimoProperties.getMandrill().getReminderTemplate()
        );
    }

    @Bean
    @ConditionalOnMissingBean(TaskEventHandler.class)
    public TaskEventHandler taskEventHandler(final ApplicationEventPublisher applicationEventPublisher) {
        return new TaskEventHandler(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(ProcessDefinitionDeployedEventPublisher.class)
    public ProcessDefinitionDeployedEventPublisher bpmnPropertyListener(
        final ApplicationEventPublisher applicationEventPublisher
    ) {
        return new ProcessDefinitionDeployedEventPublisher(applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(CamundaExceptionTranslator.class)
    public CamundaExceptionTranslator camundaExceptionTranslator() {
        return new CamundaExceptionTranslator();
    }

    @Bean
    @Order(Ordering.DEFAULT_ORDER - 2)
    public CamundaBeansPlugin camundaBeansPlugin() {
        return new CamundaBeansPlugin();
    }
}
