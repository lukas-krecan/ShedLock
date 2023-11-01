/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.mongo.reactivestreams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscriber that expects a single result and allows locking until complete or
 * error
 *
 * @param <T>
 */
class SingleLockableSubscriber<T> implements Subscriber<T> {

    @Nullable
    private T value;

    @Nullable
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

    @Nullable
    T getValue() {
        return value;
    }

    @Nullable
    Throwable getError() {
        return error;
    }

    void await() {
        try {
            int timeout = 20;
            if (!latch.await(timeout, TimeUnit.SECONDS)) {
                this.error = new TimeoutException("Did not get response in " + timeout + " seconds.");
            }
        } catch (InterruptedException e) {
            this.error = e;
        }
    }
}
