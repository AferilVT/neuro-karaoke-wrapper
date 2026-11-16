/*
 * Copyright (C) 2026 Aferil
 *
 * This file is part of Neuro Karaoke.
 *
 * Neuro Karaoke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3.
 *
 * Neuro Karaoke is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Neuro Karaoke. If not, see <https://www.gnu.org/licenses/>.
 *
 * This file is part of the Neurolings feature, ported from Shimeji-ee (maintained
 * by Kilkakon; original Shimeji by Yuki Yamada / Group Finity), which is under a
 * permissive BSD/zlib-style license. See THIRD_PARTY_NOTICES.md.
 */

package com.soul.neurokaraoke.ui.tv.neurolings

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * DOM-based parser that turns a Shimeji mascot pack's `actions.xml` + `behaviors.xml`
 * into an immutable [MascotSet].
 *
 * Uses [javax.xml.parsers.DocumentBuilderFactory] (available on both the plain JVM and
 * Android) rather than Android's `XmlPullParser`, so this loader can be unit-tested on
 * the JVM without any Android framework classes.
 */
object ShimejiXmlLoader {

    fun parse(name: String, imgDir: String, actionsXml: InputStream, behaviorsXml: InputStream): MascotSet {
        val actionsDoc = parseDocument(actionsXml)
        val behaviorsDoc = parseDocument(behaviorsXml)

        val actions = parseActions(actionsDoc)
        val behaviors = parseBehaviors(behaviorsDoc)

        return MascotSet(name = name, actions = actions, behaviors = behaviors, imgDir = imgDir)
    }

    // ---------------------------------------------------------------------
    // XML plumbing
    // ---------------------------------------------------------------------

    private fun parseDocument(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(input)
    }

    /** Direct child *elements* of [el] (text/comment nodes filtered out). */
    private fun childElements(el: Element): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = el.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) result.add(n as Element)
        }
        return result
    }

    private fun attrOrNull(el: Element, attrName: String): String? =
        if (el.hasAttribute(attrName)) el.getAttribute(attrName) else null

    private fun attrInt(el: Element, attrName: String, default: Int = 0): Int =
        attrOrNull(el, attrName)?.trim()?.toIntOrNull() ?: default

    private fun attrBool(el: Element, attrName: String, default: Boolean = false): Boolean =
        attrOrNull(el, attrName)?.equals("true", ignoreCase = true) ?: default

    /** All attributes of [el] as a map, except the structural ones named in [exclude]. */
    private fun attrsExcept(el: Element, exclude: Set<String>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val attrs = el.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            if (attr.nodeName !in exclude) map[attr.nodeName] = attr.nodeValue
        }
        return map
    }

    private fun splitIntPair(value: String?): Pair<Int, Int> {
        if (value == null) return 0 to 0
        val parts = value.split(",")
        val a = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val b = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        return a to b
    }

    private fun parseBorderType(value: String?): BorderType = when (value) {
        "Floor" -> BorderType.FLOOR
        "Wall" -> BorderType.WALL
        "Ceiling" -> BorderType.CEILING
        else -> BorderType.NONE
    }

    // ---------------------------------------------------------------------
    // actions.xml
    // ---------------------------------------------------------------------

    private val STRUCTURAL_ACTION_ATTRS = setOf("Name", "Type", "BorderType", "Class", "Loop")

    private fun parseActions(doc: Document): Map<String, ShimejiAction> {
        val actions = LinkedHashMap<String, ShimejiAction>()
        var anonCounter = 0
        fun nextAnonName(): String = "\$anon${anonCounter++}"

        fun parsePose(el: Element): Pose {
            val image = el.getAttribute("Image")
            val (ax, ay) = splitIntPair(attrOrNull(el, "ImageAnchor"))
            val (vx, vy) = splitIntPair(attrOrNull(el, "Velocity"))
            val duration = attrInt(el, "Duration", 0)
            return Pose(image, ax, ay, vx, vy, duration)
        }

        fun parseAnimations(actionEl: Element): List<Animation> =
            childElements(actionEl).filter { it.tagName == "Animation" }.map { animEl ->
                val condition = attrOrNull(animEl, "Condition")
                val poses = childElements(animEl).filter { it.tagName == "Pose" }.map { parsePose(it) }
                Animation(condition, poses)
            }

        // Forward-declared so parseActionElement and resolveRefChild can call each other.
        lateinit var parseActionElement: (Element, String?) -> ShimejiAction

        fun resolveRefChild(childEl: Element): Pair<String, Map<String, String>> = when (childEl.tagName) {
            "ActionReference" -> {
                val refName = childEl.getAttribute("Name")
                refName to attrsExcept(childEl, setOf("Name"))
            }
            "Action" -> {
                val synthName = nextAnonName()
                val parsed = parseActionElement(childEl, synthName)
                actions[synthName] = parsed
                synthName to attrsExcept(childEl, STRUCTURAL_ACTION_ATTRS)
            }
            else -> error("Unexpected child <${childEl.tagName}> where an Action/ActionReference was expected")
        }

        parseActionElement = fun(el: Element, forcedName: String?): ShimejiAction {
            val actionName = forcedName ?: attrOrNull(el, "Name")
            val type = el.getAttribute("Type")
            return when (type) {
                "Stay" -> StayAction(actionName, parseBorderType(attrOrNull(el, "BorderType")), parseAnimations(el))
                "Move" -> MoveAction(actionName, parseBorderType(attrOrNull(el, "BorderType")), parseAnimations(el))
                "Animate" -> AnimateAction(actionName, parseBorderType(attrOrNull(el, "BorderType")), parseAnimations(el))
                "Embedded" -> EmbeddedAction(actionName, el.getAttribute("Class"))
                "Sequence" -> {
                    val loop = attrBool(el, "Loop", false)
                    val refs = childElements(el)
                        .filter { it.tagName == "ActionReference" || it.tagName == "Action" }
                        .map { child ->
                            val (refName, overrides) = resolveRefChild(child)
                            ActionRef(refName, overrides)
                        }
                    SequenceAction(actionName, loop, refs)
                }
                "Select" -> {
                    val children = childElements(el)
                        .filter { it.tagName == "ActionReference" || it.tagName == "Action" }
                        .map { child ->
                            val (refName, overrides) = resolveRefChild(child)
                            val condition = overrides["Condition"]
                            val remaining = if (condition != null) overrides - "Condition" else overrides
                            condition to ActionRef(refName, remaining)
                        }
                    SelectAction(actionName, children)
                }
                else -> error("Unknown Action Type \"$type\" for Action \"$actionName\"")
            }
        }

        val actionLists = doc.getElementsByTagName("ActionList")
        for (i in 0 until actionLists.length) {
            val listEl = actionLists.item(i) as Element
            for (actionEl in childElements(listEl).filter { it.tagName == "Action" }) {
                val parsed = parseActionElement(actionEl, null)
                val actionName = parsed.name
                    ?: error("Top-level <Action> is missing a Name attribute")
                actions[actionName] = parsed
            }
        }

        return actions
    }

    // ---------------------------------------------------------------------
    // behaviors.xml
    // ---------------------------------------------------------------------

    private fun combineCondition(own: String?, inherited: String?): String? = when {
        own == null -> inherited
        inherited == null -> own
        else -> "($own) && ($inherited)"
    }

    private fun parseBehaviors(doc: Document): List<Behavior> {
        val result = mutableListOf<Behavior>()

        fun parseNextBehaviorList(el: Element): NextBehaviorList? {
            val nblEl = childElements(el).firstOrNull { it.tagName == "NextBehaviorList" } ?: return null
            val add = attrBool(nblEl, "Add", false)
            val refs = childElements(nblEl).filter { it.tagName == "BehaviorReference" }.map { refEl ->
                BehaviorRef(
                    name = refEl.getAttribute("Name"),
                    frequency = attrInt(refEl, "Frequency", 0),
                    condition = attrOrNull(refEl, "Condition"),
                    hidden = attrBool(refEl, "Hidden", false),
                )
            }
            return NextBehaviorList(add, refs)
        }

        fun collect(container: Element, inheritedCondition: String?) {
            for (child in childElements(container)) {
                when (child.tagName) {
                    "Behavior" -> {
                        val ownCondition = attrOrNull(child, "Condition")
                        result.add(
                            Behavior(
                                name = child.getAttribute("Name"),
                                frequency = attrInt(child, "Frequency", 0),
                                hidden = attrBool(child, "Hidden", false),
                                condition = combineCondition(ownCondition, inheritedCondition),
                                next = parseNextBehaviorList(child),
                            )
                        )
                    }
                    "Condition" -> {
                        val groupCondition = attrOrNull(child, "Condition")
                        collect(child, combineCondition(groupCondition, inheritedCondition))
                    }
                    else -> Unit
                }
            }
        }

        val behaviorLists = doc.getElementsByTagName("BehaviorList")
        for (i in 0 until behaviorLists.length) {
            collect(behaviorLists.item(i) as Element, null)
        }

        return result
    }
}
