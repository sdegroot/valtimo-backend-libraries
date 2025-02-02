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

package com.ritense.valtimo.camunda.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.camunda.bpm.engine.impl.persistence.entity.SuspensionState
import org.camunda.bpm.engine.task.DelegationState
import java.time.LocalDateTime

@Entity
@Table(name = "ACT_RU_TASK")
class CamundaTask(

    @Id
    @Column(name = "ID_")
    val id: String,

    @Column(name = "REV_")
    val revision: Int,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EXECUTION_ID_")
    val execution: CamundaExecution?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROC_INST_ID_")
    val processInstance: CamundaExecution?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROC_DEF_ID_")
    val processDefinition: CamundaProcessDefinition?,

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    val identityLinks: List<CamundaIdentityLink> = emptyList(),

    @Column(name = "CASE_EXECUTION_ID_")
    val caseExecutionId: String?,

    @Column(name = "CASE_INST_ID_")
    val caseInstanceId: String?,

    @Column(name = "CASE_DEF_ID_")
    val caseDefinitionId: String?,

    @Column(name = "NAME_")
    val name: String?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_TASK_ID_")
    val parentTask: CamundaTask?,

    @Column(name = "DESCRIPTION_")
    val description: String?,

    @Column(name = "TASK_DEF_KEY_")
    val taskDefinitionKey: String?,

    @Column(name = "OWNER_")
    val owner: String?,

    @Column(name = "ASSIGNEE_")
    val assignee: String?,

    @Column(name = "DELEGATION_")
    @Enumerated(EnumType.STRING)
    val delegationState: DelegationState?,

    @Column(name = "PRIORITY_")
    val priority: Int,

    @Column(name = "CREATE_TIME_")
    val createTime: LocalDateTime?,

    @Column(name = "LAST_UPDATED_")
    val lastUpdated: LocalDateTime?,

    @Column(name = "DUE_DATE_")
    val dueDate: LocalDateTime?,

    @Column(name = "FOLLOW_UP_DATE_")
    val followUpDate: LocalDateTime?,

    @Column(name = "SUSPENSION_STATE_")
    val suspensionState: Int,

    @Column(name = "TENANT_ID_")
    val tenantId: String?,

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    val variableInstances: Set<CamundaVariableInstance>
): CamundaVariableScope() {

    fun isSuspended() = suspensionState == SuspensionState.SUSPENDED.stateCode

    @Transient
    fun getProcessDefinitionId() = processDefinition!!.id

    @Transient
    fun getProcessInstanceId() = processInstance!!.id

    override fun getVariableInstancesLocal(): Collection<CamundaVariableInstance> = variableInstances

    override fun getParentVariableScope(): CamundaVariableScope? = parentTask?:execution

    override fun getVariableScopeKey() = "task"
}