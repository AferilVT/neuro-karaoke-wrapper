package com.soul.neurokaraoke

import com.soul.neurokaraoke.data.repository.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthLoginTokenTest {
    @Test
    fun prefers_token_field() {
        assertEquals("jwt-1", AuthRepository.parseLoginToken("""{"token":"jwt-1"}"""))
    }

    @Test
    fun falls_back_to_accessToken() {
        assertEquals("jwt-2", AuthRepository.parseLoginToken("""{"accessToken":"jwt-2"}"""))
    }

    @Test
    fun empty_when_absent() {
        assertEquals("", AuthRepository.parseLoginToken("""{"message":"nope"}"""))
    }
}
