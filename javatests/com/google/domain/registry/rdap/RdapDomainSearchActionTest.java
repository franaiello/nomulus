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

package com.google.domain.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static com.google.domain.registry.testing.DatastoreHelper.createTld;
import static com.google.domain.registry.testing.DatastoreHelper.persistDomainAsDeleted;
import static com.google.domain.registry.testing.DatastoreHelper.persistResource;
import static com.google.domain.registry.testing.DatastoreHelper.persistSimpleGlobalResources;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeContactResource;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeDomainResource;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeHostResource;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static com.google.domain.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static com.google.domain.registry.testing.TestDataHelper.loadFileWithSubstitutions;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.net.InetAddresses;
import com.google.domain.registry.model.domain.DomainResource;
import com.google.domain.registry.model.host.HostResource;
import com.google.domain.registry.model.ofy.Ofy;
import com.google.domain.registry.model.registrar.Registrar;
import com.google.domain.registry.model.registry.Registry;
import com.google.domain.registry.testing.AppEngineRule;
import com.google.domain.registry.testing.FakeClock;
import com.google.domain.registry.testing.FakeResponse;
import com.google.domain.registry.testing.InjectRule;

import com.googlecode.objectify.Ref;

import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapDomainSearchAction}. */
@RunWith(JUnit4.class)
public class RdapDomainSearchActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2009-06-29T20:13:00Z"));

  private final RdapDomainSearchAction action = new RdapDomainSearchAction();

  private DomainResource domainCatLol;
  private DomainResource domainCatLol2;
  private DomainResource domainCatExample;
  private HostResource hostNs1CatLol;
  private HostResource hostNs2CatLol;

  enum RequestType { NONE, NAME, NS_LDH_NAME, NS_IP }

  private Object generateActualJson(RequestType requestType, String paramValue) {
    action.requestPath = RdapDomainSearchAction.PATH;
    switch (requestType) {
      case NAME:
        action.nameParam = Optional.of(paramValue);
        action.nsLdhNameParam = Optional.absent();
        action.nsIpParam = Optional.absent();
        break;
      case NS_LDH_NAME:
        action.nameParam = Optional.absent();
        action.nsLdhNameParam = Optional.of(paramValue);
        action.nsIpParam = Optional.absent();
        break;
      case NS_IP:
        action.nameParam = Optional.absent();
        action.nsLdhNameParam = Optional.absent();
        action.nsIpParam = Optional.of(InetAddresses.forString(paramValue));
        break;
      default:
        action.nameParam = Optional.absent();
        action.nsLdhNameParam = Optional.absent();
        action.nsIpParam = Optional.absent();
        break;
    }
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  @Before
  public void setUp() throws Exception {
    // cat.lol and cat2.lol
    createTld("lol");
    Registrar registrar = persistResource(
        makeRegistrar("evilregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeRegistrarContacts(registrar));
    domainCatLol = persistResource(makeDomainResource("cat.lol",
            persistResource(makeContactResource("5372808-ERL", "Goblin Market", "lol@cat.lol")),
            persistResource(makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol")),
            persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol")),
            hostNs1CatLol = persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4")),
            hostNs2CatLol = persistResource(
                makeHostResource("ns2.cat.lol", "bad:f00d:cafe::15:beef")),
            registrar)
        .asBuilder().setSubordinateHosts(ImmutableSet.of("ns1.cat.lol", "ns2.cat.lol")).build());
    persistResource(
        hostNs1CatLol.asBuilder().setSuperordinateDomain(Ref.create(domainCatLol)).build());
    persistResource(
        hostNs2CatLol.asBuilder().setSuperordinateDomain(Ref.create(domainCatLol)).build());
    domainCatLol2 = persistResource(makeDomainResource(
        "cat2.lol",
        persistResource(makeContactResource("6372808-ERL", "Siegmund", "siegmund@cat2.lol")),
        persistResource(makeContactResource("6372808-IRL", "Sieglinde", "sieglinde@cat2.lol")),
        persistResource(makeContactResource("6372808-TRL", "Siegfried", "siegfried@cat2.lol")),
        persistResource(makeHostResource("ns1.cat.example", "10.20.30.40")),
        persistResource(makeHostResource("ns2.dog.lol", "12:feed:5000::15:beef")),
        registrar));
    // cat.example
    createTld("example");
    registrar = persistResource(
        makeRegistrar("goodregistrar", "St. John Chrysostom", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeRegistrarContacts(registrar));
    domainCatExample = persistResource(makeDomainResource("cat.example",
        persistResource(makeContactResource("7372808-ERL", "Matthew", "lol@cat.lol")),
        persistResource(makeContactResource("7372808-IRL", "Mark", "BOFH@cat.lol")),
        persistResource(makeContactResource("7372808-TRL", "Luke", "bog@cat.lol")),
        hostNs1CatLol,
        persistResource(makeHostResource("ns2.external.tld", "bad:f00d:cafe::15:beef")),
        registrar));
    // cat.みんな
    createTld("xn--q9jyb4c");
    registrar = persistResource(makeRegistrar("unicoderegistrar", "みんな", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeRegistrarContacts(registrar));
    persistResource(makeDomainResource(
        "cat.みんな", persistResource(makeContactResource("8372808-ERL", "(◕‿◕)", "lol@cat.みんな")),
        persistResource(makeContactResource("8372808-IRL", "Santa Claus", "BOFH@cat.みんな")),
        persistResource(makeContactResource("8372808-TRL", "The Raven", "bog@cat.みんな")),
        persistResource(makeHostResource("ns1.cat.みんな", "1.2.3.5")),
        persistResource(makeHostResource("ns2.cat.みんな", "bad:f00d:cafe::14:beef")),
        registrar));
    // cat.1.test
    createTld("1.test");
    registrar =
        persistResource(makeRegistrar("unicoderegistrar", "1.test", Registrar.State.ACTIVE));
    persistSimpleGlobalResources(makeRegistrarContacts(registrar));
    persistResource(makeDomainResource(
          "cat.1.test",
              persistResource(makeContactResource("9372808-ERL", "(◕‿◕)", "lol@cat.みんな")),
          persistResource(makeContactResource("9372808-IRL", "Santa Claus", "BOFH@cat.みんな")),
          persistResource(makeContactResource("9372808-TRL", "The Raven", "bog@cat.みんな")),
          persistResource(makeHostResource("ns1.cat.1.test", "1.2.3.5")),
          persistResource(makeHostResource("ns2.cat.2.test", "bad:f00d:cafe::14:beef")),
          registrar)
        .asBuilder().setSubordinateHosts(ImmutableSet.of("ns1.cat.1.test")).build());

    inject.setStaticField(Ofy.class, "clock", clock);
    action.clock = clock;
    action.response = response;
    action.rdapLinkBase = "https://example.com/rdap/";
    action.rdapWhoisServer = "whois.example.tld";
  }

  private Object generateExpectedJson(String expectedOutputFile) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        expectedOutputFile,
        ImmutableMap.of("TYPE", "domain name")));
  }

  private Object generateExpectedJson(
      String name,
      String punycodeName,
      String handle,
      String expectedOutputFile) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        expectedOutputFile,
        ImmutableMap.of(
            "NAME", name,
            "PUNYCODENAME", (punycodeName == null) ? name : punycodeName,
            "HANDLE", (handle == null) ? "(null)" : handle,
            "TYPE", "domain name")));
  }

  private Object generateExpectedJsonForDomain(
      String name,
      String punycodeName,
      String handle,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(name, punycodeName, handle, expectedOutputFile);
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("domainSearchResults", ImmutableList.of(obj));
    builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
    return builder.build();
  }

  @Test
  public void testInvalidPath_rejected() throws Exception {
    action.requestPath = RdapDomainSearchAction.PATH + "/path";
    action.run();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidRequest_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NONE, null))
        .isEqualTo(generateExpectedJson(
            "You must specify either name=XXXX, nsLdhName=YYYY or nsIp=ZZZZ",
            null,
            null,
            "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidWildcard_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "exam*ple"))
        .isEqualTo(generateExpectedJson(
            "Suffix after wildcard must be one or more domain"
                + " name labels, e.g. exam*.tld, ns*.example.tld",
            null,
            null,
            "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testMultipleWildcards_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "*.*"))
        .isEqualTo(generateExpectedJson(
            "Only one wildcard allowed", null, null, "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testFewerThanTwoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "a*"))
        .isEqualTo(generateExpectedJson(
            "At least two characters must be specified", null, null, "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testDomainMatch_found() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "cat.lol"))
        .isEqualTo(generateExpectedJsonForDomain("cat.lol", null, "7-LOL", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  /*
   * This test is flaky because IDN.toASCII may or may not remove the trailing dot of its own
   * accord. If it does, the test will pass.
   */
  @Ignore
  @Test
  public void testDomainMatchWithTrailingDot_notFound() throws Exception {
    generateActualJson(RequestType.NAME, "cat.lol.");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatch_cat2_lol_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat2.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_example_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.example");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_idn_unicode_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.みんな");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_idn_punycode_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.xn--q9jyb4c");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_1_test_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_castar_1_test_found() throws Exception {
    generateActualJson(RequestType.NAME, "ca*.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_castar_test_notFound() throws Exception {
    generateActualJson(RequestType.NAME, "ca*.test");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatch_catstar_lol_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat*.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_star_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_lstar_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.l*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_catstar_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatchWithWildcardAndEmptySuffix_fails() throws Exception {
    // Unfortunately, we can't be sure which error is going to be returned. The version of
    // IDN.toASCII used in Eclipse drops a trailing dot, if any. But the version linked in by
    generateActualJson(RequestType.NAME, "exam*..");
    assertThat(response.getStatus()).isIn(Range.closed(400, 499));
  }

  @Test
  public void testDomainMatch_dog_notFound() throws Exception {
    generateActualJson(RequestType.NAME, "dog*");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatchDeletedDomain_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc());
    assertThat(generateActualJson(RequestType.NAME, "cat.lol"))
        .isEqualTo(generateExpectedJson("No domains found", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatchDeletedDomainWithWildcard_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc());
    assertThat(generateActualJson(RequestType.NAME, "cat.lo*"))
        .isEqualTo(generateExpectedJson("No domains found", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatchDeletedDomainsWithWildcardAndTld_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc());
    persistDomainAsDeleted(domainCatLol2, clock.nowUtc());
    generateActualJson(RequestType.NAME, "cat*.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testDomainMatchDomainInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson(RequestType.NAME, "cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatch_foundMultiple() throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithWildcard_found() throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns2.cat.l*"))
        .isEqualTo(generateExpectedJsonForDomain("cat.lol", null, "7-LOL", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithWildcardAndTldSuffix_notFound() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns2.cat*.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchWithWildcardAndDomainSuffix_found() throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns*.cat.lol"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithWildcardAndEmptySuffix_unprocessable() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns*.");
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNameserverMatch_ns2_cat_lol_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns2.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_ns2_dog_lol_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns2.dog.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_ns1_cat_idn_unicode_badRequest() throws Exception {
    // nsLdhName must use punycode.
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.みんな");
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testNameserverMatch_ns1_cat_idn_punycode_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.xn--q9jyb4c");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_ns1_cat_1_test_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_nsstar_cat_1_test_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns*.cat.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_nsstar_test_notFound() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns*.1.test");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchMissing_notFound() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.missing.com");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testNameserverMatchDomainsInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson(RequestType.NS_LDH_NAME, "ns2.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchOneDeletedDomain_foundTheOther() throws Exception {
    persistDomainAsDeleted(domainCatExample, clock.nowUtc());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(generateExpectedJsonForDomain("cat.lol", null, "7-LOL", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchTwoDeletedDomains_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc());
    persistDomainAsDeleted(domainCatExample, clock.nowUtc());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("No domains found", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchDeletedNameserver_notFound() throws Exception {
    persistResource(
        hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("No domains found", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchDeletedNameserverWithWildcard_notFound() throws Exception {
    persistResource(
        hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.l*"))
        .isEqualTo(generateExpectedJson(
            "No matching nameservers found", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchDeletedNameserverWithWildcardAndTld_notFound() throws Exception {
    persistResource(
        hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat*.lol"))
        .isEqualTo(generateExpectedJson(
            "No domain found for specified nameserver suffix", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatchV4Address_foundMultiple() throws Exception {
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchV6Address_foundMultiple() throws Exception {
    assertThat(generateActualJson(RequestType.NS_IP, "bad:f00d:cafe::15:beef"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchLocalhost_notFound() throws Exception {
    generateActualJson(RequestType.NS_IP, "127.0.0.1");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testAddressMatchDomainsInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    persistResource(Registry.get("example").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson(RequestType.NS_IP, "1.2.3.4");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatchOneDeletedDomain_foundTheOther() throws Exception {
    persistDomainAsDeleted(domainCatExample, clock.nowUtc());
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(generateExpectedJsonForDomain("cat.lol", null, "7-LOL", "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchTwoDeletedDomains_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc());
    persistDomainAsDeleted(domainCatExample, clock.nowUtc());
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(generateExpectedJson("No domains found", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatchDeletedNameserver_notFound() throws Exception {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(generateExpectedJson("No domains found", null, null, "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }
}