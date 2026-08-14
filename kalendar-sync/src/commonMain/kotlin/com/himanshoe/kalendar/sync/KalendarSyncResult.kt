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

package com.himanshoe.kalendar.sync

/**
 * The result of a [KalendarSyncProvider] operation.
 *
 * Handle each outcome with a `when` expression:
 * ```kotlin
 * when (val result = sync.fetchEvents(startDate, endDate)) {
 *     is KalendarSyncResult.Success       -> show(result.data)
 *     is KalendarSyncResult.PermissionDenied -> askForPermission()
 *     is KalendarSyncResult.NotSupported  -> showUnsupportedMessage()
 *     is KalendarSyncResult.Error         -> showError(result.message)
 * }
 * ```
 */
public sealed class KalendarSyncResult<out T> {

    /**
     * The operation completed successfully.
     *
     * @param data The value produced by the operation.
     *
     * This is deliberately a plain `class` with a hand-written [equals] and [hashCode] rather
     * than a `data class`, matching the rest of this module's public surface: a `data class`
     * compiles `copy` and `componentN` into the ABI, which then cannot gain a property without
     * breaking already compiled consumers.
     */
    public class Success<T>(public val data: T) : KalendarSyncResult<T>() {

        /** Two successes are equal when they carry equal values. */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Success<*> && data == other.data
        }

        override fun hashCode(): Int = data?.hashCode() ?: 0

        override fun toString(): String = "Success(data=$data)"
    }

    /**
     * The required calendar permission was not granted by the user.
     *
     * On Android, direct the user to grant `READ_CALENDAR` / `WRITE_CALENDAR`
     * via `ActivityCompat.requestPermissions`.
     * On iOS, direct the user to Settings → Privacy → Calendars.
     */
    public data object PermissionDenied : KalendarSyncResult<Nothing>()

    /**
     * Device calendar access is not available on this platform
     * (e.g. Desktop JVM, Web/wasmJS).
     */
    public data object NotSupported : KalendarSyncResult<Nothing>()

    /**
     * The operation failed with an error.
     *
     * @param message A human-readable description of what went wrong.
     * @param cause The underlying exception, if available.
     *
     * This is deliberately a plain `class` with a hand-written [equals] and [hashCode] rather
     * than a `data class`, matching the rest of this module's public surface — see [Success].
     */
    public class Error(
        public val message: String,
        public val cause: Throwable? = null,
    ) : KalendarSyncResult<Nothing>() {

        /** Two errors are equal when both the message and the cause match. */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Error && message == other.message && cause == other.cause
        }

        override fun hashCode(): Int = HASH_FACTOR * message.hashCode() + (cause?.hashCode() ?: 0)

        override fun toString(): String = "Error(message=$message, cause=$cause)"
    }
}

private const val HASH_FACTOR = 31
