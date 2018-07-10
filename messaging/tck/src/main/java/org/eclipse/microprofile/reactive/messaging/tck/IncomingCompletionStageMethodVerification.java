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

package org.eclipse.microprofile.reactive.messaging.tck;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.tck.arquillian.Topics;
import org.eclipse.microprofile.reactive.messaging.tck.framework.ContainerController;
import org.eclipse.microprofile.reactive.messaging.tck.framework.MockedReceiver;
import org.eclipse.microprofile.reactive.messaging.tck.framework.MockPayload;
import org.eclipse.microprofile.reactive.messaging.tck.framework.QuietRuntimeException;
import org.eclipse.microprofile.reactive.messaging.tck.framework.TckMessagingManager;
import org.eclipse.microprofile.reactive.messaging.tck.spi.TestEnvironment;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.testng.annotations.Test;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.microprofile.reactive.messaging.tck.WaitAssert.waitUntil;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Provides tests for methods of the shape:
 *
 * <pre>
 * &#064;Incoming
 * public CompletionStage&gt;Void&lt; handle(SomeMessage message) {
 *   ...
 * }
 * </pre>
 */
public class IncomingCompletionStageMethodVerification extends Arquillian {

  @Deployment
  public static JavaArchive createDeployment() {
    return ShrinkWrap.create(JavaArchive.class)
        .addClass(Bean.class)
        .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
  }

  @Inject
  private TckMessagingManager manager;
  @Inject
  private ContainerController controller;
  @Inject
  private TestEnvironment environment;

  @Inject
  private Bean bean;

  private static final String VOID_METHOD = "void-method";
  private static final String NON_PARALLEL = "non-parallel";
  private static final String NON_VOID_METHOD = "non-void-method";
  private static final String SYNC_FAILING = "sync-failing";
  private static final String ASYNC_FAILING = "async-failing";
  private static final String WRAPPED_MESSAGE = "wrapped-message";
  private static final String OUTGOING_WRAPPED = "outgoing-wrapped";
  private static final String INCOMING_OUTGOING_WRAPPED = "incoming-outgoing-wrapped";

  @Topics
  public static String[] topics() {
    return new String[]{
        VOID_METHOD, NON_PARALLEL, NON_VOID_METHOD, SYNC_FAILING, ASYNC_FAILING, WRAPPED_MESSAGE, OUTGOING_WRAPPED,
        INCOMING_OUTGOING_WRAPPED
    };
  }

  @ApplicationScoped
  public static class Bean {

    @Inject
    private TckMessagingManager manager;

    @Incoming(topic = VOID_METHOD)
    public CompletionStage<Void> handleVoidMethod(MockPayload payload) {
      manager.getReceiver(VOID_METHOD).receiveMessage(payload);
      return CompletableFuture.completedFuture(null);
    }

    private final Deque<CompletableFuture<Void>> futures = new ArrayDeque<>();

    public Deque<CompletableFuture<Void>> getFutures() {
      return futures;
    }

    @Incoming(topic = NON_PARALLEL)
    public CompletionStage<Void> handleNonParallel(MockPayload payload) {
      manager.getReceiver(NON_PARALLEL).receiveMessage(payload);
      CompletableFuture<Void> future = new CompletableFuture<>();
      futures.add(future);
      return future;
    }

    @Incoming(topic = NON_VOID_METHOD)
    public CompletionStage<String> handleNonVoidMethod(MockPayload payload) {
      manager.getReceiver(NON_VOID_METHOD).receiveMessage(payload);
      return CompletableFuture.completedFuture("hello");
    }

    private final AtomicBoolean syncFailed = new AtomicBoolean();

    public AtomicBoolean getSyncFailed() {
      return syncFailed;
    }

    @Incoming(topic = SYNC_FAILING)
    public CompletionStage<Void> handleSyncFailing(MockPayload payload) {
      if (payload.getField1().equals("fail") && !syncFailed.getAndSet(true)) {
        manager.getReceiver(SYNC_FAILING).receiveMessage(payload);
        throw new QuietRuntimeException("failed");
      }
      else {
        manager.getReceiver(SYNC_FAILING).receiveMessage(payload);
        return CompletableFuture.completedFuture(null);
      }
    }

    private final AtomicBoolean asyncFailed = new AtomicBoolean();

    public AtomicBoolean getAsyncFailed() {
      return asyncFailed;
    }

    @Incoming(topic = ASYNC_FAILING)
    public CompletionStage<Void> handleAsyncFailing(MockPayload payload) {
      if (payload.getField1().equals("fail") && !asyncFailed.getAndSet(true)) {
        manager.getReceiver(ASYNC_FAILING).receiveMessage(payload);
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new QuietRuntimeException("failed"));
        return future;
      }
      else {
        manager.getReceiver(ASYNC_FAILING).receiveMessage(payload);
        return CompletableFuture.completedFuture(null);
      }
    }

    private final AtomicBoolean wrappedFailed = new AtomicBoolean();

    public AtomicBoolean getWrappedFailed() {
      return wrappedFailed;
    }

    @Incoming(topic = WRAPPED_MESSAGE)
    public CompletionStage<Void> handleWrapped(Message<MockPayload> msg) {
      if (msg.getPayload().getField1().equals("acknowledged") || wrappedFailed.get()) {
        manager.<MockPayload>getReceiver(WRAPPED_MESSAGE).receiveWrappedMessage(msg);
        return msg.ack();
      }
      else if (msg.getPayload().getField1().equals("fail")) {
        wrappedFailed.set(true);
        manager.<MockPayload>getReceiver(WRAPPED_MESSAGE).receiveWrappedMessage(msg);
        throw new QuietRuntimeException("failed");
      }
      else {
        manager.<MockPayload>getReceiver(WRAPPED_MESSAGE).receiveWrappedMessage(msg);
        return CompletableFuture.completedFuture(null);
      }
    }

    private final AtomicInteger acked = new AtomicInteger();
    private final AtomicBoolean outgoingWrappedFailed = new AtomicBoolean();

    public AtomicInteger getAcked() {
      return acked;
    }

    public AtomicBoolean getOutgoingWrappedFailed() {
      return outgoingWrappedFailed;
    }

    @Incoming(topic = OUTGOING_WRAPPED)
    public CompletionStage<Message<Void>> handleOutgoingWrapped(MockPayload msg) {
      if (msg.getField1().equals("fail") && !outgoingWrappedFailed.getAndSet(true)) {
        manager.getReceiver(OUTGOING_WRAPPED).receiveMessage(msg);
        throw new QuietRuntimeException("failed");
      }
      else {
        manager.getReceiver(OUTGOING_WRAPPED).receiveMessage(msg);
        return CompletableFuture.completedFuture(Message.of(null, () -> {
          acked.incrementAndGet();
          return CompletableFuture.completedFuture(null);
        }));
      }
    }

    private final AtomicBoolean incomingOutgoingWrappedFailed = new AtomicBoolean();

    public AtomicBoolean getIncomingOutgoingWrappedFailed() {
      return incomingOutgoingWrappedFailed;
    }

    // This is used to ensure that before we fail the stream, the first message was successfully acked.
    private final CompletableFuture<Void> incomingOutgoingWrappedAcked = new CompletableFuture<>();

    @Incoming(topic = INCOMING_OUTGOING_WRAPPED)
    public CompletionStage<Message<Void>> handleIncomingOutgoingWrapped(Message<MockPayload> msg) {
      if (msg.getPayload().getField1().equals("acknowledged") || incomingOutgoingWrappedFailed.get()) {
        manager.<MockPayload>getReceiver(INCOMING_OUTGOING_WRAPPED).receiveWrappedMessage(msg);
        return CompletableFuture.completedFuture(Message.of(null, () -> msg.ack().thenAccept(incomingOutgoingWrappedAcked::complete)));
      }
      else if (msg.getPayload().getField1().equals("fail")) {
        incomingOutgoingWrappedFailed.set(true);
        manager.<MockPayload>getReceiver(INCOMING_OUTGOING_WRAPPED).receiveWrappedMessage(msg);
        return incomingOutgoingWrappedAcked.thenApply(v -> {
          throw new QuietRuntimeException("failed");
        });
      }
      else {
        manager.<MockPayload>getReceiver(INCOMING_OUTGOING_WRAPPED).receiveWrappedMessage(msg);
        // Note - not transferring the ack.
        return CompletableFuture.completedFuture(Message.of(null));
      }
    }
  }

  @Test
  public void simpleCompletionStageVoidMethodShouldProcessMessages() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(VOID_METHOD);
    MockPayload msg1 = new MockPayload("mock", 1);
    MockPayload msg2 = new MockPayload("mock", 2);
    MockPayload msg3 = new MockPayload("mock", 3);

    controller.sendPayloads(VOID_METHOD, msg1, msg2);

    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNextMessageWithPayload(msg2);
    receiver.expectNoMessages("Didn't expect a message because didn't send one.");
    controller.sendPayloads(VOID_METHOD, msg3);
    receiver.expectNextMessageWithPayload(msg3);
  }

  @Test
  public void completionStageMethodShouldNotProcessMessagesInParallel() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(NON_PARALLEL);
    MockPayload msg1 = new MockPayload("mock", 1);
    MockPayload msg2 = new MockPayload("mock", 2);
    MockPayload msg3 = new MockPayload("mock", 3);

    controller.sendPayloads(NON_PARALLEL, msg1, msg2);

    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNoMessages("Did not redeem future from previous message yet.");
    bean.getFutures().removeFirst().complete(null);
    receiver.expectNextMessageWithPayload(msg2);
    receiver.expectNoMessages("Did not redeem future from previous message yet.");
    bean.getFutures().removeFirst().complete(null);
    controller.sendPayloads(NON_PARALLEL, msg3);
    receiver.expectNextMessageWithPayload(msg3);
    bean.getFutures().removeFirst().complete(null);
  }

  @Test
  public void completionStageNonVoidMethodShouldIgnoreReturnValue() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(NON_VOID_METHOD);
    MockPayload msg1 = new MockPayload("mock", 1);
    MockPayload msg2 = new MockPayload("mock", 2);

    controller.sendPayloads(NON_VOID_METHOD, msg1, msg2);

    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNextMessageWithPayload(msg2);
    receiver.expectNoMessages("Didn't expect a message because didn't send one.");
  }

  @Test
  public void completionStageMethodShouldRetryMessagesThatFailSynchronously() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(SYNC_FAILING);
    MockPayload msg1 = new MockPayload("fail", 1);
    MockPayload msg2 = new MockPayload("success", 2);

    controller.sendPayloads(SYNC_FAILING, msg1, msg2);
    // We should receive the fail message once, then failed should be true, then we should receive it again,
    // followed by the next message.
    receiver.expectNextMessageWithPayload(msg1);
    assertTrue(bean.getSyncFailed().get(), "Sync was not failed");
    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNextMessageWithPayload(msg2);
  }

  @Test
  public void completionStageMethodShouldRetryMessagesThatFailAsynchronously() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(ASYNC_FAILING);
    MockPayload msg1 = new MockPayload("fail", 1);
    MockPayload msg2 = new MockPayload("success", 2);

    controller.sendPayloads(ASYNC_FAILING, msg1, msg2);
    // We should receive the fail message once, then failed should be true, then we should receive it again,
    // followed by the next message.
    receiver.expectNextMessageWithPayload(msg1);
    assertTrue(bean.getAsyncFailed().get(), "Async was not failed");
    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNextMessageWithPayload(msg2);
  }

  @Test
  public void completionStageMethodShouldNotAutomaticallyAcknowledgeWrappedMessages() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(WRAPPED_MESSAGE);
    MockPayload msg1 = new MockPayload("acknowledged", 1);
    MockPayload msg2 = new MockPayload("unacknowledged", 2);
    MockPayload msg3 = new MockPayload("fail", 3);
    MockPayload msg4 = new MockPayload("success", 4);

    controller.sendPayloads(WRAPPED_MESSAGE, msg1, msg2, msg3, msg4);
    // First one should be acknwoldeged. The second one gets processed successfully, but first time, doesn't ack.
    // Third one fails on the first attempt, on the second acks. Fourth one always acks.
    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNextMessageWithPayload(msg2);
    receiver.expectNextMessageWithPayload(msg3);
    assertTrue(bean.getWrappedFailed().get(), "Wrapped was not failed");
    receiver.expectNextMessageWithPayload(msg2);
    receiver.expectNextMessageWithPayload(msg3);
    receiver.expectNextMessageWithPayload(msg4);
  }

  @Test
  public void completionStageWithOutgoingWrappedMessageShouldGetAcknowledged() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(OUTGOING_WRAPPED);
    MockPayload msg1 = new MockPayload("fail", 1);
    MockPayload msg2 = new MockPayload("success", 2);

    controller.sendPayloads(OUTGOING_WRAPPED, msg1, msg2);
    receiver.expectNextMessageWithPayload(msg1);
    assertTrue(bean.getOutgoingWrappedFailed().get(), "Outgoing wrapped was not failed");

    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNextMessageWithPayload(msg2);
    waitUntil(environment.receiveTimeout(), () -> assertEquals(bean.getAcked().get(), 2));
  }

  @Test
  public void completionStageMethodShouldAcknowldegePassedThroughAckFunctions() {
    MockedReceiver<MockPayload> receiver = manager.getReceiver(INCOMING_OUTGOING_WRAPPED);
    MockPayload msg1 = new MockPayload("acknowledged", 1);
    MockPayload msg2 = new MockPayload("unacknowledged", 2);
    MockPayload msg3 = new MockPayload("fail", 3);
    MockPayload msg4 = new MockPayload("success", 4);

    controller.sendPayloads(INCOMING_OUTGOING_WRAPPED, msg1, msg2, msg3, msg4);
    receiver.expectNextMessageWithPayload(msg1);
    receiver.expectNextMessageWithPayload(msg2);
    receiver.expectNextMessageWithPayload(msg3);
    assertTrue(bean.getIncomingOutgoingWrappedFailed().get(), "Wrapped was not failed");
    receiver.expectNextMessageWithPayload(msg2);
    receiver.expectNextMessageWithPayload(msg3);
    receiver.expectNextMessageWithPayload(msg4);
  }
}
