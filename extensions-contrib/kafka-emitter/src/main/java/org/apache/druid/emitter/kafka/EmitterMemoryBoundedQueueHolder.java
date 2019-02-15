package org.apache.druid.emitter.kafka;

import org.apache.druid.java.util.emitter.core.EmitterQueueHolder;

public class EmitterMemoryBoundedQueueHolder<T> implements EmitterQueueHolder<MemoryBoundLinkedBlockingQueue.ObjectContainer<T>>
{
  private final MemoryBoundLinkedBlockingQueue<T> eventsQueue;

  public EmitterMemoryBoundedQueueHolder(MemoryBoundLinkedBlockingQueue<T> eventsQueue) {
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
    return eventsQueue.take();
  }
}
