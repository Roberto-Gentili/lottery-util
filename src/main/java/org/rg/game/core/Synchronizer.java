package org.rg.game.core;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Synchronizer implements Closeable {
	public static final Synchronizer INSTANCE = create("Main");

	Map<String, Mutex> mutexes;
	String name;

	private Synchronizer(String name) {
		this.name = name;
		mutexes = new ConcurrentHashMap<>();
	}

	public static Synchronizer create(String name) {
		return new Synchronizer(name);
	}

	public Mutex getMutex(String id) {
		Mutex newMutex = this.new Mutex(id);
		while (true) {
			Mutex oldMutex = mutexes.putIfAbsent(id, newMutex);
	        if (oldMutex == null) {
		        return newMutex;
	        }
	        if (++oldMutex.clientsCount > 1 && mutexes.get(id) == oldMutex) {
	        	return oldMutex;
        	}
        	continue;
		}
    }

	public void execute(String id, Runnable executable) {
		try (Mutex mutex = getMutex(id);) {
			synchronized (mutex) {
				executable.run();
			}
		}
	}

	public <E extends Throwable> void execute(String id, Consumer<Mutex> executable) throws E {
		try (Mutex mutex = getMutex(id);) {
			synchronized (mutex) {
				executable.accept(mutex);
			}
		}
	}

	public <E extends Throwable> void executeThrower(String id, ThrowingRunnable<E> executable) throws E {
		try (Mutex mutex = getMutex(id);) {
			synchronized (mutex) {
				executable.run();
			}
		}
	}

	public <E extends Throwable> void executeThrower(String id, ThrowingConsumer<Mutex, E> executable) throws E {
		try (Mutex mutex = getMutex(id);) {
			synchronized (mutex) {
				executable.accept(mutex);
			}
		}
	}

	public <T> T execute(String id, Supplier<T> executable) {
		try (Mutex mutex = getMutex(id);) {
			synchronized (mutex) {
				return executable.get();
			}
		}
	}

	public <T, E extends Throwable> T executeThrower(String id, ThrowingSupplier<T, E> executable) throws E {
		try (Mutex mutex = getMutex(id);) {
			synchronized (mutex) {
				return executable.get();
			}
		}
	}

	public <T, E extends Throwable> T executeThrower(String id, ThrowingFunction<Mutex, T, E> executable) throws E {
		try (Mutex mutex = getMutex(id);) {
			synchronized (mutex) {
				return executable.apply(mutex);
			}
		}
	}

	public void clear() {
		mutexes.clear();
	}

	@Override
	public void close() {
		clear();
		mutexes = null;
	}

	public class Mutex implements java.io.Closeable {
		Mutex(String id) {
			this.id = id;
		}
		String id;
		int clientsCount = 1;

		@Override
		public void close() {
			if (--clientsCount < 1) {
				Synchronizer.this.mutexes.remove(id);
			}
		}
	}

}
