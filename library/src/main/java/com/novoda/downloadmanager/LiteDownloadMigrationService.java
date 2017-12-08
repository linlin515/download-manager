package com.novoda.downloadmanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiteDownloadMigrationService extends Service {

    private Migrator v1ToV2Migrator;
    private ExecutorService executor;
    private IBinder binder;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        executor = Executors.newSingleThreadExecutor();
        binder = new MigrationDownloadServiceBinder();
        v1ToV2Migrator = MigrationFactory.createVersionOneToVersionTwoMigrator(
                getApplicationContext(),
                getDatabasePath("downloads.db"),
                deleteOldDatabaseOnCompletion
        );

        super.onCreate();
    }

    private final Migrator.Callback deleteOldDatabaseOnCompletion = new Migrator.Callback() {
        @Override
        public void onMigrationComplete() {
            Log.d(getClass().getSimpleName(), "Deleting DB");
            deleteDatabase("downloads.db");
        }
    };

    private void migrateFromV1ToV2() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(getClass().getSimpleName(), "Begin Migration");
                v1ToV2Migrator.migrate();
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent intent = new Intent(getApplicationContext(), LiteDownloadMigrationService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_ONE_SHOT);
        schedule(pendingIntent);
        Log.d(getClass().getSimpleName(), "rescheduling");
        super.onTaskRemoved(rootIntent);
    }

    private void schedule(PendingIntent pendingIntent) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(getClass().getSimpleName(), "Could not retrieve AlarmManager for rescheduling.");
            return;
        }
        alarmManager.set(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime() + 5000, pendingIntent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopSelf();
        Log.d(getClass().getSimpleName(), "Stopping service");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    public class MigrationDownloadServiceBinder extends Binder {

        public void migrate() {
            LiteDownloadMigrationService.this.migrateFromV1ToV2();
        }
    }

}
