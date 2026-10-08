package com.stupidsavacan.clocky.widget.digital

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class WidgetGenerationQueueTest {
    @Test fun callbackOnAnotherThreadSupersedesAnInFlightGeneration() {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val started = java.util.concurrent.CountDownLatch(1)
        val resume = java.util.concurrent.CountDownLatch(1)
        val completed = java.util.concurrent.CountDownLatch(2)
        val sent = mutableListOf<Int>()
        val errors = mutableListOf<Throwable>()
        val q = WidgetGenerationQueue(executor, errors::add)
        try {
            q.submit(1, completed::countDown) { publish ->
                started.countDown()
                check(resume.await(5, java.util.concurrent.TimeUnit.SECONDS))
                publish { sent.add(1) }
            }
            assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS))
            q.submit(1, completed::countDown) { publish -> publish { sent.add(2) } }
            resume.countDown()
            assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf(2), sent)
            assertTrue(errors.isEmpty())
        } finally {
            resume.countDown()
            executor.shutdownNow()
        }
    }
    @Test fun staleRunningResultCannotSendAndPendingWorkCoalescesAndAllResultsFinish() {
        val tasks = mutableListOf<Runnable>()
        val errors = mutableListOf<Throwable>()
        val q = WidgetGenerationQueue(Executor { tasks.add(it) },errors::add)
        val sent = mutableListOf<Int>()
        val done = mutableListOf<Int>()
        q.submit(1,{done.add(1)}) { publish ->
            q.submit(1,{done.add(2)}) { p -> p { sent.add(2) } }
            q.submit(1,{done.add(3)}) { p -> p { sent.add(3) } }
            assertFalse(publish { sent.add(1) })
        }
        tasks.single().run()
        assertEquals(listOf(3),sent)
        assertEquals(listOf(2,1,3),done)
        assertTrue(errors.isEmpty())
    }
    @Test fun exceptionAndDeletionAndExecutorRejectionFinishExactlyOnce() {
        val tasks = mutableListOf<Runnable>()
        val errors = mutableListOf<Throwable>()
        val q = WidgetGenerationQueue(Executor { tasks.add(it) },errors::add)
        var finished = 0
        q.submit(1,{finished++}) { error("fit failed") }
        q.submit(2,{finished++}) { error("deleted job ran") }
        q.invalidate(2)
        tasks.single().run()
        assertEquals(2,finished)
        assertEquals(1,errors.size)
        WidgetGenerationQueue(Executor { throw java.util.concurrent.RejectedExecutionException() },errors::add)
            .submit(1,{finished++}) { error("rejected job ran") }
        assertEquals(3,finished)
        assertEquals(2,errors.size)
    }
    @Test fun independentWidgetJobsAndDeleteDuringGenerationAreSerialized() {
        val tasks = mutableListOf<Runnable>()
        val q = WidgetGenerationQueue(Executor { tasks.add(it) }, { throw it })
        val sent = mutableListOf<Int>()
        q.submit(1) { p -> q.invalidate(1); assertFalse(p { sent.add(1) }) }
        q.submit(2) { p -> p { sent.add(2) } }
        assertEquals(1,tasks.size)
        tasks.single().run()
        assertEquals(listOf(2),sent)
    }
}
