package io.github.brad1014z.hanzi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.LaunchedEffect
import io.github.brad1014z.hanzi.engine.data.CharacterData
import io.github.brad1014z.hanzi.engine.geometry.Point
import io.github.brad1014z.hanzi.engine.geometry.arcLength
import io.github.brad1014z.hanzi.engine.grading.RejectReason
import io.github.brad1014z.hanzi.engine.grading.StrokeVerdict
import io.github.brad1014z.hanzi.engine.progress.PracticeRecord
import io.github.brad1014z.hanzi.engine.quiz.QuizEngine
import io.github.brad1014z.hanzi.engine.quiz.QuizState
import io.github.brad1014z.hanzi.engine.quiz.drawnCorrectly
import io.github.brad1014z.hanzi.engine.quiz.toGrade
import io.github.brad1014z.hanzi.engine.speech.SpeechService

private enum class Mode { DEMO, QUIZ }

/**
 * The practice canvas (spec 07 screen 3): demo animation + quiz grading on one canvas.
 * Enters in DEMO (watch the character draw itself), then switches to QUIZ.
 *
 * TODO(son, S4): the verdict feedback here is deliberately plain — colors, sounds,
 * haptics, and the shake on a reject are yours to design.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
    character: CharacterData,
    sounds: SoundPlayer,
    speech: SpeechService = SpeechService.Silent,
    speechAvailable: Boolean = false,
    autoPlay: Boolean = true,
    // Recall mode (M3, spec 05/07): reviews and the boss start straight in the quiz —
    // no demo reveal — and the tracing guide defaults off (scaffolding that fades).
    startInQuiz: Boolean = false,
    guideDefaultOn: Boolean = !startInQuiz,
    allowGuide: Boolean = true, // boss: written fully from memory (spec 04)
    allowRetry: Boolean = true, // quest cards advance via the SRS re-test, not "Again"
    sessionTag: String = "practice",
    onRecord: (PracticeRecord) -> Unit = {},
    canAdvance: Boolean = true,
    isSaving: Boolean = false,
    saveError: String? = null,
    onRetrySave: () -> Unit = {},
    strokeAttemptObserver: StrokeAttemptObserver = StrokeAttemptObserver.None,
    onSoundToggle: (Boolean) -> Unit = {},
    onExit: () -> Unit,
    onNext: () -> Unit,
) {
    val engine = remember { QuizEngine() }
    var mode by remember(character) { mutableStateOf(if (startInQuiz) Mode.QUIZ else Mode.DEMO) }
    var quiz by remember(character) { mutableStateOf(engine.start(character)) }
    var quizStartedAt by remember(character) {
        mutableLongStateOf(if (startInQuiz) System.currentTimeMillis() else 0L)
    }
    var demoRun by remember(character) { mutableIntStateOf(0) }
    var demoStrokeIndex by remember(character) { mutableIntStateOf(0) }
    val demoProgress = remember(character) { Animatable(0f) }
    var showGuide by remember(character) { mutableStateOf(guideDefaultOn && allowGuide) }
    var feedback by remember(character) {
        mutableStateOf(
            if (startInQuiz) "From memory — stroke 1 of ${character.strokeCount}."
            else "Watch the stroke order, then try it yourself.",
        )
    }
    var liveStroke by remember(character) { mutableStateOf<List<Point>>(emptyList()) }
    var liveTimedStroke by remember(character) { mutableStateOf<List<TimedStrokePoint>>(emptyList()) }
    var liveStrokeStartedAt by remember(character) { mutableLongStateOf(0L) }
    var fadingReject by remember(character) { mutableStateOf<List<Point>?>(null) }
    val rejectAlpha = remember(character) { Animatable(0f) }
    var hintStrokeFlash by remember(character) { mutableStateOf(false) }
    val view = LocalView.current
    val reducedMotion = remember(view) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                view.context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }

    // Demo animation driver: strokes grow along their medians one by one.
    LaunchedEffect(character, demoRun, mode) {
        if (mode != Mode.DEMO) return@LaunchedEffect
        // Hear the character as the demo begins (spec: auto-play on demo, gated).
        if (autoPlay && speechAvailable) {
            speech.speak(
                character.lessonContent?.pronunciationAudio?.spokenText ?: character.character,
                "zh-Hans",
            )
        }
        for (i in character.medians.indices) {
            demoStrokeIndex = i
            demoProgress.snapTo(0f)
            val arc = character.medians[i].arcLength()
            val durationMs = (arc / 700.0 * 600.0).toInt().coerceIn(250, 800)
            demoProgress.animateTo(1f, tween(durationMs))
            kotlinx.coroutines.delay(120)
        }
        demoStrokeIndex = character.medians.size
        kotlinx.coroutines.delay(350)
        mode = Mode.QUIZ
        if (quizStartedAt == 0L) quizStartedAt = System.currentTimeMillis()
        feedback = "Your turn — stroke 1 of ${character.strokeCount}."
    }

    // Rejected stroke fades away instead of staying as clutter (spec 05, failure UX).
    LaunchedEffect(fadingReject) {
        if (fadingReject != null) {
            if (reducedMotion) {
                rejectAlpha.snapTo(0f)
            } else {
                rejectAlpha.snapTo(0.8f)
                rejectAlpha.animateTo(0f, tween(200))
            }
            fadingReject = null
        }
    }

    // Hint: flash the expected stroke's median briefly (spec 05: "show me").
    LaunchedEffect(hintStrokeFlash) {
        if (hintStrokeFlash) {
            kotlinx.coroutines.delay(900)
            hintStrokeFlash = false
        }
    }

    // Completion: persist the card (M1, spec 03) and hear the character one last time
    // (spec 07: reinforcement).
    LaunchedEffect(quiz.isComplete) {
        if (quiz.isComplete) {
            val now = System.currentTimeMillis()
            onRecord(
                PracticeRecord(
                    character = character.character,
                    reviewedAt = now,
                    grade = quiz.toGrade(),
                    drawnCorrectly = quiz.drawnCorrectly(),
                    durationMs = (now - quizStartedAt).takeIf { quizStartedAt > 0L },
                    session = sessionTag,
                ),
            )
            if (speechAvailable) {
                kotlinx.coroutines.delay(400) // let the completion sound land first
                speech.speak(
                    character.lessonContent?.pronunciationAudio?.spokenText ?: character.character,
                    "zh-Hans",
                )
            }
        }
    }

    fun onStrokeFinished(points: List<Point>, timedPoints: List<TimedStrokePoint>) {
        if (mode != Mode.QUIZ || quiz.isComplete) return
        val expectedIndex = quiz.expectedIndex
        val retryCount = quiz.rejectsOnCurrent
        val (next, verdict) = engine.submitStroke(quiz, points)
        quiz = next
        strokeAttemptObserver.record(
            StrokeAttemptEvent(
                character = character.character,
                expectedStrokeIndex = expectedIndex,
                points = timedPoints,
                verdict = verdict,
                retryCount = retryCount,
            ),
        )
        when (verdict) {
            is StrokeVerdict.Accept -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                if (next.isComplete) sounds.playComplete() else sounds.playCorrect()
            }
            is StrokeVerdict.WrongOrder, is StrokeVerdict.Reject -> sounds.playWrong()
            StrokeVerdict.Ignored -> Unit
        }
        feedback = when (verdict) {
            is StrokeVerdict.Accept -> when {
                next.isComplete -> "完成! Character complete."
                verdict.sloppy -> "Close enough — stroke ${next.expectedIndex + 1} of ${character.strokeCount}."
                else -> "Nice stroke — ${next.expectedIndex + 1} of ${character.strokeCount}."
            }
            is StrokeVerdict.WrongOrder -> {
                fadingReject = points
                "Right stroke, wrong order — that one is stroke ${verdict.matchedIndex + 1}."
            }
            is StrokeVerdict.Reject -> {
                fadingReject = points
                when (verdict.reason) {
                    RejectReason.WRONG_DIRECTION -> "Follow the arrow from the starting dot."
                    RejectReason.LENGTH_OUT_OF_RANGE -> "Start at the dot and finish near the arrow tip."
                    RejectReason.TOO_FAR -> "Start closer to the dot."
                }
            }
            StrokeVerdict.Ignored -> feedback
        }
        if (engine.shouldOfferHint(quiz)) {
            feedback += "  Tap Hint to see it drawn."
        }
    }

    val dark = isSystemInDarkTheme()
    val inkColor = if (dark) PracticeColors.inkDark else PracticeColors.ink

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = character.character, fontSize = 34.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (mode == Mode.DEMO) "demo" else
                    if (quiz.isComplete) "done" else "stroke ${quiz.expectedIndex + 1} / ${character.strokeCount}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onExit) { Text("Exit") }
        }
        // Pinyin + meaning + audio: the character should always be more than a shape
        // (spec 00); the speaker hides when no Mandarin voice exists (spec 01 fallback).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${character.primaryPinyin} · ${character.learnerGloss}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (speechAvailable) {
                TextButton(onClick = {
                    speech.speak(
                        character.lessonContent?.pronunciationAudio?.spokenText ?: character.character,
                        "zh-Hans",
                    )
                }) {
                    Text("🔊", fontSize = 18.sp)
                }
            }
        }

        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics {
                        contentDescription = if (mode == Mode.DEMO) {
                            "Stroke order demonstration for ${character.character}"
                        } else {
                            "Writing pad for ${character.character}, stroke ${quiz.expectedIndex + 1} " +
                                "of ${character.strokeCount}"
                        }
                    }
                    .pointerInput(character, mode) {
                        if (mode != Mode.QUIZ) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                val m = CanvasMapping(size.width.toFloat(), size.height.toFloat())
                                val point = m.toEngine(offset)
                                liveStrokeStartedAt = android.os.SystemClock.elapsedRealtime()
                                liveStroke = listOf(point)
                                liveTimedStroke = listOf(TimedStrokePoint(point, 0))
                            },
                            onDrag = { change, _ ->
                                val m = CanvasMapping(size.width.toFloat(), size.height.toFloat())
                                val point = m.toEngine(change.position)
                                liveStroke = liveStroke + point
                                liveTimedStroke = liveTimedStroke + TimedStrokePoint(
                                    point,
                                    android.os.SystemClock.elapsedRealtime() - liveStrokeStartedAt,
                                )
                            },
                            onDragEnd = {
                                onStrokeFinished(liveStroke, liveTimedStroke)
                                liveStroke = emptyList()
                                liveTimedStroke = emptyList()
                            },
                            onDragCancel = {
                                liveStroke = emptyList()
                                liveTimedStroke = emptyList()
                            },
                        )
                    },
            ) {
                val m = CanvasMapping(size.width, size.height)
                drawRiceGrid(m)

                // Faint full-character target (tracing guide; first-learn default ON).
                if (showGuide) {
                    for (outline in character.strokeOutlines) {
                        drawPath(outline.toComposePath(m), PracticeColors.faintTarget)
                    }
                }

                when (mode) {
                    Mode.DEMO -> {
                        // Thick-median demo variant (spec 05): completed strokes solid,
                        // current stroke grows along its median.
                        for (i in 0 until demoStrokeIndex.coerceAtMost(character.medians.size)) {
                            drawMedian(character.medians[i], m, inkColor)
                        }
                        if (demoStrokeIndex < character.medians.size) {
                            val partial = character.medians[demoStrokeIndex]
                                .trimToFraction(demoProgress.value.toDouble())
                            drawMedian(partial, m, PracticeColors.strokeCorrect)
                        }
                    }
                    Mode.QUIZ -> {
                        // Accepted strokes as filled outlines — the real character takes shape.
                        for (i in 0 until quiz.expectedIndex) {
                            drawPath(character.strokeOutlines[i].toComposePath(m), inkColor)
                        }
                        // Hint flash: the expected stroke's median in the sloppy tint.
                        if (hintStrokeFlash && !quiz.isComplete) {
                            drawMedian(character.medians[quiz.expectedIndex], m, PracticeColors.strokeSloppy)
                        }
                        // The in-flight finger stroke.
                        if (liveStroke.size > 1) {
                            drawScope(liveStroke, m, inkColor, widthUnits = 45f)
                        }
                        // The last rejected stroke, fading out.
                        fadingReject?.let { rejected ->
                            if (rejectAlpha.value > 0.01f) {
                                drawScope(
                                    rejected, m,
                                    PracticeColors.strokeWrong.copy(alpha = rejectAlpha.value),
                                    widthUnits = 45f,
                                )
                            }
                        }
                    }
                }
            }

            if (mode == Mode.QUIZ && quiz.isComplete) {
                CompletionOverlay(
                    quiz = quiz,
                    allowRetry = allowRetry,
                    canAdvance = canAdvance,
                    isSaving = isSaving,
                    saveError = saveError,
                    onRetrySave = onRetrySave,
                    onAgain = {
                        quiz = engine.start(character)
                        quizStartedAt = System.currentTimeMillis()
                        feedback = "Again — stroke 1 of ${character.strokeCount}."
                    },
                    onNext = onNext,
                )
            }
        }

        Text(
            text = feedback,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!startInQuiz) { // recall mode: no demo reveal (the hint path costs a grade)
                OutlinedButton(onClick = {
                    mode = Mode.DEMO
                    demoRun++
                }) { Text("Demo") }
            }
            OutlinedButton(
                onClick = {
                    if (mode == Mode.QUIZ && !quiz.isComplete) {
                        quiz = engine.useHint(quiz)
                        hintStrokeFlash = true
                        feedback = "Hint shown — trace it. (Counts as hinted.)"
                    }
                },
                enabled = mode == Mode.QUIZ && !quiz.isComplete,
            ) { Text("Hint") }
            OutlinedButton(
                onClick = { quiz = engine.undo(quiz) },
                enabled = mode == Mode.QUIZ && quiz.records.isNotEmpty(),
            ) { Text("Undo") }
            if (allowGuide) {
                OutlinedButton(onClick = { showGuide = !showGuide }) {
                    Text(if (showGuide) "Guide on" else "Guide off")
                }
            }
            var soundOn by remember { mutableStateOf(sounds.enabled) }
            OutlinedButton(onClick = {
                soundOn = !soundOn
                sounds.enabled = soundOn
                onSoundToggle(soundOn) // persisted (M1, DataStore)
            }) { Text(if (soundOn) "Sound on" else "Sound off") }
        }
    }
}

@Composable
private fun CompletionOverlay(
    quiz: QuizState,
    allowRetry: Boolean = true,
    canAdvance: Boolean,
    isSaving: Boolean,
    saveError: String?,
    onRetrySave: () -> Unit,
    onAgain: () -> Unit,
    onNext: () -> Unit,
) {
    // TODO(son, S1/S5): this is where the confetti goes. Make finishing feel amazing.
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "完成!", fontSize = 44.sp)
            Text(
                text = "${quiz.character.character} · ${quiz.character.primaryPinyin} · " +
                    quiz.character.learnerGloss,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = when {
                    saveError != null -> "Couldn’t save yet — your work is still here."
                    isSaving -> "Saving your progress…"
                    canAdvance -> "Stored in your collection."
                    else -> "Finishing up…"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (allowRetry) OutlinedButton(onClick = onAgain) { Text("Again") }
                if (saveError != null) {
                    OutlinedButton(onClick = onRetrySave) { Text("Try saving again") }
                }
                Button(onClick = onNext, enabled = canAdvance) { Text("Next") }
            }
        }
    }
}

private fun DrawScope.drawRiceGrid(m: CanvasMapping) {
    // 米字格: box + midlines + diagonals, low emphasis (spec 05).
    val tl = m.toCanvas(Point(0.0, 0.0))
    val br = m.toCanvas(Point(1000.0, 1000.0))
    val tr = Offset(br.x, tl.y)
    val bl = Offset(tl.x, br.y)
    val cTop = m.toCanvas(Point(500.0, 0.0))
    val cBottom = m.toCanvas(Point(500.0, 1000.0))
    val cLeft = m.toCanvas(Point(0.0, 500.0))
    val cRight = m.toCanvas(Point(1000.0, 500.0))
    val stroke = 2f
    val g = PracticeColors.guide
    drawLine(g, tl, tr, stroke); drawLine(g, tr, br, stroke)
    drawLine(g, br, bl, stroke); drawLine(g, bl, tl, stroke)
    drawLine(g, cTop, cBottom, stroke); drawLine(g, cLeft, cRight, stroke)
    drawLine(g, tl, br, stroke); drawLine(g, tr, bl, stroke)
}

private fun DrawScope.drawMedian(
    median: List<Point>,
    m: CanvasMapping,
    color: androidx.compose.ui.graphics.Color,
) = drawScope(median, m, color, widthUnits = 85f)

private fun DrawScope.drawScope(
    points: List<Point>,
    m: CanvasMapping,
    color: androidx.compose.ui.graphics.Color,
    widthUnits: Float,
) {
    if (points.size < 2) return
    drawPath(
        points.toLinePath(m),
        color,
        style = Stroke(width = m.widthPx(widthUnits), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
