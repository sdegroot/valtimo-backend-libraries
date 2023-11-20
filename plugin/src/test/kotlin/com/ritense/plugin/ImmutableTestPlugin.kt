/*
 * Copyright 2015-2023 Ritense BV, the Netherlands.
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
import com.ritense.plugin.annotation.PluginActionProperty
import com.ritense.plugin.annotation.PluginEvent
import com.ritense.plugin.annotation.PluginProperties
import com.ritense.plugin.domain.ActivityType
import com.ritense.plugin.domain.ActivityType.SERVICE_TASK_START
import com.ritense.plugin.domain.EventType
import java.net.URI

@Plugin(
    key = "immutable-test-plugin",
    title = "Immutable test plugin",
    description = "This is a test plugin used to verify plugin framework functionality"
)
class ImmutableTestPlugin(
    val someObject: String,
    @PluginProperties val properties: ImmutableTestPluginProperties
) : TestPluginParent(), TestPluginInterface {

    @PluginAction(
        key = "test-action",
        title = "Test action",
        description = "This is an action used to verify plugin framework functionality",
        activityTypes = [SERVICE_TASK_START]
    )
    fun testAction() {
        //do nothing
    }

    @PluginAction(
        key = "test-action-task",
        title = "Test action task",
        description = "This is an action used to verify plugin framework functionality",
        activityTypes = [ActivityType.USER_TASK_CREATE]
    )
    fun testActionTask() {
        //do nothing
    }

    @PluginAction(
        key = "other-test-action",
        title = "Test action 2",
        description = "This is an action used to test method overloading",
        activityTypes = [SERVICE_TASK_START]
    )
    fun testAction(@PluginActionProperty someString: String): String {
        return someString
    }

    @PluginAction(
        key = "test-action-with-uri-parameter",
        title = "Test action with uri parameter",
        description = "This is an action used to test having an uri as a parameter",
        activityTypes = [SERVICE_TASK_START]
    )
    fun testActionWithUriParameter(@PluginActionProperty uriParam: URI): URI {
        return uriParam
    }

    @PluginAction(
        key = "child-override-test-action",
        title = "Override test action",
        description = "This is an action used to test method inheritance",
        activityTypes = []
    )
    override fun overrideAction() {
        //do nothing
    }

    override fun interfaceAction() {
        //do nothing
    }

    private fun shouldNotBeDeployed() {
        //meant to test correct deployment of only methods annotated correctly
    }

    fun shouldAlsoNotBeDeployed() {
        //meant to test correct deployment of only methods annotated correctly
    }

    @PluginEvent([EventType.CREATE])
    fun shouldRunOnCreate() {
        //meant to test correct invocation of plugin event
    }

    @PluginEvent([EventType.CREATE, EventType.DELETE])
    fun shouldRunOnCreateAndDelete() {
        //meant to test correct multiple invocation of plugin event
    }

    @PluginEvent([EventType.UPDATE])
    fun shouldRunOnUpdate() {
        //meant to test correct invocation of plugin event
    }

    @PluginEvent([EventType.DELETE])
    fun shouldRunOnDelete() {
        //meant to test correct invocation of plugin event
    }
}
