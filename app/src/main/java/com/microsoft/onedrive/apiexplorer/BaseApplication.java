// ------------------------------------------------------------------------------
// Copyright (c) 2015 Microsoft Corporation
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
// ------------------------------------------------------------------------------

package com.microsoft.onedrive.apiexplorer;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.util.LruCache;
import android.widget.Toast;

import com.microsoft.graph.authentication.IAuthenticationAdapter;
import com.microsoft.graph.authentication.MSAAuthAndroidAdapter;
import com.microsoft.graph.concurrency.ICallback;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.core.DefaultClientConfig;
import com.microsoft.graph.core.IClientConfig;
import com.microsoft.graph.extensions.GraphServiceClient;
import com.microsoft.graph.extensions.IGraphServiceClient;
import com.microsoft.graph.logger.LoggerLevel;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Base application
 */
public class BaseApplication extends Application {

    /**
     * The number of thumbnails to cache
     */
    private static final int MAX_IMAGE_CACHE_SIZE = 300;

    /**
     * Thumbnail cache
     */
    private LruCache<String, Bitmap> mImageCache;

    /**
     * The service instance
     */
    private final AtomicReference<IGraphServiceClient> mClient = new AtomicReference<>();

    /**
     * The system connectivity manager
     */
    private ConnectivityManager mConnectivityManager;

    /**
     * The authentication adapter
     */
    private IAuthenticationAdapter mAuthenticationAdapter;

    /**
     * What to do when the application starts
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        mAuthenticationAdapter = new MSAAuthAndroidAdapter(BaseApplication.this) {
            @Override
            public String getClientId() {
                return "8b83f251-463f-42d3-b6c4-308eb5fc1c43";
            }

            @Override
            public String[] getScopes() {
                return new String[] {
                        "https://graph.microsoft.com/Files.Read",
                        "offline_access",
                        "openid"
                };
            }
        };
    }

    /**
     * Create the client configuration
     * @return the newly created configuration
     */
    private IClientConfig createConfig() {
        final IClientConfig config = DefaultClientConfig.createWithAuthenticationProvider(getAuthenticationAdapter());
        config.getLogger().setLoggingLevel(LoggerLevel.Debug);
        return config;
    }

    /**
     * Navigates the user to the wifi settings if there is a connection problem
     *
     * @return if the wifi activity was navigated to
     */
    synchronized boolean goToWifiSettingsIfDisconnected() {
        final NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            Toast.makeText(this, getString(R.string.wifi_unavailable_error_message), Toast.LENGTH_LONG).show();
            final Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        }
        return false;
    }

    /**
     * Clears out the auth token from the application store
     */
    void signOut() {
        mAuthenticationAdapter.logout(new ICallback<Void>() {
            @Override
            public void success(final Void result) {
                mClient.set(null);
                final Intent intent = new Intent(getBaseContext(), ApiExplorer.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }

            @Override
            public void failure(ClientException ex) {
                Toast.makeText(getBaseContext(), "Logout error " + ex, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Get an instance of the service
     *
     * @return The Service
     */
    synchronized IGraphServiceClient getGraphServiceClient() {
        if (mClient.get() == null) {
            mClient.set(new GraphServiceClient
                    .Builder()
                    .fromConfig(createConfig())
                    .buildClient());
        }

        return mClient.get();
    }

    /**
     * Gets the image cache for this application
     *
     * @return the image loader
     */
    public synchronized LruCache<String, Bitmap> getImageCache() {
        if (mImageCache == null) {
            mImageCache = new LruCache<>(BaseApplication.MAX_IMAGE_CACHE_SIZE);
        }
        return mImageCache;
    }

    public synchronized IAuthenticationAdapter getAuthenticationAdapter() {
        return mAuthenticationAdapter;
    }
}
