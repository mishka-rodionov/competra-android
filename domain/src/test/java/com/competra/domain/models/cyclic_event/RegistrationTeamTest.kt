package com.competra.domain.models.cyclic_event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegistrationTeamTest {

    private val juniors = RegistrationTeamOption("t1", "c1", "СК Азимут", "Юниоры", "СК Азимут (Юниоры)")
    private val bussol = RegistrationTeamOption(null, "c2", "Буссоль", null, "Буссоль")
    private val options = RegistrationTeamOptions(
        options = listOf(juniors, bussol),
        protocolNames = listOf("Лесные лисы", "буссоль", "Компас")
    )

    @Test
    fun `team id only for exact own team label`() {
        assertEquals("t1", options.teamIdFor(" ск азимут  (юниоры) "))
        assertNull(options.teamIdFor("СК Азимут (Юниоры) 2"))
        assertNull(options.teamIdFor("Буссоль"))
        assertNull(options.teamIdFor(""))
    }

    @Test
    fun `team source`() {
        assertEquals(TeamSource.CLUB_TEAM, options.teamSourceFor("СК Азимут (Юниоры)"))
        assertEquals(TeamSource.CLUB, options.teamSourceFor("буссоль"))
        assertEquals(TeamSource.PROTOCOL, options.teamSourceFor("компас"))
        assertEquals(TeamSource.CUSTOM, options.teamSourceFor("Сборная"))
        assertEquals(TeamSource.NONE, options.teamSourceFor("  "))
    }

    @Test
    fun `suggestions list own first and skip protocol duplicates`() {
        assertEquals(
            listOf("СК Азимут (Юниоры)", "Буссоль", "Лесные лисы", "Компас"),
            options.suggestionsFor("").map { it.label }
        )
    }

    @Test
    fun `suggestions filter by query and hide exact match`() {
        assertEquals(listOf("Лесные лисы"), options.suggestionsFor("лис").map { it.label })
        assertEquals(emptyList<String>(), options.suggestionsFor("Компас").map { it.label })
    }
}
