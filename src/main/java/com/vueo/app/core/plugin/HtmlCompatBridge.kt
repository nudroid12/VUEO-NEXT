package com.vueo.app.core.plugin

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Native DOM bridge used by the QuickJS cheerio compatibility wrapper.
 *
 * Provider JavaScript only sees opaque node IDs. HTML parsing and CSS
 * selector evaluation stay on the Android side in jsoup.
 */
class HtmlCompatBridge {
    private val nextDocumentId = AtomicInteger(1)
    private val nextNodeId = AtomicInteger(1)

    private val documents =
        mutableMapOf<Int, Document>()

    private val elementsById =
        mutableMapOf<Int, Element>()

    private val idsByElement =
        IdentityHashMap<Element, Int>()

    @Synchronized
    fun execute(requestJson: String): String {
        return runCatching {
            val request = JSONObject(requestJson)

            when (request.getString("op")) {
                "parse" -> parse(request)
                "select" -> select(request)
                "filter" -> filter(request)
                "find" -> find(request)
                "text" -> text(request)
                "ownText" -> ownText(request)
                "html" -> html(request)
                "outerHtml" -> outerHtml(request)
                "attr" -> attr(request)
                "parent" -> parent(request)
                "parents" -> parents(request)
                "children" -> children(request)
                "closest" -> closest(request)
                "next" -> next(request)
                "prev" -> prev(request)
                "siblings" -> siblings(request)
                "is" -> isSelector(request)
                "hasClass" -> hasClass(request)
                "remove" -> remove(request)
                else -> error(
                    "Unknown HTML bridge op: " +
                        request.optString("op")
                )
            }
        }.getOrElse { error ->
            JSONObject()
                .put(
                    "error",
                    error.message
                        ?: error::class.java.simpleName,
                )
        }.toString()
    }

    private fun parse(request: JSONObject): JSONObject {
        val html = request.optString("html")
        val baseUri = request.optString("baseUri", "")

        val document = Jsoup.parse(
            html,
            baseUri,
        )

        val documentId =
            nextDocumentId.getAndIncrement()

        documents[documentId] = document

        val rootId = register(document)

        return JSONObject()
            .put("documentId", documentId)
            .put("rootId", rootId)
    }

    private fun select(request: JSONObject): JSONObject {
        val selector = request.getString("selector")
        val roots = roots(request)

        val result = linkedSetOf<Element>()

        roots.forEach { root ->
            result.addAll(root.select(selector))
        }

        return idsResponse(result)
    }

    private fun filter(request: JSONObject): JSONObject {
        val selector = request.getString("selector")
        val result = elements(request)
            .filter { element ->
                element.`is`(selector)
            }

        return idsResponse(result)
    }

    private fun find(request: JSONObject): JSONObject {
        val selector = request.getString("selector")
        val result = linkedSetOf<Element>()

        elements(request).forEach { element ->
            result.addAll(element.select(selector))
        }

        return idsResponse(result)
    }

    private fun text(request: JSONObject): JSONObject =
        valueResponse(
            elements(request)
                .joinToString("") { it.text() }
        )

    private fun ownText(request: JSONObject): JSONObject =
        valueResponse(
            elements(request)
                .joinToString("") { it.ownText() }
        )

    private fun html(request: JSONObject): JSONObject {
        val element = elements(request).firstOrNull()
        return valueResponse(
            element?.html().orEmpty()
        )
    }

    private fun outerHtml(request: JSONObject): JSONObject {
        val element = elements(request).firstOrNull()
        return valueResponse(
            element?.outerHtml().orEmpty()
        )
    }

    private fun attr(request: JSONObject): JSONObject {
        val name = request.getString("name")
        val element = elements(request).firstOrNull()

        return valueResponse(
            element?.attr(name).orEmpty()
        )
    }

    private fun parent(request: JSONObject): JSONObject =
        idsResponse(
            elements(request)
                .mapNotNull { it.parent() }
                .distinct()
        )

    private fun parents(request: JSONObject): JSONObject {
        val selector = request.optString("selector")
        val result = linkedSetOf<Element>()

        elements(request).forEach { element ->
            element.parents().forEach { parent ->
                if (
                    selector.isBlank() ||
                    parent.`is`(selector)
                ) {
                    result.add(parent)
                }
            }
        }

        return idsResponse(result)
    }

    private fun children(request: JSONObject): JSONObject {
        val selector = request.optString("selector")
        val result = linkedSetOf<Element>()

        elements(request).forEach { element ->
            element.children().forEach { child ->
                if (
                    selector.isBlank() ||
                    child.`is`(selector)
                ) {
                    result.add(child)
                }
            }
        }

        return idsResponse(result)
    }

    private fun closest(request: JSONObject): JSONObject {
        val selector = request.getString("selector")

        return idsResponse(
            elements(request)
                .mapNotNull {
                    it.closest(selector)
                }
                .distinct()
        )
    }

    private fun next(request: JSONObject): JSONObject =
        idsResponse(
            elements(request)
                .mapNotNull {
                    it.nextElementSibling()
                }
                .distinct()
        )

    private fun prev(request: JSONObject): JSONObject =
        idsResponse(
            elements(request)
                .mapNotNull {
                    it.previousElementSibling()
                }
                .distinct()
        )

    private fun siblings(request: JSONObject): JSONObject {
        val selector = request.optString("selector")
        val result = linkedSetOf<Element>()

        elements(request).forEach { element ->
            element.siblingElements().forEach { sibling ->
                if (
                    selector.isBlank() ||
                    sibling.`is`(selector)
                ) {
                    result.add(sibling)
                }
            }
        }

        return idsResponse(result)
    }

    private fun isSelector(request: JSONObject): JSONObject {
        val selector = request.getString("selector")

        return JSONObject()
            .put(
                "bool",
                elements(request)
                    .firstOrNull()
                    ?.`is`(selector)
                    ?: false,
            )
    }

    private fun hasClass(request: JSONObject): JSONObject {
        val className = request.getString("name")

        return JSONObject()
            .put(
                "bool",
                elements(request)
                    .firstOrNull()
                    ?.hasClass(className)
                    ?: false,
            )
    }

    private fun remove(request: JSONObject): JSONObject {
        elements(request).forEach {
            it.remove()
        }
        return JSONObject().put("ok", true)
    }

    private fun roots(request: JSONObject): List<Element> {
        val ids = request.optJSONArray("ids")

        if (ids != null && ids.length() > 0) {
            return idList(ids)
                .mapNotNull(elementsById::get)
        }

        val documentId = request.getInt("documentId")
        val document = documents[documentId]
            ?: error("Unknown document ID: $documentId")

        return listOf(document)
    }

    private fun elements(request: JSONObject): List<Element> =
        idList(
            request.optJSONArray("ids")
                ?: JSONArray()
        ).mapNotNull(elementsById::get)

    private fun idList(array: JSONArray): List<Int> =
        (0 until array.length())
            .map { array.optInt(it) }

    private fun idsResponse(
        elements: Iterable<Element>,
    ): JSONObject {
        val array = JSONArray()

        elements.forEach { element ->
            array.put(register(element))
        }

        return JSONObject().put("ids", array)
    }

    private fun valueResponse(value: String): JSONObject =
        JSONObject().put("value", value)

    private fun register(element: Element): Int {
        idsByElement[element]?.let {
            return it
        }

        val id = nextNodeId.getAndIncrement()
        idsByElement[element] = id
        elementsById[id] = element
        return id
    }
}
