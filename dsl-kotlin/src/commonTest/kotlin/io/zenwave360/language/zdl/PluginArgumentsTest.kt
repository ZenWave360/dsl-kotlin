package io.zenwave360.language.zdl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Parity coverage for the argument-owning cases in the extension's command.test.ts. */
class PluginArgumentsTest {
    private val dollar = "$"

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

    private fun arguments(text: String, pluginIndex: Int, filePath: String = "/work/orders.zdl"): List<String> {
        val model = ZdlParser().parseModel(text)
        @Suppress("UNCHECKED_CAST")
        val plugin = (model["pluginsList"] as List<Map<String, Any?>>)[pluginIndex]
        return PluginArguments.build(model, plugin, filePath)
    }

    @Test
    fun pluginIsPassedWithItsNameAndInheritedConfigurationIsOverriddenOnce() {
        assertEquals(
            listOf(
                "-p", "MainPlugin", "--deps", "io.example:my-plugin:1.2.3",
                "basePackage=io.example.orders", "targetFolder=build/generated", "displayName=Orders API",
                "sourceFile=/work/orders.zdl", "maxDepth=3", "zdlFile=/work/orders.zdl",
                "--force", "--style=compact", "--label=Orders API", "--input=/work/orders.zdl",
            ),
            arguments(commandModel, 0),
        )
    }

    @Test
    fun inheritFalseOmitsEnclosingConfiguration() {
        assertEquals(listOf("-p", "StandalonePlugin", "--deps", "io.example:my-plugin:1.2.3", "targetFolder=standalone", "zdlFile=/work/orders.zdl"), arguments(commandModel, 2))
    }

    @Test
    fun zdlFileIsDefaultedUnlessZdlFilesIsDeclared() {
        val args = arguments(commandModel, 1)
        assertContains(args, "zdlFiles=orders.zdl,customers.zdl")
        assertFalse(args.any { it.startsWith("zdlFile=") })
    }

    @Test
    fun onlyMavenCoordinateImportsBecomeDependencies() {
        assertEquals("io.example:my-plugin:1.2.3", arguments(commandModel, 0)[3])
    }

    @Test
    fun filePathIsNormalizedAndSubstitutedInPropertiesAndCliOptions() {
        val args = arguments(commandModel, 0, "C:\\Users\\Ada\\My Project\\orders.zdl")
        assertContains(args, "sourceFile=C:/Users/Ada/My Project/orders.zdl")
        assertContains(args, "zdlFile=C:/Users/Ada/My Project/orders.zdl")
        assertContains(args, "--input=C:/Users/Ada/My Project/orders.zdl")
    }

    @Test
    fun cliOptionsKeepDeclarationOrder() {
        val args = arguments(commandModel, 0, "/work/My Project/orders.zdl")
        assertEquals(listOf("--force", "--style=compact", "--label=Orders API", "--input=/work/My Project/orders.zdl"), args.takeLast(4))
    }

    @Test
    fun inheritedCliOptionsAreOverriddenLocallyAndExcludedByInheritFalse() {
        val text = """
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
        assertEquals(listOf("--force", "--profile=test"), arguments(text, 0).takeLast(2))
        assertEquals(listOf("--force", "--profile=local build"), arguments(text, 1).takeLast(2))
        assertEquals(listOf("--local"), arguments(text, 2).takeLast(1))
    }

    @Test
    fun modelValuesThatLookLikeShellSyntaxRemainLiteralArguments() {
        val args = arguments(commandModel, 3, "/work/a;b/orders.zdl")
        assertContains(args, "expression=a;b ${dollar}(touch should-not-run) it's")
        assertContains(args, "--cli=${dollar}(touch should-not-run)")
        assertContains(args, "zdlFile=/work/a;b/orders.zdl")
    }

    @Test
    fun aDoubleQuoteInAModelValueRemainsAnArgumentForTheCallerToQuote() {
        val args = arguments("config { plugins { P { value \"x\\\"&calc&\\\"\" } } }", 0)
        assertContains(args, "value=x\"&calc&\"")
    }

    @Test
    fun commaContainingValuesRemainSingleArguments() {
        assertContains(arguments(commandModel, 1), "zdlFiles=orders.zdl,customers.zdl")
    }

    @Test
    fun duplicatePluginDeclarationsRetainTheirOwnConfiguration() {
        val text = "config { plugins { SamePlugin { targetFolder \"one\" } SamePlugin { targetFolder \"two\" } } }"
        assertContains(arguments(text, 0), "targetFolder=one")
        assertContains(arguments(text, 1), "targetFolder=two")
    }

    @Test
    fun shellAndCommandSettingsCasesRemainOutsideThisApi() {
        val args = arguments(commandModel, 0)
        assertFalse(args.any { it == "jbang" || it == "zw" || it == "--debug" })
        assertEquals(listOf("--a", "b c", "d"), PluginArguments.splitCommandLine("--a \"b c\" 'd'"))
    }
}
