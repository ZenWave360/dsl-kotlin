package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflSemanticModel(
    val flows: List<ZflFlow>,
    val systems: Map<String, ZflSystem>,
    val actors: Map<String, ZflActor>,
    val diagnostics: List<ZflSemanticDiagnostic> = emptyList()
)

@Serializable
data class ZflSemanticDiagnostic(
    val message: String,
    val severity: Severity,
    val sourceRef: SourceRef?
)

@Serializable
enum class Severity {
    INFO, WARNING, ERROR
}
