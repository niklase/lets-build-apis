package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.dcentb.async.leaderelection.LeaderElector;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ErrorQueueProcessorTest {

    @Test
    void isNotRunningBeforeStart() {
        ErrorQueueProcessor processor = new ErrorQueueProcessor(
                Mockito.mock(ErrorQueueStore.class), Mockito.mock(LeaderElector.class), Mockito.mock(TopicRouter.class),
                "dead-letters", 5, Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertFalse(processor.isRunning());
    }
}
