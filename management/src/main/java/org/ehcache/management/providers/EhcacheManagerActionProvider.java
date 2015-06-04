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

import org.ehcache.EhcacheManager;
import org.terracotta.management.capabilities.context.CapabilityContext;

import java.util.Collections;

/**
 * @author Ludovic Orban
 */
public class EhcacheManagerActionProvider extends AbstractActionProvider<EhcacheManager, Object> {

  @Override
  public Class<EhcacheManager> managedType() {
    return EhcacheManager.class;
  }

  @Override
  public CapabilityContext capabilityContext() {
    return new CapabilityContext(Collections.singletonList(new CapabilityContext.Attribute("cacheManagerName", true)));
  }

  @Override
  protected Object createActionWrapper(EhcacheManager contextObject) {
    return new Object();
  }
}
