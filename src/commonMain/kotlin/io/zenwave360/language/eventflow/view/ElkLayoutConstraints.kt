package io.zenwave360.language.eventflow.view

internal object ElkLayoutConstraints {

    fun partitions(viewModel: FlowViewModel): Map<String, Int> {
        if (viewModel.nodes.isEmpty()) {
            return emptyMap()
        }

        val nodeIds = viewModel.nodes.map { it.id }
        val nodeOrder = nodeIds.withIndex().associate { it.value to it.index }
        val nodeTypes = viewModel.nodes.associate { it.id to it.type }

        val fullAdjacency = nodeIds.associateWith { mutableSetOf<String>() }.toMutableMap()
        viewModel.edges.forEach { edge ->
            if (edge.source != edge.target &&
                fullAdjacency.containsKey(edge.source) &&
                fullAdjacency.containsKey(edge.target)
            ) {
                fullAdjacency.getValue(edge.source).add(edge.target)
            }
        }

        val fullSccs = stronglyConnectedComponents(nodeIds, fullAdjacency)
        val fullComponentOf = mutableMapOf<String, Int>()
        fullSccs.forEachIndexed { index, component ->
            component.forEach { nodeId -> fullComponentOf[nodeId] = index }
        }
        val incomingByNode = nodeIds.associateWith { mutableSetOf<String>() }.toMutableMap()
        fullAdjacency.forEach { (source, targets) ->
            targets.forEach { target ->
                incomingByNode.getValue(target).add(source)
            }
        }

        val forwardAdjacency = nodeIds.associateWith { mutableSetOf<String>() }.toMutableMap()
        viewModel.edges.forEach { edge ->
            if (edge.source == edge.target) {
                return@forEach
            }
            if (!forwardAdjacency.containsKey(edge.source) || !forwardAdjacency.containsKey(edge.target)) {
                return@forEach
            }

            val sourceType = nodeTypes[edge.source]
            val targetType = nodeTypes[edge.target]
            val sourceComponent = fullComponentOf[edge.source]
            val targetComponent = fullComponentOf[edge.target]
            val sameCycle = sourceComponent != null && sourceComponent == targetComponent

            val keepForward = when {
                sourceType == FlowNodeType.START -> true
                sourceType == FlowNodeType.COMMAND && targetType == FlowNodeType.EVENT -> true
                sourceType == FlowNodeType.EVENT && targetType == FlowNodeType.POLICY -> true
                sourceType == FlowNodeType.COMMAND && targetType == FlowNodeType.COMMAND &&
                    (edge.type == FlowEdgeType.CALL || edge.type == FlowEdgeType.OUTCOME_HANDLER) -> true
                sourceType == FlowNodeType.POLICY && targetType == FlowNodeType.COMMAND ->
                    !sameCycle || !hasExternalAnchor(
                        nodeId = edge.target,
                        componentIndex = targetComponent,
                        componentOfNode = fullComponentOf,
                        incomingByNode = incomingByNode
                    )
                else -> false
            }

            if (keepForward) {
                forwardAdjacency.getValue(edge.source).add(edge.target)
            }
        }

        breakRemainingCycles(nodeIds, forwardAdjacency, nodeTypes, nodeOrder)

        val dag = collapseCycles(nodeIds, forwardAdjacency)
        val componentPriority = dag.components.map { component ->
            component.minOf { nodeOrder.getValue(it) }
        }
        val componentRank = longestPathRanks(dag.adjacency, componentPriority)

        return buildMap {
            dag.components.forEachIndexed { componentIndex, component ->
                val rank = componentRank[componentIndex] ?: 0
                component.forEach { nodeId -> put(nodeId, rank) }
            }
        }
    }

    fun nodeModelOrder(viewModel: FlowViewModel): Map<String, Int> {
        if (viewModel.nodes.isEmpty()) {
            return emptyMap()
        }

        val nodeIds = viewModel.nodes.map { it.id }
        val originalOrder = nodeIds.withIndex().associate { it.value to it.index }
        val nodeTypes = viewModel.nodes.associate { it.id to it.type }

        val precedence = nodeIds.associateWith { mutableSetOf<String>() }.toMutableMap()
        val indegree = nodeIds.associateWith { 0 }.toMutableMap()

        viewModel.nodes
            .filter { it.type == FlowNodeType.COMMAND }
            .forEach { commandNode ->
                val siblingTargets = viewModel.edges
                    .asSequence()
                    .filter { edge ->
                        edge.source == commandNode.id &&
                            edge.source != edge.target &&
                            edge.type in setOf(FlowEdgeType.CALL, FlowEdgeType.CAUSATION, FlowEdgeType.OUTCOME_HANDLER)
                    }
                    .mapNotNull { edge ->
                        val targetType = nodeTypes[edge.target]
                        if (targetType == FlowNodeType.COMMAND || targetType == FlowNodeType.EVENT) {
                            edge.target
                        } else {
                            null
                        }
                    }
                    .distinct()
                    .toList()

                val commandTargets = siblingTargets.filter { nodeTypes[it] == FlowNodeType.COMMAND }
                val eventTargets = siblingTargets.filter { nodeTypes[it] == FlowNodeType.EVENT }

                commandTargets.forEach { commandTarget ->
                    eventTargets.forEach { eventTarget ->
                        if (precedence.getValue(commandTarget).add(eventTarget)) {
                            indegree[eventTarget] = indegree.getValue(eventTarget) + 1
                        }
                    }
                }
            }

        val queue = nodeIds
            .filter { indegree.getValue(it) == 0 }
            .sortedBy { originalOrder.getValue(it) }
            .toMutableList()

        val ordered = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            ordered += current
            precedence[current].orEmpty()
                .sortedBy { originalOrder.getValue(it) }
                .forEach { successor ->
                    indegree[successor] = indegree.getValue(successor) - 1
                    if (indegree.getValue(successor) == 0) {
                        queue.add(successor)
                        queue.sortBy { originalOrder.getValue(it) }
                    }
                }
        }

        val finalOrder = if (ordered.size == nodeIds.size) ordered else nodeIds
        return finalOrder.withIndex().associate { it.value to it.index }
    }

    private fun collapseCycles(
        nodeIds: List<String>,
        adjacency: Map<String, Set<String>>
    ): ComponentDag {
        val sccs = stronglyConnectedComponents(nodeIds, adjacency)
        val componentOfNode = mutableMapOf<String, Int>()
        sccs.forEachIndexed { index, component ->
            component.forEach { nodeId -> componentOfNode[nodeId] = index }
        }

        val componentAdjacency = sccs.indices.associateWith { mutableSetOf<Int>() }.toMutableMap()
        adjacency.forEach { (source, targets) ->
            val sourceComponent = componentOfNode.getValue(source)
            targets.forEach { target ->
                val targetComponent = componentOfNode.getValue(target)
                if (sourceComponent != targetComponent) {
                    componentAdjacency.getValue(sourceComponent).add(targetComponent)
                }
            }
        }

        return ComponentDag(
            components = sccs,
            adjacency = componentAdjacency.mapValues { it.value.toSet() }
        )
    }

    private fun hasExternalAnchor(
        nodeId: String,
        componentIndex: Int?,
        componentOfNode: Map<String, Int>,
        incomingByNode: Map<String, Set<String>>
    ): Boolean {
        if (componentIndex == null) {
            return false
        }
        return incomingByNode[nodeId].orEmpty().any { predecessor ->
            componentOfNode[predecessor] != componentIndex
        }
    }

    private fun breakRemainingCycles(
        nodeIds: List<String>,
        adjacency: MutableMap<String, MutableSet<String>>,
        nodeTypes: Map<String, FlowNodeType>,
        nodeOrder: Map<String, Int>
    ) {
        while (true) {
            val cyclicComponents = stronglyConnectedComponents(nodeIds, adjacency)
                .filter { it.size > 1 }
            if (cyclicComponents.isEmpty()) {
                return
            }

            var removedAnyEdge = false
            cyclicComponents.forEach { component ->
                val componentNodes = component.toSet()
                val removableEdge = component
                    .sortedBy { nodeOrder.getValue(it) }
                    .flatMap { source ->
                        adjacency[source].orEmpty()
                            .filter { target ->
                                target in componentNodes &&
                                    nodeTypes[source] == FlowNodeType.POLICY &&
                                    nodeTypes[target] == FlowNodeType.COMMAND
                            }
                            .map { target -> source to target }
                    }
                    .firstOrNull()

                if (removableEdge != null) {
                    adjacency[removableEdge.first]?.remove(removableEdge.second)
                    removedAnyEdge = true
                }
            }

            if (!removedAnyEdge) {
                return
            }
        }
    }

    private fun longestPathRanks(
        adjacency: Map<Int, Set<Int>>,
        priority: List<Int>
    ): Map<Int, Int> {
        val indegree = adjacency.keys.associateWith { 0 }.toMutableMap()
        adjacency.forEach { (_, targets) ->
            targets.forEach { target ->
                indegree[target] = indegree.getValue(target) + 1
            }
        }

        val queue = mutableListOf<Int>()
        indegree.keys
            .filter { indegree.getValue(it) == 0 }
            .sortedBy { priority[it] }
            .forEach { queue.add(it) }

        val rank = indegree.keys.associateWith { 0 }.toMutableMap()
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            adjacency[current].orEmpty()
                .sortedBy { priority[it] }
                .forEach { successor ->
                    rank[successor] = maxOf(rank.getValue(successor), rank.getValue(current) + 1)
                    indegree[successor] = indegree.getValue(successor) - 1
                    if (indegree.getValue(successor) == 0) {
                        queue.add(successor)
                        queue.sortBy { priority[it] }
                    }
                }
        }
        return rank
    }

    private fun stronglyConnectedComponents(
        nodeIds: List<String>,
        adjacency: Map<*, out Collection<String>>
    ): List<List<String>> {
        var index = 0
        val indexByNode = mutableMapOf<String, Int>()
        val lowLinkByNode = mutableMapOf<String, Int>()
        val stack = ArrayDeque<String>()
        val onStack = mutableSetOf<String>()
        val components = mutableListOf<List<String>>()

        fun strongConnect(nodeId: String) {
            indexByNode[nodeId] = index
            lowLinkByNode[nodeId] = index
            index++
            stack.addLast(nodeId)
            onStack.add(nodeId)

            adjacency[nodeId].orEmpty().sorted().forEach { successor ->
                if (successor !in indexByNode) {
                    strongConnect(successor)
                    lowLinkByNode[nodeId] = minOf(
                        lowLinkByNode.getValue(nodeId),
                        lowLinkByNode.getValue(successor)
                    )
                } else if (successor in onStack) {
                    lowLinkByNode[nodeId] = minOf(
                        lowLinkByNode.getValue(nodeId),
                        indexByNode.getValue(successor)
                    )
                }
            }

            if (lowLinkByNode.getValue(nodeId) == indexByNode.getValue(nodeId)) {
                val component = mutableListOf<String>()
                while (true) {
                    val stacked = stack.removeLast()
                    onStack.remove(stacked)
                    component += stacked
                    if (stacked == nodeId) {
                        break
                    }
                }
                components += component.sortedBy { nodeIds.indexOf(it) }
            }
        }

        nodeIds.forEach { nodeId ->
            if (nodeId !in indexByNode) {
                strongConnect(nodeId)
            }
        }

        return components.sortedBy { component ->
            component.minOf { nodeIds.indexOf(it) }
        }
    }

    private data class ComponentDag(
        val components: List<List<String>>,
        val adjacency: Map<Int, Set<Int>>
    )
}
