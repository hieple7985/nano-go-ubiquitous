package com.example.android.sunshine.wear;

import android.graphics.BitmapFactory;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;

public class SunshineWearListenerService extends WearableListenerService {

    public static final String APP_DATA_UPDATE_REQUEST = "/SunshineWearListenerService/Data";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        for (int i = 0; i < dataEvents.getCount(); i++) {
            DataEvent event = dataEvents.get(i);

            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().equals(APP_DATA_UPDATE_REQUEST)) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                Asset asset = dataMap.getAsset("dataIcon");
                WatchFaceService.highTemp = dataMap.getInt("dataHigh");
                WatchFaceService.lowTemp = dataMap.getInt("dataLow");

                doLoadBitmap(asset);
            }
        }
    }

    private void doLoadBitmap(Asset asset) {
        if (null == asset) {
            return;
        }

        final GoogleApiClient googleApiClient = getGoogleApiClient();
        googleApiClient.connect();
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(googleApiClient, asset).await().getInputStream();
        googleApiClient.disconnect();

        if (assetInputStream == null) {
            return;
        }

        WatchFaceService.currCondImage = BitmapFactory.decodeStream(assetInputStream);
        WatchFaceService.updateClockFace();
    }

    private GoogleApiClient getGoogleApiClient() {
        return new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
    }
}
