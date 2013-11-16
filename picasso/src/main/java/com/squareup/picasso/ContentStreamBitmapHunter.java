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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;

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
      BitmapFactory.Options options = data.options == null ? picasso.options : data.options;
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
        int fh = 0, fw = 0;
        if (exifRotation == 90 || exifRotation == 270 ) {
          fh = options.outWidth;
          fw = options.outHeight;
        } else {
          fh = options.outHeight;
          fw = options.outWidth;
        }
        rect = data.cropper.crop(fw, fh);
        if (exifRotation != 0) {
/*          Matrix matrix = new Matrix();
          RectF rectF = new RectF(rect.left, rect.top, rect.right, rect.bottom);
          RectF rectAll = new RectF(0, 0, fw, fh);
          matrix.preRotate(exifRotation);
          matrix.mapRect(rectAll);
          matrix.mapRect(rectF);
          rect = new Rect((int) (rectF.left - rectAll.left),
                  (int) (rectF.top - rectAll.top),
                  (int) (rectF.right - rectAll.left),
                  (int) (rectF.bottom - rectAll.top));

          Log.d("picasso", rect.toString() + rectF.toString() + rectAll.toString());*/
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
