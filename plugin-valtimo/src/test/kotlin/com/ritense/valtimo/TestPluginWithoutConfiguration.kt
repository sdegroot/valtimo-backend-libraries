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

package com.ritense.valtimo

import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginAction
import com.ritense.processlink.domain.ActivityTypeWithEventName.USER_TASK_CREATE

@Plugin(
    key = "test-plugin-without-configuration",
    title = "Test plugin",
    description = "This is a test plugin only available in tests and doesn't have any plugin configurations"
)
class TestPluginWithoutConfiguration {

    @PluginAction(
        key = "test-action-on-user-task",
        title = "Test action",
        description = "This is a test action",
        activityTypes = [USER_TASK_CREATE],
    )
    fun testActionOnUserTask() {
        //do nothing
    }
}
