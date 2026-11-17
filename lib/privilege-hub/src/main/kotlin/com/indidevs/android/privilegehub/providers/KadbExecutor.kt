package com.indidevs.android.privilegehub.providers

import android.util.Log
import com.indidevs.android.adbeesh.runtime.AdbSession
import com.indidevs.android.shellom.ShellExecutor
import java.io.EOFException
import kotlinx.coroutines.Job

/**
 * A [ShellExecutor] that wraps Kadb's [AdbSession.shell].
 *
 * Returns Result.success(Unit) in case elevation is succesful.
 */
class KadbExecutor(
    private val session: AdbSession,
    private val elevationJob: Job,
    private val isElevated: () -> Boolean,
    private val tag: String = "KadbExecutor"
) : ShellExecutor {
    override suspend fun execute(command: String): Result<Unit> {
        return try {
            val res = session.shell(command)
            if (res.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(res.error))
            }
        } catch (e: EOFException) {
            // EOFException after elevation succeeds is expected in case AdbSession is closed.
            if (elevationJob.isCancelled || isElevated()) {
                Result.success(Unit)
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            val shouldLogError = !elevationJob.isCancelled && !isElevated()
            if (shouldLogError) Log.e(tag, "Shell execution failed", e)
            Result.failure(e)
        }
    }
}
