package io.zenwave360.language.eventflow.view

/**
 * Draws a laid-out flow in full-width horizontal bands (spec:flows/eventflow#ef-r24).
 *
 * The ELK layout keeps deciding the order along the flow (x). This object then decides each node's band
 * from the commands, places the node's y inside that band, stacks nodes that would overlap inside a band,
 * and sizes the bands from their content. It is presentation only: nothing here changes what the model
 * says owns a node, and it is common code, so the JVM and JS layout engines give the same result.
 */
object FlowLaneLayout {

    private const val CANVAS_PADDING = 20.0
    private const val BAND_PADDING = 40.0
    private const val ROW_GAP = 40.0
    private const val NODE_GAP_X = 20.0

    /**
     * Places the nodes of [view], already laid out, in bands. With [FlowLanes.NONE] the view is returned unchanged.
     * Bands follow the order of first appearance in the flow; the band of the commands with no system (Systems)
     * or no service (Services) is the last one.
     */
    fun apply(view: FlowViewModel, lanes: FlowLanes): FlowViewModel {
        if (lanes == FlowLanes.NONE) return view
        if (view.nodes.isEmpty() || !view.hasLayout()) return view.copy(lanes = emptyList())

        val membership = membership(view, lanes)
        val bandKeys = orderedKeys(view, membership)
        val ids = bandKeys.associateWith { laneId(it, lanes) }

        val byBand = view.nodes.groupBy { membership.getValue(it.id) }
        val moved = HashMap<String, Point>()
        val bands = ArrayList<FlowLane>()
        var top = 0.0
        bandKeys.forEachIndexed { index, key ->
            val rows = pack(byBand.getValue(key))
            var rowTop = BAND_PADDING
            rows.forEach { row ->
                val rowHeight = row.maxOf { it.dimensions!!.height }
                row.forEach { node ->
                    moved[node.id] = Point(node.position!!.x, top + rowTop + (rowHeight - node.dimensions!!.height) / 2)
                }
                rowTop += rowHeight + ROW_GAP
            }
            val height = rowTop - ROW_GAP + BAND_PADDING
            bands += FlowLane(
                id = ids.getValue(key),
                kind = if (lanes == FlowLanes.SYSTEMS) FlowLaneKind.SYSTEM else FlowLaneKind.SERVICE,
                label = labelOf(key, lanes),
                system = if (lanes == FlowLanes.SERVICES && key != UNASSIGNED) key.system else null,
                x = 0.0, y = top, width = 0.0, height = height, order = index
            )
            top += height
        }

        val nodes = view.nodes.map {
            it.copy(position = moved.getValue(it.id), lane = ids.getValue(membership.getValue(it.id)))
        }
        val width = nodes.maxOf { it.position!!.x + it.dimensions!!.width } + CANVAS_PADDING
        return view.copy(
            nodes = nodes,
            bounds = FlowBounds(0.0, 0.0, width, top),
            systemGroups = systemGroupsOf(nodes),
            lanes = bands.map { it.copy(width = width) }
        )
    }

    // -- membership ---------------------------------------------------------------------------------------------

    private data class Key(val system: String?, val service: String?)

    private val UNASSIGNED = Key(null, null)

    private fun keyOf(node: FlowNode, lanes: FlowLanes): Key = when (lanes) {
        FlowLanes.SYSTEMS -> if (node.system == null) UNASSIGNED else Key(node.system, null)
        else -> if (node.service == null) UNASSIGNED else Key(node.system, node.service)
    }

    /** Node id to its band: a command's own, its events' with it, a policy's and a start's through the command they call. */
    private fun membership(view: FlowViewModel, lanes: FlowLanes): Map<String, Key> {
        val commands = view.nodes.filter { it.type == FlowNodeType.COMMAND }
        val textOrder = commands.withIndex().associate { it.value.id to it.index }
        val byId = view.nodes.associateBy { it.id }
        val result = LinkedHashMap<String, Key>()
        val rank = HashMap<String, Int>() // text-order rank of the command a node's band comes from
        commands.forEach { result[it.id] = keyOf(it, lanes); rank[it.id] = textOrder.getValue(it.id) }
        val fallback = commands.firstOrNull()?.let { result.getValue(it.id) } ?: UNASSIGNED
        val outgoing = view.edges.groupBy { it.source }
        val incoming = view.edges.groupBy { it.target }

        fun emitted(node: FlowNode) {
            val source = incoming[node.id].orEmpty()
                .filter { it.source != node.id && it.source in rank }
                .minByOrNull { rank.getValue(it.source) }
            if (source != null) {
                result[node.id] = result.getValue(source.source)
                rank[node.id] = rank.getValue(source.source)
            }
        }
        view.nodes.filter { it.type == FlowNodeType.READ_MODEL }.forEach(::emitted)
        view.nodes.filter { it.type == FlowNodeType.EVENT }.forEach(::emitted)
        view.nodes.filter { it.type == FlowNodeType.EVENT && it.id !in result }.forEach { node ->
            result[node.id] = if (node.service != null || node.system != null) keyOf(node, lanes) else fallback
        }

        fun calledBy(node: FlowNode): List<String> =
            outgoing[node.id].orEmpty().map { it.target }.filter { byId[it]?.type == FlowNodeType.COMMAND }

        // one band: that one; several: the first called command in text order; none: the first command in text order
        fun chosen(called: List<String>): Key {
            val bands = called.map { result.getValue(it) }.distinct()
            return when {
                bands.size == 1 -> bands.single()
                called.isEmpty() -> fallback
                else -> result.getValue(called.minByOrNull { textOrder.getValue(it) }!!)
            }
        }
        val policyCalls = HashMap<String, List<String>>()
        view.nodes.filter { it.type == FlowNodeType.POLICY }.forEach { policy ->
            val called = calledBy(policy)
            policyCalls[policy.id] = called
            result[policy.id] = chosen(called)
        }
        view.nodes.filter { it.type == FlowNodeType.START }.forEach { start ->
            val viaPolicies = outgoing[start.id].orEmpty()
                .filter { byId[it.target]?.type == FlowNodeType.POLICY }
                .flatMap { policyCalls[it.target].orEmpty() }
            result[start.id] = chosen((calledBy(start) + viaPolicies).distinct())
        }
        return result
    }

    /** Bands that hold nodes, by first appearance of a command or event in the flow; the unassigned band last. */
    private fun orderedKeys(view: FlowViewModel, membership: Map<String, Key>): List<Key> {
        val appearance = LinkedHashMap<Key, Int>()
        view.nodes.filter { it.type != FlowNodeType.START && it.type != FlowNodeType.POLICY }
            .forEach { appearance.getOrPut(membership.getValue(it.id)) { appearance.size } }
        membership.values.forEach { appearance.getOrPut(it) { appearance.size } }
        val keys = appearance.keys.filter { it != UNASSIGNED }
        return if (UNASSIGNED in appearance) keys + UNASSIGNED else keys
    }

    private fun laneId(key: Key, lanes: FlowLanes): String = when {
        key == UNASSIGNED -> "lane:unassigned"
        lanes == FlowLanes.SYSTEMS -> "lane:system:${key.system}"
        else -> "lane:service:${key.system?.let { "$it." } ?: ""}${key.service}"
    }

    private fun labelOf(key: Key, lanes: FlowLanes): String = when {
        key == UNASSIGNED -> if (lanes == FlowLanes.SYSTEMS) "No system" else "No service"
        lanes == FlowLanes.SYSTEMS -> key.system!!
        else -> key.service!!
    }

    // -- geometry -----------------------------------------------------------------------------------------------

    /** Rows of one band: a node goes in the first row where it does not overlap another node along x. */
    private fun pack(nodes: List<FlowNode>): List<List<FlowNode>> {
        val rows = ArrayList<MutableList<FlowNode>>()
        nodes.sortedWith(compareBy<FlowNode>({ it.position!!.y }, { it.position!!.x }, { it.id })).forEach { node ->
            val left = node.position!!.x - NODE_GAP_X
            val right = node.position.x + node.dimensions!!.width + NODE_GAP_X
            val row = rows.firstOrNull { row ->
                row.none { o -> o.position!!.x < right && o.position.x + o.dimensions!!.width > left }
            } ?: ArrayList<FlowNode>().also { rows += it }
            row += node
        }
        return rows
    }

    // Same formula as the layout engines: system groups stay what automatic layout would give for these nodes.
    private fun systemGroupsOf(nodes: List<FlowNode>): List<FlowSystemGroupView> =
        nodes.groupBy { it.system }.mapNotNull { (systemName, group) ->
            if (systemName == null) return@mapNotNull null
            val minX = group.minOf { it.position!!.x } - 40.0
            val minY = group.minOf { it.position!!.y } - 40.0
            val maxX = group.maxOf { it.position!!.x + it.dimensions!!.width } + 40.0
            val maxY = group.maxOf { it.position!!.y + it.dimensions!!.height } + 40.0
            FlowSystemGroupView(systemName, FlowBounds(minX, minY, maxX - minX, maxY - minY))
        }
}
