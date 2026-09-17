package dev.qtremors.acqua

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApplicationGraphTest {
    @Test
    fun `application owns one dependency graph and one worker factory`() {
        val application = ApplicationProvider.getApplicationContext<AcquaApp>()

        val firstGraph = application.dependencies
        val secondGraph = application.dependencies

        assertSame(firstGraph, secondGraph)
        assertSame(firstGraph.workerFactory, application.workManagerConfiguration.workerFactory)
        assertSame(firstGraph.ytDlpDownloads, firstGraph.ytDlpDownloads)
    }
}
