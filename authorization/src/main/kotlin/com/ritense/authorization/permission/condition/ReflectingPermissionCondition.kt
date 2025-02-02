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

package com.ritense.authorization.permission.condition

abstract class ReflectingPermissionCondition(type: PermissionConditionType) : PermissionCondition(type) {
    protected fun findEntityFieldValue(entity: Any, field: String): Any? {
        var currentEntity: Any? = entity
        val fields = field.split('.')
        fields.forEachIndexed { index, value ->
            val declaredField = currentEntity!!.javaClass.getDeclaredField(value)
            declaredField.trySetAccessible()

            // Field.get(obj) does not (always) seem to work according to spec, because it throws a NullPointerException when the value of a property is null
            currentEntity = try {
                declaredField.get(currentEntity)
            } catch (npe: NullPointerException) {
                if (index == fields.size - 1) {
                    null
                } else {
                    throw npe
                }
            }
        }
        return currentEntity
    }
}