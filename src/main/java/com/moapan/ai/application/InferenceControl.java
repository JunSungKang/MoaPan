package com.moapan.ai.application;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Interrupts HttpClient.send on failed lease renewal, including DB failure. */
public final class InferenceControl {
    private InferenceControl() {}
    public static <T> T run(BooleanSupplier renew,int intervalMillis,int deadlineMillis,Callable<T> inference)throws Exception {
        Thread owner=Thread.currentThread();var lost=new AtomicBoolean();
        try(var timer=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())){
            timer.scheduleAtFixedRate(()->{boolean valid=false;try{valid=renew.getAsBoolean();}catch(Exception ignored){}if(!valid){lost.set(true);owner.interrupt();}},intervalMillis,intervalMillis,TimeUnit.MILLISECONDS);
            timer.schedule(()->{lost.set(true);owner.interrupt();},deadlineMillis,TimeUnit.MILLISECONDS);
            try{T result=inference.call();if(lost.get()||!renew.getAsBoolean())throw new IllegalStateException("ai_lease_lost");return result;}
            finally{timer.shutdownNow();if(lost.get())Thread.interrupted();}
        }
    }
}
