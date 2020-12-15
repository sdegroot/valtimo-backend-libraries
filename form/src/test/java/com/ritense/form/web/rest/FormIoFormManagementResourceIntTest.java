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

package com.ritense.form.web.rest;

import com.jayway.jsonpath.JsonPath;
import com.ritense.form.BaseIntegrationTest;
import com.ritense.form.domain.FormIoFormDefinition;
import com.ritense.form.domain.request.CreateFormDefinitionRequest;
import com.ritense.form.domain.request.ModifyFormDefinitionRequest;
import com.ritense.valtimo.contract.utils.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
public class FormIoFormManagementResourceIntTest extends BaseIntegrationTest {

    @Inject
    private FormManagementResource resource;
    private MockMvc mockMvc;
    private FormIoFormDefinition formDefinition;

    @BeforeEach
    void setUp() {
        formDefinitionRepository.deleteAll();
        formDefinition = formDefinition();
        mockMvc = MockMvcBuilders
            .standaloneSetup(resource)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    public void shouldReturn200WithForm() throws Exception {
        formDefinitionRepository.save(formDefinition(UUID.randomUUID(), "form1"));
        formDefinitionRepository.save(formDefinition(UUID.randomUUID(), "form2"));
        formDefinitionRepository.save(formDefinition(UUID.randomUUID(), "form3"));

        mockMvc.perform(
            get("/api/form-management")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andDo(print())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    public void shouldReturn200WithFormCreated() throws Exception {
        final var request = new CreateFormDefinitionRequest(DEFAULT_FORM_DEFINITION_NAME, "{}");
        mockMvc.perform(
            post("/api/form-management")
                .characterEncoding(StandardCharsets.UTF_8.name())
                .content(TestUtil.convertObjectToJsonBytes(request))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk());

        Optional<FormIoFormDefinition> savedDefinition = formDefinitionRepository.findByName(DEFAULT_FORM_DEFINITION_NAME);
        assertThat(savedDefinition).isPresent();
    }

    @Test
    public void shouldReturn200WithFormModified() throws Exception {
        final var request = new CreateFormDefinitionRequest(DEFAULT_FORM_DEFINITION_NAME, "{}");
        final MvcResult result = mockMvc.perform(
            post("/api/form-management")
                .characterEncoding(StandardCharsets.UTF_8.name())
                .content(TestUtil.convertObjectToJsonBytes(request))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn();

        final var documentContext = JsonPath.parse(result.getResponse().getContentAsString());
        final var id = UUID.fromString(documentContext.read("$['id']").toString());
        final var newDefinition = "{\"key\":\"someValue\"}";

        mockMvc.perform(
            put("/api/form-management")
                .content(TestUtil.convertObjectToJsonBytes(
                    new ModifyFormDefinitionRequest(id, DEFAULT_FORM_DEFINITION_NAME, newDefinition)
                ))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isOk());

        Optional<FormIoFormDefinition> savedDefinition = formDefinitionRepository.findById(id);
        assertThat(savedDefinition).isPresent();
        assertThat(savedDefinition.get().getFormDefinition().toString()).isEqualTo(newDefinition);
    }

    @Test
    public void shouldReturn204WithFormDeleted() throws Exception {
        FormIoFormDefinition savedFormDefinition = formDefinitionRepository.save(formDefinition);

        assertThat(formDefinitionRepository.existsById(savedFormDefinition.getId())).isTrue();

        mockMvc.perform(
            delete("/api/form-management/{formDefinitionId}", savedFormDefinition.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andDo(print())
            .andExpect(status().isNoContent());

        assertThat(formDefinitionRepository.existsById(savedFormDefinition.getId())).isFalse();
    }

}
