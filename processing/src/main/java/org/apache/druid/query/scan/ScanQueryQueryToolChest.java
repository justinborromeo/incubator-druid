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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import org.apache.druid.java.util.common.UOE;
import org.apache.druid.java.util.common.guava.BaseSequence;
import org.apache.druid.java.util.common.guava.CloseQuietly;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.java.util.common.parsers.CloseableIterator;
import org.apache.druid.query.GenericQueryMetricsFactory;
import org.apache.druid.query.Query;
import org.apache.druid.query.QueryMetrics;
import org.apache.druid.query.QueryPlus;
import org.apache.druid.query.QueryRunner;
import org.apache.druid.query.QueryToolChest;
import org.apache.druid.query.aggregation.MetricManipulationFn;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class ScanQueryQueryToolChest extends QueryToolChest<ScanResultValue, ScanQuery>
{
  private static final TypeReference<ScanResultValue> TYPE_REFERENCE = new TypeReference<ScanResultValue>()
  {
  };

  private final ScanQueryConfig scanQueryConfig;
  private final GenericQueryMetricsFactory queryMetricsFactory;

  @Inject
  public ScanQueryQueryToolChest(
      final ScanQueryConfig scanQueryConfig,
      final GenericQueryMetricsFactory queryMetricsFactory
  )
  {
    this.scanQueryConfig = scanQueryConfig;
    this.queryMetricsFactory = queryMetricsFactory;
  }

  @Override
  public QueryRunner<ScanResultValue> mergeResults(final QueryRunner<ScanResultValue> runner)
  {
    return (queryPlus, responseContext) -> {
      // Ensure "legacy" is a non-null value, such that all other nodes this query is forwarded to will treat it
      // the same way, even if they have different default legacy values.
      final ScanQuery scanQuery = ((ScanQuery) queryPlus.getQuery()).withNonNullLegacy(scanQueryConfig);
      final QueryPlus<ScanResultValue> queryPlusWithNonNullLegacy = queryPlus.withQuery(scanQuery);
      if (scanQuery.getTimeOrder().equals(ScanQuery.TimeOrder.NONE)) {
        if (scanQuery.getLimit() == Long.MAX_VALUE) {
          return runner.run(queryPlusWithNonNullLegacy, responseContext);
        } else {
          return new BaseSequence<>(
              new BaseSequence.IteratorMaker<ScanResultValue, ScanQueryLimitRowIterator>()
              {
                @Override
                public ScanQueryLimitRowIterator make()
                {
                  return new ScanQueryLimitRowIterator(runner, queryPlusWithNonNullLegacy, responseContext);
                }

                @Override
                public void cleanup(ScanQueryLimitRowIterator iterFromMake)
                {
                  CloseQuietly.close(iterFromMake);
                }
              });
        }
      } else if (scanQuery.getLimit() <= scanQueryConfig.getMaxRowsTimeOrderQueuedInMemory()) {
        ScanQueryNoLimitRowIterator scanResultIterator =
            new BaseSequence.IteratorMaker<ScanResultValue, ScanQueryNoLimitRowIterator>()
            {
              @Override
              public ScanQueryNoLimitRowIterator make()
              {
                return new ScanQueryNoLimitRowIterator(runner, queryPlusWithNonNullLegacy, responseContext);
              }

              @Override
              public void cleanup(ScanQueryNoLimitRowIterator iterFromMake)
              {
                CloseQuietly.close(iterFromMake);
              }
            }.make();

        return new BaseSequence(
            new BaseSequence.IteratorMaker<ScanResultValue, ScanBatchedIterator>()
            {
              @Override
              public ScanBatchedIterator make()
              {
                return new ScanBatchedIterator(
                    sortAndLimitScanResultValues(scanResultIterator, scanQuery),
                    scanQuery.getBatchSize()
                );
              }

              @Override
              public void cleanup(ScanBatchedIterator iterFromMake)
              {
                CloseQuietly.close(iterFromMake);
              }
            });
      } else {
        throw new UOE(
            "Time ordering for result set limit of %,d is not supported.  Try lowering the "
            + "result set size to less than or equal to the time ordering limit of %,d.",
            scanQuery.getLimit(),
            scanQueryConfig.getMaxRowsTimeOrderQueuedInMemory()
        );
      }
    };
  }

  @Override
  public QueryMetrics<Query<?>> makeMetrics(ScanQuery query)
  {
    return queryMetricsFactory.makeMetrics(query);
  }

  @Override
  public Function<ScanResultValue, ScanResultValue> makePreComputeManipulatorFn(
      ScanQuery query,
      MetricManipulationFn fn
  )
  {
    return Functions.identity();
  }

  @Override
  public TypeReference<ScanResultValue> getResultTypeReference()
  {
    return TYPE_REFERENCE;
  }

  @Override
  public QueryRunner<ScanResultValue> preMergeQueryDecoration(final QueryRunner<ScanResultValue> runner)
  {
    return new QueryRunner<ScanResultValue>()
    {
      @Override
      public Sequence<ScanResultValue> run(QueryPlus<ScanResultValue> queryPlus, Map<String, Object> responseContext)
      {
        ScanQuery scanQuery = (ScanQuery) queryPlus.getQuery();
        if (scanQuery.getFilter() != null) {
          scanQuery = scanQuery.withDimFilter(scanQuery.getFilter().optimize());
          queryPlus = queryPlus.withQuery(scanQuery);
        }
        return runner.run(queryPlus, responseContext);
      }
    };
  }

  @VisibleForTesting
  Iterator<ScanResultValue> sortAndLimitScanResultValues(Iterator<ScanResultValue> inputIterator, ScanQuery scanQuery)
  {
    Comparator<ScanResultValue> priorityQComparator =
        new ScanResultValueTimestampComparator(scanQuery.getResultFormat(), scanQuery.getTimeOrder());

    // Converting the limit from long to int could theoretically throw an ArithmeticException but this branch
    // only runs if limit < MAX_LIMIT_FOR_IN_MEMORY_TIME_ORDERING (which should be < Integer.MAX_VALUE)
    int limit = Math.toIntExact(scanQuery.getLimit());
    PriorityQueue<ScanResultValue> q = new PriorityQueue<>(limit, priorityQComparator);

    while (inputIterator.hasNext()) {
      ScanResultValue next = inputIterator.next();
      List<Object> events = (List<Object>) next.getEvents();
      for (Object event : events) {
        // Using an intermediate unbatched ScanResultValue is not that great memory-wise, but the column list
        // needs to be preserved for queries using the compactedList result format
        q.offer(new ScanResultValue(null, next.getColumns(), Collections.singletonList(event)));
        if (q.size() > limit) {
          q.poll();
        }
      }
    }
    // Need to convert to a List because Priority Queue's iterator doesn't guarantee that the sorted order
    // will be maintained
    final Deque<ScanResultValue> sortedElements = new ArrayDeque<>(q.size());
    while (q.size() != 0) {
      // We add at the front of the list because poll removes the tail of the queue.
      sortedElements.addFirst(q.poll());
    }

    return sortedElements.iterator();
  }

  /**
   * This iterator supports iteration through any Iterable of unbatched ScanResultValues (1 event/ScanResultValue) and
   * aggregates events into ScanResultValues with {@code batchSize} events.  The columns from the first event per
   * ScanResultValue will be used to populate the column section.
   */
  private static class ScanBatchedIterator implements CloseableIterator<ScanResultValue>
  {
    private final Iterator<ScanResultValue> itr;
    private final int batchSize;

    public ScanBatchedIterator(Iterator<ScanResultValue> iterator, int batchSize)
    {
      this.itr = iterator;
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
      // Create new ScanResultValue from event map
      List<Object> eventsToAdd = new ArrayList<>(batchSize);
      List<String> columns = new ArrayList<>();
      while (eventsToAdd.size() < batchSize && itr.hasNext()) {
        ScanResultValue srv = itr.next();
        // Only replace once using the columns from the first event
        columns = columns.isEmpty() ? srv.getColumns() : columns;
        eventsToAdd.add(Iterables.getOnlyElement((List) srv.getEvents()));
      }
      return new ScanResultValue(null, columns, eventsToAdd);
    }
  }
}
