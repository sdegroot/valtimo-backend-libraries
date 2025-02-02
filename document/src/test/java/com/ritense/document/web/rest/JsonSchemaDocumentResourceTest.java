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

package com.ritense.document.web.rest;

import static com.ritense.valtimo.contract.domain.ValtimoMediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ritense.document.BaseTest;
import com.ritense.document.domain.impl.JsonDocumentContent;
import com.ritense.document.domain.impl.JsonSchemaDocument;
import com.ritense.document.domain.impl.request.ModifyDocumentRequest;
import com.ritense.document.service.impl.JsonSchemaDocumentService;
import com.ritense.document.web.rest.impl.JsonSchemaDocumentResource;
import com.ritense.valtimo.contract.authentication.NamedUser;
import com.ritense.valtimo.contract.utils.TestUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JsonSchemaDocumentResourceTest extends BaseTest {

    private JsonSchemaDocumentService documentService;
    private MockMvc mockMvc;
    private JsonSchemaDocument document;

    @BeforeEach
    void setUp() {

        documentService = mock(JsonSchemaDocumentService.class);
        DocumentResource documentResource = new JsonSchemaDocumentResource(documentService);

        mockMvc = MockMvcBuilders.standaloneSetup(documentResource)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();

        var content = new JsonDocumentContent("{\"firstName\": \"John\"}");
        final JsonSchemaDocument.CreateDocumentResultImpl result = JsonSchemaDocument.create(
            definitionOf("person"),
            content,
            USERNAME,
            documentSequenceGeneratorService,
            null
        );
        document = result.resultingDocument().orElseThrow();
        document.setAssignee("test-assignee-id", "John Doe");
        document.addRelatedFile(relatedFile());
    }

    @Test
    void shouldReturnOkWithDocument() throws Exception {
        when(documentService.findBy(any()))
            .thenReturn(Optional.of(document));

        mockMvc.perform(get("/api/v1/document/{id}", UUID.randomUUID().toString())
            .accept(APPLICATION_JSON_VALUE)
            .contentType(APPLICATION_JSON_VALUE)
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    void shouldReturnDocumentWithAssignee() throws Exception {
        when(documentService.findBy(any()))
            .thenReturn(Optional.of(document));

        mockMvc.perform(get("/api/v1/document/{id}", UUID.randomUUID().toString())
                .accept(APPLICATION_JSON_VALUE)
                .contentType(APPLICATION_JSON_VALUE)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$").isNotEmpty())
            .andExpect(jsonPath("$.assigneeId").value("test-assignee-id"))
            .andExpect(jsonPath("$.assigneeFullName").value("John Doe"));
    }

    @Test
    void shouldModifyDocument() throws Exception {
        final var json = "{\"firstName\": \"John\"}";
        final var content = new JsonDocumentContent(json);
        final var document = createDocument(definitionOf("person"), content).resultingDocument().get();
        final var modifyDocumentResult = new JsonSchemaDocument.ModifyDocumentResultImpl(document);
        when(documentService.modifyDocument(any())).thenReturn(modifyDocumentResult);
        when(documentService.get(document.id().getId().toString()))
            .thenReturn(document);

        final var modifyRequest = new ModifyDocumentRequest(
            document.id().toString(),
            document.content().asJson()
        );

        mockMvc.perform(
            put("/api/v1/document")
                .contentType(APPLICATION_JSON_VALUE)
                .content(TestUtil.convertObjectToJsonBytes(modifyRequest))
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    void shouldAddResourceForDocument() throws Exception {
        final var json = "{\"firstName\": \"John\"}";
        final var content = new JsonDocumentContent(json);
        final var document = createDocument(definitionOf("person"), content).resultingDocument().get();

        when(documentService.get(document.id().getId().toString()))
            .thenReturn(document);

        mockMvc.perform(
            post("/api/v1/document/{document-id}/resource/{resource-id}", document.id(), UUID.randomUUID())
                .contentType(APPLICATION_JSON_VALUE)
        )
            .andDo(print())
            .andExpect(status().isNoContent())
            .andReturn();

        verify(documentService).assignResource(any(), any());
    }

    @Test
    void shouldRemoveRelatedFile() throws Exception {
        final var json = "{\"firstName\": \"John\"}";
        final var content = new JsonDocumentContent(json);
        final var document = createDocument(definitionOf("person"), content).resultingDocument().get();

        when(documentService.get(document.id().getId().toString()))
            .thenReturn(document);

        mockMvc.perform(
            delete("/api/v1/document/{document-id}/resource/{resource-id}", document.id(), UUID.randomUUID())
                .contentType(APPLICATION_JSON_VALUE)
        )
            .andDo(print())
            .andExpect(status().isNoContent())
            .andReturn();

        verify(documentService).removeRelatedFile(any(), any());
    }

    @Test
    void shouldGetCandidateUsers() throws Exception {
        final var json = "{\"firstName\": \"John\"}";
        final var content = new JsonDocumentContent(json);
        final var document = createDocument(definitionOf("person"), content).resultingDocument().get();

        when(documentService.getCandidateUsers(document.id()))
            .thenReturn(List.of(new NamedUser("1234", "John", "Doe")));

        mockMvc.perform(get("/api/v1/document/{document-id}/candidate-user", document.id()).accept(APPLICATION_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].firstName").value("John"))
            .andExpect(jsonPath("$[0].lastName").value("Doe"));
    }

    @Test
    void shouldNotGetCandidateUsersWhenNoAccessToDocument() throws Exception {
        final var json = "{\"firstName\": \"John\"}";
        final var content = new JsonDocumentContent(json);
        final var document = createDocument(definitionOf("person"), content).resultingDocument().get();

        mockMvc.perform(get("/api/v1/document/{document-id}/candidate-user", document.id()).accept(APPLICATION_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldGetCandidateUsersForMultipleDocuments() throws Exception {
        when(documentService.getCandidateUsers(any(List.class)))
            .thenReturn(List.of(new NamedUser("1234", "John", "Doe")));

        mockMvc.perform(post("/api/v1/document/candidate-user")
                .contentType(APPLICATION_JSON_VALUE)
            .content("{\"documentIds\":[\"e4715f07-7c91-4015-ae18-f505cb082935\"]}"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].firstName").value("John"))
            .andExpect(jsonPath("$[0].lastName").value("Doe"));
    }

}
