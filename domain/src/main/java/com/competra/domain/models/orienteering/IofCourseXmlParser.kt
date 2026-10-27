package com.competra.domain.models.orienteering

import com.competra.domain.models.Coordinates
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Разбор IOF XML 3.0 (CourseData) из Mapper в дистанции соревнования — локально, без сервера.
 *
 * Повторяет серверный `IOFXmlParser` (eSport, `data/util/IOFXmlParser.kt`): Android создаёт
 * дистанции в Room до того, как соревнование появится на сервере, поэтому серверный
 * `POST /event/orienteering/import/courses` в мастере использовать нельзя. Меняешь правила
 * разбора — поменяй и сервер.
 *
 * В [Distance.controlPoints] попадают только КП, отмечаемые по ходу дистанции. Финиш — в
 * [Distance.finishControlPoint], старт — в [Distance.startControlPoint] (только если
 * [useStartStation]). Координаты старта и финиша сохраняются всегда, если есть в справочнике.
 * Правила «по выбору» Mapper пишет в `<Extensions>`: `<Required>true</Required>` у КП и
 * `<FreeOrder minControls="N"/>` у дистанции.
 */
object IofCourseXmlParser {

    /**
     * @param input поток с XML-файлом.
     * @param competitionId соревнование, к которому относятся дистанции.
     * @param useStartStation режим старта «По стартовой станции» — тогда сохраняется номер стартового КП.
     * @return дистанции в порядке элементов `<Course>`; пустой список, если в файле нет `RaceCourseData`.
     * @throws Exception если файл не является корректным XML.
     */
    fun parse(input: InputStream, competitionId: String, useStartStation: Boolean): List<Distance> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(input)
        val rcd = doc.documentElement
            .getElementsByTagName("RaceCourseData").item(0) as? Element
            ?: return emptyList()

        // Справочник КП — только прямые потомки RaceCourseData: getElementsByTagName рекурсивен
        // и задел бы ссылки <Control> внутри CourseControl.
        val controlPositions = mutableMapOf<String, Coordinates>()
        val rcdChildren = rcd.childNodes
        for (k in 0 until rcdChildren.length) {
            val node = rcdChildren.item(k) as? Element ?: continue
            if (node.tagName != "Control") continue
            val id = node.getElementsByTagName("Id").item(0)?.textContent ?: continue
            val position = node.getElementsByTagName("Position").item(0) as? Element ?: continue
            val lat = position.getAttribute("lat").toDoubleOrNull()
            val lng = position.getAttribute("lng").toDoubleOrNull()
            if (lat != null && lng != null) controlPositions[id] = Coordinates(lat, lng)
        }

        val courses = rcd.getElementsByTagName("Course")
        return (0 until courses.length).map { i ->
            val course = courses.item(i) as Element
            val name = course.getElementsByTagName("Name").item(0)?.textContent?.trim()
            val length = course.getElementsByTagName("Length").item(0)?.textContent?.trim()?.toIntOrNull() ?: 0
            val climb = course.getElementsByTagName("Climb").item(0)?.textContent?.trim()?.toIntOrNull() ?: 0

            val courseControls = course.getElementsByTagName("CourseControl")
            var finishNumber: Int? = null
            var startNumber: Int? = null
            var startPosition: Coordinates? = null
            var finishPosition: Coordinates? = null
            val controlPoints = mutableListOf<ControlPoint>()
            for (j in 0 until courseControls.length) {
                val cc = courseControls.item(j) as Element
                val ctrlId = cc.getElementsByTagName("Control").item(0)?.textContent?.trim() ?: ""
                val code = ctrlId.filter { it.isDigit() }.toIntOrNull()
                when (cc.getAttribute("type")) {
                    "Start" -> {
                        if (useStartStation && code != null) startNumber = code
                        startPosition = controlPositions[ctrlId]
                    }
                    "Finish" -> {
                        if (code != null) finishNumber = code
                        finishPosition = controlPositions[ctrlId]
                    }
                    else -> {
                        val position = controlPositions[ctrlId]
                        val score = cc.getElementsByTagName("Score").item(0)?.textContent?.trim()?.toIntOrNull() ?: 0
                        val required = cc.getElementsByTagName("Required").item(0)
                            ?.textContent?.trim()?.equals("true", ignoreCase = true) == true
                        controlPoints += ControlPoint(
                            number = code ?: j,
                            role = if (required) ControlPointRole.REQUIRED else ControlPointRole.ORDINARY,
                            score = score,
                            latitude = position?.latitude,
                            longitude = position?.longitude
                        )
                    }
                }
            }

            val freeOrder = course.getElementsByTagName("FreeOrder").item(0) as? Element
            val minControlsCount = freeOrder?.let { it.getAttribute("minControls").toIntOrNull() ?: 0 }

            Distance(
                competitionId = competitionId,
                name = name?.ifBlank { null },
                lengthMeters = length,
                climbMeters = climb,
                controlsCount = controlPoints.size,
                controlPoints = controlPoints,
                finishControlPoint = finishNumber,
                startControlPoint = startNumber,
                startPosition = startPosition,
                finishPosition = finishPosition,
                minControlsCount = minControlsCount
            )
        }
    }
}
