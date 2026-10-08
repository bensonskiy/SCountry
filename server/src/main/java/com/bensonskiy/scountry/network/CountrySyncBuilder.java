package com.bensonskiy.scountry.network;

import com.bensonskiy.scountry.SCountryServer;
import com.bensonskiy.scountry.data.Country;
import java.util.ArrayList;
import java.util.List;

/** Server-only builder for the authoritative country sync snapshot. */
public final class CountrySyncBuilder {
    private CountrySyncBuilder() {}

    public static CountrySyncPacket collect() {
        List<CountryDTO> result = new ArrayList<>();
        if (SCountryServer.countryManager == null) return new CountrySyncPacket(result);
        for (Country country : SCountryServer.countryManager.getCountries().values()) {
            result.add(SCountryServer.snapshotOf(country));
        }
        return new CountrySyncPacket(result);
    }
}
