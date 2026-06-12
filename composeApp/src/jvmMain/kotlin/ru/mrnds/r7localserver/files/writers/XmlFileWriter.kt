package ru.mrnds.r7localserver.files.writers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.slf4j.LoggerFactory
import ru.mrnds.r7localserver.files.model.XmlFileOptions
import ru.mrnds.r7localserver.files.model.XmlNode
import java.io.File

class XmlFileWriter : FileWriter {
    private val logger = LoggerFactory.getLogger(XmlFileWriter::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun write(file: File, content: JsonElement): File {
        val options = try {
            json.decodeFromJsonElement<XmlFileOptions>(content)
        } catch (e: Exception) {
            logger.error("Invalid XML file options JSON", e)
            throw IllegalArgumentException("Invalid XML file options JSON", e)
        }

        validateNode(options.root)
        val xmlContent = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append(options.lineSeparator)
            appendNode(
                node = options.root,
                level = 0,
                indent = options.indent,
                lineSeparator = options.lineSeparator
            )
        }

        file.writeText(xmlContent)
        return file
    }

    private fun StringBuilder.appendNode(
        node: XmlNode,
        level: Int,
        indent: String,
        lineSeparator: String
    ) {
        val currentIndent = indent.repeat(level)

        append(currentIndent)
        append("<")
        append(node.name)

        node.attributes.forEach { (name, value) ->
            append(" ")
            append(name)
            append("=\"")
            append(escapeXml(value))
            append("\"")
        }

        val hasText = !node.text.isNullOrEmpty()
        val hasChildren = node.children.isNotEmpty()

        if (!hasText && !hasChildren) {
            append("/>")
            append(lineSeparator)
            return
        }

        append(">")

        if (hasText) {
            append(escapeXml(node.text))
        }

        if (hasChildren) {
            append(lineSeparator)

            node.children.forEach { child ->
                appendNode(
                    node = child,
                    level = level + 1,
                    indent = indent,
                    lineSeparator = lineSeparator
                )
            }

            append(currentIndent)
        }

        append("</")
        append(node.name)
        append(">")
        append(lineSeparator)
    }

    private fun validateNode(node: XmlNode) {
        require(node.name.isNotBlank()) {
            "XML node name must not be empty"
        }

        require(isValidXmlName(node.name)) {
            "Invalid XML node name: ${node.name}"
        }

        node.attributes.keys.forEach { attributeName ->
            require(isValidXmlName(attributeName)) {
                "Invalid XML attribute name: $attributeName"
            }
        }

        node.children.forEach { child ->
            validateNode(child)
        }
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun isValidXmlName(value: String): Boolean {
        return value.matches(Regex("[A-Za-z_][A-Za-z0-9_.-]*"))
    }
}