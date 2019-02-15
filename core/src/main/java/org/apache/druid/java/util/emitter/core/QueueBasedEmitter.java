package org.apache.druid.java.util.emitter.core;

import org.apache.druid.java.util.common.logger.Logger;

import java.util.concurrent.atomic.AtomicLong;

public abstract class QueueBasedEmitter<T> implements Emitter
{
  private static final double LOG_FREQUENCY = 0.001;
  private static Logger log;

  public QueueBasedEmitter(Logger log) {
    this.log = log;
  }

  protected int offerAndHandleFailure(
      T event,
      EmitterQueueHolder<T> queue,
      int maxRetries,
      AtomicLong numDroppedTotal)
      throws InterruptedException
  {
    boolean successful = false;
    int numDropped = 0;
    for (int i = 0; i < maxRetries && !successful; i++) {
      if (!(successful = queue.offer(event))) {
        if (queue.poll() != null) {
          logFailureWithFrequency(numDroppedTotal);
          numDropped++;
        }
      }
    }
    return numDropped;
  }

  protected void logFailureWithFrequency(AtomicLong numEventsDropped)
  {
    if (numEventsDropped.getAndIncrement() % (1 / LOG_FREQUENCY) == 0) {
      log.error(
          "Lost total of [%s] events because of emitter queue is full. Please increase the capacity or/and the consumer frequency",
          numEventsDropped.get()
      );
    }
  }
}
