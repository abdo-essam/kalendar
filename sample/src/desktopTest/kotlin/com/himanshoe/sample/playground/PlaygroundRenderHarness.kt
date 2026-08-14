package com.himanshoe.sample.playground

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/** Writes a PNG of the playground for each view so the layout can be judged from the image. */
class PlaygroundRenderHarness {

    @get:Rule
    val rule = createComposeRule()

    private val outputDirectory = File(System.getenv("HARNESS_OUT") ?: "build/harness")

    @Test
    fun renderEveryView() {
        outputDirectory.mkdirs()
        rule.setContent {
            Playground(
                modifier = Modifier.fillMaxSize(),
                clipboard = PlaygroundClipboard { _, onResult -> onResult(true) },
            )
        }
        PlaygroundView.entries.forEach { view ->
            rule.onNodeWithTag("view-${view.name}").performScrollTo().performClick()
            rule.waitForIdle()
            capture("view-${view.name}")
        }
        listOf("preset-Brutalist", "preset-Playful", "preset-Minimal").forEach { preset ->
            rule.onNodeWithTag(preset).performScrollTo().performClick()
            rule.waitForIdle()
            capture(preset)
        }
        rule.onNodeWithTag("appearance-Dark").performScrollTo().performClick()
        rule.waitForIdle()
        capture("appearance-Dark")
        rule.onNodeWithTag("toggle-customHeader").performScrollTo()
        rule.waitForIdle()
        capture("panel-lower")
        rule.onNodeWithTag("copy-code").performScrollTo()
        rule.waitForIdle()
        capture("panel-code")
    }

    private fun capture(name: String) {
        val image = rule.onRoot().captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(outputDirectory, "$name.png"))
    }
}

/** Prints the generated snippet for every view so the API names can be checked by eye. */
class PlaygroundCodeDump {
    @Test
    fun dump() {
        PlaygroundView.entries.forEach { view ->
            println("===== " + view.name)
            println(generatePlaygroundCode(config = PlaygroundConfig(view = view), dark = false))
        }
    }
}

/** The same sweep at a phone-width window, which is the single-column layout. */
class PlaygroundNarrowRenderHarness {

    @get:Rule
    val rule = createComposeRule()

    private val outputDirectory = File(System.getenv("HARNESS_OUT") ?: "build/harness")

    @Test
    fun renderNarrowLayout() {
        outputDirectory.mkdirs()
        rule.setContent {
            Box(modifier = Modifier.width(420.dp).fillMaxSize()) {
                Playground(
                    modifier = Modifier.fillMaxSize(),
                    clipboard = PlaygroundClipboard { _, onResult -> onResult(true) },
                )
            }
        }
        listOf("view-Month", "view-Agenda", "view-DatePicker").forEach { tag ->
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.waitForIdle()
            val image = rule.onRoot().captureToImage().toAwtImage()
            ImageIO.write(image, "png", File(outputDirectory, "narrow-$tag.png"))
        }
    }
}
