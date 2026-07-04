package com.pulsefin.core.common.result

/**
 * Lightweight result wrapper used across the data/domain boundary so callers can handle
 * success/failure without exceptions leaking into the UI layer.
 */
sealed interface PulseResult<out T> {
    data class Success<T>(val data: T) : PulseResult<T>
    data class Failure(val error: Throwable) : PulseResult<Nothing>

    companion object {
        inline fun <T> runCatchingResult(block: () -> T): PulseResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(t)
        }
    }
}
