package moe.antimony.hoshi.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipInputStream

class EpubBookParserTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parsesExtractedEpubUsingContainerManifestAndSpineLikePoc() {
        val root = tempFolder.newFolder("book")
        writeExtractedEpub(root)

        val book = EpubBookParser().parse(root)

        assertEquals("Sample Book", book.title)
        assertEquals(
            listOf("OPS/text/chapter-2.xhtml", "OPS/text/chapter-1.xhtml"),
            book.chapters.map { it.href },
        )
        assertEquals("chapter-2", book.chapters.first().id)
        assertEquals("application/xhtml+xml", book.chapters.first().mediaType)
        assertEquals("<html><body>Second</body></html>", book.chapters.first().html)
        assertEquals("OPS/images/cover.jpg", book.coverHref)
        assertArrayEquals("body {}".toByteArray(), book.readResource("/OPS/styles/book.css"))
        assertEquals("text/css", book.mediaType("OPS/styles/book.css"))
        assertEquals("image/jpeg", book.mediaType("OPS/images/cover.jpg"))
    }

    @Test
    fun preservesSelfClosingExternalScriptsForDirectXhtmlLoading() {
        val root = tempFolder.newFolder("self-closing-script-book")
        writeExtractedEpub(
            root = root,
            firstChapterHtml = """
                <html>
                <head>
                  <script type="text/javascript" src="../js/kobo.js"/>
                </head>
                <body><p>Visible text</p></body>
                </html>
            """.trimIndent(),
        )

        val chapter = EpubBookParser().parse(root).chapters.last()

        assertEquals(
            """
            <html>
            <head>
              <script type="text/javascript" src="../js/kobo.js"/>
            </head>
            <body><p>Visible text</p></body>
            </html>
            """.trimIndent(),
            chapter.html,
        )
    }

    @Test
    fun parsesTest3WithReadableBookInfoAndXhtmlScriptsPreserved() {
        val root = tempFolder.newFolder("test3")
        extractEpub(File("../testdata/test3.epub"), root)

        val book = EpubBookParser().parse(root)

        assertEquals("Re：ゼロから始める異世界生活 13", book.title)
        assertEquals("item/image/cover.jpg", book.coverHref)
        assertEquals(44, book.chapters.size)
        assertEquals("item/xhtml/p-cover.xhtml", book.chapters.first().href)
        assertEquals(143553, book.bookInfo.characterCount)
        assertEquals(true, book.chapters.any { chapter -> selfClosingScriptRegex.containsMatchIn(chapter.html) })
        assertEquals("image/jpeg", book.mediaType(book.coverHref.orEmpty()))
    }

    private fun writeExtractedEpub(
        root: File,
        firstChapterHtml: String = "<html><body>First</body></html>",
    ) {
        root.resolve("META-INF").mkdirs()
        root.resolve("META-INF/container.xml").writeText(
            """
            <?xml version="1.0"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent(),
        )

        root.resolve("OPS/text").mkdirs()
        root.resolve("OPS/styles").mkdirs()
        root.resolve("OPS/images").mkdirs()
        root.resolve("OPS/js").mkdirs()
        root.resolve("OPS/text/chapter-1.xhtml").writeText(firstChapterHtml)
        root.resolve("OPS/text/chapter-2.xhtml").writeText("<html><body>Second</body></html>")
        root.resolve("OPS/styles/book.css").writeText("body {}")
        root.resolve("OPS/images/cover.jpg").writeBytes(byteArrayOf(1, 2, 3))
        root.resolve("OPS/js/kobo.js").writeText("")
        root.resolve("OPS/package.opf").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Sample Book</dc:title>
              </metadata>
              <manifest>
                <item id="chapter-1" href="text/chapter-1.xhtml" media-type="application/xhtml+xml"/>
                <item id="chapter-2" href="text/chapter-2.xhtml" media-type="application/xhtml+xml"/>
                <item id="style" href="styles/book.css" media-type="text/css"/>
                <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                <item id="script" href="js/kobo.js" media-type="application/javascript"/>
              </manifest>
              <spine page-progression-direction="rtl">
                <itemref idref="chapter-2"/>
                <itemref idref="chapter-1"/>
              </spine>
            </package>
            """.trimIndent(),
        )
    }

    private fun extractEpub(epub: File, root: File) {
        ZipInputStream(epub.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val output = root.resolve(entry.name).canonicalFile
                val canonicalRoot = root.canonicalFile
                require(output.path == canonicalRoot.path || output.path.startsWith(canonicalRoot.path + File.separator)) {
                    "Unsafe EPUB entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private companion object {
        val selfClosingScriptRegex = Regex("<script\\b[^>]*/>", RegexOption.IGNORE_CASE)
    }
}
