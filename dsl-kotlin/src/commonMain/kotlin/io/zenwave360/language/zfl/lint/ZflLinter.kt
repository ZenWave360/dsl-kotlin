package io.zenwave360.language.zfl.lint

import io.zenwave360.language.source.SourceRef
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.Severity
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.ZflSemanticDiagnostic
import io.zenwave360.language.zfl.semantic.ZflSemanticModel

/** The semantic model of one ZFL file and every diagnostic found in it. */
data class ZflLintResult(
    val model: ZflSemanticModel,
    val diagnostics: List<ZflSemanticDiagnostic>,
)

/**
 * Single-file ZFL linter: syntax errors and the checks that need only the ZFL.
 * [ZflZdlLinter] adds the checks that read the ZDLs of the flow's systems.
 */
class ZflLinter {

    fun lint(source: String, sourceName: String = "<zfl>"): ZflLintResult {
        val parsed = ZflParser().parseModel(source, sourceName)
        val problems = parsed.getProblems().map { problem ->
            val location = problem["location"] as? IntArray
            ZflSemanticDiagnostic(
                message = problem["message"]?.toString().orEmpty(),
                severity = if (problem["severity"] == "WARNING") Severity.WARNING else Severity.ERROR,
                sourceRef = SourceRef(
                    file = sourceName,
                    line = location?.getOrNull(2)?.coerceAtLeast(1) ?: 1,
                    column = (location?.getOrNull(3) ?: 0) + 1,
                ),
            )
        }
        val model = ZflSemanticAnalyzer().analyze(parsed)
        return ZflLintResult(model, problems + model.diagnostics)
    }
}
