package ru.mephi.shortlink.core.repository;

import java.util.List;
import java.util.Optional;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;

public interface LinkRepository {

    boolean save(ShortLink link);

    Optional<ShortLink> findByCode(ShortCode code);

    List<ShortLink> findByOwner(UserId owner);

    List<ShortLink> findAll();

    boolean delete(ShortCode code, UserId owner);

    int count();
}
