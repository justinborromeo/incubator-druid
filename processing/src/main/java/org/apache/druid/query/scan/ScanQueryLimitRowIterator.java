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

import com.google.common.collect.Iterables;
import org.apache.druid.java.util.common.UOE;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.java.util.common.guava.Yielder;
import org.apache.druid.java.util.common.guava.YieldingAccumulator;
import org.apache.druid.java.util.common.parsers.CloseableIterator;
import org.apache.druid.query.QueryPlus;
import org.apache.druid.query.QueryRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScanQueryLimitRowIterator implements CloseableIterator<ScanResultValue>
{
  private static final String TIME_ORDERING_SEGMENT_ID = "No segment ID available when using time ordering";
  private Yielder<ScanResultValue> yielder;
  private ScanQuery.ResultFormat resultFormat;
  private long limit;
  private long count = 0;
  private ScanQuery query;

  public ScanQueryLimitRowIterator(
      QueryRunner<ScanResultValue> baseRunner,
      QueryPlus<ScanResultValue> queryPlus,
      Map<String, Object> responseContext
  )
  {
    this.query = (ScanQuery) queryPlus.getQuery();
    this.resultFormat = query.getResultFormat();
    this.limit = query.getLimit();
    Sequence<ScanResultValue> baseSequence = baseRunner.run(queryPlus, responseContext);
    this.yielder = baseSequence.toYielder(
        null,
        new YieldingAccumulator<ScanResultValue, ScanResultValue>()
        {
          @Override
          public ScanResultValue accumulate(ScanResultValue accumulated, ScanResultValue in)
          {
            yield();
            return in;
          }
        }
    );
  }

  @Override
  public boolean hasNext()
  {
    return !yielder.isDone() && count < limit;
  }

  @Override
  public ScanResultValue next()
  {
    if (ScanQuery.ResultFormat.RESULT_FORMAT_VALUE_VECTOR.equals(resultFormat)) {
      throw new UOE(ScanQuery.ResultFormat.RESULT_FORMAT_VALUE_VECTOR + " is not supported yet");
    }

    // We don't want to perform batching at the historical-level if we're performing time ordering
    if (query.getTimeOrder() == ScanQuery.TimeOrder.NONE ||
        !query.getContextBoolean(ScanQuery.CTX_KEY_OUTERMOST, true)) {
      ScanResultValue batch = yielder.get();
      List events = (List) batch.getEvents();
      if (events.size() <= limit - count) {
        count += events.size();
        yielder = yielder.next(null);
        return batch;
      } else {
        // last batch
        // single batch length is <= Integer.MAX_VALUE, so this should not overflow
        int left = (int) (limit - count);
        count = limit;
        return new ScanResultValue(batch.getSegmentId(), batch.getColumns(), events.subList(0, left));
      }
    } else {
      // Perform single-event ScanResultValue batching.  Each scan result value in this case will only have one event
      // so there's no need to iterate through events.
      int batchSize = query.getBatchSize();
      List<Object> eventsToAdd = new ArrayList<>(batchSize);
      List<String> columns = new ArrayList<>();
      while (eventsToAdd.size() < batchSize && !yielder.isDone()) {
        ScanResultValue srv = yielder.get();
        // Only replace once using the columns from the first event
        columns = columns.isEmpty() ? srv.getColumns() : columns;
        eventsToAdd.add(Iterables.getOnlyElement((List) srv.getEvents()));
        yielder = yielder.next(null);
      }
      return new ScanResultValue(TIME_ORDERING_SEGMENT_ID, columns, eventsToAdd);
    }
  }

  @Override
  public void remove()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() throws IOException
  {
    yielder.close();
  }
}
