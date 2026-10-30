package he.travelphoto.llo.data

import android.content.Context
import he.travelphoto.llo.util.Formats
import he.travelphoto.llo.util.Geo
import he.travelphoto.llo.util.ImageCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a real .docx (Open Packaging Convention) without external libraries.
 * Images are normalised to JPEG so HEIC/PNG photos render everywhere.
 */
class DocumentExporter(
    context: Context,
    private val storage: AlbumStorage,
) {
    private val cacheRoot = context.applicationContext.cacheDir

    fun write(album: Album, accent: Int, output: OutputStream): Boolean {
        val workDir = File(cacheRoot, "docx_${UUID.randomUUID().toString().take(8)}")
        if (!workDir.mkdirs()) return false
        return try {
            val writer = DocxWriter(album, accent, workDir)
            writer.build()
            writer.zip(output)
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private inner class DocxWriter(
        private val album: Album,
        private val accent: Int,
        private val workDir: File,
    ) {
        private val body = StringBuilder()
        private val media = mutableListOf<MediaFile>()
        private val imageRelations = mutableListOf<Pair<Int, String>>()
        private var imageCounter = 0
        private val accentHex = hex(accent)
        private val inkHex = "1B1B1F"
        private val mutedHex = "6B7280"
        private val softHex = mixWithWhite(accent, 0.88f)

        fun build() {
            appendCover()
            Formats.groupByDay(album.photos).forEach { group ->
                appendDay(group)
            }
            if (album.photos.isEmpty()) {
                body.append(paragraph("这个相册还没有照片。", size = 22, color = mutedHex, align = "center"))
            }
            body.append(
                paragraph(
                    text = "由 TravelPhoto 制作",
                    size = 16,
                    color = mutedHex,
                    align = "center",
                    before = 360,
                ),
            )
        }

        private fun appendCover() {
            val titleSize = if (album.title.length > 16) 48 else 64
            body.append(
                """
                <w:p>
                  <w:pPr>
                    <w:spacing w:before="1440" w:after="120"/>
                    <w:jc w:val="left"/>
                  </w:pPr>
                  <w:r><w:rPr><w:b/><w:color w:val="$inkHex"/><w:sz w:val="$titleSize"/></w:rPr>
                    <w:t xml:space="preserve">${esc(album.title)}</w:t></w:r>
                </w:p>
                """.trimIndent(),
            )
            body.append(
                """
                <w:p>
                  <w:pPr>
                    <w:pBdr><w:bottom w:val="single" w:sz="24" w:space="12" w:color="$accentHex"/></w:pBdr>
                    <w:spacing w:after="360"/>
                  </w:pPr>
                  <w:r><w:rPr><w:color w:val="$accentHex"/><w:sz w:val="4"/></w:rPr><w:t> </w:t></w:r>
                </w:p>
                """.trimIndent(),
            )
            val start = album.startAt
            val end = album.endAt
            val range = if (start != null && end != null) {
                Formats.dateRange(start, end)
            } else {
                "尚未记录日期"
            }
            val places = runCatching { Geo.clusterPhotos(album.locatedPhotos).size }.getOrDefault(0)
            body.append(paragraph(range, size = 26, color = mutedHex, after = 120))
            body.append(
                paragraph(
                    "${album.photos.size} 张照片 · $places 个地点",
                    size = 22,
                    color = mutedHex,
                    after = 720,
                ),
            )
            body.append(
                """
                <w:p>
                  <w:pPr>
                    <w:shd w:val="clear" w:color="auto" w:fill="$softHex"/>
                    <w:spacing w:before="240" w:after="240" w:left="240" w:right="240"/>
                  </w:pPr>
                  <w:r><w:rPr><w:i/><w:color w:val="$inkHex"/><w:sz w:val="24"/></w:rPr>
                    <w:t xml:space="preserve">把走过的路，装订成册。</w:t></w:r>
                </w:p>
                """.trimIndent(),
            )
            body.append(pageBreak())
        }

        private fun appendDay(group: he.travelphoto.llo.util.DayGroup) {
            body.append(
                """
                <w:p>
                  <w:pPr>
                    <w:keepNext/>
                    <w:spacing w:before="360" w:after="60"/>
                  </w:pPr>
                  <w:r><w:rPr><w:b/><w:color w:val="$accentHex"/><w:sz w:val="32"/></w:rPr>
                    <w:t xml:space="preserve">${esc(Formats.fullDate(group.dayStart))}</w:t></w:r>
                  <w:r><w:rPr><w:color w:val="$mutedHex"/><w:sz w:val="20"/></w:rPr>
                    <w:t xml:space="preserve"> · ${group.photos.size} 张</w:t></w:r>
                </w:p>
                """.trimIndent(),
            )
            group.photos.forEach { photo -> appendPhoto(photo) }
            // The generated record note belongs to the day/place group, not to every photo.
            val records = Formats.exportRecordGroups(album).filter { it.dayStart == group.dayStart }
            if (records.isNotEmpty()) {
                body.append(
                    paragraph(
                        "这一天的记录",
                        size = 22,
                        color = accentHex,
                        before = 220,
                        after = 80,
                    ),
                )
                records.forEach { record ->
                    body.append(
                        paragraph(
                            "${record.name} · ${record.note}",
                            size = 18,
                            color = mutedHex,
                            after = 60,
                        ),
                    )
                }
            }
        }

        private fun appendPhoto(photo: Photo) {
            val file = storage.photoFile(album.id, photo)
            if (!file.isFile) return
            val bitmap = ImageCodec.decodeBitmap(file, 1440) ?: return
            val width = bitmap.width
            val height = bitmap.height
            imageCounter += 1
            val index = imageCounter
            val jpeg = File(workDir, "image_$index.jpg")
            val bytes = ByteArrayOutputStream()
            val ok = try {
                ImageCodec.compressToJpeg(bitmap, bytes, 84)
            } finally {
                bitmap.recycle()
            }
            if (!ok || bytes.size() == 0) return
            runCatching { jpeg.writeBytes(bytes.toByteArray()) }.onFailure { return }
            media += MediaFile("image_$index.jpg", jpeg)
            imageRelations += index to "media/image_$index.jpg"

            val maxWidthEmu = 5_580_000L
            val maxHeightEmu = 6_840_000L
            val scale = minOf(
                maxWidthEmu.toDouble() / width,
                maxHeightEmu.toDouble() / height,
            )
            val cx = (width * scale).toLong().coerceAtLeast(1L)
            val cy = (height * scale).toLong().coerceAtLeast(1L)
            val relId = "rId${index + 2}"
            val docPrId = index + 100

            body.append(
                """
                <w:p>
                  <w:pPr><w:jc w:val="center"/><w:spacing w:before="220" w:after="60"/></w:pPr>
                  <w:r><w:drawing>
                    <wp:inline distT="0" distB="0" distL="0" distR="0">
                      <wp:extent cx="$cx" cy="$cy"/>
                      <wp:effectExtent l="0" t="0" r="0" b="0"/>
                      <wp:docPr id="$docPrId" name="TravelPhoto $index"/>
                      <wp:cNvGraphicFramePr>
                        <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
                      </wp:cNvGraphicFramePr>
                      <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                        <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                          <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                            <pic:nvPicPr>
                              <pic:cNvPr id="$docPrId" name="image_$index.jpg"/>
                              <pic:cNvPicPr><a:picLocks noChangeAspect="1"/></pic:cNvPicPr>
                            </pic:nvPicPr>
                            <pic:blipFill>
                              <a:blip r:embed="$relId"/>
                              <a:stretch><a:fillRect/></a:stretch>
                            </pic:blipFill>
                            <pic:spPr>
                              <a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                              <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                            </pic:spPr>
                          </pic:pic>
                        </a:graphicData>
                      </a:graphic>
                    </wp:inline>
                  </w:drawing></w:r>
                </w:p>
                """.trimIndent(),
            )
            val place = if (photo.hasLocation) {
                Formats.placeName(photo)
            } else {
                "位置未标注"
            }
            body.append(
                paragraph(
                    "${Formats.time(photo.takenAt)} · $place",
                    size = 18,
                    color = mutedHex,
                    align = "center",
                    after = if (photo.note.isNullOrBlank()) 220 else 80,
                ),
            )
            photo.note?.takeIf { it.isNotBlank() }?.let { note ->
                body.append(
                    paragraph(
                        note,
                        size = 17,
                        color = mutedHex,
                        align = "center",
                        after = 180,
                    ),
                )
            }
        }

        fun zip(output: OutputStream) {
            ZipOutputStream(output.buffered()).use { zip ->
                zip.put("[Content_Types].xml", contentTypes().toByteArray(Charsets.UTF_8))
                zip.put("_rels/.rels", rootRels().toByteArray(Charsets.UTF_8))
                zip.put("word/document.xml", document().toByteArray(Charsets.UTF_8))
                zip.put("word/_rels/document.xml.rels", documentRels().toByteArray(Charsets.UTF_8))
                zip.put("word/styles.xml", styles().toByteArray(Charsets.UTF_8))
                zip.put("word/footer1.xml", footer().toByteArray(Charsets.UTF_8))
                zip.put("docProps/core.xml", coreProps().toByteArray(Charsets.UTF_8))
                zip.put("docProps/app.xml", appProps().toByteArray(Charsets.UTF_8))
                media.forEach { item ->
                    zip.putNextEntry(ZipEntry("word/media/${item.name}"))
                    item.file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        private fun ZipOutputStream.put(name: String, bytes: ByteArray) {
            putNextEntry(ZipEntry(name))
            write(bytes)
            closeEntry()
        }

        private fun document(): String = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
              <w:body>
                $body
                <w:sectPr>
                  <w:footerReference w:type="default" r:id="rId2"/>
                  <w:pgSz w:w="11906" w:h="16838"/>
                  <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="708" w:footer="708" w:gutter="0"/>
                </w:sectPr>
              </w:body>
            </w:document>
        """.trimIndent().trimStart()

        private fun documentRels(): String {
            val imageRels = imageRelations.joinToString("") { (index, target) ->
                """<Relationship Id="rId${index + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="$target"/>"""
            }
            return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
                  $imageRels
                </Relationships>
            """.trimIndent()
        }

        private fun contentTypes(): String {
            val mediaOverrides = ""
            return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="jpg" ContentType="image/jpeg"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
                  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
                  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
                  $mediaOverrides
                </Types>
            """.trimIndent()
        }

        private fun rootRels(): String = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
              <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
            </Relationships>
        """.trimIndent()

        private fun styles(): String = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:docDefaults>
                <w:rPrDefault><w:rPr>
                  <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Microsoft YaHei" w:cs="Calibri"/>
                  <w:color w:val="$inkHex"/>
                  <w:sz w:val="22"/>
                  <w:szCs w:val="22"/>
                </w:rPr></w:rPrDefault>
                <w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="288" w:lineRule="auto"/></w:pPr></w:pPrDefault>
              </w:docDefaults>
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
                <w:name w:val="Normal"/><w:qFormat/>
              </w:style>
            </w:styles>
        """.trimIndent()

        private fun footer(): String = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:p>
                <w:pPr><w:jc w:val="center"/><w:spacing w:before="0" w:after="0"/></w:pPr>
                <w:r><w:rPr><w:color w:val="$mutedHex"/><w:sz w:val="16"/></w:rPr>
                  <w:t xml:space="preserve">TravelPhoto · 旅行相册　</w:t></w:r>
                <w:r><w:rPr><w:color w:val="$mutedHex"/><w:sz w:val="16"/></w:rPr>
                  <w:fldChar w:fldCharType="begin"/></w:r>
                <w:r><w:rPr><w:color w:val="$mutedHex"/><w:sz w:val="16"/></w:rPr>
                  <w:instrText xml:space="preserve"> PAGE </w:instrText></w:r>
                <w:r><w:rPr><w:color w:val="$mutedHex"/><w:sz w:val="16"/></w:rPr>
                  <w:fldChar w:fldCharType="end"/></w:r>
              </w:p>
            </w:ftr>
        """.trimIndent()

        private fun coreProps(): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
                    xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/"
                    xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dc:title>${esc(album.title)}</dc:title>
                  <dc:creator>TravelPhoto</dc:creator>
                  <cp:lastModifiedBy>TravelPhoto</cp:lastModifiedBy>
                  <dcterms:created xsi:type="dcterms:W3CDTF">$stamp</dcterms:created>
                  <dcterms:modified xsi:type="dcterms:W3CDTF">$stamp</dcterms:modified>
                </cp:coreProperties>
            """.trimIndent()
        }

        private fun appProps(): String = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"
                xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
              <Application>TravelPhoto</Application>
            </Properties>
        """.trimIndent()

        private fun paragraph(
            text: String,
            size: Int,
            color: String,
            align: String = "left",
            before: Int = 0,
            after: Int = 120,
        ): String = """
            <w:p>
              <w:pPr><w:jc w:val="$align"/><w:spacing w:before="$before" w:after="$after"/></w:pPr>
              <w:r><w:rPr><w:color w:val="$color"/><w:sz w:val="$size"/></w:rPr>
                <w:t xml:space="preserve">${esc(text)}</w:t></w:r>
            </w:p>
        """.trimIndent()

        private fun pageBreak(): String =
            """<w:p><w:r><w:br w:type="page"/></w:r></w:p>"""
    }

    private data class MediaFile(val name: String, val file: File)

    private fun hex(color: Int): String = String.format("%06X", color and 0xFFFFFF)

    private fun mixWithWhite(color: Int, amount: Float): String {
        val r = (color shr 16 and 0xFF)
        val g = (color shr 8 and 0xFF)
        val b = (color and 0xFF)
        fun blend(channel: Int): Int = (channel + (255 - channel) * amount).toInt().coerceIn(0, 255)
        return String.format("%02X%02X%02X", blend(r), blend(g), blend(b))
    }

    private fun esc(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch == '\n' || ch == '\r') append(' ') else append(ch)
            }
        }
    }
}
