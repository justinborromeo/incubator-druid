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
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import org.apache.druid.java.util.common.UOE;
import org.apache.druid.java.util.common.guava.BaseSequence;
import org.apache.druid.java.util.common.guava.CloseQuietly;
import org.apache.druid.java.util.common.guava.Sequence;
import org.apache.druid.query.GenericQueryMetricsFactory;
import org.apache.druid.query.Query;
import org.apache.druid.query.QueryMetrics;
import org.apache.druid.query.QueryPlus;
import org.apache.druid.query.QueryRunner;
import org.apache.druid.query.QueryToolChest;
import org.apache.druid.query.aggregation.MetricManipulationFn;
import org.apache.druid.segment.column.ColumnHolder;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class ScanQueryQueryToolChest extends QueryToolChest<ScanResultValue, ScanQuery>
{
  private static final TypeReference<ScanResultValue> TYPE_REFERENCE = new TypeReference<ScanResultValue>()
  {
  };
  public static final long MAX_LIMIT_FOR_IN_MEMORY_TIME_ORDERING = 100000;

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

      BaseSequence.IteratorMaker scanQueryLimitRowIteratorMaker =
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
          };

      Sequence baseSequence = new BaseSequence<>(scanQueryLimitRowIteratorMaker);

      if (scanQuery.getTimeOrder().equals(ScanQuery.TIME_ORDER_NONE) ||
          scanQuery.getLimit() > MAX_LIMIT_FOR_IN_MEMORY_TIME_ORDERING) {
        if (scanQuery.getLimit() == Long.MAX_VALUE) {
          return runner.run(queryPlusWithNonNullLegacy, responseContext);
        }
        return baseSequence;
      } else if (scanQuery.getTimeOrder().equals(ScanQuery.TIME_ORDER_ASCENDING) ||
                 scanQuery.getTimeOrder().equals(ScanQuery.TIME_ORDER_DESCENDING)) {
        Comparator<Map<String, Object>> comparator = (val1, val2) -> {
          int comparison = Longs.compare(
              (Long) val1.get(ColumnHolder.TIME_COLUMN_NAME),
              (Long) val2.get(ColumnHolder.TIME_COLUMN_NAME)
          );
          if (scanQuery.getTimeOrder().equals(ScanQuery.TIME_ORDER_DESCENDING)) {
            return comparison * -1;
          } else {
            return comparison;
          }
        };
        // Converting the limit from long to int could theoretically throw an ArithmeticException but this branch
        // only runs if limit < MAX_LIMIT_FOR_IN_MEMORY_TIME_ORDERING (which is < Integer.MAX_VALUE)
        PriorityQueue<Map<String, Object>> q = new PriorityQueue<>(Math.toIntExact(scanQuery.getLimit()), comparator);
        Iterator<ScanResultValue> rowIterator = scanQueryLimitRowIteratorMaker.make();
        while (rowIterator.hasNext()) {
          List<Map<String, Object>> events = (List<Map<String, Object>>) rowIterator.next().getEvents();
          for (Map<String, Object> event : events) {
            q.offer(event);
          }
        }

        Iterator queueIterator = q.iterator();

        return new BaseSequence(
            new BaseSequence.IteratorMaker<ScanResultValue, QueueIterator>()
            {
              @Override
              public QueueIterator make()
              {
                return new QueueIterator(queueIterator);
              }

              @Override
              public void cleanup(QueueIterator iterFromMake)
              {
                CloseQuietly.close(iterFromMake);
              }
            });
      } else {
        throw new UOE("Time ordering [%s] is not supported", scanQuery.getTimeOrder());
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
    return (queryPlus, responseContext) -> {
      ScanQuery scanQuery = (ScanQuery) queryPlus.getQuery();
      if (scanQuery.getFilter() != null) {
        scanQuery = scanQuery.withDimFilter(scanQuery.getFilter().optimize());
        queryPlus = queryPlus.withQuery(scanQuery);
      }
      return runner.run(queryPlus, responseContext);
    };
  }
}
