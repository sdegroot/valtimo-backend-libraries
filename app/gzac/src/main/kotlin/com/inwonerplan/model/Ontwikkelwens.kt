/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.inwonerplan.model


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param id 
 * @param code 
 * @param naam 
 * @param omschrijving 
 * @param actief 
 */


data class Ontwikkelwens (

    @Json(name = "id")
    val id: java.util.UUID? = null,

    @Json(name = "code")
    val code: kotlin.String? = null,

    @Json(name = "naam")
    val naam: kotlin.String? = null,

    @Json(name = "omschrijving")
    val omschrijving: kotlin.String? = null,

    @Json(name = "actief")
    val actief: kotlin.Boolean? = null

)

