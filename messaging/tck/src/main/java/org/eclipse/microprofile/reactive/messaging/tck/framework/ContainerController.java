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

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.tck.spi.TckMessagingPuppet;
import org.eclipse.microprofile.reactive.messaging.tck.spi.TestEnvironment;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

/**
 * Convenience helper to send messages serialized using JSONB.
 */
@ApplicationScoped
public class ContainerController {

  @Inject
  private TckMessagingPuppet container;

  private final Jsonb jsonb = JsonbBuilder.create();

  public void sendMessages(String topic, Message<?>... messages) {
    for (Message<?> message: messages) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      jsonb.toJson(message.getPayload(), baos);
      container.sendMessage(topic, Message.of(baos.toByteArray()));
    }
  }

  public void sendPayloads(String topic, Object... payloads) {
    for (Object payload: payloads) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      jsonb.toJson(payload, baos);
      container.sendMessage(topic, Message.of(baos.toByteArray()));
    }
  }

  public <T> Message<T> expectNextMessageWithPayload(String topic, T payload) {
    Optional<Message<byte[]>> message = container.receiveMessage(topic, container.testEnvironment().receiveTimeout());
    if (message.isPresent()) {
      T received = jsonb.fromJson(new ByteArrayInputStream(message.get().getPayload()), (Class<T>) payload.getClass());
      if (received.equals(payload)) {
        return Message.of(received);
      }
      else {
        throw new AssertionError("Expected a message on topic " + topic + " with payload " + payload + " but got " + received);
      }
    }
    else {
      throw new AssertionError("Did not receive a message on " + topic + " within " + container.testEnvironment().receiveTimeout().toMillis() + "ms");
    }
  }

  public void expectNoMessages(String topic) {
    Optional<Message<byte[]>> message = container.receiveMessage(topic, container.testEnvironment().noMessageTimeout());
    if (message.isPresent()) {
      throw new AssertionError("Expected no messages on topic " + topic + " but got message with payload " + new String(message.get().getPayload()));
    }
  }

  @Produces
  public TestEnvironment produceTestEnvironment() {
    return container.testEnvironment();
  }

}
