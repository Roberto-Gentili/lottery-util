package org.rg.game.core;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class ConcurrentUtils {

	public static void addTask(Collection<CompletableFuture<Void>> futures, Runnable taskOperation) {
		AtomicReference<CompletableFuture<Void>> taskWrapper = new AtomicReference<>();
		taskWrapper.set(
			CompletableFuture.runAsync(
				() -> {
					synchronized (taskWrapper) {
						while (taskWrapper.get() == null) {
							try {
								taskWrapper.wait();
							} catch (InterruptedException exc) {
								Throwables.sneakyThrow(exc);
							}
						}
					}
					futures.add(taskWrapper.get());
					LogUtils.info("Launching task " + taskOperation);
					taskOperation.run();
					futures.remove(taskWrapper.get());
					synchronized(futures) {
						futures.notifyAll();
					}
				}
			)
		);
		synchronized(taskWrapper) {
			taskWrapper.notify();
		}
	}

	public static void waitUntil(
		Collection<CompletableFuture<Void>> futures,
		Predicate<Collection<CompletableFuture<Void>>> futuresPredicate
	) {
		while (futuresPredicate.test(futures)) {
			synchronized(futures) {
				try {
					futures.wait();
				} catch (InterruptedException exc) {
					Throwables.sneakyThrow(exc);
				}
			}
		}

	}

}
