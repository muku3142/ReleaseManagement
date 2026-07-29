package com.evision.releasemgmt.ports;

import java.util.Optional;

public interface CurrentUserPort {

    Optional<String> currentAccountId();
}
