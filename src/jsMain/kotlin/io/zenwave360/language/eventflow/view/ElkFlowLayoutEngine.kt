package io.zenwave360.language.eventflow.view

/**
 * JS stub for [ElkFlowLayoutEngine].
 *
 * Delegates to [FlowLayoutEngine] (the existing timeline-based algorithm) so that the JS
 * target remains functional while the real ELK.js integration is not yet implemented.
 *
 * TODO: Replace the [FlowLayoutEngine] delegation with a real ELK.js call once the
 *       ELK.js npm package is wired up in the build. The rough approach will be:
 *       1. Add `npm("elkjs", "<version>")` dependency to the `jsMain` source set.
 *       2. Dynamically import `elk.bundled.js` and call `new ELK().layout(graph)`.
 *       3. Map the resolved `ElkNode` positions back to [FlowNode] objects, then
 *          populate systemGroups / bounds exactly as the JVM implementation does.
 *
 * The input/output contract is identical to the JVM actual:
 *  Input:  [FlowViewModel] without position/dimension/bounds information.
 *  Output: [FlowViewModel] with positions, dimensions, bounds, and systemGroups populated.
 */
actual class ElkFlowLayoutEngine actual constructor() {

    // Delegate to the existing pure-Kotlin layout engine until ELK.js is integrated.
    private val delegate = FlowLayoutEngine()

    actual fun layout(viewModel: FlowViewModel): FlowViewModel = delegate.layout(viewModel)
}

