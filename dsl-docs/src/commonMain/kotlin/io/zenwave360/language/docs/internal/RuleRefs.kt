package io.zenwave360.language.docs.internal

/** The model key of a rule: `<aggregate>.<name>`, or `<name>` without an aggregate. */
internal fun ruleKey(aggregate: String?, name: String): String = if (aggregate.isNullOrBlank()) name else "$aggregate.$name"

/** The anchor key of a rule heading. */
internal fun ruleAnchor(aggregate: String?, name: String): String = "rule:" + ruleKey(aggregate, name)

/**
 * The model key a `@rules(...)` reference points to among [keys] (pairs of aggregate and name): the reference
 * as a full key first, then its plain name when exactly one rule has it. Null when none, or ambiguous.
 */
internal fun resolveRuleRef(ref: String, rules: List<Pair<String?, String>>): String? {
    rules.firstOrNull { ruleKey(it.first, it.second) == ref }?.let { return ref }
    val byName = rules.filter { it.second == ref }
    return byName.singleOrNull()?.let { ruleKey(it.first, it.second) }
}

/** The annotations of a rule as inline code tags (`@invariant`, `@rule(severity: high)`), or empty. */
internal fun ruleTags(options: Map<String, String?>): String =
    options.entries.joinToString(" ") { (name, value) ->
        Markdown.code(if (value == null || value == "true") "@$name" else "@$name($value)")
    }
