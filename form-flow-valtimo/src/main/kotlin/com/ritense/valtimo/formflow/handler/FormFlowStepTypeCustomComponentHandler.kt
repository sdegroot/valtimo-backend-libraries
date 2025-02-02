/*
 *  Copyright 2015-2024 Ritense BV, the Netherlands.
 *
 *  Licensed under EUPL, Version 1.2 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.ritense.valtimo.formflow.handler

import com.ritense.formflow.domain.definition.configuration.step.CustomComponentStepTypeProperties
import com.ritense.formflow.domain.instance.FormFlowStepInstance
import com.ritense.formflow.handler.FormFlowStepTypeHandler

class FormFlowStepTypeCustomComponentHandler(
) : FormFlowStepTypeHandler {

    override fun getType() = "custom-component"

    override fun getTypeProperties(stepInstance: FormFlowStepInstance): CustomComponentTypeProperties {
        val stepDefinitionType = stepInstance.definition.type
        assert(stepDefinitionType.name == getType())
        val angularComponentId = (stepDefinitionType.properties as CustomComponentStepTypeProperties).componentId
        return CustomComponentTypeProperties(angularComponentId)
    }
}
