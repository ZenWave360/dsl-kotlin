package io.zenwave360.language.eventflow.view

class ServiceViewLayoutEngine {

    fun layout(viewModel: ServiceViewModel): ServiceViewModel {
        if (viewModel.groups.isEmpty()) {
            return viewModel.copy(
                layout = LayoutMetadata(
                    engine = "zfl-services-grid",
                    direction = Direction.LR,
                    rankSpacing = 120.0,
                    nodeSpacing = 32.0
                ),
                bounds = FlowBounds(0.0, 0.0, 0.0, 0.0)
            )
        }

        val nodesByGroup = viewModel.nodes.groupBy { it.groupId }
        val positionedGroups = mutableListOf<ServiceGroupView>()
        val positionedNodes = mutableListOf<ServiceNodeView>()

        val columns = if (viewModel.groups.size <= 3) viewModel.groups.size else 3
        val groupGapX = 72.0
        val groupGapY = 72.0
        val commandWidth = 156.0
        val eventWidth = 156.0
        val nodeHeight = 92.0
        val nodeGapY = 18.0
        val nodeGapX = 16.0
        val groupPadding = 24.0
        val minGroupWidth = 520.0
        val contentTopPadding = 24.0
        val legendBottomReserve = 56.0
        val columnHeights = MutableList(columns) { 40.0 }
        val groupLayouts = viewModel.groups.map { group ->
            val groupNodes = nodesByGroup[group.id].orEmpty()
            val commands = groupNodes.filter { it.type == ServiceNodeType.COMMAND }.sortedBy { it.label }
            val events = groupNodes.filter { it.type == ServiceNodeType.EVENT }.sortedBy { it.label }
            val maxSideSize = maxOf(commands.size, events.size)
            val preferredRows = when {
                maxSideSize >= 8 -> 4
                maxSideSize >= 6 -> 5
                else -> 6
            }
            val commandColumns = columnCountFor(commands.size, preferredRows)
            val eventColumns = columnCountFor(events.size, preferredRows)
            val commandRows = rowCountFor(commands.size, commandColumns)
            val eventRows = rowCountFor(events.size, eventColumns)
            val rows = maxOf(commandRows, eventRows, 1)
            val contentHeight = (rows * nodeHeight) + ((rows - 1) * nodeGapY)
            val centerGap = centerGapFor(group.path)
            val commandsWidth = contentWidth(commandColumns, commandWidth, nodeGapX)
            val eventsWidth = contentWidth(eventColumns, eventWidth, nodeGapX)
            val groupWidth = maxOf(
                minGroupWidth,
                commandsWidth + centerGap + eventsWidth + (groupPadding * 2)
            )
            val groupHeight = maxOf(
                332.0,
                contentTopPadding + contentHeight + legendBottomReserve + groupPadding
            )

            ServiceGroupLayout(
                group = group,
                commands = commands,
                events = events,
                rows = rows,
                commandColumns = commandColumns,
                eventColumns = eventColumns,
                groupWidth = groupWidth,
                groupHeight = groupHeight
            )
        }
        val layoutColumnWidth = groupLayouts.maxOf { it.groupWidth }

        groupLayouts.forEach { groupLayout ->
            val column = columnHeights
                .withIndex()
                .minBy { it.value }
                .index
            val groupX = 40.0 + (column * (layoutColumnWidth + groupGapX))
            val groupY = columnHeights[column]
            val stackStartY = groupY + contentTopPadding
            val eventsStartX = groupX + groupLayout.groupWidth - groupPadding -
                totalColumnWidth(groupLayout.eventColumns, eventWidth, nodeGapX)

            positionedGroups += groupLayout.group.copy(
                position = Point(groupX, groupY),
                dimensions = Dimensions(groupLayout.groupWidth, groupLayout.groupHeight)
            )

            groupLayout.commands.forEachIndexed { commandIndex, node ->
                val nodeColumn = commandIndex / groupLayout.rows
                val nodeRow = commandIndex % groupLayout.rows
                positionedNodes += node.copy(
                    position = Point(
                        groupX + groupPadding + (nodeColumn * (commandWidth + nodeGapX)),
                        stackStartY + (nodeRow * (nodeHeight + nodeGapY))
                    ),
                    dimensions = Dimensions(commandWidth, nodeHeight)
                )
            }

            groupLayout.events.forEachIndexed { eventIndex, node ->
                val nodeColumn = eventIndex / groupLayout.rows
                val nodeRow = eventIndex % groupLayout.rows
                positionedNodes += node.copy(
                    position = Point(
                        eventsStartX + (nodeColumn * (eventWidth + nodeGapX)),
                        stackStartY + (nodeRow * (nodeHeight + nodeGapY))
                    ),
                    dimensions = Dimensions(eventWidth, nodeHeight)
                )
            }

            columnHeights[column] = groupY + groupLayout.groupHeight + groupGapY
        }

        val maxX = positionedGroups.maxOf { (it.position?.x ?: 0.0) + (it.dimensions?.width ?: 0.0) }
        val maxY = positionedGroups.maxOf { (it.position?.y ?: 0.0) + (it.dimensions?.height ?: 0.0) }

        return viewModel.copy(
            groups = positionedGroups,
            nodes = positionedNodes,
            layout = LayoutMetadata(
                engine = "zfl-services-grid",
                direction = Direction.LR,
                rankSpacing = groupGapX,
                nodeSpacing = nodeGapY
            ),
            bounds = FlowBounds(
                x = 0.0,
                y = 0.0,
                width = maxX + 40.0,
                height = maxY + 40.0
            )
        )
    }
}

private data class ServiceGroupLayout(
    val group: ServiceGroupView,
    val commands: List<ServiceNodeView>,
    val events: List<ServiceNodeView>,
    val rows: Int,
    val commandColumns: Int,
    val eventColumns: Int,
    val groupWidth: Double,
    val groupHeight: Double
)

private fun columnCountFor(itemCount: Int, preferredRows: Int): Int {
    if (itemCount <= 0) {
        return 1
    }
    return ((itemCount + preferredRows - 1) / preferredRows).coerceAtLeast(1)
}

private fun rowCountFor(itemCount: Int, columns: Int): Int {
    if (itemCount <= 0) {
        return 0
    }
    return ((itemCount + columns - 1) / columns).coerceAtLeast(1)
}

private fun contentWidth(columns: Int, nodeWidth: Double, nodeGapX: Double): Double {
    return if (columns <= 0) 0.0 else totalColumnWidth(columns, nodeWidth, nodeGapX)
}

private fun totalColumnWidth(columns: Int, nodeWidth: Double, nodeGapX: Double): Double {
    return (columns * nodeWidth) + ((columns - 1).coerceAtLeast(0) * nodeGapX)
}

private fun centerGapFor(path: String): Double {
    val segments = path.split('/').filter(String::isNotBlank)
    val centerLabel = segments.getOrNull(1) ?: segments.firstOrNull() ?: "Unbounded"
    val estimatedLabelWidth = 96.0 + (centerLabel.length * 6.5)
    return estimatedLabelWidth.coerceIn(160.0, 260.0)
}
