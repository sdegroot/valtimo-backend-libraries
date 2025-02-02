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

package com.ritense.form.service

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.authorization.AuthorizationContext.Companion.runWithoutAuthorization
import com.ritense.document.domain.Document
import com.ritense.document.domain.patch.JsonPatchFilterFlag
import com.ritense.document.domain.patch.JsonPatchService
import com.ritense.document.service.DocumentService
import com.ritense.form.domain.FormIoFormDefinition
import com.ritense.form.service.impl.FormIoFormDefinitionService
import com.ritense.processdocument.service.ProcessDocumentAssociationService
import com.ritense.valtimo.camunda.domain.CamundaExecution
import com.ritense.valtimo.contract.form.DataResolvingContext
import com.ritense.valtimo.contract.form.FormFieldDataResolver
import com.ritense.valtimo.contract.json.JsonPointerHelper
import com.ritense.valtimo.contract.json.patch.JsonPatch
import com.ritense.valtimo.contract.json.patch.JsonPatchBuilder
import com.ritense.valtimo.service.CamundaProcessService
import com.ritense.valtimo.service.CamundaTaskService
import com.ritense.valueresolver.ValueResolverService
import org.springframework.transaction.annotation.Transactional
import java.util.UUID


@Transactional
class PrefillFormService(
    private val documentService: DocumentService,
    private val formDefinitionService: FormIoFormDefinitionService,
    private val camundaProcessService: CamundaProcessService,
    private val taskService: CamundaTaskService,
    private val formFieldDataResolvers: List<FormFieldDataResolver>,
    private val processDocumentAssociationService: ProcessDocumentAssociationService,
    private val valueResolverService: ValueResolverService,
    private val objectMapper: ObjectMapper
) {

    fun getPrefilledFormDefinition(
        formDefinitionId: UUID,
        processInstanceId: String,
        taskInstanceId: String,
    ): FormIoFormDefinition {
        val processInstance = runWithoutAuthorization {
            camundaProcessService.findExecutionByProcessInstanceId(processInstanceId)
                ?: throw RuntimeException("Process instance not found by id $processInstanceId")
        }
        val documentId = processInstance.businessKey
            ?: throw RuntimeException("Process instance with id $processInstanceId has no businessKey")
        val document = runWithoutAuthorization { documentService.get(documentId) }
        val formDefinition = formDefinitionService.getFormDefinitionById(formDefinitionId)
            .orElseThrow { RuntimeException("Form definition not found by id $formDefinitionId") }
        prefillFormDefinition(formDefinition, document, processInstanceId, taskInstanceId)
        return formDefinition
    }

    fun getPrefilledFormDefinition(
        formDefinitionId: UUID,
        documentId: UUID?,
    ): FormIoFormDefinition {
        val formDefinition = formDefinitionService.getFormDefinitionById(formDefinitionId)
            .orElseThrow { RuntimeException("Form definition not found by id $formDefinitionId") }
        if (documentId != null) {
            val document = runWithoutAuthorization { documentService.get(documentId.toString()) }
            prefillFormDefinition(formDefinition, document, null, null)
        }
        return formDefinition
    }

    fun prefillFormDefinition(
        formDefinition: FormIoFormDefinition,
        document: Document,
        processInstanceId: String?,
        taskInstanceId: String? = null,
    ) {
        val processInstance = processInstanceId?.let {
            runWithoutAuthorization {
                camundaProcessService.findExecutionByProcessInstanceId(processInstanceId)
                    ?: throw RuntimeException("Process instance not found by id $processInstanceId")
            }
        }

        prefillValueResolverFields(formDefinition, document.id(), processInstance, taskInstanceId)

        val extendedDocumentContent = document.content().asJson() as ObjectNode

        val documentMetadata = buildMetaDataObject(document)
        extendedDocumentContent.set<JsonNode>("metadata", documentMetadata)

        prefillDataResolverFields(formDefinition, document, extendedDocumentContent)

        if (taskInstanceId != null) {
            prefillTaskVariables(formDefinition, taskInstanceId, extendedDocumentContent)
        } else {
            prefillProcessVariables(formDefinition, document)
        }
    }

    private fun prefillValueResolverFields(
        formDefinition: FormIoFormDefinition,
        documentInstanceId: Document.Id,
        processInstance: CamundaExecution?,
        taskInstanceId: String?
    ) {
        // Map input fields to Map<{input.key}, {input.properties.sourceKey}>
        val inputSourceKeyMap = formDefinition.inputFields
            .filter { FormIoFormDefinition.HAS_PREFILL_ENABLED.test(it) }
            .mapNotNull {
                val inputKey = FormIoFormDefinition.getKey(it)
                val sourceKey = FormIoFormDefinition.getSourceKey(it)
                if (inputKey.isPresent && sourceKey.isPresent) {
                    Pair(inputKey.get(), sourceKey.get())
                } else {
                    null
                }
            }
            .toMap()
            .filter { valueResolverService.supportsValue(it.value) }

        // Resolve sourceKeys into a Map<{sourceKey}, {dataValue}>
        val valueMap = runWithoutAuthorization {
            if (taskInstanceId != null) {
                val task = taskService.findTaskById(taskInstanceId)
                valueResolverService.resolveValues(task.getProcessInstanceId(), task, inputSourceKeyMap.values)
            } else if (processInstance != null) {
                valueResolverService.resolveValues(processInstance.id, processInstance, inputSourceKeyMap.values)
            } else {
                valueResolverService.resolveValues(documentInstanceId.toString(), inputSourceKeyMap.values)
            }
        }

        // Create a Map<{input.key}, {resolvedValue}>
        val keyValueMap = inputSourceKeyMap.entries.mapNotNull { entry ->
            valueMap[entry.value]?.let { resolvedValue -> Pair(entry.key, resolvedValue) }
        }.toMap()

        formDefinition.preFill(keyValueMap)
    }

    private fun prefillProcessVariables(formDefinition: FormIoFormDefinition, document: Document) {
        val processVarsNames = formDefinition.extractProcessVarNames()
        val processInstanceVariables = runWithoutAuthorization {
            processDocumentAssociationService.findProcessDocumentInstances(document.id())
                .map { it.processDocumentInstanceId().processInstanceId().toString() }
                .flatMap { camundaProcessService.getProcessInstanceVariables(it, processVarsNames).entries }
                .associate { it.key to it.value }
        }
        if (processInstanceVariables.isNotEmpty()) {
            formDefinition.preFillWith(FormIoFormDefinition.PROCESS_VAR_PREFIX, processInstanceVariables)
        }
    }

    private fun prefillDataResolverFields(
        formDefinition: FormIoFormDefinition,
        document: Document,
        extendedDocumentContent: JsonNode?
    ) {
        // FormFieldDataResolver pre-filling
        val dataResolvingContext = DataResolvingContext(
            document.definitionId().name(),
            document.id().id,
            formDefinition.formDefinition
        )
        formDefinition
            .buildExternalFormFieldsMap()
            .forEach { (externalFormFieldType, externalContentItems) ->
                formFieldDataResolvers
                    .stream()
                    .filter { formFieldDataResolver -> formFieldDataResolver.supports(externalFormFieldType) }
                    .findFirst()
                    .ifPresent { formFieldDataResolver ->
                        val varNames = externalContentItems.stream()
                            .map { it.name }
                            .toList()
                            .toTypedArray()
                        val externalDataMap = formFieldDataResolver.get(dataResolvingContext, *varNames)

                        // TODO: remove support for legacy separator type and clean up code
                        //support new notation prefix:some-expression
                        val dataNode = JsonNodeFactory.instance.objectNode()
                        externalContentItems.forEach { contentItem ->
                            if (contentItem.separator == FormIoFormDefinition.EXTERNAL_FORM_FIELD_TYPE_SEPARATOR) {
                                val fieldName =
                                    externalFormFieldType + FormIoFormDefinition.EXTERNAL_FORM_FIELD_TYPE_SEPARATOR + contentItem.name
                                dataNode.set<JsonNode>(
                                    fieldName,
                                    objectMapper.valueToTree(externalDataMap[contentItem.name])
                                )
                                externalDataMap.remove(contentItem.name)
                            }
                        }
                        formDefinition.preFill(dataNode)

                        // Support old notation prefix.field-a.field-b.field-c
                        val prefillDataNode = JsonNodeFactory.instance.objectNode()
                        externalContentItems.forEach { externalContentItem ->
                            JsonPointerHelper.appendJsonPointerTo(
                                prefillDataNode,
                                externalContentItem.jsonPointer,
                                objectMapper.valueToTree(externalDataMap[externalContentItem.name])
                            )
                        }
                        formDefinition.preFill(prefillDataNode)
                    }
            }
        formDefinition.preFill(extendedDocumentContent)
    }

    private fun prefillTaskVariables(
        formDefinition: FormIoFormDefinition,
        taskInstanceId: String,
        extendedDocumentContent: JsonNode
    ) {
        val taskVariables = runWithoutAuthorization { taskService.getVariables(taskInstanceId) }
        val placeholders = objectMapper.valueToTree<ObjectNode>(taskVariables)
        formDefinition.preFillWith("pv", taskVariables)
        prePreFillTransform(formDefinition, placeholders, extendedDocumentContent)
    }

    internal fun prePreFillTransform(formDefinition: FormIoFormDefinition, placeholders: JsonNode, source: JsonNode) {
        val dataToPreFill = JsonNodeFactory.instance.objectNode()
        formDefinition.inputFields.forEach { field ->
            val container = field.at("/$CUSTOM_PROPERTIES/$CONTAINER_KEY").asText()
            if (container != null) {
                val propertyName = field[FormIoFormDefinition.PROPERTY_KEY].textValue()
                if (container.contains("/{indexOf")) {
                    val indexValueJsonPointer = getIndexValueJsonPointer(container)
                    val id = placeholders.at(indexValueJsonPointer).textValue()
                    val arrayPointer = JsonPointer.compile(container.substringBefore("/{"))
                    val list = source.at(arrayPointer) as ArrayNode //get sources array
                    val calculatedArrayItemIndex = lookupIndexForIdValue(list, id)
                    val arrayItemForSourceJsonPointer =
                        JsonPointer.compile("$arrayPointer/$calculatedArrayItemIndex/$propertyName")
                    dataToPreFill.set<JsonNode>(propertyName, source.at(arrayItemForSourceJsonPointer))
                    val customPropertiesObject = field[CUSTOM_PROPERTIES] as ObjectNode
                    customPropertiesObject.remove(CONTAINER_KEY)
                }
            }
        }
        formDefinition.preFill(dataToPreFill)
    }

    /**
     *   FormDefinition can utilize value array operation by implementing the property section.
     *   note: In the Form IO builder this is called custom property on the API tab.
     *
     *     Adding a new array item - configuration:
     *     {
     *          "label": "Bread name",
     *          "key": "name", -{@literal >} Name of the property to ADD a value to, this should match object property name of an array item.
     *          "properties": {
     *              "container": "/favorites/-/" -{@literal >} indicating an new item should be added at the end of the array
     *          },
     *          "type": "textfield",
     *          "input": true
     *     }
     *
     *     Update existing array item - configuration:
     *     {
     *          "label": "Bread name",
     *          "key": "name",  -{@literal >} Name of the property to REPLACE its value, this should match object property name of an array item.
     *          "properties": {
     *              "container": "/favorites/{indexOf(/pv/breadId)}/" -{@literal >} indicating an existing items location to be modified
     *          },
     *          "type": "textfield",
     *          "input": true
     *     },
     *
     *    Source example:
     *    {
     *      "favorites" : [
     *          { "_id" : "1", "name": "White bread"},
     *          { "_id" : "2", "name": "Pita bread"}
     *      ]
     *    }
     *
     *    Submission payload:
     *    "data":
     *      { "name" : "Focaccia" }
     *    }
     *
     *    Placeholder:
     *    "pv":
     *      { "breadId" : "2" }
     *    }
     *
     *    Results:
     *
     *    Submission will be sanitized as so:
     *    "data": {} // removed name property because patch will hold its modification
     *
     *    New array item configuration - Patch result :
     *    [
     *      {
     *          "op" : "add",
     *          "path" : "/favorites/-/name",
     *          "value" : "Focaccia"
     *      }
     *    ]
     *
     *    Update existing array item/object configuration: - Patch result
     *    [
     *      {
     *          "op" : "replace",
     *          "path" : "/favorites/1/name",
     *          "value" : "Focaccia"
     *      }
     *    ]
     *
     *   Adding a new array item:
     *      Example: /favorites/-/name
     *      Notes:
     *      - Creates a patch ADD operation for appending (last) an item to an existing array.
     *
     *   Update existing array item/object:
     *     Example usage: /favorites/{indexOf(pv/key)}/name
     *     Notes:
     *     - This will create a REPLACE patch operation of a item to a specific index of a existing array.
     *     - pv.key = the key to use as matcher in the source document array item.
     *     - If index doesnt exist it will get the last index.
     *     - The name of the id to check is fixed '_id'.
     *
     * @param formDefinition The form definition
     * @param submission The data structure to process, will be sanitized to avoid issues.
     * @param placeholders The container to retrieve the indexOf(jsonPointerValue) input var. This value is used to match against _id.
     * @param source the Json to use for determining index value of an array.
     * @return JsonPatch a patch containing patch operations for array modifications.
     */
    fun preSubmissionTransform(
        formDefinition: FormIoFormDefinition,
        submission: JsonNode,
        placeholders: JsonNode,
        source: JsonNode
    ): JsonPatch {
        val sourceJsonPatchBuilder = JsonPatchBuilder()
        val submissionJsonPatchBuilder = JsonPatchBuilder()
        formDefinition.inputFields.forEach { field ->
            val container = field.at("/$CUSTOM_PROPERTIES/$CONTAINER_KEY").asText()
            if (container != null
                && submission.has(field[FormIoFormDefinition.PROPERTY_KEY].textValue())
            ) {
                val propertyName = field[FormIoFormDefinition.PROPERTY_KEY].textValue()
                val propertyValue = submission.at("/$propertyName")
                val submissionProperty =
                    JsonPointer.valueOf("/$propertyName")
                if (container.contains("/{indexOf")) {
                    val indexValueJsonPointer = getIndexValueJsonPointer(container)
                    val id = placeholders.at(indexValueJsonPointer).textValue()
                    val arrayPointer = JsonPointer.compile(container.substringBefore("/{"))
                    val list = source.at(arrayPointer) as ArrayNode //get sources array
                    val calculatedArrayItemIndex = lookupIndexForIdValue(list, id)
                    val arrayItemForSourceJsonPointer =
                        JsonPointer.compile("$arrayPointer/$calculatedArrayItemIndex/$propertyName")
                    sourceJsonPatchBuilder.replace(arrayItemForSourceJsonPointer, propertyValue)
                    submissionJsonPatchBuilder.remove(submissionProperty)
                } else if (container.contains("/-/")) {
                    val arrayPointer = JsonPointer.compile(container.substringBefore("/-"))
                    val array = source.at(arrayPointer)
                    if (array.isMissingNode) {
                        sourceJsonPatchBuilder.add(arrayPointer, JsonNodeFactory.instance.arrayNode())
                    }

                    //ensure object exist in array
                    val itemPointer =
                        arrayPointer.appendIndex(array.size()) //array.size returns 0 for MissingNode
                    sourceJsonPatchBuilder.add(itemPointer, JsonNodeFactory.instance.objectNode())
                    //Add actual item to its position
                    sourceJsonPatchBuilder.add(itemPointer.appendProperty(propertyName), propertyValue)
                    submissionJsonPatchBuilder.remove(submissionProperty)
                }
            }
        }
        //Cleaning submission to avoid issues when running diff.
        val submissionPatch = submissionJsonPatchBuilder.build()
        if (submissionPatch.patches().isNotEmpty()) {
            JsonPatchService.apply(submissionPatch, submission, JsonPatchFilterFlag.allowRemovalOperations())
        }
        return sourceJsonPatchBuilder.build()
    }

    private fun getIndexValueJsonPointer(container: String) = container.substringAfter("(").substringBefore(")")

    private fun lookupIndexForIdValue(list: ArrayNode, id: String) =
        list.indexOfFirst { item -> item[ID_KEY].textValue().equals(id, ignoreCase = true) }.toString()

    private fun buildMetaDataObject(document: Document): ObjectNode {
        val metaDataNode = JsonNodeFactory.instance.objectNode()
        metaDataNode.put("id", document.id().toString())
        metaDataNode.put("createdOn", document.createdOn().toString())
        metaDataNode.put("createdBy", document.createdBy())
        metaDataNode.put("modifiedOn", document.modifiedOn().toString())
        metaDataNode.put("sequence", document.sequence())
        return metaDataNode
    }

    companion object {
        private const val CUSTOM_PROPERTIES = "properties"
        private const val CONTAINER_KEY = "container"
        private const val ID_KEY = "_id"
    }

}
