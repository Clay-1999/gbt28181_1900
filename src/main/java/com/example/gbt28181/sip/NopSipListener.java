package com.example.gbt28181.sip;

import javax.sip.*;

/**
 * No-op SipListener placeholder. Phase 3 will replace with real message handling.
 */
public class NopSipListener implements SipListener {

    public static final NopSipListener INSTANCE = new NopSipListener();

    private NopSipListener() {}

    @Override
    public void processRequest(RequestEvent requestEvent) {}

    @Override
    public void processResponse(ResponseEvent responseEvent) {}

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {}

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {}

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {}

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {}
}
