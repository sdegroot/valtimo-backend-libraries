/*
 * Copyright 2020 Dimpact.
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

package com.ritense.openzaak.domain.request

import java.net.URI

@Deprecated("Since 12.0.0. Use CreateZaakTypeLinkRequest in zaken-api module instead")
data class CreateZaakTypeLinkRequest(
    val documentDefinitionName: String,
    val zaakTypeUrl: URI,
    val createWithDossier: Boolean? = false
)