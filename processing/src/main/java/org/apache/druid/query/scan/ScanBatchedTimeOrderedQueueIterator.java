/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.apache.druid.query.scan;

import org.apache.druid.java.util.common.parsers.CloseableIterator;
import org.apache.druid.java.util.emitter.core.Event;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ScanBatchedTimeOrderedQueueIterator implements CloseableIterator<ScanResultValue>
{
  private final Iterator<ScanResultValue> itr;
  private final int batchSize;

  public ScanBatchedTimeOrderedQueueIterator(Iterator<ScanResultValue> iterator, int batchSize)
  {
    itr = iterator;
    this.batchSize = batchSize;
  }

  @Override
  public void close() throws IOException
  {
  }

  @Override
  public boolean hasNext()
  {
    return itr.hasNext();
  }

  @Override
  public ScanResultValue next()
  {
    // Create new scanresultvalue from event map
    List<Object> eventsToAdd = new ArrayList<>(batchSize);
    List<String> columns = new ArrayList<>();
    while (eventsToAdd.size() < batchSize && itr.hasNext()) {
      ScanResultValue srv = itr.next();
      // Only replace once using the columns from the first event
      columns = columns.isEmpty() ? srv.getColumns() : columns;
      eventsToAdd.add(((List) srv.getEvents()).get(0));
    }
    return new ScanResultValue(null, columns, eventsToAdd);
  }
}
