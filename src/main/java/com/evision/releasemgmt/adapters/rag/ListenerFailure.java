package com.evision.releasemgmt.adapters.rag;

// A listener failure must neither be mapped to a Failed outcome nor mistaken for a transport error.
final class ListenerFailure extends RuntimeException {

    private ListenerFailure(RuntimeException cause) {
        super(null, cause, false, false);
    }

    static void notifyListener(Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            throw new ListenerFailure(e);
        }
    }

    RuntimeException listenerException() {
        return (RuntimeException) getCause();
    }
}
