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

package com.himanshoe.kalendar.theme

import androidx.compose.runtime.Composable

/**
 * Desktop: always `false`, because no reliable signal exists for the JVM to read.
 *
 * macOS, Windows and Linux desktops all keep a reduce-motion preference, but none of them surfaces
 * it to a JVM process — AWT's desktop properties do not carry it, and reaching for it directly
 * would mean shelling out to OS-specific commands and polling for changes. Rather than guess,
 * desktop reports "no preference"; an application that reads the OS setting through its own means
 * can still pass `KalendarAnimations(enabled = false)` to [KalendarTheme] explicitly.
 */
@Composable
internal actual fun platformPrefersReducedMotion(): Boolean = false
