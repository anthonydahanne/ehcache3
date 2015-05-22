/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ehcache.config;

import org.ehcache.statistics.StatisticsProvider;

import java.util.concurrent.TimeUnit;

/**
 * @author Ludovic Orban
 */
public class StatisticsProviderConfigurationImpl implements StatisticsProviderConfiguration {

  private long averageWindowDuration;
  private TimeUnit averageWindowUnit;
  private int historySize;
  private long historyInterval;
  private TimeUnit historyIntervalUnit;

  public StatisticsProviderConfigurationImpl(long averageWindowDuration, TimeUnit averageWindowUnit, int historySize, long historyInterval, TimeUnit historyIntervalUnit) {
    this.averageWindowDuration = averageWindowDuration;
    this.averageWindowUnit = averageWindowUnit;
    this.historySize = historySize;
    this.historyInterval = historyInterval;
    this.historyIntervalUnit = historyIntervalUnit;
  }

  @Override
  public long averageWindowDuration() {
    return averageWindowDuration;
  }

  @Override
  public TimeUnit averageWindowUnit() {
    return averageWindowUnit;
  }

  @Override
  public int historySize() {
    return historySize;
  }

  @Override
  public long historyInterval() {
    return historyInterval;
  }

  @Override
  public TimeUnit historyIntervalUnit() {
    return historyIntervalUnit;
  }

  @Override
  public Class<StatisticsProvider> getServiceType() {
    return StatisticsProvider.class;
  }
}
