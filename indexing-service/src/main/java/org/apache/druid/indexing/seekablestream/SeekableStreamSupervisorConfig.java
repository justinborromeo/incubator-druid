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

package org.apache.druid.indexing.seekablestream;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.Min;

public class SeekableStreamSupervisorConfig
{
  @JsonProperty
  private boolean storingStackTraces = false;

  // TODO improve the name of these configs

  // The number of runs failed before the supervisor flips from a RUNNING to an UNHEALTHY state
  @JsonProperty
  @Min(1)
  private int supervisorUnhealthinessThreshold = 2;

  // The number of successful before the supervisor flips from an UNHEALTHY to a RUNNING state
  @JsonProperty
  @Min(1)
  private int supervisorHealthinessThreshold = 3;

  @JsonProperty
  @Min(1)
  private int supervisorTaskUnhealthinessThreshold = 2;

  @JsonProperty
  @Min(1)
  private int supervisorTaskHealthinessThreshold = 3;

  public boolean isStoringStackTraces()
  {
    return storingStackTraces;
  }

  public int getSupervisorUnhealthinessThreshold()
  {
    return supervisorUnhealthinessThreshold;
  }

  public int getSupervisorHealthinessThreshold()
  {
    return supervisorHealthinessThreshold;
  }

  public int getSupervisorTaskUnhealthinessThreshold()
  {
    return supervisorTaskUnhealthinessThreshold;
  }

  public int getSupervisorTaskHealthinessThreshold()
  {
    return supervisorTaskHealthinessThreshold;
  }
}