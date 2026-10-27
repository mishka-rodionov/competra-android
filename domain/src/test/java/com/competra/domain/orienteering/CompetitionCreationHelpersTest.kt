package com.competra.domain.orienteering

import com.competra.domain.models.Gender
import com.competra.domain.models.orienteering.ControlPointRole
import com.competra.domain.models.orienteering.Distance
import com.competra.domain.models.orienteering.IofCourseXmlParser
import com.competra.domain.models.orienteering.StandardGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompetitionCreationHelpersTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <CourseData xmlns="http://www.orienteering.org/datastandard/3.0" iofVersion="3.0">
          <RaceCourseData>
            <Control><Id>S1</Id><Position lng="37.1" lat="55.1"/></Control>
            <Control><Id>31</Id><Position lng="37.2" lat="55.2"/></Control>
            <Control><Id>F1</Id><Position lng="37.3" lat="55.3"/></Control>
            <Course>
              <Name>М21</Name>
              <Length>4500</Length>
              <Climb>120</Climb>
              <CourseControl type="Start"><Control>S1</Control></CourseControl>
              <CourseControl type="Control"><Control>31</Control><Extensions><Required>true</Required></Extensions></CourseControl>
              <CourseControl type="Control"><Control>32</Control></CourseControl>
              <CourseControl type="Finish"><Control>F1</Control></CourseControl>
              <Extensions><FreeOrder minControls="1"/></Extensions>
            </Course>
          </RaceCourseData>
        </CourseData>
    """.trimIndent()

    @Test
    fun `IOF XML course is parsed into a local distance`() {
        val distances = IofCourseXmlParser.parse(xml.byteInputStream(), "c1", useStartStation = true)

        assertEquals(1, distances.size)
        val d = distances.first()
        assertEquals("М21", d.name)
        assertEquals(4500, d.lengthMeters)
        assertEquals(120, d.climbMeters)
        assertEquals(listOf(31, 32), d.controlPoints.map { it.number })
        assertEquals(ControlPointRole.REQUIRED, d.controlPoints[0].role)
        assertEquals(55.2, d.controlPoints[0].latitude!!, 1e-9)
        assertNull(d.controlPoints[1].latitude)
        assertEquals(1, d.finishControlPoint)
        assertEquals(1, d.startControlPoint)
        assertEquals(55.1, d.startPosition!!.latitude, 1e-9)
        assertEquals(1, d.minControlsCount)
    }

    @Test
    fun `start control number is kept only for start station mode`() {
        val d = IofCourseXmlParser.parse(xml.byteInputStream(), "c1", useStartStation = false).first()
        assertNull(d.startControlPoint)
    }

    @Test
    fun `group title gives gender and age`() {
        assertEquals(Gender.FEMALE, StandardGroups.parseTitle("Ж14")!!.gender)
        assertEquals(14, StandardGroups.parseTitle("Ж14")!!.maxAge)
        assertNull(StandardGroups.parseTitle("Ж14")!!.minAge)
        assertEquals(35, StandardGroups.parseTitle("M35")!!.minAge)
        assertEquals(Gender.MALE, StandardGroups.parseTitle("м21э")!!.gender)
        assertNull(StandardGroups.parseTitle("М21")!!.maxAge)
        assertNull(StandardGroups.parseTitle("МЖ12")!!.gender)
        assertNull(StandardGroups.parseTitle("Open"))
    }

    @Test
    fun `distance is matched by group title regardless of layout`() {
        val distances = listOf(
            Distance(competitionId = "c1", name = "M 21", lengthMeters = 0, climbMeters = 0, controlsCount = 0, controlPoints = emptyList())
        )
        assertEquals("M 21", StandardGroups.matchDistance("М21", distances)?.name)
        assertNull(StandardGroups.matchDistance("Ж21", distances))
    }
}
