/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.eclipse.microprofile.reactive.messaging.tck.framework;

import java.util.Objects;

public class MockPayload {
  private String field1;
  private int field2;

  public MockPayload(String field1, int field2) {
    this.field1 = field1;
    this.field2 = field2;
  }

  public MockPayload() {
  }

  /**
   * Transform this message by appending -transformed to field1 and adding 100 to field2.
   */
  public MockPayload transform() {
    return new MockPayload(field1 + "-transformed", field2 + 100);
  }

  public String getField1() {
    return field1;
  }

  public int getField2() {
    return field2;
  }

  public void setField1(String field1) {
    this.field1 = field1;
  }

  public void setField2(int field2) {
    this.field2 = field2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MockPayload that = (MockPayload) o;
    return field2 == that.field2 &&
        Objects.equals(field1, that.field1);
  }

  @Override
  public int hashCode() {

    return Objects.hash(field1, field2);
  }

  @Override
  public String toString() {
    return "MockPayload{" +
        "field1='" + field1 + '\'' +
        ", field2=" + field2 +
        '}';
  }
}
