package io.github.remmerw.saga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CssTest {


    @Test
    fun internalCss(): Unit = runBlocking(Dispatchers.IO) {

        val data = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<style>\n" +
                "body {background-color: powderblue;}\n" +
                "h1   {color: blue;}\n" +
                "p    {color: red;}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h1>This is a heading</h1>\n" +
                "<p>This is a paragraph.</p>\n" +
                "\n" +
                "</body>\n" +
                "</html>\n"


        val model = createModel()

        val buffer = Buffer()
        buffer.write(data.encodeToByteArray())
        model.parse(buffer)

        println(model.content())

        model.attachStylesheets()


        val h1 = model.nodes("h1").firstOrNull()
        assertNotNull(h1)
        assertEquals((h1 as Element).getProperty("color"), "blue")

        val p = model.nodes("p").firstOrNull()
        assertNotNull(p)
        assertEquals((p as Element).getProperty("color"), "red")

        val body = model.nodes("body").firstOrNull()
        assertNotNull(body)
        assertEquals((body as Element).getProperty("background-color"), "powderblue")

        println(model.content())
    }

    @Test
    fun inlineCss(): Unit = runBlocking(Dispatchers.IO) {
        val data = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<body>\n" +
                "\n" +
                "<h1 style=\"color:blue;\">A Blue Heading</h1>\n" +
                "\n" +
                "<p style=\"color:red;\">A red paragraph.</p>\n" +
                "\n" +
                "</body>\n" +
                "</html>"

        val model = createModel()

        val buffer = Buffer()
        buffer.write(data.encodeToByteArray())
        model.parse(buffer)

        model.attachStylesheets()


        val h1 = model.nodes("h1").firstOrNull()
        assertNotNull(h1)
        assertEquals((h1 as Element).getProperty("color"), "blue")

        val p = model.nodes("p").firstOrNull()
        assertNotNull(p)
        assertEquals((p as Element).getProperty("color"), "red")
    }
}