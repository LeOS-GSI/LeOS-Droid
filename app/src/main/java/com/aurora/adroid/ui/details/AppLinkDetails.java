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

package com.aurora.adroid.ui.details;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import com.aurora.adroid.Constants;
import com.aurora.adroid.R;
import com.aurora.adroid.model.App;
import com.aurora.adroid.ui.sheet.PermissionSheet;
import com.aurora.adroid.ui.view.LinkView;
import com.aurora.adroid.util.Log;
import com.aurora.adroid.util.PackageUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Modifier;

import butterknife.BindView;

public class AppLinkDetails extends AbstractDetails {


    @BindView(R.id.layout_link_perm)
    LinearLayout linkLayout;

    private Gson gson = new GsonBuilder().excludeFieldsWithModifiers(Modifier.TRANSIENT).create();

    public AppLinkDetails(DetailsActivity activity, App app) {
        super(activity, app);
    }

    @Override
    public void draw() {
        LinkView permLinkView = new LinkView(context);
        permLinkView.setLinkText(permLinkView.getResources().getString(R.string.app_details_permission));
        permLinkView.setLinkImageId(R.drawable.ic_permission_link);
        permLinkView.setColor(R.color.colorGold);
        permLinkView.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.STRING_EXTRA, gson.toJson(app));
            PermissionSheet profileFragment = new PermissionSheet();
            profileFragment.setArguments(bundle);
            profileFragment.show(activity.getSupportFragmentManager(), PermissionSheet.TAG);
        });
        permLinkView.build();

        LinkView sourceLinkView = new LinkView(context);
        sourceLinkView.setVisibility(TextUtils.isEmpty(app.getSourceCode()) ? View.GONE : View.VISIBLE);
        sourceLinkView.setLinkText(sourceLinkView.getResources().getString(R.string.app_details_source));
        sourceLinkView.setLinkImageId(R.drawable.ic_source_link);
        sourceLinkView.setColor(R.color.colorCyan);
        sourceLinkView.setOnClickListener(v -> {
            openWebView(app.getSourceCode());
        });
        sourceLinkView.build();

        LinkView websiteLinkView = new LinkView(context);
        websiteLinkView.setVisibility(TextUtils.isEmpty(app.getWebSite()) ? View.GONE : View.VISIBLE);
        websiteLinkView.setLinkText(websiteLinkView.getResources().getString(R.string.app_details_website));
        websiteLinkView.setLinkImageId(R.drawable.ic_web_link);
        websiteLinkView.setColor(R.color.colorPurple);
        websiteLinkView.setOnClickListener(v -> {
            openWebView(app.getWebSite());
        });
        websiteLinkView.build();

        LinkView donationLinkView = new LinkView(context);
        donationLinkView.setVisibility(TextUtils.isEmpty(app.getDonate()) ? View.GONE : View.VISIBLE);
        donationLinkView.setLinkText(donationLinkView.getResources().getString(R.string.app_details_donation));
        donationLinkView.setLinkImageId(R.drawable.ic_donation_link);
        donationLinkView.setColor(R.color.colorOrange);
        donationLinkView.setOnClickListener(v -> {
            openWebView(app.getDonate());
        });
        donationLinkView.build();

        LinkView settingsLinkView = new LinkView(context);
        settingsLinkView.setVisibility(PackageUtil.isInstalled(context, app.getPackageName()) ? View.VISIBLE : View.GONE);
        settingsLinkView.setLinkText(settingsLinkView.getResources().getString(R.string.app_details_settings));
        settingsLinkView.setLinkImageId(R.drawable.ic_settings_link);
        settingsLinkView.setColor(R.color.colorGreen);
        settingsLinkView.setOnClickListener(v -> {
            try {
                context.startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                        Uri.parse("package:" + app.getPackageName())));
            } catch (ActivityNotFoundException e) {
                Log.e("Could not find system app activity");
            }
        });

        settingsLinkView.build();
        linkLayout.removeAllViews();
        linkLayout.addView(permLinkView);
        linkLayout.addView(sourceLinkView);
        linkLayout.addView(websiteLinkView);
        linkLayout.addView(donationLinkView);
        linkLayout.addView(settingsLinkView);
    }

    private void openWebView(String URL) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(URL)));
        } catch (Exception e) {
            Log.e("No WebView found !");
        }
    }
}
