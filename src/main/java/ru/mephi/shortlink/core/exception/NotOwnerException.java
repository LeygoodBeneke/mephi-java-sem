package ru.mephi.shortlink.core.exception;

public class NotOwnerException extends DomainException {

    private static final long serialVersionUID = 1L;

    public NotOwnerException(String message) {
        super(message);
    }
}
