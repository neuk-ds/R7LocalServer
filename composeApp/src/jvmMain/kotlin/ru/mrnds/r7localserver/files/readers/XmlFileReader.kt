package ru.mrnds.r7localserver.files.readers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import ru.mrnds.r7localserver.files.model.XmlFileOptions
import ru.mrnds.r7localserver.files.model.XmlNode
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class XmlFileReader : FileReader {
    private val logger = LoggerFactory.getLogger(XmlFileReader::class.java)
    private val json = Json {
        encodeDefaults = true
    }

    override fun read(file: File): JsonElement {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isIgnoringComments = true
            factory.isIgnoringElementContentWhitespace = true
            factory.newDocumentBuilder().parse(file)
        } catch (e: Exception) {
            logger.error("Invalid XML file: {}", file.absolutePath, e)
            throw IllegalStateException("Invalid XML file", e)
        }

        val rootElement = document.documentElement
        val options = XmlFileOptions(
            root = rootElement.toXmlNode(),
            lineSeparator = "\n",
            indent = "\t"
        )
        return json.encodeToJsonElement(options)
    }

    private fun Element.toXmlNode(): XmlNode {
        val attributesMap = mutableMapOf<String, String>()

        val attributes = attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i)
            attributesMap[attribute.nodeName] = attribute.nodeValue
        }
        val children = mutableListOf<XmlNode>()
        val textParts = mutableListOf<String>()
        val childNodes = childNodes

        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)

            when (child.nodeType) {
                Node.ELEMENT_NODE -> {
                    children.add((child as Element).toXmlNode())
                }

                Node.TEXT_NODE -> {
                    val text = child.nodeValue.trim()

                    if (text.isNotEmpty()) {
                        textParts.add(text)
                    }
                }
            }
        }

        return XmlNode(
            name = tagName,
            attributes = attributesMap,
            text = textParts.joinToString("").ifBlank { null },
            children = children
        )
    }
}