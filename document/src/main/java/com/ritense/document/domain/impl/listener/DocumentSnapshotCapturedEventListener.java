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

package com.ritense.document.domain.impl.listener;

import com.ritense.document.domain.impl.event.JsonSchemaDocumentSnapshotCapturedEvent;
import com.ritense.document.service.DocumentSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

@RequiredArgsConstructor
@Slf4j
public class DocumentSnapshotCapturedEventListener {

    private final DocumentSnapshotService documentSnapshotService;

    @EventListener(JsonSchemaDocumentSnapshotCapturedEvent.class)
    public void handleDocumentCreatedEvent(JsonSchemaDocumentSnapshotCapturedEvent event) {
        logger.debug("{} - handle - JsonSchemaDocumentSnapshotEvent - {}", Thread.currentThread().getName(), event.documentId());
        documentSnapshotService.makeSnapshot(event.documentId(), event.createdOn(), event.createdBy());
    }

}