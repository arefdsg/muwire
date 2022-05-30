package com.muwire.core;

public class InvalidSignatureException extends Exception {

    public InvalidSignatureException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidSignatureException(String message) {
        super(message);
    }

    public InvalidSignatureException(Throwable cause) {
        super(cause);
    }

}
