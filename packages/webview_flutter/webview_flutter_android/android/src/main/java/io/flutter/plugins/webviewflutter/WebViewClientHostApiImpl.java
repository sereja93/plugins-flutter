// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;

import java.lang.reflect.Field;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Host api implementation for {@link WebViewClient}.
 *
 * <p>Handles creating {@link WebViewClient}s that intercommunicate with a paired Dart object.
 */
public class WebViewClientHostApiImpl implements GeneratedAndroidWebView.WebViewClientHostApi {
  private final InstanceManager instanceManager;
  private final WebViewClientCreator webViewClientCreator;
  private final WebViewClientFlutterApiImpl flutterApi;
  private final TrustManagerFactory tmf;


  /** Implementation of {@link WebViewClient} that passes arguments of callback methods to Dart. */
  @RequiresApi(Build.VERSION_CODES.N)
  public static class WebViewClientImpl extends WebViewClient {
    private final WebViewClientFlutterApiImpl flutterApi;
    private final TrustManagerFactory tmf;

    private boolean returnValueForShouldOverrideUrlLoading = false;

    /**
     * Creates a {@link WebViewClient} that passes arguments of callbacks methods to Dart.
     *
     * @param flutterApi handles sending messages to Dart
     */
    public WebViewClientImpl(@NonNull WebViewClientFlutterApiImpl flutterApi, TrustManagerFactory tmf) {
      this.flutterApi = flutterApi;
      this.tmf = tmf;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      flutterApi.onPageStarted(this, view, url, reply -> {});
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      flutterApi.onPageFinished(this, view, url, reply -> {});
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
      flutterApi.onReceivedRequestError(this, view, request, error, reply -> {});
    }


    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
      Log.d("WEB_VIEW_EXAMPLE", "onReceivedSslError");
      if (tmf == null) {
        handler.cancel();
        return;
      }
      boolean passVerify = false;
      if (error.getPrimaryError() == SslError.SSL_UNTRUSTED) {
        SslCertificate cert = error.getCertificate();
        String subjectDn = cert.getIssuedTo().getDName();
        Log.d("WEB_VIEW_EXAMPLE", "subjectDN: " + subjectDn);
        try {
          Field f = cert.getClass().getDeclaredField("mX509Certificate");
          f.setAccessible(true);
          X509Certificate x509 = (X509Certificate) f.get(cert);
          X509Certificate[] chain = new X509Certificate[]{x509};
          for (TrustManager trustManager : tmf.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
              X509TrustManager x509TrustManager = (X509TrustManager) trustManager;
              try {
                x509TrustManager.checkServerTrusted(chain, "generic");
                passVerify = true;
                break;
              } catch (Exception e) {
                Log.e("WEB_VIEW_EXAMPLE", "verify trustManager failed" + e);
                passVerify = false;
              }
            }
          }
          Log.d("WEB_VIEW_EXAMPLE", "passVerify: " + passVerify);
        } catch (Exception e) {
          Log.e("WEB_VIEW_EXAMPLE", "verify cert fail" + e);
        }
      }
      if (passVerify) {
        handler.proceed();
      } else {
        handler.cancel();
      }
    }

    @Override
    public void onReceivedError(
        WebView view, int errorCode, String description, String failingUrl) {
      flutterApi.onReceivedError(
          this, view, (long) errorCode, description, failingUrl, reply -> {});
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
      flutterApi.requestLoading(this, view, request, reply -> {});
      return returnValueForShouldOverrideUrlLoading;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      flutterApi.urlLoading(this, view, url, reply -> {});
      return returnValueForShouldOverrideUrlLoading;
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
      // Deliberately empty. Occasionally the webview will mark events as having failed to be
      // handled even though they were handled. We don't want to propagate those as they're not
      // truly lost.
    }

    /** Sets return value for {@link #shouldOverrideUrlLoading}. */
    public void setReturnValueForShouldOverrideUrlLoading(boolean value) {
      returnValueForShouldOverrideUrlLoading = value;
    }
  }

  /**
   * Implementation of {@link WebViewClientCompat} that passes arguments of callback methods to
   * Dart.
   */
  public static class WebViewClientCompatImpl extends WebViewClientCompat {
    private final WebViewClientFlutterApiImpl flutterApi;
    private boolean returnValueForShouldOverrideUrlLoading = false;
    private final TrustManagerFactory tmf;


    public WebViewClientCompatImpl(@NonNull WebViewClientFlutterApiImpl flutterApi, TrustManagerFactory tmf) {
      this.flutterApi = flutterApi;
      this.tmf = tmf;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      flutterApi.onPageStarted(this, view, url, reply -> {});
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      flutterApi.onPageFinished(this, view, url, reply -> {});
    }

    // This method is only called when the WebViewFeature.RECEIVE_WEB_RESOURCE_ERROR feature is
    // enabled. The deprecated method is called when a device doesn't support this.
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("RequiresFeature")
    @Override
    public void onReceivedError(
        @NonNull WebView view,
        @NonNull WebResourceRequest request,
        @NonNull WebResourceErrorCompat error) {
      flutterApi.onReceivedRequestError(this, view, request, error, reply -> {});
    }

    @Override
    public void onReceivedError(
        WebView view, int errorCode, String description, String failingUrl) {
      flutterApi.onReceivedError(
          this, view, (long) errorCode, description, failingUrl, reply -> {});
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean shouldOverrideUrlLoading(
        @NonNull WebView view, @NonNull WebResourceRequest request) {
      flutterApi.requestLoading(this, view, request, reply -> {});
      return returnValueForShouldOverrideUrlLoading;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      flutterApi.urlLoading(this, view, url, reply -> {});
      return returnValueForShouldOverrideUrlLoading;
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
      Log.d("WEB_VIEW_EXAMPLE", "onReceivedSslError");
      boolean passVerify = false;
      if (tmf == null) {
        handler.cancel();
        return;
      }
      if (error.getPrimaryError() == SslError.SSL_UNTRUSTED) {
        SslCertificate cert = error.getCertificate();
        String subjectDn = cert.getIssuedTo().getDName();
        Log.d("WEB_VIEW_EXAMPLE", "subjectDN: " + subjectDn);
        try {
          Field f = cert.getClass().getDeclaredField("mX509Certificate");
          f.setAccessible(true);
          X509Certificate x509 = (X509Certificate) f.get(cert);
          X509Certificate[] chain = new X509Certificate[]{x509};
          for (TrustManager trustManager : tmf.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager) {
              X509TrustManager x509TrustManager = (X509TrustManager) trustManager;
              try {
                x509TrustManager.checkServerTrusted(chain, "generic");
                passVerify = true;
                break;
              } catch (Exception e) {
                Log.e("WEB_VIEW_EXAMPLE", "verify trustManager failed" + e);
                passVerify = false;
              }
            }
          }
          Log.d("WEB_VIEW_EXAMPLE", "passVerify: " + passVerify);
        } catch (Exception e) {
          Log.e("WEB_VIEW_EXAMPLE", "verify cert fail" + e);
        }
      }
      if (passVerify) {
        handler.proceed();
      } else {
        handler.cancel();
      }
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
      // Deliberately empty. Occasionally the webview will mark events as having failed to be
      // handled even though they were handled. We don't want to propagate those as they're not
      // truly lost.
    }

    /** Sets return value for {@link #shouldOverrideUrlLoading}. */
    public void setReturnValueForShouldOverrideUrlLoading(boolean value) {
      returnValueForShouldOverrideUrlLoading = value;
    }
  }

  /** Handles creating {@link WebViewClient}s for a {@link WebViewClientHostApiImpl}. */
  public static class WebViewClientCreator {
    /**
     * Creates a {@link WebViewClient}.
     *
     * @param flutterApi handles sending messages to Dart
     * @return the created {@link WebViewClient}
     */
    public WebViewClient createWebViewClient(WebViewClientFlutterApiImpl flutterApi, TrustManagerFactory tmf) {
      // WebViewClientCompat is used to get
      // shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
      // invoked by the webview on older Android devices, without it pages that use iframes will
      // be broken when a navigationDelegate is set on Android version earlier than N.
      //
      // However, this if statement attempts to avoid using WebViewClientCompat on versions >= N due
      // to bug https://bugs.chromium.org/p/chromium/issues/detail?id=925887. Also, see
      // https://github.com/flutter/flutter/issues/29446.
      if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return new WebViewClientImpl(flutterApi, tmf);
      } else {
        return new WebViewClientCompatImpl(flutterApi, tmf);
      }
    }
  }

  /**
   * Creates a host API that handles creating {@link WebViewClient}s.
   *  @param instanceManager maintains instances stored to communicate with Dart objects
   * @param webViewClientCreator handles creating {@link WebViewClient}s
   * @param flutterApi handles sending messages to Dart
   * @param tmf
   */
  public WebViewClientHostApiImpl(
          InstanceManager instanceManager,
          WebViewClientCreator webViewClientCreator,
          WebViewClientFlutterApiImpl flutterApi, TrustManagerFactory tmf) {
    this.instanceManager = instanceManager;
    this.webViewClientCreator = webViewClientCreator;
    this.flutterApi = flutterApi;
    this.tmf = tmf;
  }

  @Override
  public void create(@NonNull Long instanceId) {
    final WebViewClient webViewClient = webViewClientCreator.createWebViewClient(flutterApi, tmf);
    instanceManager.addDartCreatedInstance(webViewClient, instanceId);
  }

  @Override
  public void setSynchronousReturnValueForShouldOverrideUrlLoading(
      @NonNull Long instanceId, @NonNull Boolean value) {
    final WebViewClient webViewClient =
        Objects.requireNonNull(instanceManager.getInstance(instanceId));
    if (webViewClient instanceof WebViewClientCompatImpl) {
      ((WebViewClientCompatImpl) webViewClient).setReturnValueForShouldOverrideUrlLoading(value);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        && webViewClient instanceof WebViewClientImpl) {
      ((WebViewClientImpl) webViewClient).setReturnValueForShouldOverrideUrlLoading(value);
    } else {
      throw new IllegalStateException(
          "This WebViewClient doesn't support setting the returnValueForShouldOverrideUrlLoading.");
    }
  }
}
