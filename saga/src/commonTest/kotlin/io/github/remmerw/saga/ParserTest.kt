package io.github.remmerw.saga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun breakTest(): Unit = runBlocking(Dispatchers.IO) {
        val data = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h2>Header 1</h1>\n" +
                "<br>\n" +
                "<p>A paragraph.</p>\n" +
                "\n" +
                "</body>\n" +
                "</html>"

        val model = createModel()

        val buffer = Buffer()
        buffer.write(data.encodeToByteArray())
        model.parse(buffer)

        model.normalize()

        val br = model.nodes(Tag.BR.tag()).firstOrNull()
        assertNotNull(br)
        assertTrue(model.getChildren(br.entity()).isEmpty())

    }
}