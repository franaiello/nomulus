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

package com.google.domain.registry.testing;

import static com.google.domain.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.domain.registry.flows.EppXmlTransformer;
import com.google.domain.registry.model.eppinput.EppInput;

import java.util.Map;

/** Test rule that loads an Epp object from a file. */
public class EppLoader {

  private String eppXml;

  public EppLoader(Object context, String eppXmlFilename) {
    this(context, eppXmlFilename, ImmutableMap.<String, String>of());
  }

  public EppLoader(Object context, String eppXmlFilename, Map<String, String> substitutions) {
    this.eppXml = loadFileWithSubstitutions(context.getClass(), eppXmlFilename, substitutions);
  }

  public EppInput getEpp() throws Exception {
    return EppXmlTransformer.unmarshal(eppXml.getBytes(UTF_8));
  }

  public String getEppXml() {
    return eppXml;
  }

  public void replaceAll(String regex, String substitution) {
    eppXml = eppXml.replaceAll(regex, substitution);
  }
}