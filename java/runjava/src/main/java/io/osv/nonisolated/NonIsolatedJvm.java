package io.osv.nonisolated;

import io.osv.AppThreadTerminatedWithUncaughtException;
import io.osv.Jvm;
import io.osv.MainClassNotFoundException;

import java.util.Properties;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/*
 * Copyright (C) 2016 Waldemar Kozaczuk
 *
 * This work is open source software, licensed under the terms of the
 * BSD license as described in the LICENSE file in the top-level directory.
 */
public class NonIsolatedJvm extends Jvm<Thread> {

    private static final NonIsolatedJvm instance = new NonIsolatedJvm();

    private AtomicReference<Throwable> thrownException = new AtomicReference<>();

    public static NonIsolatedJvm getInstance() {
        return instance;
    }

    private NonIsolatedJvm() {}

    @Override
    protected Thread run(ClassLoader classLoader, final String classpath, final String mainClass, final String[] args, final Properties properties) {
        thrownException.set(null);
        Thread thread = new Thread() {
            @Override
            public void run() {
            System.setProperty("java.class.path", classpath);

            for(Map.Entry<?,?> property : properties.entrySet())
                System.setProperty(property.getKey().toString(),property.getValue().toString()); //TODO Check for null

            try {
                runMain(loadClass(mainClass), args);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (MainClassNotFoundException e) {
                thrownException.set(e);
            } catch (Throwable e) {
                getUncaughtExceptionHandler().uncaughtException(this, e);
            }
            }
        };

        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                thrownException.set(e);
            }
        });
        thread.setContextClassLoader(classLoader);
        thread.start();
        return thread;
    }

    public void runSync(String... args) throws Throwable {
        Thread thread = run(args);

        while (true) {
            try {
                thread.join();
                final Throwable exception = thrownException.get();
                if (null != exception) {
                    throw new AppThreadTerminatedWithUncaughtException(exception);
                }
                return;
            } catch (InterruptedException e) {
                thread.interrupt();
            }
        }
    }

    @Override
    protected ClassLoader getParentClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public Throwable getThrownExceptionIfAny() { return thrownException.get(); }
}
