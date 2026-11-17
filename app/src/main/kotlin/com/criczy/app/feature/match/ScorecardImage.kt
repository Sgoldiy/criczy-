package com.criczy.app.feature.match

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.criczy.app.R
import com.criczy.app.core.model.BowlerLine
import com.criczy.app.core.model.MatchDetail
import com.criczy.app.ui.format.Formatters
import java.io.File

/**
 * Draws a scorecard summary as a 1080px-wide image for sharing: teams, every innings total,
 * the result, and the two best batters and bowlers of each innings.
 */
object ScorecardImage {
    private const val WIDTH = 1080
    private const val PAD = 64f
    private const val INK = 0xFF0B1018.toInt()
    private const val AMBER = 0xFFFFB627.toInt()
    private const val MUTED = 0xFFA9B3C3.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun render(context: Context, detail: MatchDetail): Bitmap {
        val score = ResourcesCompat.getFont(context, R.font.barlow_condensed_bold) ?: Typeface.DEFAULT_BOLD
        val body = ResourcesCompat.getFont(context, R.font.manrope) ?: Typeface.DEFAULT
        val match = detail.match
        val height = (520 + detail.innings.size * 330).coerceAtMost(2400)
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun paint(color: Int, size: Float, face: Typeface, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; typeface = face; textAlign = align }
        fun colorOf(hex: String?, fallback: Int) = hex?.removePrefix("#")?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() } ?: fallback
        fun fit(text: String, p: Paint, max: Float): String {
            if (p.measureText(text) <= max) return text
            var cut = text
            while (cut.length > 1 && p.measureText("$cut…") > max) cut = cut.dropLast(1)
            return "$cut…"
        }

        canvas.drawColor(INK)
        val band = Paint().apply {
            shader = LinearGradient(0f, 0f, WIDTH.toFloat(), 0f, colorOf(match.home.colorHex, AMBER), colorOf(match.away.colorHex, 0xFF58A6FF.toInt()), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 14f, band)

        var y = 110f
        canvas.drawText(fit(match.competition.name.uppercase(), paint(MUTED, 34f, body), WIDTH - 2 * PAD), PAD, y, paint(MUTED, 34f, body))
        y += 52f
        canvas.drawText(fit(listOf(match.name, match.venue?.name).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "), paint(MUTED, 30f, body), WIDTH - 2 * PAD), PAD, y, paint(MUTED, 30f, body))
        y += 110f
        for (team in listOf(match.home, match.away)) {
            val scores = match.inningsFor(team.id)
            canvas.drawText(fit(team.name, paint(WHITE, 64f, score), 560f), PAD, y, paint(WHITE, 64f, score))
            if (scores.isNotEmpty()) {
                val oversPaint = paint(MUTED, 32f, body, Paint.Align.RIGHT)
                val overs = "(${scores.last().overs})"
                canvas.drawText(overs, WIDTH - PAD, y, oversPaint)
                canvas.drawText(scores.joinToString(" & ") { it.scoreText }, WIDTH - PAD - oversPaint.measureText(overs) - 18f, y, paint(WHITE, 76f, score, Paint.Align.RIGHT))
            }
            y += 96f
        }
        y += 8f
        val status = Formatters.statusLine(match)
        val statusPaint = paint(INK, 36f, body)
        val shown = fit(status, statusPaint, WIDTH - 2 * PAD - 56f)
        canvas.drawRoundRect(RectF(PAD, y - 50f, PAD + statusPaint.measureText(shown) + 56f, y + 22f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER })
        canvas.drawText(shown, PAD + 28f, y, statusPaint)
        y += 96f

        for (innings in detail.innings) {
            if (y > height - 200) break
            val team = match.team(innings.battingTeamId)
            canvas.drawRect(PAD, y, WIDTH - PAD, y + 2f, Paint().apply { color = 0xFF283344.toInt() })
            y += 62f
            canvas.drawText("${team?.shortName ?: "INNS"}  ${innings.score.scoreText}  (${innings.score.overs} ov)", PAD, y, paint(AMBER, 46f, score))
            y += 58f
            val batters = innings.batted.sortedByDescending { it.runs }.take(2)
            val bowlers = innings.bowlers.sortedWith(compareByDescending<BowlerLine> { it.wickets }.thenBy { it.runs }).take(2)
            for (row in 0 until 2) {
                batters.getOrNull(row)?.let {
                    canvas.drawText(fit(it.name, paint(WHITE, 34f, body), 300f), PAD, y, paint(WHITE, 34f, body))
                    canvas.drawText("${it.runs}${if (it.isOut) "" else "*"} (${it.balls})", 520f, y, paint(WHITE, 40f, score, Paint.Align.RIGHT))
                }
                bowlers.getOrNull(row)?.let {
                    canvas.drawText(fit(it.name, paint(WHITE, 34f, body), 300f), 570f, y, paint(WHITE, 34f, body))
                    canvas.drawText("${it.wickets}/${it.runs} (${it.overs})", WIDTH - PAD, y, paint(WHITE, 40f, score, Paint.Align.RIGHT))
                }
                y += 58f
            }
            y += 30f
        }
        canvas.drawText("CRICZY", WIDTH - PAD, height - 48f, paint(AMBER, 44f, score, Paint.Align.RIGHT))
        return bitmap
    }

    /** Writes the image to the app's private share folder and returns a content URI for it. */
    fun write(context: Context, detail: MatchDetail): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "scorecard-${detail.match.id}.png")
        val bitmap = render(context, detail)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
