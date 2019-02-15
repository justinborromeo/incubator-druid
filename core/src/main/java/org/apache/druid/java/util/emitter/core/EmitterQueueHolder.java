package org.apache.druid.java.util.emitter.core;


/**
 * A layer of abstraction over BlockingQueue and MemoryBoundLinkedBlockingQueue(which doesn't implement
 * or extend BlockingQueue).
 */
public interface EmitterQueueHolder<T>
{
  boolean offer(T event);

  T poll() throws InterruptedException;
}
