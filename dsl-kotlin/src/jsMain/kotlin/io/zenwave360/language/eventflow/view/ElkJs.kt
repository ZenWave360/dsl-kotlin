@file:JsModule("elkjs")

package io.zenwave360.language.eventflow.view

/**
 * Kotlin/JS external declarations for the ELK.js library.
 *
 * elkjs exposes ELK as its default ES module export. The layout() method is
 * asynchronous and returns a Promise with the same graph structure enriched
 * with computed x/y coordinates on every child node.
 */
@JsName("default")
external class ELK {
    /**
     * Computes an automatic layout for the given graph.
     *
     * @param graph A plain JS object following the ELK JSON graph format:
     *   { id, layoutOptions, children: [{ id, width, height }], edges: [{ id, sources, targets }] }
     * @return Promise that resolves to the same graph with x/y added to each child.
     */
    fun layout(graph: dynamic): kotlin.js.Promise<dynamic>
}

