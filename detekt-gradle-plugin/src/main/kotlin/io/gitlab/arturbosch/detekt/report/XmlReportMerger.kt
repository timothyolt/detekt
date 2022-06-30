package io.gitlab.arturbosch.detekt.report

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * A naive implementation to merge xml assuming all input xml are written by detekt.
 */
object XmlReportMerger {

    private val documentBuilder by lazy { DocumentBuilderFactory.newInstance().newDocumentBuilder() }

    fun merge(xmlFiles: Collection<File>, output: File) {
        val document = documentBuilder.newDocument().apply {
            xmlStandalone = true
        }
        val checkstyleNode = document.createElement("checkstyle")
        checkstyleNode.setAttribute("version", "4.3")
        document.appendChild(checkstyleNode)

        class NodeEquality(val node: Node) {
            override fun equals(other: Any?): Boolean =
                ((other as? NodeEquality)?.node?.isEqualNode(node) == true) && check(node == other.node).let { true }

            override fun hashCode(): Int {
                return node.hashCode()
            }
        }

        /*

            .reduce { key, accumulator, nodes ->
                nodes.childNodes.asSequence().filterWhitespace()

                nodes.flatMap { it.childNodes.asSequence().filterWhitespace() }.distinctBy {
                    Triple(
                        it.attributes?.getNamedItem("line")?.nodeValue,
                        it.attributes?.getNamedItem("column")?.nodeValue,
                        it.attributes?.getNamedItem("source")?.nodeValue
                    )
                }
            }
         */
        data class SmellID(
            val fileName: String,
            val line: String,
            val column: String,
            val source: String
        )

        fun smellID(
            fileName: String,
            line: String?,
            column: String?,
            source: String?
        ): SmellID? {
            return SmellID(
                fileName = fileName,
                line = line ?: return null,
                column = column ?: return null,
                source = source ?: return null
            )
        }

        data class CheckstyleFileNode(
            val id: Any,
            val fileName: String,
            val errorNode: Node
        )

        xmlFiles.asSequence()
            .filter { xmlFile -> xmlFile.exists() }
            .flatMap { existingXmlFile ->
                documentBuilder
                    .parse(existingXmlFile.inputStream()) // <checkstyle>
                    .documentElement.childNodes.asSequence().filterWhitespace()  // sequence of <file>
            }
            .flatMap { fileNode ->
                val fileName = fileNode.attributes.getNamedItem("name").nodeValue  // <file>
                fileNode.childNodes.asSequence().filterWhitespace().map { errorNode ->  // sequence of <error>
                    CheckstyleFileNode(
                        id = smellID(
                            fileName,
                            errorNode.attributes.getNamedItem("line")?.nodeValue,
                            errorNode.attributes.getNamedItem("column")?.nodeValue,
                            errorNode.attributes.getNamedItem("source")?.nodeValue
                        ) ?: errorNode,
                        fileName = fileName,
                        errorNode = errorNode
                    )
                }
            }
            .distinctBy { it.id }
            .groupBy({ it.fileName }, { it.errorNode })
            .forEach { (fileName, errorNodes) ->
                checkstyleNode.appendChild(
                    document.createElement("file").apply {
                        setAttribute("name", fileName)
                        errorNodes.forEach {
                            appendChild(document.importNode(it, true))
                        }
                    }
                )
            }

        TransformerFactory.newInstance().newTransformer().run {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transform(DOMSource(document), StreamResult(output.writer()))
        }
    }

    /**
     * Use code instead of XSLT to exclude whitespaces.
     */
    private fun NodeList.filterWhitespace(): Sequence<Node> = asSequence().filterNot {
        it.nodeType == Node.TEXT_NODE && it.textContent.isBlank()
    }

    private fun Sequence<Node>.filterWhitespace(): Sequence<Node> = asSequence().filterNot {
        it.nodeType == Node.TEXT_NODE && it.textContent.isBlank()
    }
}
