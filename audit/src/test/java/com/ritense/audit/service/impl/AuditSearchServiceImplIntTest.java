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

package com.ritense.audit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.ritense.audit.BaseIntegrationTest;
import com.ritense.audit.domain.AuditRecord;
import com.ritense.audit.domain.event.TestEvent;
import com.ritense.authorization.AuthorizationContext;
import com.ritense.valtimo.contract.audit.AuditEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class AuditSearchServiceImplIntTest extends BaseIntegrationTest {

    @BeforeEach
    public void setUp() {
        auditRecordRepository.deleteAll();
        final AuditEvent testEvent = testEvent(LocalDateTime.now());
        auditEventProcessor.process(testEvent);
    }

    @Test
    void shouldFindBySearchCriteria() {
        final List<SearchCriteria> searchCriteriaList = List.of(new SearchCriteria("$.processInstanceId", TestEvent.class, "myProcessInstanceId"));
        final Page<AuditRecord> page = AuthorizationContext
            .runWithoutAuthorization(() -> auditSearchService.search(searchCriteriaList, PageRequest.of(0, 1)));
        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void shouldNotFindBySearchCriteria() {
        final List<SearchCriteria> searchCriteriaList = List.of(new SearchCriteria("$.processInstanceId2", TestEvent.class, "myProcessInstanceId"));
        final Page<AuditRecord> page = AuthorizationContext
            .runWithoutAuthorization(() -> auditSearchService.search(searchCriteriaList, PageRequest.of(0, 1)));
        assertThat(page).isNotNull();
        assertThat(page.getTotalElements()).isEqualTo(0);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

}