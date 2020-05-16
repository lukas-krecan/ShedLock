/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.reactive.mongo;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscriber that expects a single result and allows locking until complete or error
 *
 * @param <T>
 */
public class SingleLockableSubscriber<T> implements Subscriber<T> {

    private T value;
    private Throwable error;
    private boolean complete = false;
    private final Long timeoutMillis = 10000L;
    private final Long lockTime = 100L;

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
    }

    @Override
    public void onComplete() {
        complete = true;
    }

    T getValue() {
        return value;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isComplete() {
        return complete;
    }

    public void waitUntilCompleteOrError() {
        long waitTime = 0;
        while (waitTime <= timeoutMillis && !this.isComplete() && this.getError() == null) {
            try {
                Thread.sleep(lockTime);
                waitTime += lockTime;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
