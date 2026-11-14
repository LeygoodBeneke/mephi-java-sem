package ru.mephi.shortlink.core.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ru.mephi.shortlink.core.model.UserId;

public final class UserSession {

    private final Set<UserId> knownUsers = Collections.synchronizedSet(new LinkedHashSet<>());
    private volatile UserId current;

    public Optional<UserId> currentUser() {
        return Optional.ofNullable(current);
    }

    public synchronized UserId identify() {
        if (current == null) {
            current = UserId.random();
            knownUsers.add(current);
        }
        return current;
    }

    public synchronized UserId becomeNewUser() {
        current = UserId.random();
        knownUsers.add(current);
        return current;
    }

    public synchronized void use(UserId user) {
        knownUsers.add(user);
        current = user;
    }

    public List<UserId> knownUsers() {
        synchronized (knownUsers) {
            return new ArrayList<>(knownUsers);
        }
    }
}
