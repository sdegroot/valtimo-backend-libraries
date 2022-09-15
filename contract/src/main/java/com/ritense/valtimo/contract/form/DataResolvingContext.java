/*
 * Copyright 2015-2022 Ritense BV, the Netherlands.
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

package com.ritense.valtimo.contract.form;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public class DataResolvingContext {

    private final String documentDefinitionName;
    private final UUID documentId;
    private final JsonNode formDefinition;

    public DataResolvingContext(
        String documentDefinitionName,
        UUID documentId,
        JsonNode formDefinition
    ) {
        this.documentDefinitionName = documentDefinitionName;
        this.documentId = documentId;
        this.formDefinition = formDefinition;
    }

    public String getDocumentDefinitionName() {
        return documentDefinitionName;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public JsonNode getFormDefinition() {
        return formDefinition;
    }
}
