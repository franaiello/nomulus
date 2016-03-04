// Copyright 2016 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.domain.registry.tools.server;

import static com.google.domain.registry.model.registry.Registries.getTlds;
import static com.google.domain.registry.request.Action.Method.GET;
import static com.google.domain.registry.request.Action.Method.POST;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.util.Clock;

import org.joda.time.DateTime;

import javax.inject.Inject;


/** An action that lists top-level domains, for use by the registry_tool list_tlds command. */
@Action(path = ListTldsAction.PATH, method = {GET, POST})
public final class ListTldsAction extends ListObjectsAction<Registry> {

  public static final String PATH = "/_dr/admin/list/tlds";

  @Inject Clock clock;
  @Inject ListTldsAction() {}

  @Override
  public ImmutableSet<String> getPrimaryKeyFields() {
    return ImmutableSet.of("tldStr");
  }

  @Override
  public ImmutableSet<Registry> loadObjects() {
    return FluentIterable.from(getTlds())
        .transform(new Function<String, Registry>() {
            @Override
            public Registry apply(String tldString) {
              return Registry.get(tldString);
            }})
        .toSet();
  }

  @Override
  public ImmutableBiMap<String, String> getFieldAliases() {
    return ImmutableBiMap.of(
        "TLD", "tldStr",
        "dns", "dnsPaused",
        "escrow", "escrowEnabled",
        "premiumPricing", "premiumPriceAckRequired");
  }

  @Override
  public ImmutableMap<String, String> getFieldOverrides(Registry registry) {
    final DateTime now = clock.nowUtc();
    return new ImmutableMap.Builder<String, String>()
        .put("dnsPaused", registry.getDnsPaused() ? "paused" : "-")
        .put("escrowEnabled", registry.getEscrowEnabled() ? "enabled" : "-")
        .put("premiumPriceAckRequired", registry.getPremiumPriceAckRequired() ? "ack req'd" : "-")
        .put("tldState", registry.isPdt(now) ? "PDT" : registry.getTldState(now).toString())
        .put("tldStateTransitions", registry.getTldStateTransitions().toString())
        .put("renewBillingCost", registry.getStandardRenewCost(now).toString())
        .put("renewBillingCostTransitions", registry.getRenewBillingCostTransitions().toString())
        .build();
  }
}