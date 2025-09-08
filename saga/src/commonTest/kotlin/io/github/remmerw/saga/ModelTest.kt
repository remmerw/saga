package io.github.remmerw.saga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelTest {

    @Test
    fun modelTest(): Unit = runBlocking(Dispatchers.IO) {
        val model = createModel()
        val hello = model.createEntity(
            name = "hello",
            attributes = mapOf("a" to "b", "c" to "d")
        )
        assertNotNull(hello)

        model.createText(hello, "this is text")

        model.debug()

        println("set attribute e")
        model.setAttribute(hello, "e", "f")
        model.debug()

        val e = model.getAttribute(hello, "e")
        assertEquals(e, "f")

        val f = model.getAttribute(hello, "f")
        assertNull(f)

        println("remove attribute e")
        model.removeAttribute(hello, "e")
        model.debug()



        println("remove entity hello")
        model.removeEntity(entity = hello)


        model.debug()
    }
}