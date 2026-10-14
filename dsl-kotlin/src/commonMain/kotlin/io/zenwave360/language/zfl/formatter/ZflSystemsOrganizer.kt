package io.zenwave360.language.zfl.formatter

import io.zenwave360.language.zfl.ZflModel
import io.zenwave360.language.zfl.ZflParser

class ZflSystemsOrganizer {

    fun organize(input: String): String {
        val parser = ZflParser()
        val parseResult = parser.parse(input)
        require(parseResult.syntaxProblems.isEmpty()) { "Cannot organize systems for malformed ZFL input." }

        val model = parser.parseModel(input)
        val discoveredSystems = discoverSystems(model)
        if (discoveredSystems.isEmpty() && parseResult.tree.systems() == null) {
            return ZflFormatter().format(input)
        }

        val replacement = renderSystemsBlock(
            input = input,
            existingSystems = parseResult.tree.systems(),
            discoveredSystems = discoveredSystems
        )

        val rewritten = replaceSystemsBlock(
            input = input,
            existingSystems = parseResult.tree.systems(),
            firstFlow = parseResult.tree.flow().firstOrNull(),
            replacement = replacement
        )

        return ZflFormatter().format(rewritten)
    }

    private fun discoverSystems(model: ZflModel): List<DiscoveredSystem> {
        val systems = linkedMapOf<String, MutableDiscoveredSystem>()
        model.getFlows().values.forEach { flowValue ->
            val flow = flowValue as? Map<*, *> ?: return@forEach
            val actions = flow["actions"] as? Map<*, *> ?: return@forEach
            actions.values.forEach { actionValue ->
                val action = actionValue as? Map<*, *> ?: return@forEach
                val commandName = action["name"] as? String ?: return@forEach
                val servicePath = action["servicePath"] as? String ?: return@forEach
                val segments = servicePath.split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) return@forEach

                val systemName = segments[0]
                val serviceName = segments.getOrNull(1) ?: systemName
                val aggregateName = segments.getOrNull(2)

                val system = systems.getOrPut(systemName) { MutableDiscoveredSystem(systemName) }
                val service = system.services.getOrPut(serviceName) { MutableDiscoveredService(serviceName) }
                if (aggregateName != null) {
                    service.hasPolicyAggregates = true
                    service.aggregates.add(aggregateName)
                }
                service.commands.add(commandName)
            }
        }
        return systems.values.map { it.freeze() }
    }

    private fun renderSystemsBlock(
        input: String,
        existingSystems: io.zenwave360.language.antlr.ZflParser.SystemsContext?,
        discoveredSystems: List<DiscoveredSystem>
    ): String {
        val preservedSystems = preserveExistingSystems(input, existingSystems)
        val blockHeader = if (existingSystems != null) {
            val lbrace = existingSystems.LBRACE().symbol
            slice(input, existingSystems.start!!.startIndex, lbrace.stopIndex + 1)
        } else {
            "systems {"
        }
        val trailingSuffix = preservedSystems.trailingSuffix.takeUnless { it.isBlank() } ?: ""

        val renderedSystems = buildString {
            discoveredSystems.forEachIndexed { index, system ->
                val preserved = preservedSystems.byName[system.name]
                val leadingTrivia = preserved?.leadingTrivia ?: if (index == 0) "\n" else "\n\n"
                append(leadingTrivia)
                append(renderSystem(system, preserved))
            }
            if (discoveredSystems.isNotEmpty()) {
                if (!toString().endsWithNewline()) {
                    append('\n')
                }
            } else if (trailingSuffix.isEmpty()) {
                append('\n')
            }
            append(trailingSuffix)
            if (!trailingSuffix.endsWith("\n") && !toString().endsWithNewline()) {
                append('\n')
            }
            append("}")
        }

        return blockHeader + renderedSystems
    }

    private fun renderSystem(system: DiscoveredSystem, preserved: PreservedSystem?): String {
        val header = preserved?.header ?: "${system.name} {"
        return buildString {
            append(header)
            if (system.services.isNotEmpty()) {
                system.services.forEachIndexed { index, service ->
                    val preservedService = preserved?.servicesByName?.get(service.name)
                    val leadingTrivia = preservedService?.leadingTrivia ?: if (index == 0) "\n" else "\n\n"
                    append(leadingTrivia)
                    append(renderService(service, preservedService))
                }
                append('\n')
            }
            append("}")
        }
    }

    private fun renderService(service: DiscoveredService, preserved: PreservedService?): String {
        val headerPrefix = preserved?.headerPrefix ?: "service ${service.name}"
        val effectiveAggregates = if (service.hasPolicyAggregates) {
            service.aggregates
        } else {
            preserved?.aggregates ?: emptyList()
        }
        val aggregates = if (effectiveAggregates.isEmpty()) {
            ""
        } else {
            " for(${effectiveAggregates.joinToString(", ")})"
        }
        return buildString {
            append(headerPrefix)
            append(aggregates)
            append(" {")
            append('\n')
            append("commands: ${service.commands.joinToString(", ")}")
            append('\n')
            append("}")
        }
    }

    private fun preserveExistingSystems(
        input: String,
        existingSystems: io.zenwave360.language.antlr.ZflParser.SystemsContext?
    ): PreservedSystems {
        if (existingSystems == null) {
            return PreservedSystems(emptyMap(), "")
        }

        val systems = linkedMapOf<String, PreservedSystem>()
        var cursor = existingSystems.LBRACE().symbol.stopIndex + 1
        existingSystems.system().forEach { system ->
            val startIndex = system.start!!.startIndex
            val stopIndex = system.stop!!.stopIndex + 1
            val leadingTrivia = slice(input, cursor, startIndex)
            val header = slice(input, startIndex, system.LBRACE().symbol.stopIndex + 1)
            val name = system.system_name().text
            val preservedServices = preserveExistingServices(input, system)
            systems[name] = PreservedSystem(
                name = name,
                leadingTrivia = leadingTrivia,
                header = header,
                servicesByName = preservedServices
            )
            cursor = stopIndex
        }
        val trailingSuffix = slice(input, cursor, existingSystems.RBRACE().symbol.startIndex)
        return PreservedSystems(systems, trailingSuffix)
    }

    private fun preserveExistingServices(
        input: String,
        system: io.zenwave360.language.antlr.ZflParser.SystemContext
    ): Map<String, PreservedService> {
        val services = linkedMapOf<String, PreservedService>()
        val systemServices = system.system_body().system_services().system_service()
        var cursor = system.LBRACE().symbol.stopIndex + 1
        systemServices.forEach { service ->
            val startIndex = service.start!!.startIndex
            val stopIndex = service.stop!!.stopIndex + 1
            val leadingTrivia = slice(input, cursor, startIndex)
            val serviceName = service.system_service_name().text
            val headerPrefix = slice(input, startIndex, service.system_service_name().stop!!.stopIndex + 1)
            val aggregates = service.system_service_aggregates()
                ?.ID()
                ?.mapNotNull { it.text }
                .orEmpty()
            services[serviceName] = PreservedService(
                name = serviceName,
                leadingTrivia = leadingTrivia,
                headerPrefix = headerPrefix,
                aggregates = aggregates
            )
            cursor = stopIndex
        }
        return services
    }

    private fun replaceSystemsBlock(
        input: String,
        existingSystems: io.zenwave360.language.antlr.ZflParser.SystemsContext?,
        firstFlow: io.zenwave360.language.antlr.ZflParser.FlowContext?,
        replacement: String
    ): String {
        if (existingSystems != null) {
            val start = existingSystems.start!!.startIndex
            val end = existingSystems.stop!!.stopIndex + 1
            return input.replaceRange(start, end, replacement)
        }

        val insertAt = firstFlow?.start?.startIndex ?: input.length
        val prefix = input.substring(0, insertAt)
        val suffix = input.substring(insertAt)
        val separatorBefore = if (prefix.isBlank() || prefix.endsWith("\n\n")) "" else if (prefix.endsWith("\n")) "\n" else "\n\n"
        val separatorAfter = if (suffix.isBlank() || suffix.startsWith("\n")) "\n" else "\n\n"
        return prefix + separatorBefore + replacement + separatorAfter + suffix
    }

    private fun slice(input: String, start: Int, endExclusive: Int): String {
        if (start >= endExclusive) return ""
        return input.substring(start, endExclusive)
    }

    private fun String.endsWithNewline(): Boolean =
        endsWith("\n") || endsWith("\r\n")

    private data class MutableDiscoveredSystem(
        val name: String,
        val services: LinkedHashMap<String, MutableDiscoveredService> = linkedMapOf()
    ) {
        fun freeze(): DiscoveredSystem = DiscoveredSystem(
            name = name,
            services = services.values.map { it.freeze() }
        )
    }

    private data class MutableDiscoveredService(
        val name: String,
        val aggregates: LinkedHashSet<String> = linkedSetOf(),
        val commands: LinkedHashSet<String> = linkedSetOf(),
        var hasPolicyAggregates: Boolean = false
    ) {
        fun freeze(): DiscoveredService = DiscoveredService(
            name = name,
            aggregates = aggregates.toList(),
            commands = commands.toList(),
            hasPolicyAggregates = hasPolicyAggregates
        )
    }

    private data class DiscoveredSystem(
        val name: String,
        val services: List<DiscoveredService>
    )

    private data class DiscoveredService(
        val name: String,
        val aggregates: List<String>,
        val commands: List<String>,
        val hasPolicyAggregates: Boolean
    )

    private data class PreservedSystems(
        val byName: Map<String, PreservedSystem>,
        val trailingSuffix: String
    )

    private data class PreservedSystem(
        val name: String,
        val leadingTrivia: String,
        val header: String,
        val servicesByName: Map<String, PreservedService>
    )

    private data class PreservedService(
        val name: String,
        val leadingTrivia: String,
        val headerPrefix: String,
        val aggregates: List<String>
    )
}
