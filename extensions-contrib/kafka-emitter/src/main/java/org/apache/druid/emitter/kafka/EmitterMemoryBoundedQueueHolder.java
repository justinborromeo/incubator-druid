package org.apache.druid.emitter.kafka;

import org.apache.druid.java.util.emitter.core.EmitterQueueHolder;

import java.util.concurrent.TimeUnit;

public class EmitterMemoryBoundedQueueHolder<T> implements EmitterQueueHolder<MemoryBoundLinkedBlockingQueue.ObjectContainer<T>>
{
  private final MemoryBoundLinkedBlockingQueue<T> eventsQueue;
  private final long DEFAULT_OFFER_TIMEOUT_MILLIS = 10;
  private final long DEFAULT_POLL_TIMEOUT_MILLIS = 10;

  public EmitterMemoryBoundedQueueHolder(MemoryBoundLinkedBlockingQueue<T> eventsQueue)
  {
    this.eventsQueue = eventsQueue;
  }

  @Override
  public boolean offer(MemoryBoundLinkedBlockingQueue.ObjectContainer<T> event)
  {
    return eventsQueue.offer(event);
  }

  @Override
  public MemoryBoundLinkedBlockingQueue.ObjectContainer<T> poll() throws InterruptedException
  {
    return eventsQueue.poll(DEFAULT_POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
  }
}
