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

package com.ritense.valtimo.contract.authentication;

import com.ritense.valtimo.contract.authentication.model.SearchByUserGroupsCriteria;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserManagementService {

    ManageableUser createUser(ManageableUser user);

    ManageableUser updateUser(ManageableUser user);

    void deleteUser(String userId);

    boolean resendVerificationEmail(String userId);

    void activateUser(String userId);

    void deactivateUser(String userId);

    Page<ManageableUser> getAllUsers(Pageable pageable);

    List<ManageableUser> getAllUsers();

    Page<ManageableUser> queryUsers(String searchTerm, Pageable pageable);

    Optional<ManageableUser> findByEmail(String email);

    Optional<NamedUser> findNamedUserByEmail(String email);

    ManageableUser findById(String userId);

    List<ManageableUser> findByRole(String authority);

    List<ManageableUser> findByRoles(SearchByUserGroupsCriteria groupsCriteria);

    List<NamedUser> findNamedUserByRoles(Set<String> roles);

    default ManageableUser getCurrentUser() {
        throw new NotImplementedException("Failed to get current user because method is not implemented.");
    }

    default String getCurrentUserId() {
        throw new NotImplementedException("Failed to get current user ID because method is not implemented.");
    }
}
