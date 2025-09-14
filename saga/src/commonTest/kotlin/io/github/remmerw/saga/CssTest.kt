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

        model.normalize()


        val h1 = model.nodes("h1").firstOrNull()
        assertNotNull(h1)
        assertEquals((h1 as Element).getAttribute("color"), "blue")

        val p = model.nodes("p").firstOrNull()
        assertNotNull(p)
        assertEquals((p as Element).getAttribute("color"), "red")

        val body = model.nodes("body").firstOrNull()
        assertNotNull(body)
        assertEquals((body as Element).getAttribute("background-color"), "powderblue")

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

        model.normalize()

        val h1 = model.nodes(Tag.H1.tag()).firstOrNull()
        assertNotNull(h1)
        assertEquals((h1 as Element).getAttribute("color"), "blue")

        val p = model.nodes(Tag.P.tag()).firstOrNull()
        assertNotNull(p)
        assertEquals((p as Element).getAttribute("color"), "red")
    }

    @Test
    fun internalClassCss(): Unit = runBlocking(Dispatchers.IO) {
        val data = "<html>\n" +
                "<head>\n" +
                "<style>\n" +
                "h1.intro {\n" +
                "  color: blue;\n" +
                "}\n" +
                "\n" +
                "p.important {\n" +
                "  color: green;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h1 class=\"intro\">Header 1</h1>\n" +
                "<p>A paragraph.</p>\n" +
                "<p class=\"important\">Note that this is an important paragraph. :)</p>\n" +
                "\n" +
                "</body>\n" +
                "</html>"

        val model = createModel()

        val buffer = Buffer()
        buffer.write(data.encodeToByteArray())
        model.parse(buffer)

        model.normalize()


        val h1 = model.nodes(Tag.H1.tag()).firstOrNull()
        assertNotNull(h1)
        assertEquals((h1 as Element).getAttribute("color"), "blue")

        val p = model.nodes(Tag.P.tag())[1]
        assertNotNull(p)
        assertEquals((p as Element).getAttribute("color"), "green")
    }


    @Test
    fun internalMultiClassCss(): Unit = runBlocking(Dispatchers.IO) {
        val data = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<style>\n" +
                "h2.intro {\n" +
                "  color: blue;\n" +
                "  text-align: center;\n" +
                "}\n" +
                "\n" +
                ".important {\n" +
                "  background-color: yellow;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h2 class=\"intro important\">Header 1</h1>\n" +
                "<p>A paragraph.</p>\n" +
                "\n" +
                "</body>\n" +
                "</html>"

        val model = createModel()

        val buffer = Buffer()
        buffer.write(data.encodeToByteArray())
        model.parse(buffer)

        model.normalize()


        val h1 = model.nodes(Tag.H2.tag()).firstOrNull()
        assertNotNull(h1)
        assertEquals((h1 as Element).getAttribute("color"), "blue")
        assertEquals(h1.getAttribute("text-align"), "center")
        assertEquals(h1.getAttribute("background-color"), "yellow")

    }
}