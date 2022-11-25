package com.kasukusakura.miraimockframework

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "RESULT_CLASS_IN_RETURN_TYPE")
@kotlin.internal.InlineOnly
@kotlin.internal.LowPriorityInOverloadResolution
inline fun <R, T : R> Result<T>.recoverCatchingSuppressed(transform: (exception: Throwable) -> R): Result<R> {
    return when (val exception = exceptionOrNull()) {
        null -> this
        else -> {
            try {
                Result.success(transform(exception))
            } catch (e: Throwable) {
                e.addSuppressed(exception)
                Result.failure(e)
            }
        }
    }
}
