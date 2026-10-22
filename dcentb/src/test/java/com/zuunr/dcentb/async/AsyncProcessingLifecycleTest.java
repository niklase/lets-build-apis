package com.zuunr.dcentb.async;

import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.changestream.CheckpointStore;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueProcessor;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AsyncProcessingLifecycleTest {

    @Test
    void gettersReturnExactlyWhatTheConstructorWasGiven() {
        ChangeStreamListener listener = Mockito.mock(ChangeStreamListener.class);
        ErrorQueueProcessor errorQueueProcessor = Mockito.mock(ErrorQueueProcessor.class);
        CheckpointStore checkpointStore = Mockito.mock(CheckpointStore.class);
        ErrorQueueStore errorQueueStore = Mockito.mock(ErrorQueueStore.class);

        AsyncProcessingLifecycle lifecycle = new AsyncProcessingLifecycle(
                listener, errorQueueProcessor, checkpointStore, errorQueueStore, "dead-letters");

        assertEquals(listener, lifecycle.getListener());
        assertEquals(errorQueueProcessor, lifecycle.getErrorQueueProcessor());
        assertEquals(checkpointStore, lifecycle.getCheckpointStore());
        assertEquals(errorQueueStore, lifecycle.getErrorQueueStore());
        assertEquals("dead-letters", lifecycle.getDeadLetterCollection());
    }

    @Test
    void allFieldsAreNullWhenAsyncProcessingIsDisabled() {
        AsyncProcessingLifecycle lifecycle = new AsyncProcessingLifecycle(null, null, null, null, null);

        assertNull(lifecycle.getListener());
        assertNull(lifecycle.getErrorQueueProcessor());
        assertNull(lifecycle.getCheckpointStore());
        assertNull(lifecycle.getErrorQueueStore());
        assertNull(lifecycle.getDeadLetterCollection());

        // Every method stays a no-op in this state - see class Javadoc.
        lifecycle.start();
        lifecycle.stop();
    }
}
