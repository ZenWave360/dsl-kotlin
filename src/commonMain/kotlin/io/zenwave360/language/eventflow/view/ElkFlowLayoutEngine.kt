package io.zenwave360.language.eventflow.view

/**
 * Layout engine that uses the Eclipse Layout Kernel (ELK) for automatic graph layout.
 *
 * Configures ELK's layered algorithm with a left-to-right (LR) direction so that
 * START nodes appear on the left and END nodes on the right, following the temporal
 * sequence of the event storming model (START → EVENT → POLICY → COMMAND → EVENT → END).
 *
 * Nodes are NOT grouped by system/service during layout — all nodes participate in a flat
 * graph so the temporal order is driven purely by the edges. System groups (swim lanes)
 * are derived from node metadata and added to the output after layout is complete.
 *
 * Input:  [FlowViewModel] with semantic data but no position/dimension/bounds information.
 * Output: [FlowViewModel] with [FlowNode.position], [FlowNode.dimensions],
 *         [FlowViewModel.systemGroups], [FlowViewModel.bounds], and [FlowViewModel.layout]
 *         all populated.
 *
 * Platform-specific implementations:
 *  - JVM: real ELK layout via `org.eclipse.elk.alg.layered`
 *  - JS:  stub/placeholder (ELK.js integration to be added later)
 */
expect class ElkFlowLayoutEngine() {
    fun layout(viewModel: FlowViewModel): FlowViewModel
}

