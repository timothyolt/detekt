package io.github.detekt.report.xml

import io.github.detekt.psi.toUnifiedString
import io.gitlab.arturbosch.detekt.api.Detektion
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.api.OutputReport
import io.gitlab.arturbosch.detekt.api.SetupContext
import io.gitlab.arturbosch.detekt.api.UnstableApi
import java.util.Locale

/**
 * Contains rule violations in an XML format. The report follows the structure of a Checkstyle report.
 * See: https://detekt.dev/configurations.html#output-reports
 */
class XmlOutputReport : OutputReport() {

    private lateinit var logger: Appendable

    override val ending = "xml"

    override val name = "Checkstyle XML report"

    private val Finding.severityLabel: String
        get() = severity.name.toLowerCase(Locale.US)

    @OptIn(UnstableApi::class)
    override fun init(context: SetupContext) {
        logger = context.outputChannel
    }

    override fun render(detektion: Detektion): String {
        val smells = detektion.findings.flatMap { it.value }

        val lines = ArrayList<String>()
        lines += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        lines += "<checkstyle version=\"4.3\">"

        smells.groupBy { it.location.filePath.relativePath ?: it.location.filePath.absolutePath }
            .forEach { (filePath, findings) ->
                lines += "<file name=\"${filePath.toUnifiedString().toXmlString()}\">"
                findings.forEach {
                    logger.appendLine("relative: ${it.location.filePath.relativePath} absolute: ${it.location.filePath.absolutePath}")
                    lines += arrayOf(
                        "\t<error line=\"${it.location.source.line.toXmlString()}\"",
                        "column=\"${it.location.source.column.toXmlString()}\"",
                        "severity=\"${it.severityLabel.toXmlString()}\"",
                        "message=\"${it.messageOrDescription().toXmlString()}\"",
                        "source=\"${"detekt.${it.id.toXmlString()}"}\" />"
                    ).joinToString(separator = " ")
                }
                lines += "</file>"
            }

        lines += "</checkstyle>"
        return lines.joinToString(separator = "\n")
    }

    private fun Any.toXmlString() = XmlEscape.escapeXml(toString().trim())
}
