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

package com.ritense.audit.domain;

import com.ritense.valtimo.contract.audit.AuditEvent;

import java.util.UUID;

public class AuditRecordBuilder {
    private AuditRecordId auditRecordId;
    private MetaData metaData;
    private AuditEvent auditEvent;

    public AuditRecordBuilder() {
    }

    public AuditRecordBuilder id(UUID id) {
        this.auditRecordId = AuditRecordId.newId(id);
        return this;
    }

    public AuditRecordBuilder auditEvent(AuditEvent auditEvent) {
        this.auditEvent = auditEvent;
        return this;
    }

    public AuditRecordBuilder metaData(MetaData metaData) {
        this.metaData = metaData;
        return this;
    }

    public AuditRecord build() {
        return new AuditRecord(auditRecordId, metaData, auditEvent);
    }

}