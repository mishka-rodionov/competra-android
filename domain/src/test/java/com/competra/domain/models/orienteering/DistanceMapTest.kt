package com.competra.domain.models.orienteering

import com.competra.domain.models.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceMapTest {

    @Test
    fun `three-point georef derives bottom-left as TL plus BR minus TR`() {
        val map = DistanceMap(
            url = "u",
            topLeft = Coordinates(55.7580, 37.6100),
            topRight = Coordinates(55.7550, 37.6320),
            bottomRight = Coordinates(55.7420, 37.6280)
        )

        val corners = map.corners()

        assertEquals(55.7450, corners.bottomLeft.latitude, 1e-9)
        assertEquals(37.6060, corners.bottomLeft.longitude, 1e-9)
        assertEquals(map.topRight, corners.topRight)
    }

    @Test
    fun `legacy bbox georef builds north-up rectangle`() {
        val map = DistanceMap(
            url = "u",
            topLeft = Coordinates(55.758, 37.610),
            topRight = null,
            bottomRight = Coordinates(55.742, 37.630)
        )

        val corners = map.corners()

        assertEquals(Coordinates(55.758, 37.630), corners.topRight)
        assertEquals(Coordinates(55.742, 37.610), corners.bottomLeft)
    }

    @Test
    fun `fromFields returns null for incomplete map and ignores half-filled top-right`() {
        assertNull(DistanceMap.fromFields("u", 1.0, 2.0, null, null, null, 4.0))
        assertNull(DistanceMap.fromFields(null, 1.0, 2.0, null, null, 3.0, 4.0))

        val map = DistanceMap.fromFields("u", 1.0, 2.0, 5.0, null, 3.0, 4.0)

        assertNull(map?.topRight)
        assertEquals(Coordinates(3.0, 4.0), map?.bottomRight)
    }
}
