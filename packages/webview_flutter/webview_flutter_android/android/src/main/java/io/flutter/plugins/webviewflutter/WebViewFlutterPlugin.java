// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.content.Context;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.TrustManagerFactory;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.platform.PlatformViewRegistry;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.CookieManagerHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.DownloadListenerHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.FlutterAssetManagerHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.JavaObjectHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.JavaScriptChannelHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.WebChromeClientHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.WebSettingsHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.WebStorageHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.WebViewClientHostApi;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.WebViewHostApi;

/**
 * Java platform implementation of the webview_flutter plugin.
 *
 * <p>Register this in an add to app scenario to gracefully handle activity and context changes.
 *
 * <p>Call {@link #registerWith} to use the stable {@code io.flutter.plugin.common} package instead.
 */
public class WebViewFlutterPlugin implements FlutterPlugin, ActivityAware {
  @Nullable private InstanceManager instanceManager;

  private FlutterPluginBinding pluginBinding;
  private WebViewHostApiImpl webViewHostApi;
  private JavaScriptChannelHostApiImpl javaScriptChannelHostApi;

  /**
   * Add an instance of this to {@link io.flutter.embedding.engine.plugins.PluginRegistry} to
   * register it.
   *
   * <p>THIS PLUGIN CODE PATH DEPENDS ON A NEWER VERSION OF FLUTTER THAN THE ONE DEFINED IN THE
   * PUBSPEC.YAML. Text input will fail on some Android devices unless this is used with at least
   * flutter/flutter@1d4d63ace1f801a022ea9ec737bf8c15395588b9. Use the V1 embedding with {@link
   * #registerWith} to use this plugin with older Flutter versions.
   *
   * <p>Registration should eventually be handled automatically by v2 of the
   * GeneratedPluginRegistrant. https://github.com/flutter/flutter/issues/42694
   */
  public WebViewFlutterPlugin() {}

  /**
   * Registers a plugin implementation that uses the stable {@code io.flutter.plugin.common}
   * package.
   *
   * <p>Calling this automatically initializes the plugin. However plugins initialized this way
   * won't react to changes in activity or context, unlike {@link WebViewFlutterPlugin}.
   */
  @SuppressWarnings({"unused", "deprecation"})
  public static void registerWith(io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
    new WebViewFlutterPlugin()
        .setUp(
            registrar.messenger(),
            registrar.platformViewRegistry(),
            registrar.activity(),
            registrar.view(),
            new FlutterAssetManager.RegistrarFlutterAssetManager(
                registrar.context().getAssets(), registrar));
  }

  private InputStream fromPem(String pem) {
    String base64cert = pemKeyContent(pem);
    return fromBase64String(base64cert);
  }

  private InputStream fromBase64String(String base64cert) {
    byte[] decoded = Base64.decode(base64cert, Base64.NO_WRAP);
    return new ByteArrayInputStream(decoded);
  }

  private String pemKeyContent(String pem) {
    return pem.replace("\\s+", "")
            .replace("\n", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "");
  }

  private InputStream readPemCert(String certName, Context context) {
    return fromPem(getPemAsString(certName, context));
  }

  private String getPemAsString(String certName, Context context) {
    InputStream ins = context.getResources().openRawResource(
            context.getResources().getIdentifier(
                    certName, "raw", context.getPackageName()));
    StringBuilder textBuilder = new StringBuilder();
    try (Reader reader = new BufferedReader(new InputStreamReader
            (ins, Charset.forName(StandardCharsets.UTF_8.name())))) {
      int c = 0;
      while ((c = reader.read()) != -1) {
        textBuilder.append((char) c);
      }
    } catch (IOException e) {
      Log.d("WEB_VIEW_EXAMPLE", "read pem error");
    }
    return textBuilder.toString();
  }



  private TrustManagerFactory initTrustStore(Context context) throws Exception {
    try {

      String ROOT_CERTIFICATE = "root";
      String SUB_CERTIFICATE = "sub";
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      InputStream subIns = readPemCert(SUB_CERTIFICATE, context);
      Certificate sub = cf.generateCertificate(subIns);
      InputStream rootIns = readPemCert(ROOT_CERTIFICATE, context);
      Certificate root = cf.generateCertificate(rootIns);
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      keyStore.setCertificateEntry(SUB_CERTIFICATE, sub);
      keyStore.setCertificateEntry(ROOT_CERTIFICATE, root);
      String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
      tmf.init(keyStore);
      return tmf;
    } catch (Exception e) {
      throw new Exception("Error during TrustManagerFactory initialization");
    }
  }

  private void setUp(
      BinaryMessenger binaryMessenger,
      PlatformViewRegistry viewRegistry,
      Context context,
      View containerView,
      FlutterAssetManager flutterAssetManager) {
    instanceManager =
        InstanceManager.open(
            identifier ->
                new GeneratedAndroidWebView.JavaObjectFlutterApi(binaryMessenger)
                    .dispose(identifier, reply -> {}));

    viewRegistry.registerViewFactory(
        "plugins.flutter.io/webview", new FlutterWebViewFactory(instanceManager));

    webViewHostApi =
        new WebViewHostApiImpl(
            instanceManager,
            binaryMessenger,
            new WebViewHostApiImpl.WebViewProxy(),
            context,
            containerView);
    javaScriptChannelHostApi =
        new JavaScriptChannelHostApiImpl(
            instanceManager,
            new JavaScriptChannelHostApiImpl.JavaScriptChannelCreator(),
            new JavaScriptChannelFlutterApiImpl(binaryMessenger, instanceManager),
            new Handler(context.getMainLooper()));

    JavaObjectHostApi.setup(binaryMessenger, new JavaObjectHostApiImpl(instanceManager));
    WebViewHostApi.setup(binaryMessenger, webViewHostApi);
    JavaScriptChannelHostApi.setup(binaryMessenger, javaScriptChannelHostApi);

    TrustManagerFactory tmf = null;
    try {
      tmf = initTrustStore(context);
    } catch (Exception e) {
      Log.d("WEB_VIEW_EXAMPLE", e.getMessage());
    }

    WebViewClientHostApi.setup(
        binaryMessenger,
        new WebViewClientHostApiImpl(
            instanceManager,
            new WebViewClientHostApiImpl.WebViewClientCreator(),
            new WebViewClientFlutterApiImpl(binaryMessenger, instanceManager), tmf));
    WebChromeClientHostApi.setup(
        binaryMessenger,
        new WebChromeClientHostApiImpl(
            instanceManager,
            new WebChromeClientHostApiImpl.WebChromeClientCreator(),
            new WebChromeClientFlutterApiImpl(binaryMessenger, instanceManager)));
    DownloadListenerHostApi.setup(
        binaryMessenger,
        new DownloadListenerHostApiImpl(
            instanceManager,
            new DownloadListenerHostApiImpl.DownloadListenerCreator(),
            new DownloadListenerFlutterApiImpl(binaryMessenger, instanceManager)));
    WebSettingsHostApi.setup(
        binaryMessenger,
        new WebSettingsHostApiImpl(
            instanceManager, new WebSettingsHostApiImpl.WebSettingsCreator()));
    FlutterAssetManagerHostApi.setup(
        binaryMessenger, new FlutterAssetManagerHostApiImpl(flutterAssetManager));
    CookieManagerHostApi.setup(binaryMessenger, new CookieManagerHostApiImpl());
    WebStorageHostApi.setup(
        binaryMessenger,
        new WebStorageHostApiImpl(instanceManager, new WebStorageHostApiImpl.WebStorageCreator()));
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    pluginBinding = binding;
    setUp(
        binding.getBinaryMessenger(),
        binding.getPlatformViewRegistry(),
        binding.getApplicationContext(),
        null,
        new FlutterAssetManager.PluginBindingFlutterAssetManager(
            binding.getApplicationContext().getAssets(), binding.getFlutterAssets()));
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (instanceManager != null) {
      instanceManager.close();
      instanceManager = null;
    }
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
    updateContext(activityPluginBinding.getActivity());
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    updateContext(pluginBinding.getApplicationContext());
  }

  @Override
  public void onReattachedToActivityForConfigChanges(
      @NonNull ActivityPluginBinding activityPluginBinding) {
    updateContext(activityPluginBinding.getActivity());
  }

  @Override
  public void onDetachedFromActivity() {
    updateContext(pluginBinding.getApplicationContext());
  }

  private void updateContext(Context context) {
    webViewHostApi.setContext(context);
    javaScriptChannelHostApi.setPlatformThreadHandler(new Handler(context.getMainLooper()));
  }

  /** Maintains instances used to communicate with the corresponding objects in Dart. */
  @Nullable
  public InstanceManager getInstanceManager() {
    return instanceManager;
  }
}
