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

package com.ritense.formflow.domain.instance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.ritense.formflow.domain.definition.FormFlowNextStep
import com.ritense.formflow.domain.definition.FormFlowStep
import com.ritense.formflow.event.ApplicationEventPublisherHolder
import com.ritense.formflow.event.FormFlowStepCompletedEvent
import com.ritense.formflow.expression.ExpressionProcessorFactoryHolder
import com.ritense.formflow.json.MapperSingleton
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.Objects
import org.hibernate.annotations.Type

@Entity
@Table(name = "form_flow_step_instance")
data class FormFlowStepInstance(
    @EmbeddedId
    val id: FormFlowStepInstanceId = FormFlowStepInstanceId.newId(),
    @JoinColumn(name = "form_flow_instance_id", updatable = false, nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    val instance: FormFlowInstance,
    @Column(name = "form_flow_step_key", updatable = false, nullable = false)
    val stepKey: String,
    @Column(name = "form_flow_step_instance_order", updatable = false, nullable = false)
    val order: Int,
    @Type(value = JsonType::class)
    @Column(name = "submission_data")
    var submissionData: String? = null,
    @Type(value = JsonType::class)
    @Column(name = "temporary_submission_data")
    var temporarySubmissionData: String? = null
    // On complete, clear temporary submission from the current step
    // We only use temporarySubmissionData of the current step when determining context
) {

    val definition: FormFlowStep
        get() = instance.formFlowDefinition.getStepByKey(stepKey)

    fun back() {
        processExpressions<Any>(definition.onBack)
    }

    fun saveTemporary(incompleteSubmissionData: String) {
        this.temporarySubmissionData = incompleteSubmissionData
    }

    fun open() {
        processExpressions<Any>(definition.onOpen)
    }

    fun complete(submissionData: String) {
        this.submissionData = submissionData
        this.temporarySubmissionData = null

        processExpressions<Any>(definition.onComplete)
        ApplicationEventPublisherHolder.getInstance().publishEvent(
            FormFlowStepCompletedEvent(this)
        )
    }

    fun determineNextStep(): FormFlowNextStep? {
        val conditions = definition.nextSteps
            .map { nextStep -> nextStep.condition }

        val stepsWithResult = definition.nextSteps
            .zip(processExpressions<Boolean>(conditions))

        val firstStepWithResultTrue = stepsWithResult
            .firstOrNull { (_, result) -> result != null && result }
            ?.first

        if (firstStepWithResultTrue != null) {
            return firstStepWithResultTrue
        }

        return stepsWithResult
            .lastOrNull { (_, result) -> result == null }
            ?.first
    }

    private fun <T> processExpressions(expressions: List<String?>): List<T?> {
        return ExpressionProcessorFactoryHolder.getInstance().let {
            val variables = createVarMap()
            val expressionProcessor = it.create(variables)

            expressions.map { expression ->
                expression?.let { expressionProcessor.process<T>(expression) }
            }
        }
    }

    private fun createVarMap(): Map<String, Any> {
        return mapOf(
            "step" to mapOf(
                "id" to id,
                "key" to stepKey,
                "submissionData" to MapperSingleton.get().readValue<JsonNode>(instance.getSubmissionDataContext())
            ),
            "instance" to mapOf(
                "id" to instance.id
            ),
            "additionalProperties" to instance.getAdditionalProperties()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FormFlowStepInstance

        if (id != other.id) return false
        if (stepKey != other.stepKey) return false
        if (order != other.order) return false
        if (submissionData != other.submissionData) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(id, stepKey, order, submissionData)
    }

    fun getCurrentSubmissionData(): String? {
        return temporarySubmissionData ?: submissionData
    }
}
