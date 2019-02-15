package org.apache.druid.java.util.emitter.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class EmitterCountBoundedQueueHolder<T> implements EmitterQueueHolder<T>
{
  private final BlockingQueue<T> eventsQueue;

  private final long DEFAULT_OFFER_TIMEOUT_MILLIS = 10;
  private final long DEFAULT_POLL_TIMEOUT_MILLIS = 10;

  public EmitterCountBoundedQueueHolder(BlockingQueue<T> eventsQueue) {
    this.eventsQueue = eventsQueue;
  }

  @Override
  public boolean offer(T event) throws InterruptedException
  {
    return eventsQueue.offer(event, DEFAULT_OFFER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }

  @Override
  public T poll() throws InterruptedException
  {
    return eventsQueue.poll(DEFAULT_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }
}
