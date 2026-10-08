package com.bensonskiy.scountry.client.compat.journeymap;

import com.bensonskiy.scountry.SCountry;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;

@JourneyMapPlugin(apiVersion = "2.0.0")
public class SCountryJourneyMapPlugin implements IClientPlugin {

    private static SCountryJourneyMapPlugin instance;
    private static volatile IClientAPI api;

    @Override
    public void initialize(IClientAPI iClientAPI) {
        instance = this;
        api = iClientAPI;
        JourneyMapOverlayManager.registerApi(iClientAPI);
        ClientEventRegistry.MAPPING_EVENT.subscribe(SCountry.MODID, this::onMappingEvent);
        SCountry.LOGGER.info("[SCountry] JourneyMap plugin initialized");
    }

    @Override
    public String getModId() { return SCountry.MODID; }

    public static SCountryJourneyMapPlugin getInstance() { return instance; }
    public static IClientAPI getApi() { return api; }

    public static void onCountriesUpdated() {
        if (instance == null) return;
        // refreshAll() сам разрулит: если JM ещё не готов — поставит флаг
        // pendingRefresh, если готов — поставит задачу в scheduler.
        JourneyMapOverlayManager.refreshAll();
    }

    private void onMappingEvent(MappingEvent event) {
        if (event.getStage() != MappingEvent.Stage.MAPPING_STARTED) return;
        // НЕ строим оверлеи прямо здесь — иначе NPE внутри JM API.
        // markReady() только поднимет флаг и поставит refresh в очередь
        // к JourneyMapRefreshScheduler.
        JourneyMapOverlayManager.markReady();
    }
}