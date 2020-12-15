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

package com.ritense.document.web.rest.impl;

import com.ritense.document.domain.DocumentDefinition;
import com.ritense.document.service.DocumentDefinitionService;
import com.ritense.document.web.rest.DocumentDefinitionResource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class JsonSchemaDocumentDefinitionResource implements DocumentDefinitionResource {

    private final DocumentDefinitionService documentDefinitionService;

    @Override
    @GetMapping(value = "/document-definition")
    public ResponseEntity<Page<? extends DocumentDefinition>> getDocumentDefinitions(
        @PageableDefault(sort = {"createdOn"}, direction = DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(documentDefinitionService.findAll(pageable));
    }

    @Override
    @GetMapping(value = "/document-definition/{name}")
    public ResponseEntity<? extends DocumentDefinition> getDocumentDefinition(
        @PathVariable String name
    ) {
        return documentDefinitionService.findLatestByName(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

}