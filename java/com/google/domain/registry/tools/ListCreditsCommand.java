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

package com.google.domain.registry.tools;

import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.domain.registry.model.billing.RegistrarCredit;
import com.google.domain.registry.model.billing.RegistrarCreditBalance.BalanceMap;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.tools.Command.GtechCommand;
import com.google.domain.registry.tools.Command.RemoteApiCommand;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.googlecode.objectify.Work;

import org.joda.money.Money;

/** Command to list registrar credits and balances. */
@Parameters(commandDescription = "List registrar credits and balances")
final class ListCreditsCommand implements RemoteApiCommand, GtechCommand {

  @Parameter(
      names = {"-a", "--all"},
      description = "Pass this flag to show all credits, even those with a zero active balance")
  private boolean showAll;

  @Override
  public void run() {
    for (Registrar registrar : Registrar.loadAll()) {
      ImmutableList<String> creditStrings = createCreditStrings(registrar);
      if (!creditStrings.isEmpty()) {
        System.out.println(registrar.getClientIdentifier());
        System.out.print(Joiner.on("").join(creditStrings));
        System.out.println();
      }
    }
  }

  private ImmutableList<String> createCreditStrings(final Registrar registrar) {
    return ofy()
        .transactNewReadOnly(
            new Work<ImmutableList<String>>() {
              @Override
              public ImmutableList<String> run() {
                ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
                for (RegistrarCredit credit : RegistrarCredit.loadAllForRegistrar(registrar)) {
                  BalanceMap balanceMap = BalanceMap.createForCredit(credit);
                  Optional<Money> activeBalance =
                      balanceMap.getActiveBalanceAtTime(ofy().getTransactionTime());
                  // Unless showAll is true, only show credits with a positive active balance (which
                  // excludes just zero-balance credits since credit balances cannot be negative).
                  if (showAll || (activeBalance.isPresent() && activeBalance.get().isPositive())) {
                    builder.add(credit.getSummary() + "\n" + balanceMap);
                  }
                }
                return builder.build();
              }
            });
  }
}