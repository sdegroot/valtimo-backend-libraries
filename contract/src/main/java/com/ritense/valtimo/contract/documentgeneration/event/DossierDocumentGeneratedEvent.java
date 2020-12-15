/*
 * Copyright 2015-2020 Ritense BV, the Netherlands.
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

package com.ritense.valtimo.contract.documentgeneration.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.ritense.valtimo.contract.audit.AuditEvent;
import com.ritense.valtimo.contract.audit.AuditMetaData;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class DossierDocumentGeneratedEvent extends AuditMetaData implements AuditEvent, Serializable {

    private static final long serialVersionUID = -1001787410861765142L;

    private final String templateIdentifier;
    private final String dossierId;

    @JsonCreator
    public DossierDocumentGeneratedEvent(
        UUID id,
        String origin,
        LocalDateTime occurredOn,
        String user,
        String templateIdentifier,
        String dossierId
    ) {
        super(id, origin, occurredOn, user);
        this.templateIdentifier = templateIdentifier;
        this.dossierId = dossierId;
    }

    public String getTemplateIdentifier() {
        return templateIdentifier;
    }

    public String getDossierId() {
        return dossierId;
    }
}
