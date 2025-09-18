package io.github.remmerw.saga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class ModelTest {

    @Test
    fun parseTest() {
        val model = createModel("hello".toTag())
        val a = model.createEntity("a".toTag(), mapOf("a".toKey() to "b".toValue()))
        model.createEntity("text".toTag(), "hello \n moin")

        val child = model.createEntity("child".toTag(), a, mapOf())
        model.createEntity("text".toTag(), child, "hello \n moin \t dddddd")

        println(model.content())
        val buffer = Buffer()
        model.content(buffer)
        val cmp = createModel("hello".toTag(), buffer)
        println(cmp.content())
        assertEquals(model.content(), cmp.content())
    }


    @Test
    fun basicTest() {


        val name = "hello".toTag()
        checkNotNull(name)

        val data = "hello".toValue()
        checkNotNull(data)


        try {
            "hello2".toTag()
            fail()
        } catch (_: IllegalArgumentException) {
        }

        try {
            "Hello".toTag()
            fail()
        } catch (_: IllegalArgumentException) {
        }


        try {
            "hello-abc".toTag()
        } catch (_: IllegalArgumentException) {
        }


        try {
            "hello\n2".toValue()
            fail()
        } catch (_: IllegalArgumentException) {
        }

    }

    @Test
    fun removeTest() = runTest {
        val model = createModel("test".toTag())
        val hello = model.createEntity(
            tag = "hello".toTag(),
            attributes = mapOf("a".toKey() to "b".toValue(), "c".toKey() to "d".toValue())
        )
        assertNotNull(hello)

        val moin = model.createEntity(
            tag = "moin".toTag(),
            parent = hello,
            attributes = mapOf("a".toKey() to "b".toValue(), "c".toKey() to "d".toValue())
        )
        assertNotNull(moin)

        assertEquals(model.getChildren(hello).size, 1)

        model.removeEntity(hello, moin)

        assertEquals(model.getChildren(hello).size, 0)

    }

    @Test
    fun modelTest() = runTest {
        val model = createModel("test".toTag())
        val hello = model.createEntity(
            tag = "hello".toTag(),
            attributes = mapOf("a".toKey() to "b".toValue(), "c".toKey() to "d".toValue())
        )
        assertNotNull(hello)

        model.setData(hello, "this is text")

        println(model.content())


        var children = model.getChildren(model.entity, "hello".toTag())
        assertEquals(children.size, 1)

        children = model.getChildren(hello, "nope".toTag())
        assertEquals(children.size, 0)

        children = model.getChildren(hello)
        assertEquals(children.size, 0)



        println("set attribute e")
        model.setAttribute(hello, "e".toKey(), "f".toValue())
        println(model.content())

        val e = model.getAttribute(hello, "e".toKey())
        assertEquals(e, "f".toValue())

        val f = model.getAttribute(hello, "f".toKey())
        assertNull(f)

        println("remove attribute e")
        model.removeAttribute(hello, "e".toKey())
        println(model.content())

        println("remove entity hello")
        model.removeEntity(entity = hello.copy())


        println(model.content(model.entity))
    }

    @Test
    fun parallelTest() = runTest {
        val model = createModel("para".toTag())


        launch {
            repeat(10) { i ->
                val hello = model.createEntity(
                    tag = "a".toTag(),
                    attributes = mapOf("a".toKey() to "b".toValue(), "c".toKey() to "d".toValue())
                )
                model.setData(hello, "this is text")
            }
        }



        launch(Dispatchers.Default) {
            repeat(10) { i ->
                val hello = model.createEntity(
                    tag = "b".toTag(),
                    attributes = mapOf("a".toKey() to "b".toValue(), "c".toKey() to "d".toValue())
                )
                model.setData(hello, "this is text")
            }
        }

        launch {
            repeat(10) { i ->
                val hello = model.createEntity(
                    tag = "c".toTag(),
                    attributes = mapOf("a".toKey() to "b".toValue(), "c".toKey() to "d".toValue())
                )
                model.setData(hello, "this is text")
            }
        }

        launch {
            model.data(model.entity).collect { value ->
                cancel()
            }
        }

        launch {
            model.attributes(model.entity).collect { value ->
                cancel()
            }
        }

        launch {
            model.children(model.entity).collect { entities ->
                println(entities.toString())

                if (entities.size == 30) {
                    cancel()
                }
            }
        }

    }
}