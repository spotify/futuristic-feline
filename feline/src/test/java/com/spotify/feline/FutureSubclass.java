package com.spotify.feline;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureSubclass<T> extends CompletableFuture<T> {
  @Override
  public T get() throws InterruptedException, ExecutionException {
    return super.get();
  }

  @Override
  public T get(long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return super.get(timeout, unit);
  }

  @Override
  public T join() {
    return super.join();
  }
}
