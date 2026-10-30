package he.travelphoto.llo.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import he.travelphoto.llo.util.ExportRecordGroup
import he.travelphoto.llo.util.Formats
import he.travelphoto.llo.util.Geo
import he.travelphoto.llo.util.ImageCodec
import java.io.OutputStream

/**
 * Print-ready A4 PDF.
 *
 * The page grid can hold 1..8 photos per page. Photos are always fitted inside their frame
 * (never cropped) and ImageCodec applies the EXIF orientation first, so portraits/photos taken
 * with a rotated phone still come out upright.
 *
 * The generated record note of a day is printed once after that day's photos rather than under
 * every picture. A custom note typed on an individual photo stays attached to that photo.
 */
class PdfExporter(private val storage: AlbumStorage) {

    fun write(
        album: Album,
        accent: Int,
        output: OutputStream,
        photosPerPage: Int = 4,
    ): Boolean {
        val document = PdfDocument()
        return try {
            PdfWriter(document, album, accent, photosPerPage.coerceIn(1, 8)).render()
            document.writeTo(output)
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { document.close() }
        }
    }

    private inner class PdfWriter(
        private val document: PdfDocument,
        private val album: Album,
        accent: Int,
        private val photosPerPage: Int,
    ) {
        private val pageWidth = 595f
        private val pageHeight = 842f
        private val left = 48f
        private val right = 547f
        private val contentTop = 76f
        private val contentBottom = 776f

        private val ink = 0xFF1B1B1F.toInt()
        private val muted = 0xFF6B7280.toInt()
        private val line = 0xFFE5E7EB.toInt()
        private val accentColor = accent or 0xFF000000.toInt()
        private val accentSoft = blend(accent, 0.88f)

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 36f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        private val recordHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 10f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 8f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = line
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var pageNumber = 0

        fun render() {
            drawCover()
            drawTimeline()
        }

        private fun startPage() {
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(
                pageWidth.toInt(),
                pageHeight.toInt(),
                pageNumber,
            ).create()
            page = document.startPage(info)
            val target = page!!.canvas
            target.drawColor(Color.WHITE)
            canvas = target
        }

        private fun finishPage() {
            val target = canvas ?: return
            val current = page ?: return
            target.drawLine(left, 800f, right, 800f, linePaint)
            target.drawText("TravelPhoto", left, 816f, footerPaint)
            val label = pageNumber.toString()
            target.drawText(label, right - footerPaint.measureText(label), 816f, footerPaint)
            document.finishPage(current)
            canvas = null
            page = null
        }

        private fun newContentPage(): Float {
            finishPage()
            startPage()
            return contentTop
        }

        private fun drawCover() {
            startPage()
            val target = canvas ?: return
            val start = album.startAt
            val end = album.endAt
            val range = if (start != null && end != null) Formats.dateRange(start, end) else "一段尚未命名的旅程"
            val places = runCatching { Geo.clusterPhotos(album.locatedPhotos).size }.getOrDefault(0)
            titlePaint.textSize = if (album.title.length > 15) 30f else 38f
            val titleBottom = drawWrapped(target, album.title, titlePaint, right - left, left, 124f, titlePaint.textSize * 1.24f)
            target.drawRect(left, titleBottom + 12f, left + 86f, titleBottom + 16f, accentPaint)
            var y = titleBottom + 44f
            target.drawText(range, left, y, bodyPaint)
            y += 20f
            target.drawText(
                "${album.photos.size} 张照片 · $places 个地点 · ${Formats.groupByDay(album.photos).size} 天",
                left,
                y,
                mutedPaint,
            )

            val imageTop = y + 40f
            val imageBottom = 636f
            val coverPhoto = album.coverPhoto ?: album.photos.firstOrNull()
            val drew = coverPhoto != null && drawContainedPhoto(
                target = target,
                photo = coverPhoto,
                destination = RectF(left, imageTop, right, imageBottom),
                maxSize = 1800,
            )
            if (!drew) {
                val placeholder = RectF(left, imageTop, right, imageBottom)
                target.drawRoundRect(placeholder, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentSoft })
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    textSize = 22f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                target.drawText("旅 行 相 册", placeholder.centerX(), placeholder.centerY(), labelPaint)
            }

            val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                textSize = 15f
                typeface = Typeface.create("serif", Typeface.ITALIC)
            }
            target.drawText("把走过的路，装订成册。", left, 704f, quotePaint)
            target.drawText("TravelPhoto 旅行相册", left, 728f, footerPaint)
            finishPage()
        }

        private fun drawTimeline() {
            if (album.photos.isEmpty()) {
                startPage()
                canvas?.drawText("这个相册还没有照片。", left, contentTop + 20f, bodyPaint)
                finishPage()
                return
            }

            val recordsByDay = Formats.exportRecordGroups(album).groupBy { it.dayStart }
            val days = Formats.groupByDay(album.photos)
            startPage()
            var firstDay = true
            days.forEach { day ->
                if (!firstDay) newContentPage()
                firstDay = false
                var y = contentTop
                drawDayHeader(day.dayStart, day.photos.size, y)
                y += 48f

                val records = recordsByDay[day.dayStart].orEmpty()
                val chunks = day.photos.chunked(photosPerPage)
                chunks.forEachIndexed { index, chunk ->
                    if (index > 0) {
                        y = newContentPage()
                        drawDayHeader(day.dayStart, day.photos.size, y)
                        y += 48f
                    }
                    val lastChunk = index == chunks.lastIndex
                    val reserved = if (lastChunk) noteBandHeight(records) else 0f
                    val gridBottom = (contentBottom - reserved).coerceAtLeast(y + 90f)
                    drawPhotoGrid(chunk, y, gridBottom)
                    if (lastChunk && records.isNotEmpty()) {
                        drawRecordNotes(records, gridBottom + 16f)
                    }
                    y = gridBottom
                }
            }
            finishPage()
        }

        private fun drawDayHeader(dayStart: Long, count: Int, top: Float) {
            val target = canvas ?: return
            target.drawText(Formats.fullDate(dayStart), left, top + 18f, headingPaint)
            val dateWidth = headingPaint.measureText(Formats.fullDate(dayStart))
            target.drawText("· $count 张", left + dateWidth + 8f, top + 18f, mutedPaint)
            target.drawRect(left, top + 28f, left + 36f, top + 31f, accentPaint)
        }

        private fun drawPhotoGrid(photos: List<Photo>, top: Float, bottom: Float) {
            val target = canvas ?: return
            val columns = when {
                photos.size == 1 -> 1
                photos.size == 2 -> 1
                else -> 2
            }
            val rows = ((photos.size + columns - 1) / columns).coerceAtLeast(1)
            val gap = 10f
            val cellWidth = (right - left - gap * (columns - 1)) / columns
            val cellHeight = ((bottom - top) - gap * (rows - 1)).coerceAtLeast(70f) / rows
            photos.forEachIndexed { index, photo ->
                val column = index % columns
                val row = index / columns
                val x = left + column * (cellWidth + gap)
                val y = top + row * (cellHeight + gap)
                drawPhotoCell(photo, x, y, cellWidth, cellHeight)
            }
        }

        private fun drawPhotoCell(
            photo: Photo,
            x: Float,
            top: Float,
            width: Float,
            height: Float,
        ) {
            val target = canvas ?: return
            val customNote = photo.note?.trim().orEmpty()
            val noteLines = if (customNote.isNotEmpty() && height > 150f) 2 else 1
            val captionHeight = if (noteLines == 2) 40f else 27f
            val imageHeight = (height - captionHeight).coerceAtLeast(54f)
            val frame = RectF(x, top, x + width, top + imageHeight - 4f)
            val decodeSize = when {
                photosPerPage <= 2 -> 2400
                photosPerPage <= 4 -> 2000
                else -> 1600
            }
            val drew = drawContainedPhoto(target, photo, frame, maxSize = decodeSize)
            if (!drew) {
                target.drawText("照片不可用", frame.centerX() - 33f, frame.centerY(), mutedPaint)
            }
            val place = if (photo.hasLocation) Formats.placeName(photo) else "位置未标注"
            val caption = "${Formats.time(photo.takenAt)} · $place"
            target.drawText(ellipsize(caption, captionPaint, width), x, top + imageHeight + 12f, captionPaint)
            if (noteLines == 2) {
                target.drawText(ellipsize(customNote, mutedPaint, width), x, top + imageHeight + 27f, mutedPaint)
            }
        }

        private fun noteBandHeight(records: List<ExportRecordGroup>): Float {
            if (records.isEmpty()) return 0f
            return (30f + records.take(4).size * 52f).coerceAtMost(260f)
        }

        private fun drawRecordNotes(records: List<ExportRecordGroup>, top: Float) {
            val target = canvas ?: return
            var y = top
            target.drawText("这一天的记录", left, y + 12f, recordHeadingPaint)
            y += 30f
            records.take(4).forEach { record ->
                if (y > contentBottom - 18f) return@forEach
                target.drawText(ellipsize("· ${record.name}", recordHeadingPaint, right - left), left, y, recordHeadingPaint)
                y += 15f
                y = drawWrapped(target, record.note, mutedPaint, right - left, left, y, 13f) + 22f
            }
        }

        /**
         * Fits the complete bitmap inside [destination] without cropping; the photo keeps its
         * original aspect ratio. ImageCodec has already applied the EXIF orientation.
         */
        private fun drawContainedPhoto(
            target: Canvas,
            photo: Photo,
            destination: RectF,
            maxSize: Int,
        ): Boolean {
            val file = storage.photoFile(album.id, photo)
            if (!file.isFile) return false
            val bitmap = ImageCodec.decodeBitmap(file, maxSize) ?: return false
            return try {
                // No background and no rounded clip: a light coloured photo would otherwise show
                // a paper-coloured frame around the picture. Draw the photo edge to edge instead.
                val scale = minOf(
                    destination.width() / bitmap.width,
                    destination.height() / bitmap.height,
                ).coerceAtMost(1f)
                val drawWidth = bitmap.width * scale
                val drawHeight = bitmap.height * scale
                val drawLeft = destination.left + (destination.width() - drawWidth) / 2f
                val drawTop = destination.top + (destination.height() - drawHeight) / 2f
                target.drawBitmap(
                    bitmap,
                    null,
                    RectF(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight),
                    imagePaint,
                )
                true
            } catch (_: Throwable) {
                false
            } finally {
                bitmap.recycle()
            }
        }

        private fun drawWrapped(
            target: Canvas,
            text: String,
            paint: Paint,
            maxWidth: Float,
            x: Float,
            baseline: Float,
            lineHeight: Float,
        ): Float {
            var y = baseline
            text.split('\n').forEach { paragraph ->
                var remaining = paragraph
                if (remaining.isEmpty()) {
                    y += lineHeight
                    return@forEach
                }
                while (remaining.isNotEmpty()) {
                    val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                    val piece = remaining.substring(0, count)
                    target.drawText(piece, x, y, paint)
                    remaining = remaining.substring(count)
                    y += lineHeight
                }
            }
            return y - lineHeight
        }

        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val count = paint.breakText(text, true, maxWidth - paint.measureText("…"), null)
            return text.take(count.coerceAtLeast(1)).trimEnd() + "…"
        }

        private fun blend(color: Int, amount: Float): Int {
            val red = color shr 16 and 0xFF
            val green = color shr 8 and 0xFF
            val blue = color and 0xFF
            fun channel(value: Int): Int = (value + (255 - value) * amount).toInt().coerceIn(0, 255)
            return Color.rgb(channel(red), channel(green), channel(blue))
        }
    }
}
