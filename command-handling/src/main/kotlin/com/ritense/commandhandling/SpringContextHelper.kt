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

package com.ritense.commandhandling

import mu.KotlinLogging
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class SpringContextHelper : ApplicationContextAware {

    override fun setApplicationContext(context: ApplicationContext) {
        // Store ApplicationContext reference to access required beans later on.
        Companion.context = context
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private var context: ApplicationContext? = null

        /**
         * Returns the Spring managed bean instance of the given class type if it exists.
         * Returns exception otherwise.
         * @param beanClass
         * @return Object
         */
        fun <T : Any?> getBean(beanClass: Class<T>): T {
            logger.trace { "Retrieving bean $beanClass" }
            return context?.getBean(beanClass) ?: throw IllegalStateException("Cannot getBean $beanClass").also {
                logger.error { it.message }
            }
        }

    }

}