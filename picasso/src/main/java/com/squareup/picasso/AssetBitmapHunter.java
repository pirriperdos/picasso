package com.squareup.picasso;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;

import static com.squareup.picasso.Picasso.LoadedFrom.DISK;

class AssetBitmapHunter extends BitmapHunter {
  private AssetManager assetManager;

  public AssetBitmapHunter(Context context, Picasso picasso, Dispatcher dispatcher, Cache cache,
      Stats stats, Action action) {
    super(picasso, dispatcher, cache, stats, action);
    assetManager = context.getAssets();
  }

  // assume this is not multi request!!
  @Override Bitmap decode(Request data) throws IOException {
    String filePath = data.uri.toString().substring(ASSET_PREFIX_LENGTH);
    return decodeAsset(filePath);
  }

  @Override Picasso.LoadedFrom getLoadedFrom() {
    return DISK;
  }

  Bitmap decodeAsset(String filePath) throws IOException {
    BitmapFactory.Options options = picasso.options;
    Rect rect = null;
    if (data.hasSize() || data.cropper != null) {
      options = Utils.copyBitmapFactoryOptions(options);
      options.inJustDecodeBounds = true;
      InputStream is = null;
      try {
        is = assetManager.open(filePath);
        BitmapFactory.decodeStream(is, null, options);
      } finally {
        Utils.closeQuietly(is);
      }
      if (data.cropper != null) {
        rect = data.cropper.crop(options.outWidth, options.outHeight);
          if (data.hasSize())
            calculateInSampleSize(data.targetWidth, data.targetHeight, options, rect.width(), rect.height());
      } else if (data.hasSize())
        calculateInSampleSize(data.targetWidth, data.targetHeight, options);
    }
    InputStream is = assetManager.open(filePath);
    try {
        if (data.cropper != null) return BitmapRegionDecoder.newInstance(is, false).decodeRegion(rect, options);
        else return BitmapFactory.decodeStream(is, null, options);
    } finally {
      Utils.closeQuietly(is);
    }
  }
}
