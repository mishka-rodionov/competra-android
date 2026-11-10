package com.competra.profile.data

/**
 * Инструкция для организатора — HTML-страница на сайте.
 *
 * @property title название, как в списке «Инструкции».
 * @property description одна строка о том, что внутри.
 * @property url адрес страницы на competra.ru.
 */
data class Guide(
    val title: String,
    val description: String,
    val url: String,
)

/**
 * Гиды организатора. Сами страницы лежат в `competra-web-ts/public/guides/`
 * и публикуются вместе с сайтом, поэтому текст правится без релиза приложения.
 * Тот же список ссылок — в профиле веб-клиента (`ProfilePage.tsx`).
 */
object GuideLinks {
    private const val BASE_URL = "https://competra.ru/guides/"

    /** Все инструкции в порядке показа. */
    val all: List<Guide> = listOf(
        Guide(
            title = "Как провести своё первое соревнование",
            description = "От создания события до публикации результатов",
            url = BASE_URL + "first-competition-guide.html",
        ),
        Guide(
            title = "Соревнование без предварительной регистрации",
            description = "Регистрация на финише: участников вносите при считывании чипа",
            url = BASE_URL + "finish-registration-guide.html",
        ),
        Guide(
            title = "Участник вне конкурса",
            description = "Результат в протоколе с «ВК» вместо места",
            url = BASE_URL + "out-of-competition-guide.html",
        ),
        Guide(
            title = "Как подготовить дистанции в Mapper",
            description = "Планирование в OpenOrienteering Mapper и экспорт в Competra",
            url = BASE_URL + "mapper-course-planning-guide.html",
        ),
    )
}
