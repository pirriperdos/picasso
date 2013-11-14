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

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.*;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class ContentStreamBitmapHunter extends BitmapHunter {
  final Context context;

  ContentStreamBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(picasso, dispatcher, cache, stats, action);
    this.context = context;
  }

  @Override Bitmap decode(Request data)
      throws IOException {
    return decodeContentStream(data);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  protected Bitmap decodeContentStream(Request data) throws IOException {
    ContentResolver contentResolver = context.getContentResolver();
    BitmapFactory.Options options = picasso.options;
    Rect rect = null;
    if (data.hasSize() || data.cropper != null) {
      options = Utils.copyBitmapFactoryOptions(options);
      options.inJustDecodeBounds = true;
      InputStream is = null;
      try {
        is = contentResolver.openInputStream(data.uri);
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }

      if (data.cropper != null) {
        if (exifRotation == 90 || exifRotation == 180 )
          rect = data.cropper.crop(options.outHeight, options.outWidth);
        else
          rect = data.cropper.crop(options.outWidth, options.outHeight);
        if (exifRotation != 0) {
          Matrix matrix = new Matrix();
          RectF rectF = new RectF(rect.left, rect.top, rect.right, rect.bottom);
          Log.d("picasso", "returned rect: " +  rect.toString());
          matrix.postRotate(exifRotation);
          matrix.mapRect(rectF);
          rect = new Rect((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
          if (rect.left < 0) rect.left += options.outWidth;
          if (rect.right < 0) rect.right += options.outWidth;
          if (rect.top < 0) rect.top += options.outHeight;
          if (rect.bottom < 0) rect.bottom += options.outHeight;
          Log.d("picasso", "maped rect: " + rect.toString());
          Log.d("picasso", "OriBitmapWidth: " + options.outWidth + ", OriBitmapHeight: " + options.outHeight + ", exifRotation: " + exifRotation);
        }
        if (data.hasSize())
          calculateInSampleSize(data.targetWidth, data.targetHeight, options, rect.width(), rect.height());
      } else if (data.hasSize())
        calculateInSampleSize(data.targetWidth, data.targetHeight, options);
    }
    InputStream is = contentResolver.openInputStream(data.uri);
    try {
      if (data.cropper != null) return BitmapRegionDecoder.newInstance(is, false).decodeRegion(rect, options);
      else return BitmapFactory.decodeStream(is, null, options);
    } finally {
      Utils.closeQuietly(is);
    }
  }
}
