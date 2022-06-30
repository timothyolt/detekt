package io.gitlab.arturbosch.detekt.report

import org.w3c.dom.Node
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
        val mergedDocument = documentBuilder.newDocument().apply {
            xmlStandalone = true
        }
        val mergedCheckstyleNode = mergedDocument.createElement("checkstyle")
        mergedCheckstyleNode.setAttribute("version", "4.3")
        mergedDocument.appendChild(mergedDocument.createElement("checkstyle"))

        xmlFiles
            .filter { xmlFile -> xmlFile.exists() }
            .flatMap { existingXmlFile ->
                val checkstyleNode = documentBuilder.parse(existingXmlFile.inputStream())
                val fileNodes = checkstyleNode.documentElement.childNodes.asSequence().filterWhitespace()
                fileNodes
            }
            .flatMap { fileNode ->
                val fileNameAttribute = fileNode.attributes.getNamedItem("name").nodeValue
                val errorNodes = fileNode.childNodes.asSequence().filterWhitespace()
                errorNodes.map { errorNode ->
                    CheckstyleErrorNodeWithFileData(
                        errorID = errorID(fileNameAttribute, errorNode),
                        fileName = fileNameAttribute,
                        errorNode = errorNode
                    )
                }
            }
            .distinctBy { it.errorID }
            .groupBy({ it.fileName }, { it.errorNode })
            .forEach { (fileName, errorNodes) ->
                mergedCheckstyleNode.appendChild(
                    mergedDocument.createElement("file").apply {
                        setAttribute("name", fileName)
                        errorNodes.forEach {
                            appendChild(mergedDocument.importNode(it, true))
                        }
                    }
                )
            }

        TransformerFactory.newInstance().newTransformer().run {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transform(DOMSource(mergedDocument), StreamResult(output.writer()))
        }
    }

    private class CheckstyleErrorNodeWithFileData(
        val errorID: Any,
        val fileName: String,
        val errorNode: Node
    )

    private data class ErrorID(
        val fileName: String,
        val line: String,
        val column: String,
        val source: String
    )

    private fun errorID(fileNameAttribute: String, errorNode: Node): Any {
        val line = errorNode.attributes.getNamedItem("line")?.nodeValue
        val column = errorNode.attributes.getNamedItem("column")?.nodeValue
        val source = errorNode.attributes.getNamedItem("source")?.nodeValue

        return if (line != null && column != null && source != null) {
            ErrorID(fileName = fileNameAttribute, line = line, column = column, source = source)
        } else {
            // if the error node does not contain the expected attributes,
            // use org.w3c.dom.Node's more strict hashCode/equals method to determine error uniqueness
            errorNode
        }
    }

    /**
     * Use code instead of XSLT to exclude whitespaces.
     */
    private fun Sequence<Node>.filterWhitespace(): Sequence<Node> = asSequence().filterNot {
        it.nodeType == Node.TEXT_NODE && it.textContent.isBlank()
    }
}
