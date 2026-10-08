package com.stupidsavacan.clocky.widget.digital

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** One drain task, latest pending work per widget, atomic generation check + send. No Android dependency. */
internal class WidgetGenerationQueue(
    private val executor: Executor,
    private val onError: (Throwable) -> Unit,
) {
    private val lock = Any()
    private var sequence = 0L
    private var draining = false
    private val latest = mutableMapOf<Int, Long>()
    private val pending = linkedMapOf<Int, Job>()

    private class Job(val id: Int, val token: Long, val work: ((() -> Unit) -> Boolean) -> Unit, val done: () -> Unit) {
        private val finished = AtomicBoolean()
        fun finish() { if (finished.compareAndSet(false, true)) done() }
    }

    fun submit(id: Int, done: () -> Unit = {}, work: ((() -> Unit) -> Boolean) -> Unit) {
        val replaced: Job?
        val start: Boolean
        synchronized(lock) {
            val job = Job(id, ++sequence, work, done)
            latest[id] = job.token
            replaced = pending.put(id, job)
            start = !draining
            draining = true
        }
        finish(replaced)
        if (start) try { executor.execute(::drain) } catch (e: Exception) {
            val abandoned = synchronized(lock) {
                draining = false
                pending.values.toList().also { pending.clear(); latest.clear() }
            }
            abandoned.forEach(::finish)
            onError(e)
        }
    }

    fun invalidate(id: Int) {
        val cancelled = synchronized(lock) { latest.remove(id); pending.remove(id) }
        finish(cancelled)
    }

    private fun drain() {
        while (true) {
            val job = synchronized(lock) {
                if (pending.isEmpty()) { draining = false; return }
                val first = pending.entries.first()
                pending.remove(first.key)!!
            }
            try {
                job.work { send ->
                    synchronized(lock) {
                        if (latest[job.id] != job.token) false else { send(); true }
                    }
                }
            } catch (e: Exception) { onError(e) }
            finally {
                synchronized(lock) { if (latest[job.id] == job.token) latest.remove(job.id) }
                finish(job)
            }
        }
    }

    private fun finish(job: Job?) {
        try { job?.finish() } catch (e: Exception) { onError(e) }
    }
}
