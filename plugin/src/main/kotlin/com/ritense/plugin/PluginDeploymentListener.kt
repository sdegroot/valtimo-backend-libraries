/*
 * Copyright 2015-2022 Ritense BV, the Netherlands.
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

package com.ritense.plugin

import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginAction
import com.ritense.plugin.domain.PluginActionDefinition
import com.ritense.plugin.domain.PluginActionDefinitionId
import com.ritense.plugin.domain.PluginDefinition
import com.ritense.plugin.repository.PluginActionDefinitionRepository
import com.ritense.plugin.repository.PluginDefinitionRepository
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import java.lang.reflect.Method

class PluginDeploymentListener(
    private val pluginDefinitionResolver: PluginDefinitionResolver,
    private val pluginDefinitionRepository: PluginDefinitionRepository,
    private val pluginActionDefinitionRepository: PluginActionDefinitionRepository
) {

    @EventListener(ApplicationStartedEvent::class)
    fun deployPluginDefinitions() {
        logger.info { "Deploying plugins" }
        val classes = findPluginClasses()

        classes.forEach { (clazz, pluginAnnotation) ->
            try {
                val pluginDefinition = deployPluginDefinition(
                    PluginDefinition(pluginAnnotation.key, pluginAnnotation.title, pluginAnnotation.description, clazz.name)
                )

                val actions = findPluginActions(clazz)
                actions.forEach { (method, actionAnnotation) ->
                    deployActionDefinition(
                        PluginActionDefinition(
                            PluginActionDefinitionId(
                                actionAnnotation.key,
                                pluginDefinition
                            ),
                            actionAnnotation.title,
                            actionAnnotation.description,
                            method.name,
                            actionAnnotation.activityTypes.toList()
                        )
                    )
                }
            } catch (e: Exception) {
                throw PluginDefinitionNotDeployedException(pluginAnnotation.key, clazz.name, e)
            }
        }
    }

    private fun findPluginClasses() : Map<Class<*>, Plugin> {
        return pluginDefinitionResolver.findPluginClasses()
    }

    private fun deployPluginDefinition(pluginDefinition: PluginDefinition): PluginDefinition {
        logger.info { "Deploying plugin $pluginDefinition.key" }
        return pluginDefinitionRepository.save(pluginDefinition)
    }

    private fun findPluginActions(pluginClass: Class<*>) : Map<Method, PluginAction> {
        return pluginClass.methods.filter { method ->
            method.isAnnotationPresent(PluginAction::class.java)
        }.associate { method ->
            method to method.getAnnotation(PluginAction::class.java)
        }
    }

    private fun deployActionDefinition(pluginActionDefinition: PluginActionDefinition): PluginActionDefinition {
        logger.debug { "Deploying action ${pluginActionDefinition.id.key} for plugin ${pluginActionDefinition.id.pluginDefinition.key}" }
        return pluginActionDefinitionRepository.save(pluginActionDefinition)
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}