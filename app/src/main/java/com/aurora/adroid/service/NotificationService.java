/*
 * Aurora Droid
 * Copyright (C) 2019-20, Rahul Kumar Patel <whyorean@gmail.com>
 *
 * Aurora Droid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Aurora Droid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aurora Droid.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.adroid.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.aurora.adroid.Constants;
import com.aurora.adroid.GlideApp;
import com.aurora.adroid.R;
import com.aurora.adroid.download.DownloadManager;
import com.aurora.adroid.receiver.DownloadCancelReceiver;
import com.aurora.adroid.receiver.DownloadPauseReceiver;
import com.aurora.adroid.receiver.DownloadResumeReceiver;
import com.aurora.adroid.receiver.InstallReceiver;
import com.aurora.adroid.ui.details.DetailsActivity;
import com.aurora.adroid.ui.generic.activity.DownloadsActivity;
import com.aurora.adroid.util.Log;
import com.aurora.adroid.util.Util;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.tonyodev.fetch2.AbstractFetchGroupListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchGroup;
import com.tonyodev.fetch2.Status;
import com.tonyodev.fetch2core.Extras;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import lombok.Getter;

public class NotificationService extends Service {

    public static final String FETCH_GROUP_ID = "FETCH_GROUP_ID";

    public static NotificationService INSTANCE = null;
    private final ArrayMap<String, DownloadBundle> bundleArrayMap = new ArrayMap<>();

    private Fetch fetch;
    private AbstractFetchGroupListener fetchListener;
    private NotificationManager notificationManager;

    public static boolean isNotAvailable() {
        try {
            return INSTANCE == null;
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("Notification Service Started");

        INSTANCE = this;

        //Create Notification Channels : General & Alert
        createNotificationChannel();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        fetch = DownloadManager.getFetchInstance(this);
        fetchListener = new AbstractFetchGroupListener() {
            @Override
            public void onCancelled(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onCompleted(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onError(int groupId, @NotNull Download download, @NotNull Error error, @Nullable Throwable throwable, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onProgress(int groupId, @NotNull Download download, long etaInMilliSeconds, long downloadedBytesPerSecond, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onQueued(int groupId, @NotNull Download download, boolean waitingNetwork, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }

            @Override
            public void onPaused(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                showNotification(groupId, download, fetchGroup);
            }
        };

        fetch.addListener(fetchListener);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            final ArrayList<NotificationChannel> channels = new ArrayList<>();

            channels.add(new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ALERT,
                    getString(R.string.notification_channel_alert),
                    NotificationManager.IMPORTANCE_HIGH));

            channels.add(new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_GENERAL,
                    getString(R.string.notification_channel_general),
                    NotificationManager.IMPORTANCE_MIN));

            final NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannels(channels);
            }
        }
    }

    private void showNotification(int groupId, Download download, FetchGroup fetchGroup) {

        if (groupId == 1337) //Skip Repo Sync notifications
            return;

        final Status status = download.getStatus();

        //Ignore notifications for completion of sub-parts of a bundled apk
        if (status == Status.COMPLETED && fetchGroup.getGroupDownloadProgress() < 100)
            return;

        synchronized (bundleArrayMap) {
            DownloadBundle downloadBundle = bundleArrayMap.get(download.getTag());
            if (downloadBundle == null) {
                downloadBundle = new DownloadBundle(download);
                bundleArrayMap.put(download.getTag(), downloadBundle);
            }

            final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_GENERAL);

            builder.setContentTitle(downloadBundle.getDisplayName());
            builder.setSmallIcon(R.drawable.ic_notification_outlined);
            builder.setColor(ContextCompat.getColor(this, R.color.colorAccent));
            builder.setWhen(download.getCreated());
            builder.setContentIntent(getContentIntentForDownloads());

            switch (status) {
                case PAUSED:
                    builder.setSmallIcon(R.drawable.ic_download_pause);
                    builder.setContentText(getString(R.string.download_paused));
                    break;
                case CANCELLED:
                    builder.setSmallIcon(R.drawable.ic_download_cancel);
                    builder.setContentText(getString(R.string.download_canceled));
                    builder.setColor(ContextCompat.getColor(this, R.color.colorRed));
                    break;
                case FAILED:
                    builder.setSmallIcon(R.drawable.ic_download_fail);
                    builder.setContentText(getString(R.string.download_failed));
                    builder.setColor(ContextCompat.getColor(this, R.color.colorRed));
                    break;
                case COMPLETED:
                    if (fetchGroup.getGroupDownloadProgress() == 100) {
                        builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
                        builder.setContentText(getString(R.string.download_completed));
                    }
                    break;
                default:
                    builder.setSmallIcon(android.R.drawable.stat_sys_download);
                    builder.setContentText(getString(R.string.download_metadata));
                    break;
            }

            final int progress = fetchGroup.getGroupDownloadProgress();
            final NotificationCompat.BigTextStyle progressBigText = new NotificationCompat.BigTextStyle();

            //Set Notification data
            switch (status) {
                case QUEUED:
                    builder.setProgress(100, 0, true);
                    progressBigText.bigText(getString(R.string.download_queued));
                    builder.setStyle(progressBigText);
                    break;

                case DOWNLOADING:
                    final String contentString = getString(R.string.download_progress);
                    final String speedString = Util.humanReadableByteSpeed(download.getDownloadedBytesPerSecond(), true);

                    progressBigText.bigText(StringUtils.joinWith(" \u2022 ",
                            contentString,
                            speedString));
                    builder.setStyle(progressBigText);

                    builder.addAction(new NotificationCompat.Action.Builder(R.drawable.ic_download_pause,
                            getString(R.string.action_pause),
                            getPauseIntent(groupId)).build());

                    builder.addAction(new NotificationCompat.Action.Builder(R.drawable.ic_download_cancel,
                            getString(R.string.action_cancel),
                            getCancelIntent(groupId)).build());

                    if (progress < 0)
                        builder.setProgress(100, 0, true);
                    else
                        builder.setProgress(100, progress, false);
                    break;

                case PAUSED:
                    progressBigText.bigText(getString(R.string.download_paused));
                    builder.setStyle(progressBigText);
                    builder.addAction(new NotificationCompat.Action.Builder(R.drawable.ic_download_pause,
                            getString(R.string.action_resume),
                            getResumeIntent(groupId)).build());
                    break;

                case COMPLETED:
                    if (fetchGroup.getGroupDownloadProgress() == 100) {
                        builder.setAutoCancel(true);
                        builder.setContentIntent(getContentIntentForDetails(downloadBundle.getPackageName()));
                        //Check for Aurora Services, if available do not show install notification.
                        if (Util.isPrivilegedInstall(this)) {
                            progressBigText.bigText(getString(R.string.action_installing));
                        } else {
                            //Check for Enforced Native & Add Install action via notification, only if app is not bundled.
                            progressBigText.bigText(getString(R.string.notification_installation_auto));
                            builder.addAction(R.drawable.ic_installation,
                                    getString(R.string.action_install),
                                    getInstallIntent(downloadBundle.getPackageName(), download));
                        }
                        builder.setStyle(progressBigText);
                    }
                    break;
            }

            //Set Notification category
            switch (status) {
                case DOWNLOADING:
                    builder.setCategory(Notification.CATEGORY_PROGRESS);
                    break;
                case FAILED:
                case CANCELLED:
                    builder.setCategory(Notification.CATEGORY_ERROR);
                    break;
                default:
                    builder.setCategory(Notification.CATEGORY_STATUS);
                    break;
            }

            //Set icon
            GlideApp.with(this)
                    .asBitmap()
                    .load(downloadBundle.getIconUrl())
                    .circleCrop()
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NotNull Bitmap bitmap, Transition<? super Bitmap> transition) {
                            builder.setLargeIcon(bitmap);
                        }
                    });

            notificationManager.notify(downloadBundle.getPackageName(),
                    downloadBundle.getPackageName().hashCode(),
                    builder.build());
        }
    }

    private PendingIntent getPauseIntent(int groupId) {
        final Intent intent = new Intent(this, DownloadPauseReceiver.class);
        intent.putExtra(FETCH_GROUP_ID, groupId);
        return PendingIntent.getBroadcast(this, groupId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getResumeIntent(int groupId) {
        final Intent intent = new Intent(this, DownloadResumeReceiver.class);
        intent.putExtra(FETCH_GROUP_ID, groupId);
        return PendingIntent.getBroadcast(this, groupId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getCancelIntent(int groupId) {
        final Intent intent = new Intent(this, DownloadCancelReceiver.class);
        intent.putExtra(FETCH_GROUP_ID, groupId);
        return PendingIntent.getBroadcast(this, groupId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getContentIntentForDetails(String packageName) {
        final Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(Constants.INTENT_PACKAGE_NAME, packageName);
        return PendingIntent.getActivity(this, packageName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getContentIntentForDownloads() {
        final Intent intent = new Intent(this, DownloadsActivity.class);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getInstallIntent(String packageName, Download download) {
        final Intent intent = new Intent(this, InstallReceiver.class);
        intent.putExtra(Constants.INTENT_PACKAGE_NAME, packageName);
        intent.putExtra(Constants.STRING_EXTRA, download.getFile());
        return PendingIntent.getBroadcast(this, packageName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onDestroy() {
        Log.i("Notification Service Stopped");
        fetch.removeListener(fetchListener);
        INSTANCE = null;
        super.onDestroy();
    }

    @Getter
    private static class DownloadBundle {
        private String packageName;
        private String displayName;
        private String versionName;
        private String versionCode;
        private String iconUrl;
        private String apkName;

        public DownloadBundle(Download download) {
            final Extras extras = download.getExtras();
            this.packageName = extras.getString(Constants.DOWNLOAD_PACKAGE_NAME, StringUtils.EMPTY);
            this.displayName = extras.getString(Constants.DOWNLOAD_DISPLAY_NAME, StringUtils.EMPTY);
            this.versionName = extras.getString(Constants.DOWNLOAD_VERSION_NAME, StringUtils.EMPTY);
            this.versionCode = extras.getString(Constants.DOWNLOAD_VERSION_CODE, StringUtils.EMPTY);
            this.apkName = extras.getString(Constants.DOWNLOAD_APK_NAME, StringUtils.EMPTY);
            this.iconUrl = extras.getString(Constants.DOWNLOAD_ICON_URL, StringUtils.EMPTY);
        }
    }
}
