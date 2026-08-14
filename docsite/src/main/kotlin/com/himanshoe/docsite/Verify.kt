package com.himanshoe.docsite

import java.io.File

private val referencePattern = Regex("""(?:href|src)="([^"]+)"""")
private val idPattern = Regex("""\bid="([^"]+)"""")

/**
 * Directories this generator does not write. `assembleSite` copies the Wasm playground and the Dokka
 * output in beside the docs afterwards, so their pages are absent here and every link into them
 * would look broken.
 *
 * Skipping two known prefixes is narrower and more honest than skipping missing files in general,
 * which would hide the exact thing this check exists to catch.
 */
private val assembledLater = listOf("playground/", "api/")

/**
 * Fails the build on a link, an image or an anchor that points at nothing.
 *
 * Worth doing because a broken link is invisible in a generated site: the page still builds, still
 * renders, still looks finished, and the mistake is found by a reader. The one thing a generator can
 * do that a reader cannot is check every link on every page every time, so it does.
 */
fun verifyLinks(
    outputDir: File,
    strict: Boolean = true
) {
    val pages = outputDir.walkTopDown().filter { file -> file.extension == "html" }.toList()
    val anchors = mutableMapOf<File, Set<String>>()
    val broken = pages.flatMap { page -> brokenIn(outputDir = outputDir, page = page, anchors = anchors) }
    if (broken.isEmpty()) {
        return
    }

    val report =
        buildString {
            append("The generated site has ${broken.size} dead link(s):\n")
            broken.sorted().forEach { failure -> append("  ").append(failure).append('\n') }
            append("\nEvery link above came from the markdown under docs/. ")
            append("Fix the target, or point the link somewhere that exists.")
        }
    check(!strict) { report }
    System.err.println("WARNING: $report")
}

private fun brokenIn(
    outputDir: File,
    page: File,
    anchors: MutableMap<File, Set<String>>
): List<String> {
    val source = page.relativeTo(outputDir).invariantSeparatorsPath
    return referencePattern
        .findAll(page.readText())
        .map { match -> match.groupValues[1] }
        .filter { reference -> isCheckable(reference) }
        .mapNotNull { reference -> failureFor(page = page, source = source, reference = reference, anchors = anchors) }
        .distinct()
        .toList()
}

private fun failureFor(
    page: File,
    source: String,
    reference: String,
    anchors: MutableMap<File, Set<String>>
): String? {
    val path = reference.substringBefore('#')
    val anchor = reference.substringAfter('#', "")
    val target =
        if (path.isEmpty()) {
            page
        } else {
            page.parentFile.resolve(path).normalize()
        }
    return when {
        !target.exists() -> "$source -> $reference (no such file)"
        anchor.isEmpty() -> null
        anchor in anchorsOf(target = target, anchors = anchors) -> null
        else -> "$source -> $reference (no heading with that anchor)"
    }
}

private fun anchorsOf(
    target: File,
    anchors: MutableMap<File, Set<String>>
): Set<String> =
    anchors.getOrPut(target) {
        if (target.isFile && target.extension == "html") {
            idPattern.findAll(target.readText()).map { match -> match.groupValues[1] }.toSet()
        } else {
            emptySet()
        }
    }

/**
 * `404.html` is the one page that links from the deployed root rather than relatively, because it is
 * served under a URL it was not written for. Those links resolve against the live site, not against
 * this directory, so there is nothing here to check them against.
 */
private fun isCheckable(reference: String): Boolean =
    reference.isNotEmpty() &&
        !isExternal(reference) &&
        !reference.startsWith(SITE_BASE_PATH) &&
        assembledLater.none { prefix -> reference.contains(prefix) }
