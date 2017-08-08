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
package org.ehcache.clustered.management;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.clustered.client.config.builders.ClusteredResourcePoolBuilder;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.management.registry.DefaultManagementRegistryConfiguration;
import org.ehcache.management.registry.DefaultManagementRegistryService;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.terracotta.management.model.context.Context;
import org.terracotta.management.model.stats.ContextualStatistics;
import org.terracotta.management.registry.ResultSet;
import org.terracotta.testing.rules.Cluster;

import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.TreeMap;

import static org.ehcache.clustered.client.config.builders.ClusteringServiceConfigurationBuilder.cluster;
import static org.ehcache.config.builders.CacheManagerBuilder.newCacheManagerBuilder;
import static org.ehcache.config.builders.ResourcePoolsBuilder.newResourcePoolsBuilder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.terracotta.testing.rules.BasicExternalClusterBuilder.newCluster;

public class OffHeapAsTheMiddleTierBehaviourChangeTest {

  private static final String RESOURCE_CONFIG =
    "<config xmlns:ohr='http://www.terracotta.org/config/offheap-resource'>"
      + "<ohr:offheap-resources>"
      + "<ohr:resource name=\"primary-server-resource\" unit=\"MB\">64</ohr:resource>"
      + "</ohr:offheap-resources>" +
      "</config>\n";

  protected static CacheManager cacheManager;


  @ClassRule
  // pre master style
  // public static Cluster CLUSTER = new BasicExternalCluster(new File("build/cluster"), 1, Collections.emptyList(), "", RESOURCE_CONFIG, "");
  // master style
  public static Cluster CLUSTER = newCluster().in(new File("build/cluster"))
    .withServiceFragment(RESOURCE_CONFIG).build();


  private static final String CACHE2_NAME = "plif";
  private static final String CM_NAME = "MyCM";
  private static DefaultManagementRegistryService managementRegistry;
  public static final Context[] CONTEXTS = new Context[]{
    Context.empty()
      .with("cacheManagerName", CM_NAME)
      .with("cacheName", CACHE2_NAME)
  };


  @BeforeClass
  public static void beforeClass() throws Exception {
    CLUSTER.getClusterControl().waitForActive();

    CacheConfiguration<Long, String> config2 = CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class, newResourcePoolsBuilder()
      .heap(10, EntryUnit.ENTRIES)
      .offheap(10, MemoryUnit.MB)
      .with(ClusteredResourcePoolBuilder.clusteredDedicated("primary-server-resource", 50, MemoryUnit.MB)))
      .build();

    DefaultManagementRegistryConfiguration managementRegistryConfiguration = new DefaultManagementRegistryConfiguration()
      .setCacheManagerAlias(CM_NAME);

    managementRegistryConfiguration.addTags("plaf", "plouf");

    managementRegistry = new DefaultManagementRegistryService(managementRegistryConfiguration);


    cacheManager = newCacheManagerBuilder()
      .with(cluster(CLUSTER.getConnectionURI().resolve("/my-server-entity-offheap")).autoCreate().defaultServerResource("primary-server-resource"))
      .withCache(CACHE2_NAME, config2)
      .using(managementRegistry)
      .build(true);

    startStatPrinting();

  }

  @Test
  public void offheapUsedToGetMappingsTest() throws Exception {

    Cache<Long, String> cache2 = cacheManager.getCache(CACHE2_NAME, Long.class, String.class);
    Random random = new Random();
    for (int i = 0; i < 100; i++) {
      Long key = Long.valueOf(i);
      String value = new BigInteger(1024, random).toString(16);

      cache2.put(key, value);
      cache2.get(key);
    }
    Thread.sleep(10_000);


    ResultSet<ContextualStatistics> executeQuery = managementRegistry.withCapability("StatisticsCapability")
      .queryAllStatistics()
      .on(Arrays.asList(CONTEXTS))
      .build()
      .execute();

    assertThat(executeQuery.results().values().stream().findFirst().get().getStatistic("OffHeap:MappingCount"), equalTo(90L));

  }


  static void startStatPrinting() {

    Thread statPrinter = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        ResultSet<ContextualStatistics> executeQuery = managementRegistry.withCapability("StatisticsCapability")
          .queryAllStatistics()
          .on(Arrays.asList(CONTEXTS))
          .build()
          .execute();


        executeQuery.results().forEach((context, contextualStatistics) -> {

          new TreeMap<>(contextualStatistics.getStatistics()).forEach((s, number) -> {
            System.out.println(s + " : " + number);
          });

        });

        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });
    statPrinter.start();

  }


}
