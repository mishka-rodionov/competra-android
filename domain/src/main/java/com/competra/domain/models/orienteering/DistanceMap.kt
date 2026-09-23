package com.competra.domain.models.orienteering

import com.competra.domain.models.Coordinates

/**
 * Растровая карта дистанции (экспорт из mapper) с геопривязкой углов в WGS84.
 *
 * Прикрепляется организатором через веб; на Android только читается с сервера.
 *
 * Две схемы привязки:
 * - [topRight] задан → [topLeft] и [bottomRight] — **точные** углы растра (привязка по трём
 *   точкам; карта может быть повёрнута на магнитное склонение);
 * - [topRight] = `null` → старые данные: [topLeft] и [bottomRight] задают прямоугольник
 *   «север строго вверх» (bbox).
 *
 * @property url Адрес растра карты.
 * @property topLeft Верхний левый угол растра.
 * @property topRight Верхний правый угол растра или `null` для привязки bbox.
 * @property bottomRight Нижний правый угол растра.
 */
data class DistanceMap(
    val url: String,
    val topLeft: Coordinates,
    val topRight: Coordinates?,
    val bottomRight: Coordinates
) {

    /**
     * Все четыре угла растра с учётом схемы привязки. Для привязки по трём точкам нижний левый
     * угол достраивается как `TL + BR − TR` (линейная интерполяция lat/lon на масштабе дистанции
     * даёт погрешность много меньше GPS).
     */
    fun corners(): MapCorners {
        if (topRight != null) {
            return MapCorners(
                topLeft = topLeft,
                topRight = topRight,
                bottomRight = bottomRight,
                bottomLeft = Coordinates(
                    latitude = topLeft.latitude + bottomRight.latitude - topRight.latitude,
                    longitude = topLeft.longitude + bottomRight.longitude - topRight.longitude
                )
            )
        }
        return MapCorners(
            topLeft = topLeft,
            topRight = Coordinates(topLeft.latitude, bottomRight.longitude),
            bottomRight = bottomRight,
            bottomLeft = Coordinates(bottomRight.latitude, topLeft.longitude)
        )
    }

    companion object {

        /**
         * Собирает карту из плоских полей (DTO сервера, колонки Room). Возвращает `null`, если карта
         * прикреплена не полностью; неполный верхний правый угол трактуется как его отсутствие.
         */
        fun fromFields(
            url: String?,
            topLeftLat: Double?,
            topLeftLng: Double?,
            topRightLat: Double?,
            topRightLng: Double?,
            bottomRightLat: Double?,
            bottomRightLng: Double?
        ): DistanceMap? {
            if (url.isNullOrBlank() || topLeftLat == null || topLeftLng == null ||
                bottomRightLat == null || bottomRightLng == null
            ) {
                return null
            }
            val topRight = if (topRightLat != null && topRightLng != null) {
                Coordinates(topRightLat, topRightLng)
            } else {
                null
            }
            return DistanceMap(
                url = url,
                topLeft = Coordinates(topLeftLat, topLeftLng),
                topRight = topRight,
                bottomRight = Coordinates(bottomRightLat, bottomRightLng)
            )
        }
    }
}

/**
 * Четыре угла растровой карты в WGS84.
 *
 * @property topLeft Верхний левый угол.
 * @property topRight Верхний правый угол.
 * @property bottomRight Нижний правый угол.
 * @property bottomLeft Нижний левый угол.
 */
data class MapCorners(
    val topLeft: Coordinates,
    val topRight: Coordinates,
    val bottomRight: Coordinates,
    val bottomLeft: Coordinates
)
