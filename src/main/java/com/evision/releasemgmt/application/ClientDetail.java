package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientInstall;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;
import com.evision.releasemgmt.domain.portfolio.Product;

import java.util.List;
import java.util.UUID;

public record ClientDetail(Client client, List<Run> runs, List<HistoryRow> history) {

    /** current null means Not installed. */
    public record Run(Product product, boolean hasInstalls, CurrentRun current, boolean currentInProduction) { }

    public record HistoryRow(ClientInstall install, String productName, boolean inProduction, UUID supersededById) {

        public boolean effective() {
            return supersededById == null;
        }
    }
}
