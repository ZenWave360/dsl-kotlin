package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.asList
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.string

/** `events.md`: per AsyncAPI file (v2 or v3), its operations with their channels, and its messages. */
internal object AsyncApiPage {

    const val PATH = "events.md"
    const val TITLE = "Event APIs"

    fun render(documents: List<SpecDocument>, problems: MutableList<String>): String =
        specPage(TITLE, documents, problems, ::section)

    private fun section(document: SpecDocument): String = buildString {
        val spec = document.spec
        append(document.heading("asyncapi", "AsyncAPI"))

        val v2 = spec.string("asyncapi")?.startsWith("2.") == true
        val operations = if (v2) operationsV2(spec) else operationsV3(spec)
        append("### Operations\n\n")
        if (operations.isEmpty()) {
            append("No operations.\n\n")
        } else {
            append(Markdown.table(listOf("Operation", "Action", "Channel", "Address", "Messages"), operations)).append('\n')
        }

        val messages = spec["components"].asMap()["messages"].asMap()
        if (messages.isNotEmpty()) {
            append("### Messages\n\n")
            val rows = messages.map { (name, message) -> message(name, message.asMap()) }
            append(Markdown.table(listOf("Message", "Payload", "Schema format", "Summary"), rows)).append('\n')
        }
    }

    /** AsyncAPI 3: operations point at channels; channels carry the address and the messages. */
    private fun operationsV3(spec: Map<String, Any?>): List<List<String?>> {
        val channels = spec["channels"].asMap()
        return spec["operations"].asMap().map { (name, operation) ->
            val op = operation.asMap()
            val channelRef = op["channel"].asMap().string("\$ref").orEmpty()
            val channelName = refName(channelRef)
            val channel = channels[channelName].asMap()
            val address = channel.string("address")
                ?: channel.string("\$ref")?.let { "Defined in ${Markdown.code(it)}" }
            val messages = op["messages"].asList().mapNotNull { it.asMap().string("\$ref")?.let(::refName) }
                .ifEmpty { channel["messages"].asMap().keys.toList() }
            listOf(name, op.string("action"), channelName, address, messages.joinToString(", "))
        }
    }

    /** AsyncAPI 2: channels are keyed by address and hold `publish` and `subscribe` operations. */
    private fun operationsV2(spec: Map<String, Any?>): List<List<String?>> =
        spec["channels"].asMap().flatMap { (address, channel) ->
            listOf("publish", "subscribe").mapNotNull { action ->
                val op = channel.asMap()[action]?.asMap() ?: return@mapNotNull null
                val message = op["message"].asMap()
                val messages = message["oneOf"].asList().map { it.asMap() }.ifEmpty { listOf(message) }
                    .mapNotNull { it.string("\$ref")?.let(::refName) ?: it.string("name") }
                listOf(op.string("operationId"), action, address, address, messages.joinToString(", "))
            }
        }

    private fun message(name: String, message: Map<String, Any?>): List<String?> {
        val ref = message.string("\$ref")
        if (ref != null) return listOf(name, "", "", "Defined in ${Markdown.code(ref)}")
        val payload = message["payload"].asMap()
        val schemaRef = payload["schema"].asMap().string("\$ref") ?: payload.string("\$ref")
        val payloadText = when {
            schemaRef != null -> Markdown.code(schemaRef)
            payload.isNotEmpty() -> "inline"
            else -> ""
        }
        val format = payload.string("schemaFormat") ?: message.string("schemaFormat")
        val summary = message.string("summary") ?: message.string("title") ?: message.string("description")
        return listOf(message.string("name") ?: name, payloadText, format, summary)
    }
}
