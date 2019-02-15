package org.apache.druid.java.util.emitter.core;

import org.apache.druid.java.util.common.logger.Logger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class QueueBasedEmitter<T> implements Emitter
{
  private static final double LOG_FREQUENCY = 0.001;
  private static Logger log;

  public QueueBasedEmitter(Logger log)
  {
    this.log = log;
  }

  protected boolean offerAndHandleFailure(
      T event,
      EmitterQueueHolder<T> queue,
      int maxRetries,
      AtomicLong numDroppedTotal
  )
  {
    try {
      boolean successful = queue.offer(event);
      for (int i = 0; i < maxRetries && !successful; i++) {
        // poll only runs if successful == false
        if (!successful && queue.poll() != null) {
          logFailureWithFrequency(numDroppedTotal);
          successful = queue.offer(event);
        }
      }
      if (!successful) {
        logFailureWithFrequency(numDroppedTotal);
        return successful;
      }
    }
    catch (InterruptedException e) {
      logFailureWithFrequency(numDroppedTotal);
      log.error(e, "got interrupted with message [%s]", e.getMessage());
      Thread.currentThread().interrupt();
    }
    return false;
  }

  protected void logFailureWithFrequency(AtomicLong numEventsDropped)
  {
    if (numEventsDropped.getAndIncrement() % (1 / LOG_FREQUENCY) == 0) {
      log.error(
          "Lost total of [%s] events because emitter queue is full. Please increase the capacity and/or the consumer frequency",
          numEventsDropped.get()
      );
    }
  }
}
