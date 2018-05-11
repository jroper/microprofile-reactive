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

package org.eclipse.microprofile.reactive.streams;

import org.eclipse.microprofile.reactive.streams.spi.Graph;
import org.eclipse.microprofile.reactive.streams.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.spi.Stage;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * A builder for a {@link Publisher}.
 *
 * @param <T> The type of the elements that the publisher emits.
 * @see ReactiveStreams
 */
public final class PublisherBuilder<T> extends ReactiveStreamsBuilder {

  PublisherBuilder(Stage stage, ReactiveStreamsBuilder previous) {
    super(stage, previous);
  }

  /**
   * Map the elements emitted by this publisher using the {@code mapper} function.
   *
   * @param mapper The function to use to map the elements.
   * @param <R>    The type of elements that the {@code mapper} function emits.
   * @return A new publisher builder that emits the mapped elements.
   */
  public <R> PublisherBuilder<R> map(Function<? super T, ? extends R> mapper) {
    return new PublisherBuilder<>(new Stage.Map(mapper), this);
  }

  /**
   * Filter elements emitted by this publisher using the given {@link Predicate}.
   * <p>
   * Any elements that return {@code true} when passed to the {@link Predicate} will be emitted, all other
   * elements will be dropped.
   *
   * @param predicate The predicate to apply to each element.
   * @return A new publisher builder.
   */
  public PublisherBuilder<T> filter(Predicate<? super T> predicate) {
    return new PublisherBuilder<>(new Stage.Filter(() -> predicate), this);
  }

  /**
   * Map the elements to publishers, and flatten so that the elements emitted by publishers produced by the
   * {@code mapper} function are emitted from this stream.
   * <p>
   * This method operates on one publisher at a time. The result is a concatenation of elements emitted from all the
   * publishers produced by the mapper function.
   *
   * @param mapper The mapper function.
   * @param <S>    The type of the elements emitted from the new publisher.
   * @return A new publisher builder.
   */
  public <S> PublisherBuilder<S> flatMap(Function<? super T, PublisherBuilder<? extends S>> mapper) {
    return new PublisherBuilder<>(new Stage.FlatMap(
        mapper.andThen(PublisherBuilder::toGraph)), this);
  }

  /**
   * Map the elements to {@link CompletionStage}, and flatten so that the elements the values redeemed by each
   * {@link CompletionStage} are emitted from this publisher.
   * <p>
   * This method only works with one element at a time. When an element is received, the {@code mapper} function is
   * executed, and the next element is not consumed or passed to the {@code mapper} function until the previous
   * {@link CompletionStage} is redeemed. Hence this method also guarantees that ordering of the stream is maintained.
   *
   * @param mapper The mapper function.
   * @param <S>    The type of the elements emitted from the new publisher.
   * @return A new publisher builder.
   */
  public <S> PublisherBuilder<S> flatMapCompletionStage(Function<? super T, ? extends CompletionStage<? extends S>> mapper) {
    return new PublisherBuilder<>(new Stage.FlatMapCompletionStage((Function) mapper), this);
  }

  /**
   * Map the elements to {@link Iterable}'s, and flatten so that the elements contained in each iterable are
   * emitted by this stream.
   * <p>
   * This method operates on one iterable at a time. The result is a concatenation of elements contain in all the
   * iterables returned by the {@code mapper} function.
   *
   * @param mapper The mapper function.
   * @param <S>    The type of the elements emitted from the new publisher.
   * @return A new publisher builder.
   */
  public <S> PublisherBuilder<S> flatMapIterable(Function<? super T, ? extends Iterable<? extends S>> mapper) {
    return new PublisherBuilder<>(new Stage.FlatMapIterable((Function) mapper), this);
  }

  /**
   * Truncate this stream, ensuring the stream is no longer than {@code maxSize} elements in length.
   * <p>
   * If {@code maxSize} is reached, the stream will be completed, and upstream will be cancelled. Completion of the
   * stream will occur immediately when the element that satisfies the {@code maxSize} is received.
   *
   * @param maxSize The maximum size of the returned stream.
   * @return A new publisher builder.
   */
  public PublisherBuilder<T> limit(long maxSize) {
    if (maxSize < 0) {
      throw new IllegalArgumentException("Cannot limit a stream to less than zero elements.");
    }
    else if (maxSize == 0) {
      return takeWhile(e -> false);
    }
    else {
      return new PublisherBuilder<>(new Stage.TakeWhile(() -> new Predicates.LimitPredicate<>(maxSize), true), this);
    }
  }

  /**
   * Discard the first {@code n} of this stream. If this stream contains fewer than {@code n} elements, this stream will
   * effectively be an empty stream.
   *
   * @param n The number of elements to discard.
   * @return A new publisher builder.
   */
  public PublisherBuilder<T> skip(long n) {
    return new PublisherBuilder<>(new Stage.Filter(() -> new Predicates.SkipPredicate<>(n)), this);
  }

  /**
   * Take the longest prefix of elements from this stream that satisfy the given {@code predicate}.
   * <p>
   * When the {@code predicate} returns false, the stream will be completed, and upstream will be cancelled.
   *
   * @param predicate The predicate.
   * @return A new publisher builder.
   */
  public PublisherBuilder<T> takeWhile(Predicate<? super T> predicate) {
    return new PublisherBuilder<>(new Stage.TakeWhile(() -> predicate, false), this);
  }

  /**
   * Drop the longest prefix of elements from this stream that satisfy the given {@code predicate}.
   * <p>
   * As long as the {@code predicate} returns true, no elements will be emitted from this stream. Once the first element
   * is encountered for which the {@code predicate} returns false, all subsequent elements will be emitted, and the
   * {@code predicate} will no longer be invoked.
   *
   * @param predicate The predicate.
   * @return A new publisher builder.
   */
  public PublisherBuilder<T> dropWhile(Predicate<? super T> predicate) {
    return new PublisherBuilder<>(new Stage.Filter(() -> new Predicates.DropWhilePredicate<>(predicate)), this);
  }

  /**
   * Performs an action for each element on this stream.
   * <p>
   * The returned {@link CompletionStage} will be redeemed when the stream completes, either successfully if the stream
   * completes normally, or with an error if the stream completes with an error or if the action throws an exception.
   *
   * @param action The action.
   * @return A new completion builder.
   */
  public CompletionBuilder<Void> forEach(Consumer<? super T> action) {
    return collect(Collector.<T, Void, Void>of(
        () -> null,
        (n, t) -> action.accept(t),
        (v1, v2) -> null,
        v -> null
    ));
  }

  /**
   * Ignores each element of this stream.
   * <p>
   * The returned {@link CompletionStage} will be redeemed when the stream completes, either successfully if the
   * stream completes normally, or with an error if the stream completes with an error or if the action throws an
   * exception.
   *
   * @return A new completion builder.
   */
  public CompletionBuilder<Void> ignore() {
    return forEach(r -> {
    });
  }

  /**
   * Cancels the stream as soon as it starts.
   * <p>
   * The returned {@link CompletionStage} will be immediately redeemed as soon as the stream starts.
   *
   * @return A new completion builder.
   */
  public CompletionBuilder<Void> cancel() {
    return new CompletionBuilder<>(Stage.Cancel.INSTANCE, this);
  }

  /**
   * Perform a reduction on the elements of this stream, using the provided identity value and the accumulation
   * function.
   * <p>
   * The result of the reduction is returned in the {@link CompletionStage}.
   *
   * @param identity    The identity value.
   * @param accumulator The accumulator function.
   * @return A new completion builder.
   */
  public CompletionBuilder<T> reduce(T identity, BinaryOperator<T> accumulator) {
    return new CompletionBuilder<>(new Stage.Collect(Reductions.reduce(identity, accumulator)), this);
  }

  /**
   * Perform a reduction on the elements of this stream, using provided the accumulation function.
   * <p>
   * The result of the reduction is returned in the {@link CompletionStage}. If there are no elements in this stream,
   * empty will be returned.
   *
   * @param accumulator The accumulator function.
   * @return A new completion builder.
   */
  public CompletionBuilder<Optional<T>> reduce(BinaryOperator<T> accumulator) {
    return new CompletionBuilder<>(new Stage.Collect(Reductions.reduce(accumulator)), this);
  }

  /**
   * Perform a reduction on the elements of this stream, using the provided identity value, accumulation function and
   * combiner function.
   * <p>
   * The result of the reduction is returned in the {@link CompletionStage}.
   *
   * @param identity    The identity value.
   * @param accumulator The accumulator function.
   * @param combiner    The combiner function.
   * @return A new completion builder.
   */
  public <S> CompletionBuilder<S> reduce(S identity,
      BiFunction<S, ? super T, S> accumulator,
      BinaryOperator<S> combiner) {

    return new CompletionBuilder<>(new Stage.Collect(Reductions.reduce(identity, accumulator, combiner)), this);
  }

  /**
   * Find the first element emitted by the {@link Publisher}, and return it in a
   * {@link CompletionStage}.
   * <p>
   * If the stream is completed before a single element is emitted, then {@link Optional#empty()} will be emitted.
   *
   * @return A {@link CompletionBuilder} that emits the element when found.
   */
  public CompletionBuilder<Optional<T>> findFirst() {
    return new CompletionBuilder<>(Stage.FindFirst.INSTANCE, this);
  }

  /**
   * Collect the elements emitted by this publisher builder using the given {@link Collector}.
   * <p>
   * Since Reactive Streams are intrinsically sequential, only the accumulator of the collector will be used, the
   * combiner will not be used.
   *
   * @param collector The collector to collect the elements.
   * @param <R>       The result of the collector.
   * @param <A>       The accumulator type.
   * @return A {@link CompletionBuilder} that emits the collected result.
   */
  public <R, A> CompletionBuilder<R> collect(Collector<? super T, A, R> collector) {
    return new CompletionBuilder<>(new Stage.Collect(collector), this);
  }

  /**
   * Collect the elements emitted by this publisher builder into a {@link List}
   *
   * @return A {@link CompletionBuilder} that emits the list.
   */
  public CompletionBuilder<List<T>> toList() {
    return collect(Collectors.toList());
  }

  /**
   * Connect the outlet of the {@link Publisher} built by this builder to the given {@link Subscriber}.
   *
   * @param subscriber The subscriber to connect.
   * @return A {@link CompletionBuilder} that completes when the stream completes.
   */
  public CompletionBuilder<Void> to(Subscriber<T> subscriber) {
    return new CompletionBuilder<>(new Stage.SubscriberStage(subscriber), this);
  }

  /**
   * Connect the outlet of this publisher builder to the given {@link SubscriberBuilder} graph.
   *
   * @param subscriber The subscriber builder to connect.
   * @return A {@link CompletionBuilder} that completes when the stream completes.
   */
  public <R> CompletionBuilder<R> to(SubscriberBuilder<T, R> subscriber) {
    return new CompletionBuilder<>(new InternalStages.Nested(subscriber), this);
  }

  /**
   * Connect the outlet of the {@link Publisher} built by this builder to the given {@link Processor}.
   *
   * @param processor The processor to connect.
   * @return A {@link PublisherBuilder} that represents the passed in processors outlet.
   */
  public <R> PublisherBuilder<R> via(ProcessorBuilder<T, R> processor) {
    return new PublisherBuilder<>(new InternalStages.Nested(processor), this);
  }

  /**
   * Connect the outlet of this publisher builder to the given {@link ProcessorBuilder} graph.
   *
   * @param processor The processor builder to connect.
   * @return A {@link PublisherBuilder} that represents the passed in processor builders outlet.
   */
  public <R> PublisherBuilder<R> via(Processor<T, R> processor) {
    return new PublisherBuilder<>(new Stage.ProcessorStage(processor), this);
  }

  /**
   * Create the graph for this publisher builder.
   *
   * This is primarily useful for engines that want to convert a graph directly to their representations.
   *
   * @return The graph.
   */
  public Graph toGraph() {
    return toGraph(false, true);
  }

  /**
   * Build this stream, using the first {@link ReactiveStreamsEngine} found by the {@link java.util.ServiceLoader}.
   *
   * @return A {@link Processor} that will run this stream.
   */
  public Publisher<T> buildRs() {
    return buildRs(defaultEngine());
  }

  /**
   * Build this stream, using the supplied {@link ReactiveStreamsEngine}.
   *
   * @param engine The engine to run the stream with.
   * @return A {@link Publisher} that will run this stream.
   */
  public Publisher<T> buildRs(ReactiveStreamsEngine engine) {
    return engine.buildPublisher(toGraph());
  }
}
