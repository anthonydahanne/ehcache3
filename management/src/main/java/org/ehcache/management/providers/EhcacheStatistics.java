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
package org.ehcache.management.providers;

import org.ehcache.Ehcache;
import org.ehcache.management.config.StatisticsProviderConfiguration;
import org.ehcache.statistics.CacheOperationOutcomes;
import org.terracotta.context.ContextManager;
import org.terracotta.context.TreeNode;
import org.terracotta.context.extended.ExposedStatistic;
import org.terracotta.context.extended.StatisticsRegistry;
import org.terracotta.context.query.Matcher;
import org.terracotta.context.query.Query;
import org.terracotta.management.capabilities.descriptors.Descriptor;
import org.terracotta.management.capabilities.descriptors.StatisticDescriptor;
import org.terracotta.management.capabilities.descriptors.StatisticDescriptorCategory;
import org.terracotta.management.stats.Sample;
import org.terracotta.management.stats.Statistic;
import org.terracotta.management.stats.StatisticType;
import org.terracotta.management.stats.primitive.Setting;
import org.terracotta.management.stats.sampled.SampledCounter;
import org.terracotta.management.stats.sampled.SampledDuration;
import org.terracotta.management.stats.sampled.SampledRate;
import org.terracotta.management.stats.sampled.SampledRatio;
import org.terracotta.statistics.archive.Timestamped;
import org.terracotta.statistics.extended.Result;
import org.terracotta.statistics.extended.SampledStatistic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.terracotta.context.query.Matchers.attributes;
import static org.terracotta.context.query.Matchers.context;
import static org.terracotta.context.query.Matchers.hasAttribute;
import static org.terracotta.context.query.QueryBuilder.queryBuilder;

/**
 * @author Ludovic Orban
 */
public class EhcacheStatistics {

  private static final Set<CacheOperationOutcomes.PutOutcome> ALL_CACHE_PUT_OUTCOMES = EnumSet.allOf(CacheOperationOutcomes.PutOutcome.class);
  private static final Set<CacheOperationOutcomes.GetOutcome> ALL_CACHE_GET_OUTCOMES = EnumSet.allOf(CacheOperationOutcomes.GetOutcome.class);
  private static final Set<CacheOperationOutcomes.GetOutcome> ALL_CACHE_MISS_OUTCOMES = EnumSet.of(CacheOperationOutcomes.GetOutcome.FAILURE, CacheOperationOutcomes.GetOutcome.MISS_NO_LOADER, CacheOperationOutcomes.GetOutcome.MISS_WITH_LOADER);
  private static final Set<CacheOperationOutcomes.RemoveOutcome> ALL_CACHE_REMOVE_OUTCOMES = EnumSet.allOf(CacheOperationOutcomes.RemoveOutcome.class);
  private static final Set<CacheOperationOutcomes.GetOutcome> GET_WITH_LOADER_OUTCOMES = EnumSet.of(CacheOperationOutcomes.GetOutcome.HIT_WITH_LOADER, CacheOperationOutcomes.GetOutcome.MISS_WITH_LOADER);
  private static final Set<CacheOperationOutcomes.GetOutcome> GET_NO_LOADER_OUTCOMES = EnumSet.of(CacheOperationOutcomes.GetOutcome.HIT_NO_LOADER, CacheOperationOutcomes.GetOutcome.MISS_NO_LOADER);
  private static final Set<CacheOperationOutcomes.CacheLoadingOutcome> ALL_CACHE_LOADER_OUTCOMES = EnumSet.allOf(CacheOperationOutcomes.CacheLoadingOutcome.class);

  private final StatisticsRegistry statisticsRegistry;
  private final Ehcache<?, ?> contextObject;

  EhcacheStatistics(Ehcache<?, ?> contextObject, StatisticsProviderConfiguration configuration, ScheduledExecutorService executor) {
    this.contextObject = contextObject;
    this.statisticsRegistry = new StatisticsRegistry(StandardOperationStatistic.class, contextObject, executor, configuration.averageWindowDuration(),
        configuration.averageWindowUnit(), configuration.historySize(), configuration.historyInterval(), configuration.historyIntervalUnit(),
        configuration.timeToDisable(), configuration.timeToDisableUnit());

    statisticsRegistry.registerCompoundOperation("AllCacheGet", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Result"), StandardOperationStatistic.CACHE_GET, ALL_CACHE_GET_OUTCOMES);
    statisticsRegistry.registerCompoundOperation("AllCacheMiss", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Result"), StandardOperationStatistic.CACHE_GET, ALL_CACHE_MISS_OUTCOMES);
    statisticsRegistry.registerCompoundOperation("AllCachePut", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Result"), StandardOperationStatistic.CACHE_PUT, ALL_CACHE_PUT_OUTCOMES);
    statisticsRegistry.registerCompoundOperation("AllCacheRemove", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Result"), StandardOperationStatistic.CACHE_REMOVE, ALL_CACHE_REMOVE_OUTCOMES);
    statisticsRegistry.registerCompoundOperation("GetWithLoader", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Result"), StandardOperationStatistic.CACHE_GET, GET_WITH_LOADER_OUTCOMES);
    statisticsRegistry.registerCompoundOperation("GetNoLoader", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Result"), StandardOperationStatistic.CACHE_GET, GET_NO_LOADER_OUTCOMES);
    statisticsRegistry.registerCompoundOperation("AllCacheLoader", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Result"), StandardOperationStatistic.CACHE_LOADING, ALL_CACHE_LOADER_OUTCOMES);
    statisticsRegistry.registerRatio("Hit", Collections.singleton("cache"), Collections.<String, Object>singletonMap("type", "Ratio"), StandardOperationStatistic.CACHE_GET, EnumSet.of(CacheOperationOutcomes.GetOutcome.HIT_NO_LOADER), ALL_CACHE_GET_OUTCOMES);
  }

  public String getCacheName() {
    Query q = queryBuilder()
        .children()
        .filter(context(attributes(hasAttribute("tags", new Matcher<Set<String>>() {
          @Override
          protected boolean matchesSafely(Set<String> object) {
            return object.containsAll(Arrays.asList("cache", "exposed"));
          }
        }))))
        .build();

    Set<TreeNode> queryResult = q.execute(Collections.singleton(ContextManager.nodeFor(contextObject)));
    if (queryResult.size() != 1) {
      throw new RuntimeException("Cache without name setting : " + contextObject);
    }
    TreeNode treeNode = queryResult.iterator().next();
    return (String) treeNode.getContext().attributes().get("CacheName");
  }

  public Statistic<?> queryStatistic(String statisticName) {
    Collection<ExposedStatistic> registrations = statisticsRegistry.getRegistrations();
    for (ExposedStatistic registration : registrations) {
      Object type = registration.getProperties().get("type");
      String name = registration.getName();

      if ("Result".equals(type)) {
        Result result = (Result) registration.getStat();

        if ((name + "Count").equals(statisticName)) {
          SampledStatistic<Long> count = result.count();
          return new SampledCounter(statisticName, buildSamples(count));
        } else if ((name + "Rate").equals(statisticName)) {
          SampledStatistic<Double> rate = result.rate();
          return new SampledRate(statisticName, buildSamples(rate), TimeUnit.SECONDS); //TODO: get the TimeUnit from config
        } else if ((name + "LatencyMinimum").equals(statisticName)) {
          SampledStatistic<Long> minimum = result.latency().minimum();
          return new SampledDuration(statisticName, buildSamples(minimum), TimeUnit.SECONDS); //TODO: get the TimeUnit from config
        } else if ((name + "LatencyMaximum").equals(statisticName)) {
          SampledStatistic<Long> maximum = result.latency().maximum();
          return new SampledDuration(statisticName, buildSamples(maximum), TimeUnit.SECONDS); //TODO: get the TimeUnit from config
        } else if ((name + "LatencyAverage").equals(statisticName)) {
          SampledStatistic<Double> average = result.latency().average();
          return new SampledRatio(statisticName, buildSamples(average));
        }
      } else if ("Ratio".equals(type)) {
        if ((name + "Ratio").equals(statisticName)) {
          SampledStatistic<Double> ratio = (SampledStatistic) registration.getStat();
          return new SampledRatio(statisticName, buildSamples(ratio));
        }
      }
    }

    Query q = queryBuilder()
        .children()
        .filter(context(attributes(hasAttribute("tags", new Matcher<Set<String>>() {
          @Override
          protected boolean matchesSafely(Set<String> object) {
            return object.containsAll(Arrays.asList("cache", "exposed"));
          }
        }))))
        .build();

    Set<TreeNode> queryResult = q.execute(Collections.singleton(ContextManager.nodeFor(contextObject)));

    for (TreeNode treeNode : queryResult) {
      Object attributesProperty = treeNode.getContext().attributes().get("properties");
      if (attributesProperty != null && attributesProperty instanceof Map) {
        Map<String, Object> attributes = (Map<String, Object>) attributesProperty;

        Object setting = attributes.get("Setting");
        if (setting != null && setting.equals(statisticName)) {
          return new Setting<String>(statisticName, (String) treeNode.getContext().attributes().get("CacheName"));
        }
      }
    }

    throw new IllegalArgumentException("Unknown statistic name : " + statisticName);
  }

  private <T extends Number> List<Sample<T>> buildSamples(SampledStatistic<T> sampledStatistic) {
    List<Sample<T>> result = new ArrayList<Sample<T>>();

    List<Timestamped<T>> history = sampledStatistic.history();
    for (Timestamped<T> timestamped : history) {
      result.add(new Sample<T>(timestamped.getTimestamp(), timestamped.getSample()));
    }

    return result;
  }

  public Set<Descriptor> capabilities() {
    Set<Descriptor> capabilities = new HashSet<Descriptor>();

    capabilities.addAll(searchContextTreeForSettings());
    capabilities.addAll(queryStatisticsRegistry());

    return capabilities;
  }

  private Set<Descriptor> queryStatisticsRegistry() {
    Set<Descriptor> capabilities = new HashSet<Descriptor>();

    Collection<ExposedStatistic> registrations = statisticsRegistry.getRegistrations();
    for (ExposedStatistic registration : registrations) {
      String name = registration.getName();
      Object type = registration.getProperties().get("type");
      if ("Result".equals(type)) {
        List<StatisticDescriptor> statistics = new ArrayList<StatisticDescriptor>();
        statistics.add(new StatisticDescriptor(name + "Count", StatisticType.SAMPLED_COUNTER));
        statistics.add(new StatisticDescriptor(name + "Rate", StatisticType.SAMPLED_RATE));
        statistics.add(new StatisticDescriptor(name + "LatencyMinimum", StatisticType.SAMPLED_DURATION));
        statistics.add(new StatisticDescriptor(name + "LatencyMaximum", StatisticType.SAMPLED_DURATION));
        statistics.add(new StatisticDescriptor(name + "LatencyAverage", StatisticType.SAMPLED_RATIO));

        capabilities.add(new StatisticDescriptorCategory(name, statistics));
      } else if ("Ratio".equals(type)) {
        capabilities.add(new StatisticDescriptor(name + "Ratio", StatisticType.SAMPLED_RATIO));
      }
    }

    return capabilities;
  }

  private Set<Descriptor> searchContextTreeForSettings() {
    Set<Descriptor> capabilities = new HashSet<Descriptor>();

    Query q = queryBuilder()
        .children()
        .filter(context(attributes(hasAttribute("tags", new Matcher<Set<String>>() {
          @Override
          protected boolean matchesSafely(Set<String> object) {
            return object.containsAll(Arrays.asList("cache", "exposed"));
          }
        }))))
        .build();

    Set<TreeNode> queryResult = q.execute(Collections.singleton(ContextManager.nodeFor(contextObject)));
    for (TreeNode treeNode : queryResult) {
      capabilities.addAll(buildCapabilities(treeNode));
    }

    return capabilities;
  }

  private Set<Descriptor> buildCapabilities(TreeNode treeNode) {
    Set<Descriptor> capabilities = new HashSet<Descriptor>();

    Object attributesProperty = treeNode.getContext().attributes().get("properties");
    if (attributesProperty != null && attributesProperty instanceof Map) {
      Map<String, Object> attributes = (Map<String, Object>) attributesProperty;

      Object setting = attributes.get("Setting");
      if (setting != null) {
        capabilities.add(new StatisticDescriptor(setting.toString(), StatisticType.SETTING));
      }
    }

    return capabilities;
  }

  public void dispose() {
    statisticsRegistry.clearRegistrations();
  }
}
