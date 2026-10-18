package io.zenwave360.language.docs.pages

import io.zenwave360.language.apis.view.ApiSchemaGraphBuilder
import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.asList
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.string

/**
 * `events.md`: per AsyncAPI file (v2 or v3), the channels and operations it provides, with a class diagram and field
 * tables per message, then the channels it is a client of: those defined in another file through `$ref`.
 * Client channels get no field tables, and a diagram only when the referenced file can be read.
 */
internal object AsyncApiPage {

    const val PATH = "events.md"
    const val TITLE = "Event APIs"

    fun render(documents: List<SpecDocument>, problems: MutableList<String>): String =
        specPage(TITLE, documents, problems, ::Model, ::section)

    private class Msg(val id: String, val name: String, val node: Map<String, Any?>, val ctx: RefCtx)

    /** [definedIn] is the `$ref` of a client channel (defined in another document), null for a provided one. */
    private class Chan(val key: String, val address: String?, val description: String?, val msgs: List<Msg>, val definedIn: String?)

    private class Op(val name: String, val action: String?, val channel: String, val msgs: List<Msg>, val names: List<String>, val summary: String?, val client: Boolean)

    private class Payload(val root: String, val format: String?)

    private class Model(val document: SpecDocument) {
        val refs = document.refs
        val ctx = document.ctx
        val builder = ApiSchemaGraphBuilder(document.spec, document.source.path, refs.resolver)
        val v2 = document.spec.string("asyncapi")?.startsWith("2.") == true
        val channels: List<Chan>
        val operations: List<Op>
        val provided: List<Msg>
        val client: List<Msg>
        val payloads = linkedMapOf<String, Payload?>()
        lateinit var placement: Placement

        /** The message whose section describes each class: the first message with that payload root. */
        val describedBy = linkedMapOf<String, String>()

        init {
            if (v2) {
                val parsed = channelsV2()
                channels = parsed.first
                operations = parsed.second
            } else {
                channels = channelsV3()
                operations = operationsV3(channels)
            }
            val seen = linkedSetOf<String>()
            val provided = mutableListOf<Msg>()
            fun add(msgs: List<Msg>, into: MutableList<Msg>) = msgs.forEach { if (seen.add(it.id)) into += it }
            channels.filter { it.definedIn == null }.forEach { add(it.msgs, provided) }
            operations.filter { !it.client }.forEach { add(it.msgs, provided) }
            for (key in document.spec["components"].asMap()["messages"].asMap().keys) {
                message(mapOf("\$ref" to "#/components/messages/$key"), key, "components/messages/$key", ctx)?.let { add(listOf(it), provided) }
            }
            val client = mutableListOf<Msg>()
            channels.filter { it.definedIn != null }.forEach { add(it.msgs, client) }
            operations.filter { it.client }.forEach { add(it.msgs, client) }
            this.provided = provided
            this.client = client

            for (m in provided + client) payloads[m.id] = payload(m)
            val roots = payloads.values.mapNotNull { it?.root }.distinct()
            placement = Placement(builder.build(), roots)
            for (m in provided + client) payloads[m.id]?.let { describedBy.getOrPut(it.root) { m.id } }
        }

        // ------------------------------------------------------------ messages

        fun message(value: Any?, key: String, inlinePath: String, ctx: RefCtx): Msg? {
            var failedRef: String? = null
            val r = refs.deref(value, ctx) { failedRef = it }
                ?: return Msg("${ctx.base}#/$inlinePath", key, mapOf("description" to "Defined in ${failedRef ?: "another file"}"), ctx)
            val node = r.value.asMap()
            return Msg(r.id ?: "${ctx.base}#/$inlinePath", node.string("name") ?: r.name ?: key, node, r.ctx)
        }

        private fun payload(msg: Msg): Payload? {
            val message = msg.node
            val raw = message["payload"] ?: return null
            val p = raw.asMap()
            val schema = if (p.containsKey("schema")) p["schema"] else raw
            val format = p.string("schemaFormat") ?: message.string("schemaFormat")
            val resolved = refs.deref(schema, msg.ctx)
            val root = if (format?.contains("avro", ignoreCase = true) == true && resolved != null) {
                builder.addAvro(resolved.value, resolved.ctx.base)
            } else {
                builder.addJsonSchema("${msg.name}Payload", schema, msg.ctx.document, msg.ctx.base)
            }
            return Payload(root, format)
        }

        // ------------------------------------------------------------ AsyncAPI 3

        private fun channelsV3(): List<Chan> = document.spec["channels"].asMap().map { (key, raw) ->
            val ref = raw.asMap().string("\$ref")
            val resolved = refs.deref(raw, ctx)
            val node = resolved?.value.asMap()
            val cctx = resolved?.ctx ?: ctx
            val msgs = node["messages"].asMap().mapNotNull { (k, v) -> message(v, k, "channels/$key/messages/$k", cctx) }
            Chan(key, node.string("address"), node.string("description"), msgs, ref?.takeIf { !it.startsWith("#") })
        }

        private fun operationsV3(channels: List<Chan>): List<Op> = document.spec["operations"].asMap().map { (name, raw) ->
            val op = raw.asMap()
            val channelRef = op["channel"].asMap().string("\$ref").orEmpty()
            val key = channelRef.substringAfterLast('/').replace("~1", "/").replace("~0", "~")
            val channel = channels.firstOrNull { it.key == key && channelRef.startsWith("#") }
            val isClient = channel?.definedIn != null || (channel == null && channelRef.isNotEmpty() && !channelRef.startsWith("#"))
            val listed = op["messages"].asList()
            val msgs = mutableListOf<Msg>()
            val names = mutableListOf<String>()
            if (listed.isEmpty()) {
                channel?.msgs?.forEach { msgs += it; names += it.name }
            } else {
                for (item in listed) {
                    val m = message(item, refName(item.asMap().string("\$ref").orEmpty()), "operations/$name/messages", ctx)
                    if (m != null) { msgs += m; names += m.name } else names += refName(item.asMap().string("\$ref").orEmpty())
                }
            }
            Op(name, op.string("action"), key.ifEmpty { channelRef }, msgs, names, op.string("summary") ?: op.string("description"), isClient)
        }

        // ------------------------------------------------------------ AsyncAPI 2

        private fun channelsV2(): Pair<List<Chan>, List<Op>> {
            val chans = mutableListOf<Chan>()
            val ops = mutableListOf<Op>()
            for ((address, raw) in document.spec["channels"].asMap()) {
                val ref = raw.asMap().string("\$ref")?.takeIf { !it.startsWith("#") }
                val resolved = refs.deref(raw, ctx)
                val node = resolved?.value.asMap()
                val cctx = resolved?.ctx ?: ctx
                val channelMsgs = mutableListOf<Msg>()
                for (action in listOf("publish", "subscribe")) {
                    val op = node[action]?.asMap() ?: continue
                    val message = op["message"].asMap()
                    val items = message["oneOf"].asList().ifEmpty { listOf(op["message"]) }
                    val msgs = mutableListOf<Msg>()
                    val names = mutableListOf<String>()
                    for (item in items) {
                        val m = message(item, "message", "channels/$address/$action/message", cctx)
                        if (m != null) { msgs += m; names += m.name } else item.asMap().string("\$ref")?.let { names += refName(it) }
                    }
                    channelMsgs += msgs
                    ops += Op(op.string("operationId") ?: "$action $address", action, address, msgs, names, op.string("summary") ?: op.string("description"), ref != null)
                }
                chans += Chan(address, address, node.string("description"), channelMsgs.distinctBy { it.id }, ref)
            }
            return chans to ops
        }
    }

    private fun msgKey(index: Int, prefix: String, id: String) = "asyncapi:$index:$prefix:$id"

    private fun section(document: SpecDocument, model: Model, index: Int, anchors: Anchors): String = buildString {
        append(document.heading(anchors, "asyncapi", "AsyncAPI"))

        val providedChannels = model.channels.filter { it.definedIn == null }
        val providedOps = model.operations.filter { !it.client }
        val clientChannels = model.channels.filter { it.definedIn != null }
        val clientOps = model.operations.filter { it.client }

        fun messages(chan: List<Msg>, names: List<String>, prefix: String): String =
            chan.joinToString(", ") { anchors.link(msgKey(index, prefix, it.id), Markdown.code(it.name)) }
                .ifEmpty { names.joinToString(", ") { Markdown.code(it) } }

        fun operations(ops: List<Op>, prefix: String) {
            append(anchors.heading(4, "Operations"))
            val rows = ops.map { listOf(Markdown.code(it.name), it.action, Markdown.code(it.channel), messages(it.msgs, it.names, prefix), it.summary) }
            append(Markdown.table(listOf("Operation", "Action", "Channel", "Messages", "Summary"), rows)).append('\n')
        }

        if (providedChannels.isEmpty() && providedOps.isEmpty() && model.provided.isEmpty() && clientChannels.isEmpty() && clientOps.isEmpty()) {
            append("No channels or operations.\n\n")
            return@buildString
        }

        if (providedChannels.isNotEmpty() || providedOps.isNotEmpty() || model.provided.isNotEmpty()) {
            append(anchors.heading(3, "Provided"))
            append("Channels and messages defined in this file.\n\n")
            if (providedChannels.isNotEmpty()) {
                append(anchors.heading(4, "Channels"))
                val rows = providedChannels.map { c ->
                    listOf(Markdown.code(c.key), c.address?.let { Markdown.code(it) }, messages(c.msgs, emptyList(), "msg"), c.description)
                }
                append(Markdown.table(listOf("Channel", "Address", "Messages", "Description"), rows)).append('\n')
            }
            if (providedOps.isNotEmpty()) operations(providedOps, "msg")
            if (model.provided.isNotEmpty()) {
                append(anchors.heading(4, "Messages"))
                for (m in model.provided) append(message(m, model, index, anchors, "msg", tables = true))
            }
        }

        if (clientChannels.isNotEmpty() || clientOps.isNotEmpty()) {
            append(anchors.heading(3, "Client of"))
            append("Channels this application uses that are defined in other files (`\$ref`). It may receive from them and send to them. ")
            append("Their messages are not described here.\n\n")
            if (clientChannels.isNotEmpty()) {
                append(anchors.heading(4, "Channels"))
                val rows = clientChannels.map { c ->
                    listOf(Markdown.code(c.key), Markdown.code(c.definedIn.orEmpty()), c.address?.let { Markdown.code(it) }, messages(c.msgs, emptyList(), "cmsg"))
                }
                append(Markdown.table(listOf("Channel", "Defined in", "Address", "Messages"), rows)).append('\n')
            }
            if (clientOps.isNotEmpty()) operations(clientOps, "cmsg")
            if (model.client.isNotEmpty()) {
                append(anchors.heading(4, "Messages"))
                for (m in model.client) append(message(m, model, index, anchors, "cmsg", tables = false))
            } else {
                val refs = clientChannels.mapNotNull { it.definedIn }.distinct()
                if (refs.isNotEmpty()) {
                    append("The channels are defined in other files that cannot be read here: ")
                    append(refs.joinToString(", ") { Markdown.code(it) }).append(".\n\n")
                }
            }
        }
    }

    private fun message(msg: Msg, model: Model, index: Int, anchors: Anchors, prefix: String, tables: Boolean): String = buildString {
        append(anchors.heading(5, Markdown.inline(msg.name), msgKey(index, prefix, msg.id)))
        (msg.node.string("summary") ?: msg.node.string("title"))?.let { append(Markdown.inline(it)).append("\n\n") }
        msg.node.string("description")?.takeIf { it != msg.node.string("summary") }?.let { append(Markdown.inline(it)).append("\n\n") }
        val payload = model.payloads[msg.id]
        if (payload == null) {
            append("No payload.\n\n")
            return@buildString
        }
        append("Payload ").append(Markdown.code(payload.root))
        payload.format?.let { append(" · ").append(Markdown.code(it)) }
        append("\n\n")
        val owner = model.describedBy[payload.root]
        if (owner != null && owner != msg.id) {
            val other = (model.provided + model.client).first { it.id == owner }
            val key = msgKey(index, if (model.provided.any { it.id == owner }) "msg" else "cmsg", owner)
            append("Same payload as ").append(anchors.link(key, Markdown.inline(other.name))).append(".\n\n")
            return@buildString
        }
        append(SchemaBlocks.render(anchors, model.placement, payload.root, "message", tables) { root ->
            val id = model.describedBy.getValue(root)
            msgKey(index, if (model.provided.any { it.id == id }) "msg" else "cmsg", id)
        })
    }
}
