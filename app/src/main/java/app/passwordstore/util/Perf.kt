// It's okay if this stays unused for the most part since it is development tooling.
@file:Suppress("Unused")

package app.passwordstore.util

import android.os.Looper
import android.os.SystemClock
import logcat.logcat

/**
 * Small helper to execute a given [block] and log the time it took to execute it. Intended for use
 * in day-to-day perf investigations and code using it should probably not be shipped.
 */
suspend fun <T> logExecutionTime(tag: String, block: suspend () -> T): T {
  val start = SystemClock.uptimeMillis()
  val res = block()
  val end = SystemClock.uptimeMillis()
  logcat(tag) { "Finished in ${end - start}ms" }
  return res
}

fun <T> logExecutionTimeBlocking(tag: String, block: () -> T): T {
  val start = SystemClock.uptimeMillis()
  val res = block()
  val end = SystemClock.uptimeMillis()
  logcat(tag) { "Finished in ${end - start}ms" }
  return res
}

/**
 * Throws if called on the main thread, used to ensure an operation being offloaded to a background
 * thread is correctly being moved off the main thread.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun checkMainThread() {
  require(Looper.myLooper() != Looper.getMainLooper()) {
    "This operation must not run on the main thread"
  }
}