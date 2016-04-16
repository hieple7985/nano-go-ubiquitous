/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;


public class WatchFaceService extends CanvasWatchFaceService {

    private static WearFaceEngine engine;
    private static final int MSG_UPDATE_TIME = 0;
    private static final long INTERACTIVE_UPDATE_RATE_MS = 60000;

    static Bitmap currCondImage;
    static int highTemp = Integer.MIN_VALUE;
    static int lowTemp = Integer.MIN_VALUE;

    @Override
    public WearFaceEngine onCreateEngine() {
        return new WearFaceEngine();
    }

    public static void updateClockFace() {
        if (engine != null) {
            engine.invalidate();
        }
    }

    private class WearFaceEngine extends CanvasWatchFaceService.Engine {

        final Handler engineUpdateHandler = new EngineHandler(this);

        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                currentTime = Calendar.getInstance(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
            }
        };

        Calendar currentTime;
        float timeOffsetY;
        float dateOffsetY;
        float lineOffsetY;
        float currentWeatherOffsetY;

        boolean isAmbient;
        boolean registeredTimeZoneReceiver = false;
        boolean supportsLowBitAmbient;

        Paint backgroundPaint;
        Paint timePaint;
        Paint datePaint;
        Paint linePaint;
        Paint highTempPaint;
        Paint lowTempPaint;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            engine = this;

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = WatchFaceService.this.getResources();

            timeOffsetY = resources.getDimension(R.dimen.clock_offset_y);
            dateOffsetY = resources.getDimension(R.dimen.offset_date_y);
            lineOffsetY = resources.getDimension(R.dimen.offset_line_y);
            currentWeatherOffsetY = resources.getDimension(R.dimen.offset_current_weather_y);

            initPaint();

            currentTime = Calendar.getInstance();
            notifyHandheld();
        }

        private void initPaint() {
            backgroundPaint = new Paint();
            backgroundPaint.setColor(ContextCompat.getColor(WatchFaceService.this, R.color.background_primary));

            highTempPaint = new Paint();
            highTempPaint.setColor(ContextCompat.getColor(WatchFaceService.this, R.color.clock_text));
            highTempPaint.setAntiAlias(true);

            lowTempPaint = new Paint();
            lowTempPaint.setColor(ContextCompat.getColor(WatchFaceService.this, R.color.clock_text));
            lowTempPaint.setAlpha(180);
            lowTempPaint.setAntiAlias(true);

            linePaint = new Paint();
            linePaint.setColor(ContextCompat.getColor(WatchFaceService.this, R.color.clock_text));
            linePaint.setAlpha(128);
            linePaint.setAntiAlias(true);

            datePaint = new Paint();
            datePaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.clock_text));
            datePaint.setAlpha(180);
            datePaint.setAntiAlias(true);

            timePaint = new Paint();
            timePaint.setColor(ContextCompat.getColor(WatchFaceService.this, R.color.clock_text));
            timePaint.setAntiAlias(true);
        }

        private void notifyHandheld() {
            PutDataMapRequest dataSyncRequest = PutDataMapRequest.create("/SunshineWearListenerService/Sync");
            dataSyncRequest.getDataMap().putLong("dataTime", System.currentTimeMillis());

            PutDataRequest dataPutRequest = dataSyncRequest.asPutDataRequest();
            GoogleApiClient googleApiClient = getGoogleApiClient();
            googleApiClient.connect();

            Wearable.DataApi.putDataItem(googleApiClient, dataPutRequest);
            googleApiClient.disconnect();
        }

        private GoogleApiClient getGoogleApiClient() {
            return new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .build();
        }

        @Override
        public void onDestroy() {
            engineUpdateHandler.removeMessages(MSG_UPDATE_TIME);
            engine = null;
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                currentTime.setTimeZone(TimeZone.getDefault());
                currentTime.setTime(new Date());
            } else {
                unregisterReceiver();
            }

            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFaceService.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(timeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            Resources res = WatchFaceService.this.getResources();
            boolean isRound = insets.isRound();

            float highTempTextSize = res.getDimension(isRound ? R.dimen.round_size_text_high_temp : R.dimen.square_size_text_high_temp);
            float lowTempTextSize = res.getDimension(isRound ? R.dimen.round_size_text_low_temp : R.dimen.square_size_text_low_temp);
            float textSize = res.getDimension(isRound ? R.dimen.clock_text_size_round : R.dimen.clock_text_size);
            float dateTextSize = res.getDimension(isRound ? R.dimen.round_size_text_date : R.dimen.square_size_text_date);

            highTempPaint.setTextSize(highTempTextSize);
            lowTempPaint.setTextSize(lowTempTextSize);
            timePaint.setTextSize(textSize);
            datePaint.setTextSize(dateTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            supportsLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (isAmbient != inAmbientMode) {
                isAmbient = inAmbientMode;

                if (supportsLowBitAmbient) {
                    timePaint.setAntiAlias(!inAmbientMode);
                }

                invalidate();
            }

            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            drawCommon(canvas, bounds);

            if (!isInAmbientMode()) {
                drawInAmbientMode(canvas, bounds);
            }
        }

        private void drawCommon(Canvas canvas, Rect bounds) {
            // Background
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), backgroundPaint);

            // Time
            currentTime.setTimeInMillis(System.currentTimeMillis());
            String timeText = String.format("%d:%02d", currentTime.get(Calendar.HOUR_OF_DAY), currentTime.get(Calendar.MINUTE));
            canvas.drawText(timeText, bounds.centerX() - timePaint.measureText(timeText) / 2, timeOffsetY, timePaint);
        }

        private void drawInAmbientMode(Canvas canvas, Rect bounds) {
            // Background
            canvas.drawColor(Color.BLACK);

            // Date
            String dateText = String.format("%s, %s %02d %04d",
                    currentTime.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()),
                    currentTime.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()),
                    currentTime.get(Calendar.DAY_OF_MONTH),
                    currentTime.get(Calendar.YEAR));
            canvas.drawText(dateText, bounds.centerX() - datePaint.measureText(dateText) / 2, dateOffsetY, datePaint);

            // Icon
            if (currCondImage != null) {
                int imageWidth = getApplicationContext().getResources().getDimensionPixelSize(R.dimen.icon_size);
                canvas.drawBitmap(currCondImage, (bounds.width() * .15f) - imageWidth / 2, currentWeatherOffsetY - 45, null);
            }

            // Divider Line
            canvas.drawLine(bounds.width() * .25f, lineOffsetY, bounds.width() * .75f, lineOffsetY, linePaint);

            // Temperature
            if (highTemp == Integer.MIN_VALUE || lowTemp == Integer.MIN_VALUE) {
                return;
            }

            String tempFormat = "%d\u00b0";
            String highTempString = String.format(tempFormat, highTemp);
            String lowTempString = String.format(tempFormat, lowTemp);

            canvas.drawText(highTempString, bounds.centerX() - highTempPaint.measureText(highTempString) / 2, currentWeatherOffsetY, highTempPaint);
            canvas.drawText(lowTempString, (bounds.width() * .75f) - lowTempPaint.measureText(lowTempString) / 2, currentWeatherOffsetY, lowTempPaint);
        }

        private void updateTimer() {
            engineUpdateHandler.removeMessages(MSG_UPDATE_TIME);
            if (isTimerRunning()) {
                engineUpdateHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean isTimerRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void handleTimeMsg() {
            invalidate();
            if (isTimerRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                engineUpdateHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WearFaceEngine> mWeakReference;

        public EngineHandler(WearFaceEngine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WearFaceEngine wearSunshineEngine = mWeakReference.get();
            if (wearSunshineEngine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        wearSunshineEngine.handleTimeMsg();
                        break;
                }
            }
        }
    }
}
