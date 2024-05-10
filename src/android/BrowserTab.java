/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cordova.plugin.browsertab;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import androidx.browser.customtabs.CustomTabsIntent;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public class BrowserTab extends CordovaPlugin {

    private static final String LOG_TAG = "BrowserTab";
    private String mCustomTabsBrowser;
    private CallbackContext callbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if ("openUrl".equals(action)) {
            openUrl(args, callbackContext);
            return true;
        }
        return false;
    }

    private void openUrl(JSONArray args, CallbackContext callbackContext) {
        if (args.length() < 1) {
            callbackContext.error("URL argument missing");
            return;
        }

        String urlStr;
        try {
            urlStr = args.getString(0);
        } catch (JSONException e) {
            callbackContext.error("URL argument is not a string");
            return;
        }

        String customTabsBrowser = findCustomTabBrowser();
        if (customTabsBrowser == null) {
            callbackContext.error("No in-app browser tab implementation available");
            return;
        }

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        CustomTabsIntent customTabsIntent = builder.build();
        customTabsIntent.intent.setPackage(customTabsBrowser);
        customTabsIntent.launchUrl(cordova.getActivity(), Uri.parse(urlStr));

        this.callbackContext = callbackContext;
        sendOpenResult(callbackContext);
    }

    private String findCustomTabBrowser() {
        if (mCustomTabsBrowser != null) {
            return mCustomTabsBrowser;
        }

        PackageManager pm = cordova.getActivity().getPackageManager();
        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
        List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(webIntent, PackageManager.GET_RESOLVED_FILTER);

        for (ResolveInfo info : resolvedActivityList) {
            if (supportsCustomTabs(pm, info.activityInfo.packageName)) {
                mCustomTabsBrowser = info.activityInfo.packageName;
                return mCustomTabsBrowser;
            }
        }

        return null;
    }

    private boolean supportsCustomTabs(PackageManager pm, String packageName) {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction("android.support.customtabs.action.CustomTabsService");
        serviceIntent.setPackage(packageName);
        return (pm.resolveService(serviceIntent, 0) != null);
    }

    private void sendOpenResult(CallbackContext callbackContext) {
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, 0);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if(lastScheme != null && intent.getData() != null) {
      String openUrl = intent.getData().toString();
      if (openUrl.startsWith(lastScheme)) {
        lastScheme = null;
        sendSuccessResult(callbackContext, openUrl);
        this.callbackContext = null;
        if(!isInvokedActivitResultClose) {
          closeCustomTab();
        }
        isInvokedActivitResultClose = false;
      }
    }
  }


  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);
    if(callbackContext != null && isInvokedActivitResultClose){
      sendCloseResult(callbackContext);
      callbackContext = null;
      isInvokedActivitResultClose = false;
    }
  }

  private void openUrl(JSONArray args, CallbackContext callbackContext) {
    isInvokedActivitResultClose = false;
    if (args.length() < 1) {
        Log.d(LOG_TAG, "openUrl: no url argument received");
        callbackContext.error("URL argument missing");
        return;
    }

    String urlStr;
    try {
        urlStr = args.getString(0);
        JSONObject options = args.getJSONObject(1);
        if(options.has("scheme") && options.getString("scheme").length() != 0) {
            lastScheme = options.getString("scheme");
        }

    } catch (JSONException e) {
        Log.d(LOG_TAG, "openUrl: failed to parse url argument");
        callbackContext.error("URL argument is not a string");
        return;
    }

    String customTabsBrowser = findCustomTabBrowser();
    if (customTabsBrowser == null) {
        Log.d(LOG_TAG, "openUrl: no in app browser tab available");
        callbackContext.error("no in app browser tab implementation available");
    }

    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
    CustomTabsIntent customTabsIntent = builder.build();
    customTabsIntent.intent.setPackage(customTabsBrowser);
    customTabsIntent.launchUrl(cordova.getActivity(), Uri.parse(urlStr));

    this.callbackContext = callbackContext;
    sendOpenResult(callbackContext);
  }


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if(requestCode == CUSTOM_TAB_REQUEST_CODE){
      isInvokedActivitResultClose = true;
    }
  }


  public void openExternal(JSONArray args, CallbackContext callbackContext) {
    if (args.length() < 1) {
      Log.d(LOG_TAG, "openUrl: no url argument received");
      callbackContext.error("URL argument missing");
      return;
    }

    String urlStr;
    try {
      urlStr = args.getString(0);
    } catch (JSONException e) {
      Log.d(LOG_TAG, "openUrl: failed to parse url argument");
      callbackContext.error("URL argument is not a string");
      return;
    }

    try {
      Intent intent = null;
      intent = new Intent(Intent.ACTION_VIEW);
      Uri uri = Uri.parse(urlStr);
      if ("file".equals(uri.getScheme())) {
        intent.setDataAndType(uri, webView.getResourceApi().getMimeType(uri));
      } else {
        intent.setData(uri);
      }
      intent.putExtra(Browser.EXTRA_APPLICATION_ID, cordova.getActivity().getPackageName());
      this.cordova.getActivity().startActivity(intent);
      callbackContext.success();
    } catch (java.lang.RuntimeException e) {
      callbackContext.error("Error loading url "+urlStr+":"+ e.toString());
    }
  }

  private String findCustomTabBrowser() {
    if (mFindCalled) {
      return mCustomTabsBrowser;
    }

    PackageManager pm = cordova.getActivity().getPackageManager();
    Intent webIntent = new Intent(
        Intent.ACTION_VIEW,
        Uri.parse("http://www.example.com"));
    List<ResolveInfo> resolvedActivityList =
        pm.queryIntentActivities(webIntent, PackageManager.GET_RESOLVED_FILTER);

    for (ResolveInfo info : resolvedActivityList) {
      if (!isFullBrowser(info)) {
        continue;
      }

      if (hasCustomTabWarmupService(pm, info.activityInfo.packageName)) {
        mCustomTabsBrowser = info.activityInfo.packageName;
        break;
      }
    }

    mFindCalled = true;
    return mCustomTabsBrowser;
  }

  private boolean isFullBrowser(ResolveInfo resolveInfo) {
    // The filter must match ACTION_VIEW, CATEGORY_BROWSEABLE, and at least one scheme,
    if (!resolveInfo.filter.hasAction(Intent.ACTION_VIEW)
            || !resolveInfo.filter.hasCategory(Intent.CATEGORY_BROWSABLE)
            || resolveInfo.filter.schemesIterator() == null) {
        return false;
    }

    // The filter must not be restricted to any particular set of authorities
    if (resolveInfo.filter.authoritiesIterator() != null) {
        return false;
    }

    // The filter must support both HTTP and HTTPS.
    boolean supportsHttp = false;
    boolean supportsHttps = false;
    Iterator<String> schemeIter = resolveInfo.filter.schemesIterator();
    while (schemeIter.hasNext()) {
        String scheme = schemeIter.next();
        supportsHttp |= "http".equals(scheme);
        supportsHttps |= "https".equals(scheme);

        if (supportsHttp && supportsHttps) {
            return true;
        }
    }

    // at least one of HTTP or HTTPS is not supported
    return false;
  }

  private boolean hasCustomTabWarmupService(PackageManager pm, String packageName) {
    Intent serviceIntent = new Intent();
    serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
    serviceIntent.setPackage(packageName);
    return (pm.resolveService(serviceIntent, 0) != null);
  }
}
