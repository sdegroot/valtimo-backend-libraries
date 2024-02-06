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

package com.ritense.form

import com.ritense.valueresolver.ValueResolverFactory
import org.camunda.bpm.engine.delegate.VariableScope
import org.springframework.context.ApplicationEventPublisher
import java.util.function.Function

/**
 * This resolver returns the requestedValue as the value.
 * It will do a best-effort of guessing the type of the given requestedValue before returning it.
 *
 * For instance, "true" will become the boolean <code>true</code>
 *
 * These requestedValues do not have a prefix
 */
open class TestValueResolverFactory(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val prefix: String = "test"
) : ValueResolverFactory {

    override fun supportedPrefix(): String {
        return prefix
    }

    override fun createResolver(
        processInstanceId: String,
        variableScope: VariableScope
    ): Function<String, Any?> {
        return createResolver()
    }

    override fun createResolver(documentInstanceId: String): Function<String, Any?> {
        return createResolver()
    }

    override fun handleValues(
        processInstanceId: String,
        variableScope: VariableScope?,
        values: Map<String, Any>
    ) {
        applicationEventPublisher.publishEvent(
            TestValueResolverHandleEvent(processInstanceId, variableScope, values)
        )
    }

    private fun createResolver(): Function<String, Any?> {
        return Function { requestedValue ->
            if (requestedValue.startsWith("!")) {
                null
            } else {
                requestedValue.toBooleanStrictOrNull()
                    ?: requestedValue.toLongOrNull()
                    ?: requestedValue.toDoubleOrNull()
                    ?: "My${requestedValue.replaceFirstChar { c -> c.uppercase() }}Value"
            }
        }
    }

    data class TestValueResolverHandleEvent(
        val processInstanceId: String,
        val variableScope: VariableScope?,
        val values: Map<String, Any>
    )

}
