// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.tools;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.difference;
import static com.google.common.collect.Sets.intersection;
import static com.google.common.collect.Sets.union;
import static google.registry.model.registry.Registries.assertTldExists;
import static google.registry.util.CollectionUtils.nullToEmpty;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.googlecode.objectify.Key;

import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/** Command to update a TLD. */
@Parameters(separators = " =", commandDescription = "Update existing TLD(s)")
class UpdateTldCommand extends CreateOrUpdateTldCommand {
  @Nullable
  @Parameter(
      names = "--add_reserved_lists",
      description = "A comma-separated list of reserved list names to be added to the TLD")
  List<String> reservedListsAdd;

  @Nullable
  @Parameter(
      names = "--remove_reserved_lists",
      description = "A comma-separated list of reserved list names to be removed from the TLD")
  List<String> reservedListsRemove;

  @Nullable
  @Parameter(
      names = "--add_allowed_registrants",
      description = "A comma-separated list of allowed registrants to be added to the TLD")
  List<String> allowedRegistrantsAdd;

  @Nullable
  @Parameter(
      names = "--remove_allowed_registrants",
      description = "A comma-separated list of allowed registrants to be removed from the TLD")
  List<String> allowedRegistrantsRemove;

  @Nullable
  @Parameter(
      names = "--add_allowed_nameservers",
      description = "A comma-separated list of allowed nameservers to be added to the TLD")
  List<String> allowedNameserversAdd;

  @Nullable
  @Parameter(
      names = "--remove_allowed_nameservers",
      description = "A comma-separated list of allowed nameservers to be removed from the TLD")
  List<String> allowedNameserversRemove;

  @Override
  Registry getOldRegistry(String tld) {
    return Registry.get(assertTldExists(tld));
  }

  @Override
  ImmutableSet<String> getAllowedRegistrants(Registry oldRegistry) {
    return formUpdatedList(
        "allowed registrants",
        oldRegistry.getAllowedRegistrantContactIds(),
        allowedRegistrants,
        allowedRegistrantsAdd,
        allowedRegistrantsRemove);
  }

  @Override
  ImmutableSet<String> getAllowedNameservers(Registry oldRegistry) {
    return formUpdatedList(
        "allowed nameservers",
        oldRegistry.getAllowedFullyQualifiedHostNames(),
        allowedNameservers,
        allowedNameserversAdd,
        allowedNameserversRemove);
  }

  @Override
  ImmutableSet<String> getReservedLists(Registry oldRegistry) {
    return formUpdatedList(
        "reserved lists",
        FluentIterable
            .from(oldRegistry.getReservedLists())
            .transform(
                new Function<Key<ReservedList>, String>() {
                  @Override
                  public String apply(Key<ReservedList> key) {
                    return key.getName();
                  }})
            .toSet(),
        reservedListNames,
        reservedListsAdd,
        reservedListsRemove);
  }

  @Override
  protected void initTldCommand() throws Exception {
    checkConflicts("reserved_lists", reservedListNames, reservedListsAdd, reservedListsRemove);
    checkConflicts(
        "allowed_registrants", allowedRegistrants, allowedRegistrantsAdd, allowedRegistrantsRemove);
    checkConflicts(
        "allowed_nameservers", allowedNameservers, allowedNameserversAdd, allowedNameserversRemove);
  }

  private static ImmutableSet<String> formUpdatedList(
      String description,
      ImmutableSet<String> originals,
      List<String> fullReplacement,
      List<String> itemsToAdd,
      List<String> itemsToRemove) {
    if (fullReplacement != null) {
      return ImmutableSet.copyOf(fullReplacement);
    }
    Set<String> toAdd = ImmutableSet.copyOf(nullToEmpty(itemsToAdd));
    Set<String> toRemove = ImmutableSet.copyOf(nullToEmpty(itemsToRemove));
    checkIsEmpty(
        intersection(toAdd, toRemove),
        String.format(
            "Adding and removing the same %s simultaneously doesn't make sense", description));
    checkIsEmpty(
        intersection(originals, toAdd),
        String.format("Cannot add %s that were previously present", description));
    checkIsEmpty(
        difference(toRemove, originals),
        String.format("Cannot remove %s that were not previously present", description));
    return ImmutableSet.copyOf(difference(union(originals, toAdd), toRemove));
  }

  private static void checkIsEmpty(Set<String> set, String errorString) {
    checkArgument(set.isEmpty(), String.format("%s: %s", errorString, set));
  }

  private static void checkConflicts(
      String baseFlagName, Object overwriteValue, Object addValue, Object removeValue) {
    checkNotBoth(baseFlagName, overwriteValue, "add_" + baseFlagName, addValue);
    checkNotBoth(baseFlagName, overwriteValue, "remove_" + baseFlagName, removeValue);
  }

  private static void checkNotBoth(String nameA, Object valueA, String nameB, Object valueB) {
    checkArgument(valueA == null || valueB == null, "Don't pass both --%s and --%s", nameA, nameB);
  }
}