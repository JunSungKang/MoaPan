package com.moapan.identity.application;

import com.moapan.shared.api.ApiError;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** One shared bound for login, signup, changes and recovery hashing. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public final class IdentityPasswords {
    private final Semaphore capacity=new Semaphore(4);
    public <T>T hash(Supplier<T> work){if(!capacity.tryAcquire())throw new ApiError(503,"hash_capacity");try{return work.get();}finally{capacity.release();}}
}
