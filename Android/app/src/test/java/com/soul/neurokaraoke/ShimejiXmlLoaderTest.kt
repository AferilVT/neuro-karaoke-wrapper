package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.neurolings.*
import org.junit.Assert.*
import org.junit.Test

class ShimejiXmlLoaderTest {
    private val actions = """
      <Mascot><ActionList>
        <Action Name="Stand" Type="Stay" BorderType="Floor">
          <Animation><Pose Image="/a.png" ImageAnchor="64,128" Velocity="0,0" Duration="150"/></Animation>
        </Action>
        <Action Name="Walk" Type="Move" BorderType="Floor">
          <Animation><Pose Image="/w1.png" ImageAnchor="64,128" Velocity="-2,0" Duration="7"/></Animation>
        </Action>
        <Action Name="Fall" Type="Embedded" Class="com.group_finity.mascot.action.Fall"/>
      </ActionList></Mascot>""".trimIndent()

    private val behaviors = """
      <Mascot><BehaviorList>
        <Behavior Name="Fall" Frequency="0" Hidden="true"/>
        <Condition Condition="#{mascot.environment.floor.isOn(mascot.anchor)}">
          <Behavior Name="Stand" Frequency="200"/>
          <Behavior Name="Walk" Frequency="100"/>
        </Condition>
      </BehaviorList></Mascot>""".trimIndent()

    @Test fun parses_actions_and_behaviors() {
        val set = ShimejiXmlLoader.parse("Test", "mascots/Test/img",
            actions.byteInputStream(), behaviors.byteInputStream())
        assertEquals(3, set.actions.size)
        val walk = set.actions["Walk"] as MoveAction
        assertEquals(-2, walk.animations[0].poses[0].velX)
        assertEquals(BorderType.FLOOR, walk.border)
        val stand = set.behaviors.first { it.name == "Stand" }
        assertEquals(200, stand.frequency)
        // Condition-group condition was pushed onto the child behavior:
        assertTrue(stand.condition!!.contains("floor.isOn"))
        val fall = set.actions["Fall"] as EmbeddedAction
        assertTrue(fall.className.endsWith("Fall"))
    }
}
