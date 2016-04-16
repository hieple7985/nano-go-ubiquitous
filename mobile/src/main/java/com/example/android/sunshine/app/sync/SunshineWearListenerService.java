package com.example.android.sunshine.app.sync;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWearListenerService extends WearableListenerService {

    public static final String WEAR_SYNC_REQUEST = "/SunshineWearListenerService/Sync";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (int i = 0; i < dataEvents.getCount(); i++) {
            DataEvent event = dataEvents.get(i);

            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals(WEAR_SYNC_REQUEST)) {
                SunshineSyncAdapter.syncImmediately(getApplicationContext());
            }
        }
    }
}
