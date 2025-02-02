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

package com.ritense.valtimo.formflow.domain

import com.ritense.processlink.domain.ActivityTypeWithEventName
import com.ritense.processlink.domain.ProcessLink
import com.ritense.valtimo.formflow.mapper.FormFlowProcessLinkMapper.Companion.PROCESS_LINK_TYPE_FORM_FLOW
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.util.UUID

@Entity
@DiscriminatorValue(PROCESS_LINK_TYPE_FORM_FLOW)
class FormFlowProcessLink(
    id: UUID,
    processDefinitionId: String,
    activityId: String,
    activityType: ActivityTypeWithEventName,

    @Column(name = "form_flow_definition_id", nullable = false)
    val formFlowDefinitionId: String

) : ProcessLink(
    id,
    processDefinitionId,
    activityId,
    activityType,
    PROCESS_LINK_TYPE_FORM_FLOW,
) {

    override fun copy(
        id: UUID,
        processDefinitionId: String,
    ) = copy(
        id = id,
        processDefinitionId = processDefinitionId,
        activityId = activityId
    )

    fun copy(
        id: UUID = this.id,
        processDefinitionId: String = this.processDefinitionId,
        activityId: String = this.activityId,
        activityType: ActivityTypeWithEventName = this.activityType,
        formFlowDefinitionId: String = this.formFlowDefinitionId
    ) = FormFlowProcessLink(
        id = id,
        processDefinitionId = processDefinitionId,
        activityId = activityId,
        activityType = activityType,
        formFlowDefinitionId = formFlowDefinitionId
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as FormFlowProcessLink

        return formFlowDefinitionId == other.formFlowDefinitionId
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + formFlowDefinitionId.hashCode()
        return result
    }
}
