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

package com.ritense.valtimo.camunda.authorization

import com.ritense.authorization.AuthorizationContext.Companion.runWithoutAuthorization
import com.ritense.authorization.permission.Permission
import com.ritense.authorization.request.AuthorizationRequest
import com.ritense.authorization.specification.AuthorizationSpecification
import com.ritense.valtimo.camunda.domain.CamundaTask
import com.ritense.valtimo.contract.database.QueryDialectHelper
import com.ritense.valtimo.service.CamundaTaskService
import jakarta.persistence.criteria.AbstractQuery
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root

class CamundaTaskSpecification(
        authRequest: AuthorizationRequest<CamundaTask>,
        permissions: List<Permission>,
        private val taskService: CamundaTaskService,
        private val queryDialectHelper: QueryDialectHelper
) : AuthorizationSpecification<CamundaTask>(authRequest, permissions) {
    override fun toPredicate(
        root: Root<CamundaTask>,
        query: AbstractQuery<*>,
        criteriaBuilder: CriteriaBuilder
    ): Predicate {
        val predicates = permissions
            .filter { permission ->
                CamundaTask::class.java == permission.resourceType
                    && authRequest.action == permission.action
            }
            .map { permission ->
                permission.toPredicate(
                    root,
                    query,
                    criteriaBuilder,
                    authRequest.resourceType,
                    queryDialectHelper
                )
            }
        return combinePredicates(criteriaBuilder, predicates)
    }

    override fun identifierToEntity(identifier: String): CamundaTask {
        return runWithoutAuthorization {
            taskService.findTaskById(identifier)
        }
    }
}

