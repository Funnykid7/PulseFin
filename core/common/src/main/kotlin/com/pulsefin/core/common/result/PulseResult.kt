package com.pulsefin.core.common.result

import kotlinx.coroutines.CancellationException

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
        } catch (c: CancellationException) {
            // Rethrow so coroutine cancellation keeps propagating instead of being swallowed
            // as a Failure, which would break structured concurrency for the caller.
            throw c
        } catch (t: Throwable) {
            Failure(t)
        }
    }
}
