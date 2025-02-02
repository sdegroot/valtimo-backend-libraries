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

package com.ritense.document.service;

import com.ritense.authorization.Action;
import com.ritense.document.domain.DocumentDefinition;
import com.ritense.document.domain.impl.JsonSchemaDocumentDefinition;
import com.ritense.document.domain.impl.JsonSchemaDocumentDefinitionId;
import com.ritense.document.service.result.DeployDocumentDefinitionResult;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DocumentDefinitionService {

    Page<? extends DocumentDefinition> findAll(Pageable pageable);

    Page<? extends DocumentDefinition> findAllForManagement(Pageable pageable);

    JsonSchemaDocumentDefinitionId findIdByName(String name);

    Optional<? extends DocumentDefinition> findBy(DocumentDefinition.Id id);

    Optional<? extends DocumentDefinition> findLatestByName(String documentDefinitionName);

    void requirePermission(String documentDefinitionName, Action action);

    Optional<? extends DocumentDefinition> findByNameAndVersion(String documentDefinitionName, long version);

    List<Long> findVersionsByName(String documentDefinitionName);

    void deployAll();

    DeployDocumentDefinitionResult deploy(String schema);

    void deploy(InputStream inputStream) throws IOException;

    void deployAll(boolean readOnly, boolean force);

    DeployDocumentDefinitionResult deploy(String schema, boolean readOnly, boolean force);

    void deploy(InputStream inputStream, boolean readOnly, boolean force) throws IOException;

    void store(JsonSchemaDocumentDefinition documentDefinition);

    void removeDocumentDefinition(String documentDefinitionName);

    boolean currentUserCanAccessDocumentDefinition(String documentDefinitionName);

    void validateJsonPath(String documentDefinitionName, String jsonPathExpression) throws ValidationException;

    boolean isValidJsonPath(JsonSchemaDocumentDefinition definition, String jsonPathExpression);

    void validateJsonPointer(String documentDefinitionName, String jsonPointer) throws ValidationException;
}
