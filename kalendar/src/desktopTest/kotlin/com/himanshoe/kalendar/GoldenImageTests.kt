/*
 *
 *  * Copyright 2026 Kalendar Contributors (https://www.himanshoe.com). All rights reserved.
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *
 */

package com.himanshoe.kalendar

/**
 * JUnit category for tests that compare a rendered frame against a checked-in image.
 *
 * Those images are rasterised on macOS, and text rasterises differently elsewhere, so off macOS
 * they fail on font metrics rather than on anything the library did. That is a property of these
 * three tests alone — every other desktop test asserts on semantics or measured geometry and is
 * portable — but the whole `:kalendar:desktopTest` task was excluded from CI to avoid them, taking
 * ~209 tests with it, drag and keyboard navigation and accessibility included.
 *
 * Carrying this category is what lets the build skip the image comparisons and run the rest; see
 * the `desktopTest` configuration in kalendar/build.gradle.kts. Put it on any new test that decides
 * whether a picture is correct by looking at the picture.
 */
interface GoldenImageTests
