/*
 * SonarQube OpenAPI Plugin
 * Copyright (C) 2018-2019 Societe Generale
 * vincent.girard-reydet AT socgen DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.openapi.api.v3;

import com.sonar.sslr.api.AstNode;
import org.junit.Test;
import org.sonar.openapi.BaseNodeTest;
import org.sonar.sslr.yaml.grammar.JsonNode;

public class FullApiTest extends BaseNodeTest<OpenApi3Grammar> {
  @Test
  public void can_parse_full_contract() {
    JsonNode node = parseResource(OpenApi3Grammar.ROOT, "/models/v3/pet-store.yaml");

    assertEquals("3.0.0", node, "/openapi");
    assertEquals("http://petstore.swagger.io/api", node, "/servers/0/url");

    assertKeys(node.at("/paths").properties()).containsOnly("/pets", "/pets/{id}");
    assertPropertyKeys(node, "/components/schemas").containsOnly("Pet", "NewPet", "Error");
    assertElements(node, "/paths/~1pets/get/security/0/test").containsOnly("one", "two");
  }
}
