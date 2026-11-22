package io.zenwave360.language.zfl.lint

import io.zenwave360.language.source.SourceRef
import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zfl.semantic.Severity
import io.zenwave360.language.zfl.semantic.ZflCallStep
import io.zenwave360.language.zfl.semantic.ZflCommand
import io.zenwave360.language.zfl.semantic.ZflEndVariant
import io.zenwave360.language.zfl.semantic.ZflFlow
import io.zenwave360.language.zfl.semantic.ZflSemanticDiagnostic
import io.zenwave360.language.zfl.semantic.ZflSemanticModel
import io.zenwave360.language.zfl.semantic.ZflView

/**
 * Cross-file ZFL linter: checks a flow's view, timers and expected end states against the ZDLs of its systems.
 * The caller parses the ZDLs and maps each system name of the ZFL to its model. Every finding is a warning,
 * except an INFO for a system without a ZDL.
 */
class ZflZdlLinter {

    fun lint(model: ZflSemanticModel, zdls: Map<String, ZdlModel>): List<ZflSemanticDiagnostic> {
        val result = mutableListOf<ZflSemanticDiagnostic>()
        val usedSystems = model.systems.keys + model.flows.flatMap { flow -> flow.commands.mapNotNull { it.system } }
        usedSystems.filterNot(zdls::containsKey).distinct().forEach {
            result += ZflSemanticDiagnostic("System '$it' has no ZDL: its aggregates, events and commands are not checked.", Severity.INFO, null)
        }
        val index = ZdlIndex(zdls)
        model.flows.forEach { flow -> result += FlowLint(flow, index).run() }
        return result
    }

    private class FlowLint(val flow: ZflFlow, val zdl: ZdlIndex) {
        private val result = mutableListOf<ZflSemanticDiagnostic>()
        private val view: ZflView? = flow.view

        fun run(): List<ZflSemanticDiagnostic> {
            view?.let(::checkView)
            checkCorrelation()
            checkStartFields()
            checkTimers()
            checkEndStates()
            return result
        }

        /** each member is an aggregate of a ZDL; each path exists and has the type of the key. */
        private fun checkView(view: ZflView) {
            val key = zdl.entity(view.keyAggregate)?.let { (model, entity) -> resolve(model, entity, view.keyPath) }
            when {
                key == null -> Unit
                key.missing != null -> warn("Key '${view.keyAggregate}.${view.keyPath.joinToString(".")}' of view '${view.name}' does not exist: ${key.missing}.", view.sourceRef)
                !key.unique -> warn("Key '${view.keyAggregate}.${view.keyPath.joinToString(".")}' of view '${view.name}' is not unique.", view.sourceRef)
            }
            view.members.forEach { member ->
                val found = zdl.entity(member.aggregate)
                if (found == null) {
                    warn("Aggregate '${member.aggregate}' of view '${view.name}' is in no ZDL of the flow's systems.", member.sourceRef)
                    return@forEach
                }
                if (member.path.isEmpty()) return@forEach
                val path = "${member.aggregate}.${member.path.joinToString(".")}"
                val field = resolve(found.first, found.second, member.path)
                when {
                    field.missing != null -> warn("Path '$path' of view '${view.name}' does not exist: ${field.missing}.", member.sourceRef)
                    key != null && key.missing == null && !sameType(key.type, field.type) ->
                        warn("Path '$path' of view '${view.name}' is ${field.type}, but the key is ${key.type}.", member.sourceRef)
                    !member.many && !field.unique ->
                        warn("'$path' is not unique, so there may be several ${member.aggregate} per key: write '${member.aggregate}[]'.", member.sourceRef)
                }
            }
        }

        /** an event correlates by the key, or by `<member>Id` resolved through the member's path. */
        private fun checkCorrelation() {
            val view = view ?: return
            val names = correlationFields(view)
            val starts = flow.starts.map { it.name }.toSet()
            // a call response that nothing publishes is not an event of the flow: `emits response E` is published
            val published = flow.commands.flatMap { it.publishedEvents() }.toSet()
            val responses = flow.commands.flatMap { it.responses }.toSet() - published
            val used = (flow.events.map { it.name } + flow.policies.flatMap { it.triggers } +
                flow.timers.flatMap { listOfNotNull(it.after) + it.unless } + flow.end.endOutcomes.values.flatten())
                .distinct().filterNot { it in starts || it in responses }
            used.forEach { name ->
                val event = zdl.event(name)
                when {
                    event == null -> warn("Event '$name' cannot be correlated: no ZDL of the flow's systems declares it.", flow.sourceRef)
                    names.none { it in fields(event) } -> warn(
                        "Event '$name' cannot be correlated with view '${view.name}': it carries none of ${names.joinToString(", ") { "'$it'" }}.",
                        flow.sourceRef,
                    )
                }
            }
        }

        private fun correlationFields(view: ZflView): List<String> = buildList {
            if (view.keyPath.size == 1 && view.keyField != "id") add(view.keyField)
            add(lowerFirst(view.keyAggregate) + "Id")
            view.members.filter { it.aggregate != view.keyAggregate }.forEach { add(lowerFirst(it.aggregate) + "Id") }
        }.distinct()

        /**
         * a `start` field that a later command takes as input reaches it, in the triggering event or in
         * a view aggregate. Otherwise it is lost after the command that emitted that event.
         */
        private fun checkStartFields() {
            val startFields = flow.starts.flatMap { it.fields.keys }.toSet()
            if (startFields.isEmpty()) return
            val viewFields = view?.members.orEmpty().flatMap { member -> zdl.entity(member.aggregate)?.second?.let(::fields).orEmpty() }.toSet()
            val triggered = flow.policies.flatMap { policy -> policy.triggers.map { policy.command to it } } +
                flow.timers.map { it.command to it.after }
            val reported = mutableSetOf<String>()
            triggered.forEach { (commandName, trigger) ->
                val command = flow.commands.firstOrNull { it.name == commandName } ?: return@forEach
                val inputFields = zdl.inputFields(command.system, command.name) ?: return@forEach
                inputFields.filter { it in startFields && it !in viewFields }.forEach { field ->
                    // a start carries its own fields; an event the ZDLs do not declare is reported by the correlation check
                    val carried = when {
                        trigger == null -> false
                        flow.starts.any { it.name == trigger } -> true
                        else -> zdl.event(trigger)?.let { field in fields(it) } ?: true
                    }
                    if (carried || !reported.add("$field/$trigger/$commandName")) return@forEach
                    val emitters = flow.commands.filter { trigger != null && trigger in it.publishedEvents() }.map { it.name }
                    val after = emitters.ifEmpty { listOfNotNull(trigger) }.joinToString("', '")
                    val source = trigger?.let { "'$it' does not carry it" } ?: "the timer carries no event"
                    val where = view?.let { "no aggregate of view '${it.name}' has it" } ?: "the flow has no view"
                    warn("Start field '$field' is lost after '$after': '${command.name}' needs it, but $source and $where.", command.sourceRef)
                }
            }
        }

        /** `at` names a date or time field of a view member. */
        private fun checkTimers() {
            flow.timers.forEach { timer ->
                val at = timer.at ?: return@forEach
                val segments = at.split('.')
                if (view?.member(segments.first()) == null) return@forEach
                val (model, entity) = zdl.entity(segments.first()) ?: return@forEach
                val field = resolve(model, entity, segments.drop(1))
                when {
                    field.missing != null -> warn("Timer of '${timer.command}' fires at '$at', which does not exist: ${field.missing}.", timer.sourceRef)
                    field.type !in DATE_TIME_TYPES -> warn("Timer of '${timer.command}' fires at '$at', which is ${field.type}, not a date or time.", timer.sourceRef)
                }
            }
        }

        /** an expected state is a state of the aggregate's `@lifecycle` that some command reaches, or `none`. */
        private fun checkEndStates() {
            val view = view ?: return
            flow.end.outcomes.forEach { outcome ->
                outcome.variants.forEach { variant ->
                    variant.expectedStates.forEach { (aggregate, states) ->
                        if (view.member(aggregate) == null) return@forEach
                        val (model, entity) = zdl.entity(aggregate) ?: return@forEach
                        val lifecycle = zdl.lifecycle(model, entity)
                        states.filter { it != ZflEndVariant.NONE }.forEach { state ->
                            when {
                                lifecycle == null -> warn("Outcome '${outcome.name}' expects '$aggregate' to be $state, but '$aggregate' has no @lifecycle.", variant.sourceRef)
                                state !in lifecycle.states -> warn(
                                    "Outcome '${outcome.name}' expects '$aggregate' to be $state, which is not a state of its lifecycle (${lifecycle.states.joinToString(", ")}).",
                                    variant.sourceRef,
                                )
                                state !in lifecycle.reached -> warn(
                                    "Outcome '${outcome.name}' expects '$aggregate' to be $state, but no command of its ZDL transitions to $state.",
                                    variant.sourceRef,
                                )
                            }
                        }
                    }
                }
            }
        }

        private fun warn(message: String, sourceRef: SourceRef?) {
            result += ZflSemanticDiagnostic(message, Severity.WARNING, sourceRef)
        }
    }

    /** The type found at the end of a field path, or why the path does not exist. */
    private data class ResolvedField(val type: String? = null, val unique: Boolean = false, val missing: String? = null)

    private class Lifecycle(val states: Set<String>, val reached: Set<String>)

    /** Lookups over the ZDLs of every system, by aggregate, event and command name. */
    private class ZdlIndex(val zdls: Map<String, ZdlModel>) {

        fun entity(name: String): Pair<ZdlModel, Map<String, Any?>>? =
            zdls.values.firstNotNullOfOrNull { model -> model.getEntities().map(name)?.let { model to it } }

        fun event(name: String): Map<String, Any?>? =
            zdls.values.firstNotNullOfOrNull { it.getEvents().map(name) }

        fun inputFields(system: String?, command: String): List<String>? {
            val models = listOfNotNull(system?.let(zdls::get)) + zdls.values
            models.forEach { model ->
                val method = (model["services"] as? Map<*, *>).orEmpty().values
                    .firstNotNullOfOrNull { (((it as? Map<*, *>)?.get("methods")) as? Map<*, *>)?.get(command) as? Map<*, *> }
                    ?: return@forEach
                val parameter = method["parameter"] as? String ?: return emptyList()
                val type = model.getInputs().map(parameter) ?: model.getEntities().map(parameter) ?: return emptyList()
                return fields(type)
            }
            return null
        }

        fun lifecycle(model: ZdlModel, entity: Map<String, Any?>): Lifecycle? {
            val lifecycle = (entity["lifecycle"] ?: (entity["options"] as? Map<*, *>)?.get("lifecycle")) as? Map<*, *> ?: return null
            val field = lifecycle["field"] as? String ?: return null
            val enumName = (entity.fieldMap()[field] as? Map<*, *>)?.get("type") as? String ?: return null
            val states = ((model.getEnums().map(enumName))?.get("values") as? Map<*, *>)?.keys?.map { it.toString() }?.toSet() ?: return null
            val name = entity["name"]
            val reached = mutableSetOf<String>()
            (lifecycle["initial"] as? String)?.let { reached += it }
            (model["services"] as? Map<*, *>).orEmpty().values.mapNotNull { it as? Map<*, *> }
                .filter { name in (it["aggregates"] as? List<*>).orEmpty() }
                .flatMap { ((it["methods"] as? Map<*, *>).orEmpty()).values.mapNotNull { m -> m as? Map<*, *> } }
                .forEach { method ->
                    val transitions = (method["optionsList"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                        .filter { it["name"] == "transition" }.mapNotNull { it["value"] as? Map<*, *> } +
                        listOfNotNull(method["transition"] as? Map<*, *>)
                    transitions.forEach { reached += states(it["to"]) }
                }
            return Lifecycle(states, reached)
        }

        private fun states(value: Any?): List<String> = when (value) {
            is List<*> -> value.map { it.toString() }
            null -> emptyList()
            else -> listOf(value.toString())
        }
    }

    private companion object {
        val DATE_TIME_TYPES = setOf("Instant", "LocalDate", "LocalDateTime", "ZonedDateTime", "OffsetDateTime", "Date")

        @Suppress("UNCHECKED_CAST")
        fun MutableMap<String, Any?>.map(name: String): Map<String, Any?>? = this[name] as? Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        fun Map<String, Any?>.fieldMap(): Map<String, Any?> = this["fields"] as? Map<String, Any?> ?: emptyMap()

        fun fields(type: Map<String, Any?>): List<String> = type.fieldMap().keys.toList()

        /**
         * Follows [path] from [entity] through its fields and nested types. `id` is the implicit identifier of an
         * entity or aggregate, not of an input, output or event: present, unique, and of a type that the ZDL leaves to the generator.
         */
        fun resolve(model: ZdlModel, entity: Map<String, Any?>, path: List<String>): ResolvedField {
            var current: Map<String, Any?> = entity
            var isEntity = true
            path.forEachIndexed { index, segment ->
                val field = current.fieldMap()[segment] as? Map<*, *>
                if (field == null) {
                    if (segment == "id" && index == path.lastIndex && isEntity) return ResolvedField(type = IMPLICIT_ID, unique = true)
                    return ResolvedField(missing = "'${current["name"]}' has no field '$segment'")
                }
                val type = field["type"] as? String
                if (index == path.lastIndex) {
                    val unique = (field["validations"] as? Map<*, *>)?.containsKey("unique") == true
                    return ResolvedField(type = type + if (field["isArray"] == true) "[]" else "", unique = unique)
                }
                val entityType = type?.let { model.getEntities().map(it) }
                isEntity = entityType != null
                current = type?.let { entityType ?: model.getInputs().map(it) ?: model.getOutputs().map(it) ?: model.getEvents().map(it) }
                    ?: return ResolvedField(missing = "'$segment' of '${current["name"]}' is $type, which has no fields")
            }
            return ResolvedField(missing = "the path is empty")
        }

        const val IMPLICIT_ID = "the implicit id"

        fun sameType(a: String?, b: String?) = a == IMPLICIT_ID || b == IMPLICIT_ID || a == b

        fun lowerFirst(name: String) = name.replaceFirstChar { it.lowercase() }

        fun ZflCommand.publishedEvents(): Set<String> =
            (emits.map { it.eventName } + steps.filterIsInstance<ZflCallStep>().flatMap { it.emittedEvents() }).toSet()

        fun ZflCallStep.emittedEvents(): List<String> =
            handlers.flatMap { handler -> handler.signal?.takeIf { it.emits }?.events.orEmpty() + handler.chainedCall?.emittedEvents().orEmpty() }
    }
}
