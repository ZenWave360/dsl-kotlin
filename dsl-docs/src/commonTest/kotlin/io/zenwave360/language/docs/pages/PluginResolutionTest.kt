package io.zenwave360.language.docs.pages

import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The resolved properties of a plugin, port of the cases of the VS Code extension that concern them. Each case names the test
 * it comes from (`packages/extension/test/unit/<file>.test.ts`, test title), so a disagreement is easy to find. The expected
 * lists are written `name=value|G` for an inherited value, `|P` for the plugin's own and `|P+` for one that replaced an inherited one.
 *
 * Not ported, because they are about building or running a command: PowerShell and POSIX quoting, `findUnquotableArgument`,
 * the `-p` and `--deps` arguments, the configured CLI command and extra arguments, Windows path normalisation, the `zdlFile`
 * default, and the substitution of `${this.file.path}` (the page has no file path, so the placeholder shows as written).
 */
class PluginResolutionTest {

    private val dollar = "$"

    /** command.test.ts MODEL, with its imports. */
    private val commandModel = """
        @import("io.example:my-plugin:1.2.3")
        @import("./shared/common.zdl")
        config {
          basePackage "io.example.orders"
          targetFolder "."
          plugins {
            MainPlugin {
              targetFolder "build/generated"
              displayName "Orders API"
              sourceFile "${dollar}{this.file.path}"
              maxDepth 3
              --force
              --style=compact
              --label="Orders API"
              --input="${dollar}{this.file.path}"
            }
            FilesPlugin { zdlFiles "orders.zdl,customers.zdl" }
            StandalonePlugin (inherit false) { targetFolder "standalone" }
            InjectedPlugin {
              expression "a;b ${dollar}(touch should-not-run) it's"
              --cli="${dollar}(touch should-not-run)"
            }
          }
        }
    """.trimIndent()

    private val cliModel = """
        config {
          --force
          --profile="local build"
          plugins {
            InheritedPlugin { --profile=test }
            SecondPlugin {}
            StandalonePlugin (inherit false) { --local }
          }
        }
    """.trimIndent()

    private val sameTypeModel = """
        config {
          plugins {
            SamePlugin { targetFolder "one" }
            SamePlugin { targetFolder "two" }
          }
        }
    """.trimIndent()

    private class Case(
        /** The TS test this case comes from, as `file: title`. */
        val ts: String,
        val model: String,
        val plugin: Int,
        val properties: List<String>,
        val cli: List<String> = emptyList(),
    )

    private val cases = listOf(
        Case(
            "command.test.ts: config options are inherited and plugin options override them once",
            commandModel, 0,
            listOf("basePackage=io.example.orders|G", "targetFolder=build/generated|P+", "displayName=Orders API|P", "sourceFile=${dollar}{this.file.path}|P", "maxDepth=3|P"),
            listOf("force=null|P", "style=compact|P", "label=Orders API|P", "input=${dollar}{this.file.path}|P"),
        ),
        Case(
            "command.test.ts: parser-normalized string values containing spaces / numeric option values are passed as their digits",
            commandModel, 0,
            listOf("basePackage=io.example.orders|G", "targetFolder=build/generated|P+", "displayName=Orders API|P", "sourceFile=${dollar}{this.file.path}|P", "maxDepth=3|P"),
            listOf("force=null|P", "style=compact|P", "label=Orders API|P", "input=${dollar}{this.file.path}|P"),
        ),
        Case(
            "command.test.ts: (inherit false) omits enclosing config options",
            commandModel, 2,
            listOf("targetFolder=standalone|P"),
        ),
        Case(
            "command.test.ts: zdlFile is defaulted unless zdlFile or zdlFiles is declared (the declared zdlFiles only; no zdlFile is added)",
            commandModel, 1,
            listOf("basePackage=io.example.orders|G", "targetFolder=.|G", "zdlFiles=orders.zdl,customers.zdl|P"),
        ),
        Case(
            "command.test.ts: POSIX: model values that look like shell syntax reach the SDK literally (the values)",
            commandModel, 3,
            listOf("basePackage=io.example.orders|G", "targetFolder=.|G", "expression=a;b ${dollar}(touch should-not-run) it's|P"),
            listOf("cli=${dollar}(touch should-not-run)|P"),
        ),
        Case(
            "command.test.ts: config-level CLI options are inherited, overridden locally, and excluded by inherit false (inherited and overridden)",
            cliModel, 0,
            emptyList(),
            listOf("force=null|G", "profile=test|P+"),
        ),
        Case(
            "command.test.ts: config-level CLI options ... (an inheriting plugin with none of its own: declaration order)",
            cliModel, 1,
            emptyList(),
            listOf("force=null|G", "profile=local build|G"),
        ),
        Case(
            "command.test.ts: config-level CLI options ... (excluded by inherit false)",
            cliModel, 2,
            emptyList(),
            listOf("local=null|P"),
        ),
        Case(
            "command.test.ts: each same-type plugin declaration builds its own command (first)",
            sameTypeModel, 0,
            listOf("targetFolder=one|P"),
        ),
        Case(
            "command.test.ts: each same-type plugin declaration builds its own command (second)",
            sameTypeModel, 1,
            listOf("targetFolder=two|P"),
        ),
    )

    private fun load(text: String): PluginModel = PluginModel.of(ZdlParser().parseModel(text), text)

    private fun properties(model: PluginModel, plugin: Int) =
        PluginResolution.resolve(model, model.plugins[plugin]).properties.map { "${it.name}=${it.value}|${mark(it.origin, it.overrides)}" }

    private fun cli(model: PluginModel, plugin: Int) =
        PluginResolution.resolve(model, model.plugins[plugin]).cliOptions.map { "${it.name}=${it.value}|${mark(it.origin, it.overrides)}" }

    private fun mark(origin: Origin, overrides: Boolean) = if (origin == Origin.GLOBAL) "G" else if (overrides) "P+" else "P"

    @Test
    fun portedCases() {
        for (case in cases) {
            val model = load(case.model)
            assertEquals(case.properties, properties(model, case.plugin), case.ts)
            assertEquals(case.cli, cli(model, case.plugin), case.ts)
        }
    }

    @Test
    fun inheritIsReadFromTheDeclaration() {
        // model.test.ts: plugin discovery reads all declarations, disabled state, and inherit strings
        val text = """
            config {
              basePackage "io.example"
              plugins {
                DefaultPlugin { own default }
                ExplicitPlugin (inherit true) { own explicit }
                disabled DisabledPlugin (inherit false) { own disabled }
              }
            }
        """.trimIndent()
        val model = load(text)

        assertEquals(
            listOf(Triple("DefaultPlugin", false, true), Triple("ExplicitPlugin", false, true), Triple("DisabledPlugin", true, false)),
            model.plugins.map { Triple(it.name, it.disabled, it.inherit) },
        )
        assertEquals(listOf("basePackage=io.example|G", "own=default|P"), properties(model, 0))
        assertEquals(listOf("basePackage=io.example|G", "own=explicit|P"), properties(model, 1))
        assertEquals(listOf("own=disabled|P"), properties(model, 2))
    }

    @Test
    fun configCliOptionsDoNotCorruptTheFollowingConfig() {
        // model.test.ts: parseModel exposes config-level CLI options without corrupting following config
        val model = load(
            """
            config {
                title "shared"
                --force
                --profile="local build"
                useJSpecify true
                plugins { MyPlugin {} }
              }
            """.trimIndent(),
        )

        assertEquals(listOf(CliOption("force", null), CliOption("profile", "\"local build\"")), model.cliOptions)
        assertEquals("shared", model.config["title"])
        assertEquals(true, model.config["useJSpecify"])
        assertEquals(listOf("title=shared|G", "useJSpecify=true|G"), properties(model, 0))
    }

    @Test
    fun everyDeclarationOfASharedTypeIsKept() {
        // model.test.ts: plugin discovery keeps every declaration when several share a type name
        val model = load(
            """
            config {
              plugins {
                SamePlugin { targetFolder "one" }
                SamePlugin { targetFolder "two" }
                OtherPlugin { targetFolder "three" }
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("SamePlugin" to "one", "SamePlugin" to "two", "OtherPlugin" to "three"), model.plugins.map { it.name to it.config["targetFolder"] })
    }

    @Test
    fun anUnfinishedPluginIsNotExposed() {
        // model.test.ts: an unfinished plugin recovered by ANTLR is not exposed as runnable
        assertEquals(emptyList(), load("config { plugins { BrokenPlugin { targetFolder \".\"").plugins)
    }

    @Test
    fun aModelWithoutConfigHasNoPlugins() {
        // model.test.ts: a model without a config block has no plugins
        assertEquals(emptyList(), load("entity Order { id UUID }").plugins)
    }

    @Test
    fun splitsSourceFormValuesLikeAShell() {
        // command.test.ts: CLI options keep declaration order and are quoted like any other value (the splitting)
        assertEquals(listOf("--a", "b c", "d"), PluginResolution.splitCommandLine("--a \"b c\" 'd'"))
        assertEquals(listOf("a b"), PluginResolution.splitCommandLine("a\\ b"))
        assertEquals(listOf("\$x"), PluginResolution.splitCommandLine("'\$x'"))
        assertEquals(listOf(""), PluginResolution.splitCommandLine("\"\""))
        assertEquals(emptyList(), PluginResolution.splitCommandLine("   "))
    }

    @Test
    fun objectsAndListsAreFormattedAsTheCommandDoes() {
        val model = load("config { plugins { P { paths [\"a\", \"b\"] settings { \"key\": \"value\", \"n\": 2 } } } }")

        assertEquals(listOf("paths=a,b|P", "settings={\"key\":\"value\",\"n\":2}|P"), properties(model, 0))
        assertTrue(model.plugins.size == 1)
    }
}
