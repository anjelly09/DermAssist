package com.dermassist.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DeviceScreeningTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    @Test fun bundledModelMatchesPythonGoldenVectorOffline() {
        val modelBytes = target.assets.open("screening.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder()).apply { put(modelBytes); rewind() }
        val inputBytes = testContext.assets.open("golden_input.bin").use { it.readBytes() }
        val input = ByteBuffer.allocateDirect(inputBytes.size).order(ByteOrder.nativeOrder()).apply { put(inputBytes); rewind() }
        val expected = JSONArray(testContext.assets.open("golden_output.json").bufferedReader().use { it.readText() })
        val output = arrayOf(FloatArray(expected.length()))
        val times = mutableListOf<Long>()
        Interpreter(model, Interpreter.Options().setNumThreads(2)).use { interpreter ->
            repeat(11) { index ->
                input.rewind()
                val start = System.nanoTime(); interpreter.run(input, output)
                if (index > 0) times += (System.nanoTime() - start) / 1_000_000
            }
        }
        val maximumError = output[0].indices.maxOf { abs(output[0][it] - expected.getDouble(it)) }
        assertTrue("Python/Android output mismatch: $maximumError", maximumError < .02)
        val report = JSONObject().apply {
            put("runs", times.size); put("median_ms", times.sorted()[times.size / 2])
            put("max_ms", times.max()); put("max_absolute_output_error", maximumError)
            put("device", android.os.Build.MODEL); put("api", android.os.Build.VERSION.SDK_INT)
            put("note", "Emulator CPU timing; not representative of an inexpensive phone")
        }
        File(target.filesDir, "device-benchmark.json").writeText(report.toString(2))
    }

    @Test fun qualityGuardRejectsAFlatPhotoBeforePrediction() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff888888.toInt()) }
        val file = File(target.cacheDir, "flat-device-test.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        try {
            val result = ScreeningEngine(target).assess(file.absolutePath)
            assertEquals(ScreeningStatus.PHOTO_REJECTED, result.status)
            assertNull(result.condition)
        } finally { file.delete() }
    }

    @Test fun consentPhotoQuestionnaireScreeningSaveRestoreAndDelete() {
        target.getSharedPreferences("local_journal", 0).edit().clear().commit()
        val vm = ViewModelProvider(compose.activity)[DermViewModel::class.java]
        compose.onNodeWithText("Start a skin check").performScrollTo().performClick()
        compose.onNodeWithText("Continue to photo").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("I understand the limitations and agree to local photo processing.").performScrollTo().performClick()
        compose.onNodeWithText("Continue to photo").assertIsEnabled().performClick()
        // Import through the same production preparation path; a synthetic fixture
        // avoids testing with a person's private photo or creating training data.
        val file = syntheticPhoto()
        compose.runOnIdle { vm.importPhoto(Uri.fromFile(file)) }
        compose.waitUntil(20_000) { !vm.busy && vm.photo != null }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("The area is clear, well lit, and in focus.").performScrollTo().performClick()
        compose.onNodeWithText("Use this photo").performScrollTo().performClick()
        compose.onNodeWithText("Face or scalp").performScrollTo().performClick()
        compose.onNodeWithText("A few days").performScrollTo().performClick()
        for (i in 0..2) compose.onAllNodesWithText("No")[i].performScrollTo().performClick()
        compose.onNodeWithText("Run research screening").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(30_000) { !vm.busy && vm.screen == Screen.RESULT }
        assertEquals(ScreeningStatus.UNCERTAIN, vm.screening.status)
        assertNotNull(vm.screening.modelId)
        assertTrue(vm.screening.latencyMs > 0)
        assertTrue(vm.current.report().contains("not a diagnosis"))
        capture("result")
        compose.onNodeWithText("Save notes to journal").performScrollTo().performClick()
        compose.waitUntil(10_000) { vm.isSaved && !vm.busy }
        compose.onNodeWithText("Saved in your journal").assertIsNotEnabled()
        val recordId = vm.currentId
        assertEquals(1, JournalStore(target).read().count { it.id == recordId })
        compose.onNodeWithText("Finish and remove photo").performScrollTo().performClick()
        assertNull(vm.photo)
        compose.onNodeWithText("Journal").performClick()
        compose.onNodeWithText("Face or scalp").assertExists()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete record").performClick()
        compose.waitUntil(10_000) { vm.history.none { it.id == recordId } }
        compose.onNodeWithText("A fresh page.").assertExists()
        file.delete()
    }

    @Test fun homeAndConsentRemainScrollableAtLargeText() {
        val header = compose.onNodeWithTag("app-top-bar").getUnclippedBoundsInRoot()
        assertTrue("Header must not consume the screen at large font sizes", header.bottom - header.top < 120.dp)
        compose.onNodeWithText("Journal").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>(); action(layouts)
            assertEquals(1, layouts.single().lineCount)
        }
        capture("home")
        compose.onNodeWithText("Start a skin check").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Continue to photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("I understand the limitations and agree to local photo processing.").performScrollTo().performClick()
        compose.onNodeWithText("Continue to photo").performScrollTo().assertIsEnabled()
        capture("consent")
    }

    @Test fun photoPreparationRemovesGpsAndCorrectsOrientation() {
        val source = File(target.cacheDir, "orientation-device-test.jpg")
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff998877.toInt()) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        val exif = androidx.exifinterface.media.ExifInterface(source)
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "6")
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, "1/1,2/1,3/1")
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        exif.saveAttributes()
        val store = PhotoStore(target)
        val prepared = store.prepare(Uri.fromFile(source))
        try {
            val decoded = android.graphics.BitmapFactory.decodeFile(prepared)
            assertEquals(600, decoded.width); assertEquals(400, decoded.height); decoded.recycle()
            val metadata = androidx.exifinterface.media.ExifInterface(prepared)
            assertNull(metadata.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE))
        } finally { source.delete(); store.delete(prepared) }
    }

    @Test fun cameraAndPhotoPickerCancellationLeaveNoPendingPhoto() {
        androidx.test.espresso.intent.Intents.init()
        try {
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null))
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction("android.provider.action.PICK_IMAGES"))
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null))
            compose.onNodeWithText("Start a skin check").performScrollTo().performClick()
            compose.onNodeWithText("I understand the limitations and agree to local photo processing.").performScrollTo().performClick()
            compose.onNodeWithText("Continue to photo").performScrollTo().performClick()
            compose.onNodeWithText("Take a photo").performScrollTo().performClick()
            compose.waitForIdle()
            androidx.test.espresso.intent.Intents.intended(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.provider.MediaStore.ACTION_IMAGE_CAPTURE))
            compose.onNodeWithText("Choose from photos").performScrollTo().performClick()
            compose.waitForIdle()
            androidx.test.espresso.intent.Intents.intended(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction("android.provider.action.PICK_IMAGES"))
            val vm = ViewModelProvider(compose.activity)[DermViewModel::class.java]
            assertNull(vm.pendingCamera); assertNull(vm.photo); assertFalse(vm.busy)
        } finally { androidx.test.espresso.intent.Intents.release() }
    }

    @Test fun sharingUsesAUserInitiatedTextOnlyChooser() {
        androidx.test.espresso.intent.Intents.init()
        try {
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_CHOOSER))
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null))
            compose.runOnIdle { shareReport(compose.activity, Assessment("share-test", 0, Symptoms("Arms or hands"))) }
            val chooser = androidx.test.espresso.intent.Intents.getIntents().last { it.action == android.content.Intent.ACTION_CHOOSER }
            @Suppress("DEPRECATION")
            val payload = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            assertEquals("text/plain", payload.type)
            assertTrue(payload.getStringExtra(android.content.Intent.EXTRA_TEXT)!!.contains("not a diagnosis"))
            assertFalse(payload.hasExtra(android.content.Intent.EXTRA_STREAM))
        } finally { androidx.test.espresso.intent.Intents.release() }
    }

    private fun syntheticPhoto(): File {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        for (y in 0 until 400) for (x in 0 until 400) {
            val v = if ((x / 7 + y / 7) % 2 == 0) 80 else 190
            bitmap.setPixel(x, y, android.graphics.Color.rgb(v, v, v))
        }
        val file = File(target.cacheDir, "synthetic-device-test.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        return file
    }

    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(target.filesDir, "test-screens").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
