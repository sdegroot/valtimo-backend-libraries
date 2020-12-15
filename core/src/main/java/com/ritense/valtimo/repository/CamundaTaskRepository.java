/*
 * Copyright 2015-2020 Ritense BV, the Netherlands.
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

package com.ritense.valtimo.repository;

import com.ritense.valtimo.repository.camunda.dto.TaskExtended;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.camunda.bpm.engine.impl.Direction;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.TaskQueryProperty;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CamundaTaskRepository {

    private final SqlSession session;

    public Page<TaskExtended> findTasks(Pageable pageable, Map<String, Object> parameters) {
        var query = new ListQueryParameterObject(
            parameters,
            pageable.getPageNumber() * pageable.getPageSize(),
            pageable.getPageSize()
        );
        query.setOrderingProperties(
            Collections.singletonList(new QueryOrderingProperty(TaskQueryProperty.CREATE_TIME, Direction.DESCENDING))
        );
        List<TaskExtended> taskWithVariables = session.selectList(
            "com.ritense.valtimo.mapper.findTasks",
            query
        );
        Long taskCount = session.selectOne(
            "com.ritense.valtimo.mapper.findTasksCount",
            query
        );
        return new PageImpl<>(taskWithVariables, pageable, taskCount);
    }

}