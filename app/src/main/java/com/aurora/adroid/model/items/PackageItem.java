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

package com.aurora.adroid.model.items;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;

import com.aurora.adroid.AuroraApplication;
import com.aurora.adroid.R;
import com.aurora.adroid.download.DownloadManager;
import com.aurora.adroid.download.RequestBuilder;
import com.aurora.adroid.event.Event;
import com.aurora.adroid.event.EventType;
import com.aurora.adroid.model.App;
import com.aurora.adroid.model.Package;
import com.aurora.adroid.util.LocalizationUtil;
import com.aurora.adroid.util.Log;
import com.aurora.adroid.util.PackageUtil;
import com.aurora.adroid.util.Util;
import com.mikepenz.fastadapter.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.tonyodev.fetch2.AbstractFetchGroupListener;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchGroup;
import com.tonyodev.fetch2.Request;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PackageItem extends AbstractItem<PackageItem.ViewHolder> {

    private Package pkg;
    private App app;

    public PackageItem(Package pkg, App app) {
        this.pkg = pkg;
        this.app = app;
    }

    @NotNull
    @Override
    public ViewHolder getViewHolder(@NotNull View view) {
        return new ViewHolder(view);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_package;
    }

    @Override
    public int getType() {
        return R.id.fastadapter_item;
    }

    public static class ViewHolder extends FastItemAdapter.ViewHolder<PackageItem> {
        @BindView(R.id.img)
        AppCompatImageView img;
        @BindView(R.id.line1)
        AppCompatTextView line1;
        @BindView(R.id.line2)
        AppCompatTextView line2;
        @BindView(R.id.line3)
        AppCompatTextView line3;
        @BindView(R.id.txt_apk_suggested)
        AppCompatTextView txtApkSuggested;
        @BindView(R.id.txt_apk_installed)
        AppCompatTextView txtApkInstalled;

        private Context context;

        ViewHolder(@NonNull View view) {
            super(view);
            ButterKnife.bind(this, view);
            context = view.getContext();
        }

        @Override
        public void bindView(@NotNull PackageItem item, @NotNull List<?> list) {

            final Package pkg = item.getPkg();
            final App app = item.getApp();

            final boolean installed = PackageUtil.isInstalledVersion(context, pkg);
            final boolean isArchDependent = PackageUtil.isArchSpecificPackage(pkg);

            line1.setText(StringUtils.joinWith(".", pkg.getVersionName(), pkg.getVersionCode()));
            line2.setText(StringUtils.joinWith(" \u2022 ", isArchDependent ? pkg.getNativecode().get(0)
                    : "Universal", app.getRepoName(), Util.humanReadableByteValue(pkg.getSize(), true)));
            line3.setText(Util.getDateFromMilli(pkg.getAdded()));

            if (isSuggested(pkg, app))
                txtApkSuggested.setVisibility(View.VISIBLE);

            if (installed)
                txtApkInstalled.setVisibility(View.VISIBLE);

            img.setOnClickListener(v -> initDownload(pkg, app));
        }

        private void initDownload(Package pkg, App app) {
            final Request request = RequestBuilder.buildRequest(context, app, pkg);
            final List<Request> requestList = new ArrayList<>();
            requestList.add(request);

            final Fetch fetch = DownloadManager.getFetchInstance(context);
            fetch.addListener(new AbstractFetchGroupListener() {

                @Override
                public void onQueued(int groupId, @NotNull Download download, boolean waitingNetwork, @NotNull FetchGroup fetchGroup) {
                    super.onQueued(groupId, download, waitingNetwork, fetchGroup);
                    if (groupId == app.getPackageName().hashCode()) {
                        AuroraApplication.rxNotify(new Event(EventType.SUB_DOWNLOAD_INITIATED, app.getPackageName()));
                    }
                }

                @Override
                public void onCancelled(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                    super.onCancelled(groupId, download, fetchGroup);
                    if (groupId == app.getPackageName().hashCode()) {
                        AuroraApplication.rxNotify(new Event(EventType.DOWNLOAD_CANCELLED));
                        fetch.removeListener(this);
                    }
                }

                @Override
                public void onCompleted(int groupId, @NotNull Download download, @NotNull FetchGroup fetchGroup) {
                    super.onCompleted(groupId, download, fetchGroup);
                    if (groupId == app.getPackageName().hashCode()) {
                        AuroraApplication.rxNotify(new Event(EventType.DOWNLOAD_COMPLETED));
                        fetch.removeListener(this);
                    }
                }
            });

            fetch.enqueue(requestList, result -> {
                Log.i("Downloading : %s", LocalizationUtil.getLocalizedName(context, app));
            });
        }

        private boolean isSuggested(Package pkg, App app) {
            try {
                return app.getSuggestedVersionName().equals(pkg.getVersionName()) && app.getSuggestedVersionCode() == pkg.getVersionCode();
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void unbindView(@NotNull PackageItem item) {
            line1.setText(null);
            line2.setText(null);
            line3.setText(null);
            txtApkSuggested.setVisibility(View.GONE);
            txtApkInstalled.setVisibility(View.GONE);
        }
    }
}
