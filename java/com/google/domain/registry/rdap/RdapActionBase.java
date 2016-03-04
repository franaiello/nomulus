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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.domain.registry.model.ofy.ObjectifyService.ofy;
import static com.google.domain.registry.model.registry.Registries.findTldForName;
import static com.google.domain.registry.model.registry.Registries.getTlds;
import static com.google.domain.registry.util.DateTimeUtils.END_OF_TIME;
import static com.google.domain.registry.util.DomainNameUtils.canonicalizeDomainName;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InternetDomainName;
import com.google.common.net.MediaType;
import com.google.domain.registry.config.ConfigModule.Config;
import com.google.domain.registry.model.EppResource;
import com.google.domain.registry.request.Action;
import com.google.domain.registry.request.HttpException;
import com.google.domain.registry.request.HttpException.BadRequestException;
import com.google.domain.registry.request.HttpException.NotFoundException;
import com.google.domain.registry.request.RequestMethod;
import com.google.domain.registry.request.RequestPath;
import com.google.domain.registry.request.Response;
import com.google.domain.registry.util.FormattingLogger;

import com.googlecode.objectify.cmd.Query;

import org.json.simple.JSONValue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import javax.inject.Inject;

/**
 * Base RDAP (new WHOIS) action for single-item domain, nameserver and entity requests.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7482">
 *        RFC 7482: Registration Data Access Protocol (RDAP) Query Format</a>
 */
public abstract class RdapActionBase implements Runnable {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /**
   * Pattern for checking LDH names, which must officially contains only alphanumeric plus dots and
   * hyphens. In this case, allow the wildcard asterisk as well.
   */
  static final Pattern LDH_PATTERN = Pattern.compile("[-.a-zA-Z0-9*]+");

  private static final MediaType RESPONSE_MEDIA_TYPE = MediaType.create("application", "rdap+json");

  @Inject Response response;
  @Inject @RequestMethod Action.Method requestMethod;
  @Inject @RequestPath String requestPath;
  @Inject @Config("rdapLinkBase") String rdapLinkBase;
  @Inject @Config("rdapWhoisServer") String rdapWhoisServer;

  /** Returns a string like "domain name" or "nameserver", used for error strings. */
  abstract String getHumanReadableObjectTypeName();

  /** Returns the servlet action path; used to extract the search string from the incoming path. */
  abstract String getActionPath();

  /** Does the actual search and returns an RDAP JSON object. */
  abstract ImmutableMap<String, Object> getJsonObjectForResource(String searchString)
      throws HttpException;

  @Override
  public void run() {
    try {
      // Extract what we're searching for from the request path. Some RDAP commands use trailing
      // data in the path itself (e.g. /rdap/domain/mydomain.com), and some use the query string
      // (e.g. /rdap/domains?name=mydomain); the query parameters are extracted by the subclasses
      // directly as needed.
      response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
      URI uri = new URI(requestPath);
      String pathProper = uri.getPath();
      checkArgument(
          pathProper.startsWith(getActionPath()),
          "%s doesn't start with %s", pathProper, getActionPath());
      ImmutableMap<String, Object> rdapJson =
          getJsonObjectForResource(pathProper.substring(getActionPath().length()));
      response.setStatus(SC_OK);
      if (requestMethod != Action.Method.HEAD) {
        response.setPayload(
            JSONValue.toJSONString(RdapJsonFormatter.makeFinalRdapJson(rdapJson)));
      }
      response.setContentType(MediaType.create("application", "rdap+json"));
    } catch (HttpException e) {
      setError(e.getResponseCode(), e.getResponseCodeString(), e.getMessage());
    } catch (URISyntaxException | IllegalArgumentException e) {
      setError(SC_BAD_REQUEST, "Bad Request", "Not a valid " + getHumanReadableObjectTypeName());
    } catch (RuntimeException e) {
      setError(SC_INTERNAL_SERVER_ERROR, "Internal Server Error", "An error was encountered");
      logger.severe(e, "Exception encountered while processing RDAP command");
    }
  }

  void setError(int status, String title, String description) {
    response.setStatus(status);
    try {
      if (requestMethod != Action.Method.HEAD) {
        response.setPayload(
            JSONValue.toJSONString(RdapJsonFormatter.makeError(status, title, description)));
      }
      response.setContentType(RESPONSE_MEDIA_TYPE);
    } catch (Exception ex) {
      if (requestMethod != Action.Method.HEAD) {
        response.setPayload("");
      }
    }
  }

  void validateDomainName(String name) {
    try {
      Optional<InternetDomainName> tld = findTldForName(InternetDomainName.from(name));
      if (!tld.isPresent() || !getTlds().contains(tld.get().toString())) {
        throw new NotFoundException(name + " not found");
      }
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(
          name + " is not a valid " + getHumanReadableObjectTypeName());
    }
  }

  String canonicalizeName(String name) {
    name = canonicalizeDomainName(name);
    if (name.endsWith(".")) {
      name = name.substring(0, name.length() - 1);
    }
    return name;
  }

  /**
   * Handles prefix searches in cases where there are no pending deletes. In such cases, it is
   * sufficient to check whether {@code deletionTime} is equal to {@code END_OF_TIME}, because any
   * other value means it has already been deleted. This allows us to use an equality query for the
   * deletion time.
   *
   * @param clazz the type of resource to be queried
   * @param filterField the database field of interest
   * @param partialStringQuery the details of the search string
   * @param resultSetMaxSize the maximum number of results to return
   * @return the results of the query
   */
  static <T extends EppResource> Query<T> queryUndeleted(
      Class<T> clazz,
      String filterField,
      RdapSearchPattern partialStringQuery,
      int resultSetMaxSize) {
    checkArgument(partialStringQuery.getHasWildcard(), "search string doesn't have wildcard");
    return ofy().load()
        .type(clazz)
        .filter(filterField + " >=", partialStringQuery.getInitialString())
        .filter(filterField + " <", partialStringQuery.getNextInitialString())
        .filter("deletionTime", END_OF_TIME)
        .limit(resultSetMaxSize);
  }
}