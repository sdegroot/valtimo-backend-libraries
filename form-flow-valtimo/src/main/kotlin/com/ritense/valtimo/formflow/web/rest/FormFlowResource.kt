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

package com.ritense.valtimo.formflow.web.rest

import com.fasterxml.jackson.databind.JsonNode
import com.ritense.formflow.domain.instance.FormFlowInstanceId
import com.ritense.formflow.domain.instance.FormFlowStepInstance
import com.ritense.formflow.domain.instance.FormFlowStepInstanceId
import com.ritense.formflow.service.FormFlowService
import com.ritense.valtimo.contract.annotation.SkipComponentScan
import com.ritense.valtimo.contract.domain.ValtimoMediaType.APPLICATION_JSON_UTF8_VALUE
import com.ritense.valtimo.formflow.service.FormFlowValtimoService
import com.ritense.valtimo.formflow.web.rest.result.CompleteStepResult
import com.ritense.valtimo.formflow.web.rest.result.FormFlowStepResult
import com.ritense.valtimo.formflow.web.rest.result.GetFormFlowStateResult
import jakarta.transaction.Transactional
import org.json.JSONObject
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@SkipComponentScan
@RequestMapping("/api", produces = [APPLICATION_JSON_UTF8_VALUE])
class FormFlowResource(
    private val formFlowService: FormFlowService,
    private val formFlowValtimoService: FormFlowValtimoService,
) {
    @GetMapping(
        value = [
            "/v1/form-flow/instance/{formFlowInstanceId}",
            "/v1/form-flow/{formFlowInstanceId}" // Deprecated since 11.0.0
        ]
    )
    @Transactional
    fun getFormFlowState(
        @PathVariable(name = "formFlowInstanceId") instanceId: String,
    ): ResponseEntity<GetFormFlowStateResult>? {
        val instance = formFlowService.getByInstanceIdIfExists(
            FormFlowInstanceId.existingId(instanceId)
        ) ?: return ResponseEntity
            .badRequest()
            .body(GetFormFlowStateResult(null, null, "No form flow instance can be found for the given instance id"))

        val stepInstance = instance.getCurrentStep()
        formFlowService.save(instance)

        return ResponseEntity.ok(GetFormFlowStateResult(instance.id.id, openStep(stepInstance)))
    }

    @PostMapping(
        value = [
            "/v1/form-flow/instance/{formFlowId}/step/instance/{stepInstanceId}",
            "/v1/form-flow/{formFlowId}/step/{stepInstanceId}" // Deprecated since 11.0.0
        ]
    )
    @Transactional
    fun completeStep(
        @PathVariable(name = "formFlowId") formFlowId: String,
        @PathVariable(name = "stepInstanceId") stepInstanceId: String,
        @RequestBody submissionData: JsonNode?
    ): ResponseEntity<CompleteStepResult> {
        val instance = formFlowService.getByInstanceIdIfExists(FormFlowInstanceId.existingId(formFlowId))!!
        val verifiedSubmissionData = formFlowValtimoService.getVerifiedSubmissionData(submissionData, instance)

        val stepInstance = instance.complete(
            FormFlowStepInstanceId.existingId(stepInstanceId),
            toJsonObject(verifiedSubmissionData)
        )
        formFlowService.save(instance)

        return ResponseEntity.ok(CompleteStepResult(instance.id.id, openStep(stepInstance)))
    }

    @PostMapping(
        value = [
            "/v1/form-flow/instance/{formFlowId}/back",
            "/v1/form-flow/{formFlowId}/back" // Deprecated since 11.0.0
        ]
    )
    @Transactional
    fun backStep(
        @PathVariable(name = "formFlowId") formFlowId: String,
        @RequestBody incompleteSubmissionData: JsonNode?
    ): ResponseEntity<GetFormFlowStateResult> {
        val instance = formFlowService.getByInstanceIdIfExists(FormFlowInstanceId.existingId(formFlowId))!!
        val verifiedSubmissionData = formFlowValtimoService.getVerifiedSubmissionData(incompleteSubmissionData, instance)
        if (verifiedSubmissionData != null) {
            instance.saveTemporary(toJsonObject(verifiedSubmissionData))
        }
        val stepInstance = instance.back()
        formFlowService.save(instance)

        return ResponseEntity.ok(GetFormFlowStateResult(instance.id.id, openStep(stepInstance)))
    }

    @PostMapping(
        value = [
            "/v1/form-flow/instance/{formFlowId}/save",
            "/v1/form-flow/{formFlowId}/save" // Deprecated since 11.0.0
        ]
    )
    @Transactional
    fun saveStep(
        @PathVariable(name = "formFlowId") formFlowId: String,
        @RequestBody incompleteSubmissionData: JsonNode?
    ): ResponseEntity<Unit> {
        val instance = formFlowService.getByInstanceIdIfExists(FormFlowInstanceId.existingId(formFlowId))!!
        val verifiedSubmissionData = formFlowValtimoService.getVerifiedSubmissionData(incompleteSubmissionData, instance)
        instance.saveTemporary(toJsonObject(verifiedSubmissionData))
        formFlowService.save(instance)

        return ResponseEntity.noContent().build()
    }

    private fun openStep(stepInstance: FormFlowStepInstance?): FormFlowStepResult? {
        return if (stepInstance != null) {
            stepInstance.open()
            FormFlowStepResult(
                stepInstance.id.id,
                stepInstance.definition.type.name,
                formFlowService.getTypeProperties(stepInstance)
            )
        } else {
            null
        }
    }

    private fun toJsonObject(jsonNode: JsonNode?): JSONObject {
        return if (jsonNode == null) {
            JSONObject()
        } else {
            JSONObject(jsonNode.toString())
        }
    }
}
