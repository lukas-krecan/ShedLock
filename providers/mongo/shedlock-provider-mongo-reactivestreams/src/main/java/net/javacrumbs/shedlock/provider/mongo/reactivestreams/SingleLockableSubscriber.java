/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.mongo.reactivestreams;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Subscriber that expects a single result and allows locking until complete or error
 *
 * @param <T>
 */
class SingleLockableSubscriber<T> implements Subscriber<T> {

    private T value;
    private Throwable error;
    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(1);
    }

    @Override
    public void onNext(T document) {
        value = document;
    }

    @Override
    public void onError(Throwable throwable) {
        this.error = throwable;
        onComplete();
    }

    @Override
    public void onComplete() {
        latch.countDown();
    }

    T getValue() {
        return value;
    }

    Throwable getError() {
        return error;
    }

    void await() {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            this.error = e;
        }
    }
}
