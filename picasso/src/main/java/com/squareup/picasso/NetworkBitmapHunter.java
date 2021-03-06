/*
 * Copyright (C) 2013 Square, Inc.
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
package com.squareup.picasso;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.NetworkInfo;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Downloader.Response;
import static com.squareup.picasso.Picasso.LoadedFrom.DISK;
import static com.squareup.picasso.Picasso.LoadedFrom.NETWORK;

class NetworkBitmapHunter extends BitmapHunter {
  static final int DEFAULT_RETRY_COUNT = 2;
  private static final int MARKER = 65536;

  private final Downloader downloader;

  int retryCount;
  final boolean onlyLocal;

  public NetworkBitmapHunter(Picasso picasso, Dispatcher dispatcher, Cache cache, Stats stats,
      Action action, Downloader downloader) {
    super(picasso, dispatcher, cache, stats, action);
    this.downloader = downloader;
    this.onlyLocal = action.onlyLocal;
    this.retryCount = DEFAULT_RETRY_COUNT;
  }

  private Bitmap decode(Uri uri, boolean localOnly) throws IOException {
      Response response = downloader.load(uri, localOnly);
      if (response == null) {
          return null;
      }

      loadedFrom = response.cached ? DISK : NETWORK;

      Bitmap result = response.getBitmap();
      if (result != null) {
          return result;
      }

      InputStream is = response.getInputStream();
      try {
          return decodeStream(is, data);
      } finally {
          Utils.closeQuietly(is);
      }
  }

  private int networkLevel = Utils.NETWORK_WIFI;
  @Override Bitmap decode(Request data) throws IOException {
    boolean loadFromLocalCacheOnly = retryCount == 0 || onlyLocal;
    Bitmap bitmap = null;
    if (data.uris != null) {
      for (int i = Math.min(Utils.NETWORK_WIFI, data.uris.length - 1); i >= 0; i--) {
        if (data.uris[i] == null)
          continue;
        bitmap = decode(data.uris[i], loadFromLocalCacheOnly || i > Picasso.NETWORK_LEVEL);
        if (bitmap != null) {
          networkLevel = i;
          return bitmap;
        }
        if (i <= Picasso.NETWORK_LEVEL)
          break;
      }
    } else {
      return decode(data.uri, loadFromLocalCacheOnly);
    }
    return null;
  }


  @Override protected int getNetworkLevel() {
    return networkLevel;
  }

  @Override boolean shouldRetry(boolean airplaneMode, NetworkInfo info) {
    boolean hasRetries = retryCount > 0;
    if (!hasRetries) {
      return false;
    }
    retryCount--;
    return info == null || info.isConnectedOrConnecting();
  }

  private Bitmap decodeStream(InputStream stream, Request data) throws IOException {
    if (stream == null) {
      return null;
    }
      BitmapFactory.Options options = data.options == null ? picasso.options : data.options;
    Rect rect = null;
    if (data.hasSize() || data.cropper != null) {
      options = Utils.copyBitmapFactoryOptions(options);
      options.inJustDecodeBounds = true;

      MarkableInputStream markStream = new MarkableInputStream(stream);
      stream = markStream;

      long mark = markStream.savePosition(MARKER);
      BitmapFactory.decodeStream(stream, null, options);
      if (data.cropper != null) {
        rect = data.cropper.crop(options.outWidth, options.outHeight);
        if (data.hasSize())
          calculateInSampleSize(data.targetWidth, data.targetHeight, options, rect.width(), rect.height());
      } else if (data.hasSize())
        calculateInSampleSize(data.targetWidth, data.targetHeight, options);

      markStream.reset(mark);
    }

    if (data.cropper != null) return BitmapRegionDecoder.newInstance(stream, false).decodeRegion(rect, options);
    else return BitmapFactory.decodeStream(stream, null, options);
  }
}
