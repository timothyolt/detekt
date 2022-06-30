package io.gitlab.arturbosch.detekt.report

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object BaselineMerger {

    private val documentBuilder by lazy { DocumentBuilderFactory.newInstance().newDocumentBuilder() }

    fun merge(inputs: Collection<File>, output: File) {
        val document = documentBuilder.newDocument().apply {
            xmlStandalone = true
            val baselineNode = createElement("SmellBaseline")
            baselineNode.appendChild(createElement("ManuallySuppressedIssues"))
            baselineNode.appendChild(createElement("CurrentIssues"))
            appendChild(baselineNode)
        }

        inputs
            .filter { it.exists() }
            .flatMap { currentIssues(it) }
            .distinctBy { it.textContent }
            .forEach {
                document.documentElement.appendChild(document.importNode(it, true))
            }

        TransformerFactory.newInstance().newTransformer().run {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transform(DOMSource(document), StreamResult(output.writer()))
        }
    }

    private fun currentIssues(it: File) =
        documentBuilder
            .parse(it.inputStream())
            .documentElement
            .childNodes.asSequence().first {
                it.nodeName == "CurrentIssues"
            }.childNodes.asSequence().filterNot {
                it.nodeType == Node.TEXT_NODE && it.textContent.isBlank()
            }

}

fun NodeList.asSequence() = sequence {
    for (index in 0 until length) {
        yield(item(index))
    }
}

fun NamedNodeMap.asSequence() = sequence {
    for (index in 0 until length) {
        yield(item(index))
    }
}
