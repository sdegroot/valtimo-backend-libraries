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

package com.ritense.audit.service;

import com.ritense.audit.domain.AuditRecord;
import com.ritense.audit.service.impl.SearchCriteria;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service to search for audit records.
 *
 * @deprecated Since 12.0.0.
 */
@Deprecated(since = "Since 12.0.0", forRemoval = true)
public interface AuditSearchService {

    Page<AuditRecord> search(List<SearchCriteria> criteriaList, Pageable pageable);

}