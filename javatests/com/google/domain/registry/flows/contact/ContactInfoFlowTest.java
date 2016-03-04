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

package com.google.domain.registry.flows.contact;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.model.EppResourceUtils.isDeleted;
import static com.google.domain.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.newDomainResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.flows.ResourceFlowTestCase;
import com.google.domain.registry.flows.ResourceQueryFlow.ResourceToQueryDoesNotExistException;
import com.google.domain.registry.model.contact.ContactAddress;
import com.google.domain.registry.model.contact.ContactAuthInfo;
import com.google.domain.registry.model.contact.ContactPhoneNumber;
import com.google.domain.registry.model.contact.ContactResource;
import com.google.domain.registry.model.contact.Disclose;
import com.google.domain.registry.model.contact.PostalInfo;
import com.google.domain.registry.model.contact.PostalInfo.Type;
import com.google.domain.registry.model.eppcommon.AuthInfo.PasswordAuth;
import com.google.domain.registry.model.eppcommon.PresenceMarker;
import com.google.domain.registry.model.eppcommon.StatusValue;

import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link ContactInfoFlow}. */
public class ContactInfoFlowTest extends ResourceFlowTestCase<ContactInfoFlow, ContactResource> {

  public ContactInfoFlowTest() {
    setEppInput("contact_info.xml");
  }

  private ContactResource persistContactResource(boolean active) {
    ContactResource contact = persistResource(
        new ContactResource.Builder()
            .setContactId("sh8013")
            .setRepoId("2FF-ROID")
            .setDeletionTime(active ? null : DateTime.now().minusDays(1))
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_DELETE_PROHIBITED))
            .setInternationalizedPostalInfo(new PostalInfo.Builder()
                .setType(Type.INTERNATIONALIZED)
                .setName("John Doe")
                .setOrg("Example Inc.")
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("123 Example Dr.", "Suite 100"))
                    .setCity("Dulles")
                    .setState("VA")
                    .setZip("20166-6503")
                    .setCountryCode("US")
                    .build())
                .build())
            .setVoiceNumber(
                new ContactPhoneNumber.Builder()
                .setPhoneNumber("+1.7035555555")
                .setExtension("1234")
                .build())
            .setFaxNumber(
                new ContactPhoneNumber.Builder()
                .setPhoneNumber("+1.7035555556")
                .build())
            .setEmailAddress("jdoe@example.com")
            .setCurrentSponsorClientId("TheRegistrar")
            .setCreationClientId("NewRegistrar")
            .setLastEppUpdateClientId("NewRegistrar")
            .setCreationTimeForTest(DateTime.parse("1999-04-03T22:00:00.0Z"))
            .setLastEppUpdateTime(DateTime.parse("1999-12-03T09:00:00.0Z"))
            .setLastTransferTime(DateTime.parse("2000-04-08T09:00:00.0Z"))
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("2fooBAR")))
            .setDisclose(new Disclose.Builder()
                .setFlag(true)
                .setVoice(new PresenceMarker())
                .setEmail(new PresenceMarker())
                .build())
            .build());
    assertThat(isDeleted(contact, DateTime.now())).isNotEqualTo(active);
    return contact;
  }

  @Test
  public void testSuccess() throws Exception {
    persistContactResource(true);
    // Check that the persisted contact info was returned.
    assertTransactionalFlow(false);
    runFlowAssertResponse(
        readFile("contact_info_response.xml"),
        // We use a different roid scheme than the samples so ignore it.
        "epp.response.resData.infData.roid");
    assertNoHistory();
    assertNoBillingEvents();
  }

  @Test
  public void testSuccess_linked() throws Exception {
    createTld("foobar");
    persistResource(newDomainResource("example.foobar", persistContactResource(true)));
    // Check that the persisted contact info was returned.
    assertTransactionalFlow(false);
    runFlowAssertResponse(
        readFile("contact_info_response_linked.xml"),
        // We use a different roid scheme than the samples so ignore it.
        "epp.response.resData.infData.roid");
    assertNoHistory();
    assertNoBillingEvents();
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    thrown.expect(
        ResourceToQueryDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    thrown.expect(
        ResourceToQueryDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    persistContactResource(false);
    runFlow();
  }

  // Extra methods so the test runner doesn't produce empty shards.
  @Test public void testNothing1() {}
}