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

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.registry.label.ReservedListTest.GET_NAME_FUNCTION;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistPremiumList;
import static com.google.domain.registry.testing.DatastoreHelper.persistReservedList;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;
import static com.google.domain.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.JPY;
import static org.joda.money.CurrencyUnit.USD;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Duration.standardMinutes;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Range;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.model.registry.Registry.TldState;
import com.google.domain.registry.model.registry.label.PremiumList;

import com.beust.jcommander.ParameterException;
import com.googlecode.objectify.Key;

import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/** Unit tests for {@link UpdateTldCommand}. */
public class UpdateTldCommandTest extends CommandTestCase<UpdateTldCommand> {

  private final DateTime now = DateTime.now(UTC);

  @Before
  public void initTest() {
    persistReservedList("common_abuse", "baa,FULLY_BLOCKED");
    persistReservedList("xn--q9jyb4c_abuse", "lamb,FULLY_BLOCKED");
    persistReservedList("tld_banned", "kilo,FULLY_BLOCKED", "lima,MISTAKEN_PREMIUM");
    persistReservedList("soy_expurgated", "fireflies,FULLY_BLOCKED");
    persistPremiumList("xn--q9jyb4c", "minecraft,USD 1000");
    persistReservedList("xn--q9jyb4c_r1", "foo,FULLY_BLOCKED");
    persistReservedList("xn--q9jyb4c_r2", "moop,FULLY_BLOCKED");
    createTld("xn--q9jyb4c");
  }

  @Test
  public void testSuccess_updateAutoTimestamp() throws Exception {
    Registry registry = Registry.get("xn--q9jyb4c");
    DateTime creationTime = registry.getCreationTime();
    assertThat(creationTime).isNotNull();
    assertThat(registry.getUpdateAutoTimestamp().getTimestamp()).isEqualTo(creationTime);

    DateTime before = DateTime.now(UTC);
    runCommandForced("xn--q9jyb4c");
    DateTime after = DateTime.now(UTC);

    registry = Registry.get("xn--q9jyb4c");
    assertThat(registry.getCreationTime()).isEqualTo(creationTime);
    assertThat(registry.getUpdateAutoTimestamp().getTimestamp()).isIn(Range.closed(before, after));
  }

  @Test
  public void testSuccess_tldStateTransitions() throws Exception {
    DateTime sunriseStart = now;
    DateTime sunrushStart = sunriseStart.plusMonths(2);
    DateTime quietPeriodStart = sunrushStart.plusMonths(1);
    DateTime gaStart = quietPeriodStart.plusWeeks(1);
    runCommandForced(
        String.format(
            "--tld_state_transitions=%s=PREDELEGATION,%s=SUNRISE,%s=SUNRUSH,%s=QUIET_PERIOD,"
                + "%s=GENERAL_AVAILABILITY",
            START_OF_TIME,
            sunriseStart,
            sunrushStart,
            quietPeriodStart,
            gaStart),
        "xn--q9jyb4c");

    Registry registry = Registry.get("xn--q9jyb4c");
    assertThat(registry.getTldState(sunriseStart.minusMillis(1))).isEqualTo(TldState.PREDELEGATION);
    assertThat(registry.getTldState(sunriseStart)).isEqualTo(TldState.SUNRISE);
    assertThat(registry.getTldState(sunriseStart.plusMillis(1))).isEqualTo(TldState.SUNRISE);
    assertThat(registry.getTldState(sunrushStart.minusMillis(1))).isEqualTo(TldState.SUNRISE);
    assertThat(registry.getTldState(sunrushStart)).isEqualTo(TldState.SUNRUSH);
    assertThat(registry.getTldState(sunrushStart.plusMillis(1))).isEqualTo(TldState.SUNRUSH);
    assertThat(registry.getTldState(quietPeriodStart.minusMillis(1))).isEqualTo(TldState.SUNRUSH);
    assertThat(registry.getTldState(quietPeriodStart)).isEqualTo(TldState.QUIET_PERIOD);
    assertThat(registry.getTldState(quietPeriodStart.plusMillis(1)))
        .isEqualTo(TldState.QUIET_PERIOD);
    assertThat(registry.getTldState(gaStart.minusMillis(1))).isEqualTo(TldState.QUIET_PERIOD);
    assertThat(registry.getTldState(gaStart)).isEqualTo(TldState.GENERAL_AVAILABILITY);
    assertThat(registry.getTldState(gaStart.plusMillis(1)))
        .isEqualTo(TldState.GENERAL_AVAILABILITY);
    assertThat(registry.getTldState(END_OF_TIME)).isEqualTo(TldState.GENERAL_AVAILABILITY);
  }

  @Test
  public void testSuccess_renewBillingCostTransitions() throws Exception {
    DateTime later = now.plusMonths(1);
    runCommandForced(
        String.format(
            "--renew_billing_cost_transitions=\"%s=USD 1,%s=USD 2.00,%s=USD 100\"",
            START_OF_TIME,
            now,
            later),
        "xn--q9jyb4c");

    Registry registry = Registry.get("xn--q9jyb4c");
    assertThat(registry.getStandardRenewCost(START_OF_TIME)).isEqualTo(Money.of(USD, 1));
    assertThat(registry.getStandardRenewCost(now.minusMillis(1))).isEqualTo(Money.of(USD, 1));
    assertThat(registry.getStandardRenewCost(now)).isEqualTo(Money.of(USD, 2));
    assertThat(registry.getStandardRenewCost(now.plusMillis(1))).isEqualTo(Money.of(USD, 2));
    assertThat(registry.getStandardRenewCost(later.minusMillis(1))).isEqualTo(Money.of(USD, 2));
    assertThat(registry.getStandardRenewCost(later)).isEqualTo(Money.of(USD, 100));
    assertThat(registry.getStandardRenewCost(later.plusMillis(1))).isEqualTo(Money.of(USD, 100));
    assertThat(registry.getStandardRenewCost(END_OF_TIME)).isEqualTo(Money.of(USD, 100));
  }

  @Test
  public void testSuccess_multipleArguments() throws Exception {
    assertThat(Registry.get("xn--q9jyb4c").getAddGracePeriodLength())
        .isNotEqualTo(standardMinutes(5));
    createTld("example");
    assertThat(Registry.get("example").getAddGracePeriodLength()).isNotEqualTo(standardMinutes(5));

    runCommandForced("--add_grace_period=PT300S", "xn--q9jyb4c", "example");

    assertThat(Registry.get("xn--q9jyb4c").getAddGracePeriodLength()).isEqualTo(standardMinutes(5));
    assertThat(Registry.get("example").getAddGracePeriodLength()).isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_addGracePeriodFlag() throws Exception {
    assertThat(Registry.get("xn--q9jyb4c").getAddGracePeriodLength())
        .isNotEqualTo(standardMinutes(5));

    runCommandForced("--add_grace_period=PT300S", "xn--q9jyb4c");

    assertThat(Registry.get("xn--q9jyb4c").getAddGracePeriodLength()).isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_redemptionGracePeriodFlag() throws Exception {
    assertThat(Registry.get("xn--q9jyb4c").getRedemptionGracePeriodLength())
        .isNotEqualTo(standardMinutes(5));

    runCommandForced("--redemption_grace_period=PT300S", "xn--q9jyb4c");

    assertThat(Registry.get("xn--q9jyb4c").getRedemptionGracePeriodLength())
        .isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_pendingDeleteLengthFlag() throws Exception {
    assertThat(Registry.get("xn--q9jyb4c").getPendingDeleteLength())
        .isNotEqualTo(standardMinutes(5));

    runCommandForced("--pending_delete_length=PT300S", "xn--q9jyb4c");

    assertThat(Registry.get("xn--q9jyb4c").getPendingDeleteLength()).isEqualTo(standardMinutes(5));
  }

  @Test
  public void testSuccess_escrow() throws Exception {
    runCommandForced("--escrow=true", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getEscrowEnabled()).isTrue();
  }

  @Test
  public void testSuccess_noEscrow() throws Exception {
    runCommandForced("--escrow=false", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getEscrowEnabled()).isFalse();
  }

  @Test
  public void testSuccess_createBillingCostFlag() throws Exception {
    runCommandForced("--create_billing_cost=\"USD 42.42\"", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getStandardCreateCost()).isEqualTo(Money.of(USD, 42.42));
  }

  @Test
  public void testSuccess_restoreBillingCostFlag() throws Exception {
    runCommandForced("--restore_billing_cost=\"USD 42.42\"", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getStandardRestoreCost())
        .isEqualTo(Money.of(USD, 42.42));
  }

  @Test
  public void testSuccess_nonUsdBillingCostFlag() throws Exception {
    persistResource(
        Registry.get("xn--q9jyb4c")
            .asBuilder()
            .setCurrency(JPY)
            .setCreateBillingCost(Money.ofMajor(JPY, 1))
            .setRestoreBillingCost(Money.ofMajor(JPY, 1))
            .setRenewBillingCostTransitions(
                ImmutableSortedMap.of(START_OF_TIME, Money.ofMajor(JPY, 1)))
            .build());
    runCommandForced(
        "--create_billing_cost=\"JPY 12345\"",
        "--restore_billing_cost=\"JPY 67890\"",
        "--renew_billing_cost_transitions=\"0=JPY 101112\"",
        "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getStandardCreateCost())
        .isEqualTo(Money.ofMajor(JPY, 12345));
    assertThat(Registry.get("xn--q9jyb4c").getStandardRestoreCost())
        .isEqualTo(Money.ofMajor(JPY, 67890));
    assertThat(Registry.get("xn--q9jyb4c").getStandardRenewCost(START_OF_TIME))
        .isEqualTo(Money.ofMajor(JPY, 101112));
  }

  @Test
  public void testSuccess_setPremiumPriceAckRequired() throws Exception {
    runCommandForced("--premium_price_ack_required=true", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPremiumPriceAckRequired()).isTrue();
  }

  @Test
  public void testSuccess_clearPremiumPriceAckRequired() throws Exception {
    persistResource(Registry.get("xn--q9jyb4c").asBuilder().setPremiumPriceAckRequired(true).build());
    runCommandForced("--premium_price_ack_required=false", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPremiumPriceAckRequired()).isFalse();
  }

  @Test
  public void testSuccess_setLordnUsername() throws Exception {
    runCommandForced("--lordn_username=lordn000", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getLordnUsername()).isEqualTo("lordn000");
  }

  @Test
  public void testSuccess_setOptionalParamsNullString() throws Exception {
    persistResource(Registry.get("xn--q9jyb4c").asBuilder().setLordnUsername("lordn000").build());
    runCommandForced("--lordn_username=null", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getLordnUsername()).isNull();
  }

  @Test
  public void testSuccess_setOptionalParamsEmptyString() throws Exception {
    persistResource(Registry.get("xn--q9jyb4c").asBuilder().setLordnUsername("lordn000").build());
    runCommandForced("--lordn_username=", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getLordnUsername()).isNull();
  }

  @Test
  public void testSuccess_setReservedLists() throws Exception {
    runCommandForced("--reserved_lists=xn--q9jyb4c_r1,xn--q9jyb4c_r2", "xn--q9jyb4c");

    assertThat(transform(Registry.get("xn--q9jyb4c").getReservedLists(), GET_NAME_FUNCTION))
        .containsExactly("xn--q9jyb4c_r1", "xn--q9jyb4c_r2");
  }

  @Test
  public void testSuccess_setReservedListsOverwritesCorrectly() throws Exception {
    Registry registry = addTwoReservedListsToRegistry();
    runCommandForced("--reserved_lists=xn--q9jyb4c_r2", "xn--q9jyb4c");
    registry = Registry.get("xn--q9jyb4c");
    assertThat(registry.getReservedLists()).hasSize(1);
    assertThat(registry.getReservedLists().asList().get(0).getName()).isEqualTo("xn--q9jyb4c_r2");
  }

  @Test
  public void testSuccess_addReservedListsWorksCorrectly() throws Exception {
    runCommandForced("--add_reserved_lists=xn--q9jyb4c_r1,xn--q9jyb4c_r2", "xn--q9jyb4c");
    assertThat(transform(Registry.get("xn--q9jyb4c").getReservedLists(), GET_NAME_FUNCTION))
        .containsExactly("xn--q9jyb4c_r1", "xn--q9jyb4c_r2");
  }

  @Test
  public void testSuccess_removeAllReservedListsWorksCorrectly() throws Exception {
    addTwoReservedListsToRegistry();
    runCommandForced("--remove_reserved_lists=xn--q9jyb4c_r1,xn--q9jyb4c_r2", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getReservedLists()).isEmpty();
  }

  @Test
  public void testSuccess_removeSomeReservedListsWorksCorrectly() throws Exception {
    addTwoReservedListsToRegistry();
    runCommandForced("--remove_reserved_lists=xn--q9jyb4c_r1", "xn--q9jyb4c");
    assertThat(transform(Registry.get("xn--q9jyb4c").getReservedLists(), GET_NAME_FUNCTION))
        .containsExactly("xn--q9jyb4c_r2");
  }

  private Registry addTwoReservedListsToRegistry() throws Exception {
    persistResource(
        Registry.get("xn--q9jyb4c")
            .asBuilder()
            .setReservedListsByName(ImmutableSet.of("xn--q9jyb4c_r1", "xn--q9jyb4c_r2"))
            .build());
    return Registry.get("xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidAddGracePeriod() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--add_grace_period=5m", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidRedemptionGracePeriod() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--redemption_grace_period=5m", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidPendingDeleteLength() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("--pending_delete_length=5m", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidTldState() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--tld_state_transitions=" + START_OF_TIME + "=INVALID_STATE", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidTldStateTransitionTime() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--tld_state_transitions=tomorrow=INVALID_STATE", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_tldStatesOutOfOrder() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        String.format(
            "--tld_state_transitions=%s=SUNRISE,%s=PREDELEGATION", now, now.plusMonths(1)),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_duplicateTldStateTransitions() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        String.format("--tld_state_transitions=%s=SUNRISE,%s=SUNRISE", now, now.plusMonths(1)),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_duplicateTldStateTransitionTimes() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        String.format("--tld_state_transitions=%s=PREDELEGATION,%s=SUNRISE", now, now),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_outOfOrderTldStateTransitionTimes() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        String.format("--tld_state_transitions=%s=PREDELEGATION,%s=SUNRISE", now, now.minus(1)),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidRenewBillingCost() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        String.format("--renew_billing_cost_transitions=%s=US42", START_OF_TIME),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_negativeRenewBillingCost() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced(
        String.format("--renew_billing_cost_transitions=%s=USD-42", START_OF_TIME),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_invalidRenewCostTransitionTime() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced("--renew_billing_cost_transitions=tomorrow=USD 1", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_duplicateRenewCostTransitionTimes() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        String.format("--renew_billing_cost_transitions=\"%s=USD 1,%s=USD 2\"", now, now),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_outOfOrderRenewCostTransitionTimes() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced(
        String.format("--renew_billing_cost_transitions=\"%s=USD 1,%s=USD 2\"", now, now.minus(1)),
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_noTldName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommandForced();
  }

  @Test
  public void testFailure_oneArgumentDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("foo", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_duplicateArguments() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("xn--q9jyb4c", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_tldDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommandForced("foobarbaz");
  }

  @Test
  public void testFailure_setNonExistentReservedLists() throws Exception {
    thrown.expect(
        IllegalStateException.class,
        "Could not find reserved list xn--q9jyb4c_ZZZ to add to the tld");
    runCommandForced("--reserved_lists", "xn--q9jyb4c_ZZZ", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_cantAddDuplicateReservedList() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Cannot add reserved list(s) xn--q9jyb4c_r1 to TLD xn--q9jyb4c "
        + "because they're already on it");
    addTwoReservedListsToRegistry();
    runCommandForced("--add_reserved_lists=xn--q9jyb4c_r1", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_cantRemoveReservedListThatIsntPresent() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Cannot remove reserved list xn--q9jyb4c_Z from TLD xn--q9jyb4c because it isn't on it");
    addTwoReservedListsToRegistry();
    runCommandForced("--remove_reserved_lists=xn--q9jyb4c_Z", "xn--q9jyb4c");
  }

  @Test
  public void testFailure_cantAddAndRemoveSameReservedListSimultaneously() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Adding and removing the same reserved list simultaneously doesn't make sense");
    runCommandForced(
        "--add_reserved_lists=xn--q9jyb4c_r1",
        "--remove_reserved_lists=xn--q9jyb4c_r1",
        "xn--q9jyb4c");
  }

  @Test
  public void testFailure_roidSuffixAlreadyInUse() throws Exception {
    createTld("foo", "BLAH");
    createTld("bar", "BAR");
    thrown.expect(IllegalArgumentException.class, "The roid suffix BLAH is already in use");
    runCommandForced("--roid_suffix=BLAH", "bar");
  }

  @Test
  public void testSuccess_canSetRoidSuffixToWhatItAlreadyIs() throws Exception {
    createTld("foo", "BLAH");
    runCommandForced("--roid_suffix=BLAH", "foo");
    assertThat(Registry.get("foo").getRoidSuffix()).isEqualTo("BLAH");
  }

  @Test
  public void testSuccess_updateRoidSuffix() throws Exception {
    createTld("foo", "ARGLE");
    runCommandForced("--roid_suffix=BARGLE", "foo");
    assertThat(Registry.get("foo").getRoidSuffix()).isEqualTo("BARGLE");
  }

  @Test
  public void testSuccess_removePremiumListWithNull() throws Exception {
    runCommandForced("--premium_list=null", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPremiumList()).isNull();
  }

  @Test
  public void testSuccess_removePremiumListWithBlank() throws Exception {
    runCommandForced("--premium_list=", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPremiumList()).isNull();
  }

  @Test
  public void testSuccess_premiumListNotRemovedWhenNotSpecified() throws Exception {
    runCommandForced("--add_reserved_lists=xn--q9jyb4c_r1,xn--q9jyb4c_r2", "xn--q9jyb4c");
    Key<PremiumList> premiumListKey = Registry.get("xn--q9jyb4c").getPremiumList();
    assertThat(premiumListKey).isNotNull();
    assertThat(premiumListKey.getName()).isEqualTo("xn--q9jyb4c");
  }

  @Test
  public void testSuccess_driveFolderId_notRemovedWhenNotSpecified() throws Exception {
    persistResource(Registry.get("xn--q9jyb4c").asBuilder().setDriveFolderId("foobar").build());
    runCommandForced("xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getDriveFolderId()).isEqualTo("foobar");
  }

  @Test
  public void testSuccess_setCommonReservedListOnTld() throws Exception {
    runSuccessfulReservedListsTest("common_abuse");
  }

  @Test
  public void testSuccess_setTldSpecificReservedListOnTld() throws Exception {
    runSuccessfulReservedListsTest("xn--q9jyb4c_abuse");
  }

  @Test
  public void testSuccess_setCommonReservedListAndTldSpecificReservedListOnTld() throws Exception {
    runSuccessfulReservedListsTest("common_abuse,xn--q9jyb4c_abuse");
  }

  @Test
  public void testFailure_setReservedListFromOtherTld() throws Exception {
    runFailureReservedListsTest("tld_banned",
        IllegalArgumentException.class,
        "The reserved list(s) tld_banned cannot be applied to the tld xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setReservedListFromOtherTld_withOverride() throws Exception {
    runReservedListsTestOverride("tld_banned");
  }

  @Test
  public void testFailure_setCommonAndReservedListFromOtherTld() throws Exception {
    runFailureReservedListsTest("common_abuse,tld_banned",
        IllegalArgumentException.class,
        "The reserved list(s) tld_banned cannot be applied to the tld xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setCommonAndReservedListFromOtherTld_withOverride() throws Exception {
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errContent));
    runReservedListsTestOverride("common_abuse,tld_banned");
    String errMsg =
        "Error overriden: The reserved list(s) tld_banned cannot be applied to the tld xn--q9jyb4c";
    assertThat(errContent.toString()).contains(errMsg);
    System.setOut(null);
  }

  @Test
  public void testFailure_setMultipleReservedListsFromOtherTld() throws Exception {
    runFailureReservedListsTest("tld_banned,soy_expurgated",
        IllegalArgumentException.class,
        "The reserved list(s) tld_banned, soy_expurgated cannot be applied to the tld xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setMultipleReservedListsFromOtherTld_withOverride() throws Exception {
    runReservedListsTestOverride("tld_banned,soy_expurgated");
  }

  @Test
  public void testSuccess_setPremiumList() throws Exception {
    runCommandForced("--premium_list=xn--q9jyb4c", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getPremiumList().getName()).isEqualTo("xn--q9jyb4c");
  }

  @Test
  public void testSuccess_setDriveFolderIdToValue() throws Exception {
    runCommandForced("--drive_folder_id=madmax2030", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getDriveFolderId()).isEqualTo("madmax2030");
  }

  @Test
  public void testSuccess_setDriveFolderIdToNull() throws Exception {
    runCommandForced("--drive_folder_id=null", "xn--q9jyb4c");
    assertThat(Registry.get("xn--q9jyb4c").getDriveFolderId()).isNull();
  }

  @Test
  public void testFailure_setPremiumListThatDoesntExist() throws Exception {
    thrown.expect(IllegalArgumentException.class, "The premium list 'phonies' doesn't exist");
    runCommandForced("--premium_list=phonies", "xn--q9jyb4c");
  }

  private void runSuccessfulReservedListsTest(String reservedLists) throws Exception {
    runCommandForced("--reserved_lists", reservedLists, "xn--q9jyb4c");
  }

  private void runReservedListsTestOverride(String reservedLists) throws Exception {
    runCommandForced("--override_reserved_list_rules",
        "--reserved_lists",
        reservedLists,
        "xn--q9jyb4c");
  }

  private void runFailureReservedListsTest(
      String reservedLists,
      Class<? extends Exception> errorClass,
      String errorMsg) throws Exception {
    thrown.expect(errorClass, errorMsg);
    runCommandForced("--reserved_lists", reservedLists, "xn--q9jyb4c");
  }
}