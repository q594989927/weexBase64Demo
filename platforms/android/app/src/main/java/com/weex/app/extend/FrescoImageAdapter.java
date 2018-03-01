package com.weex.app.extend;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.internal.Preconditions;
import com.facebook.common.logging.FLog;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.BaseDataSubscriber;
import com.facebook.datasource.DataSource;
import com.facebook.datasource.DataSubscriber;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.controller.ControllerListener;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.DraweeView;
import com.facebook.imagepipeline.common.ImageDecodeOptions;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.image.CloseableStaticBitmap;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.image.QualityInfo;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.taobao.weex.WXSDKManager;
import com.taobao.weex.adapter.IWXImgLoaderAdapter;
import com.taobao.weex.common.WXImageStrategy;
import com.taobao.weex.devtools.common.LogUtil;
import com.taobao.weex.dom.WXImageQuality;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author cai.pengrui on 2018/3/1.
 * @Package com.weex.app.extend
 * @Title: FrescoImageAdapter
 * @Description: (用一句话描述该文件做什么)
 * Copyright (c) 传化公路港物流有限公司版权所有 2017<br>
 * Create DateTime: 2018/3/1<br>
 */
public class FrescoImageAdapter implements IWXImgLoaderAdapter {
    private LruCache<String,Bitmap> bankCardLruCache = new LruCache<>(3*1024*1024);
    public FrescoImageAdapter() {
    }

    @Override
    public void setImage(final String url, final ImageView view,
                         WXImageQuality quality, WXImageStrategy strategy) {

        WXSDKManager.getInstance().postOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (view == null || view.getLayoutParams() == null) {
                    return;
                }
                if (TextUtils.isEmpty(url)) {
                    view.setImageBitmap(null);
                    return;
                }
                String temp = url;
                Uri uri = Uri.parse(url);
                if (url.startsWith("//")) {
                    temp = "http:" + url;

                    uri = Uri.parse(temp);
                }else if(url.startsWith("/")){
                    uri = Uri.parse("asset://" + url);
                    if(view.getContext()!=null) {
                        if(bankCardLruCache.get(url)==null){
                            InputStream inputStream = null;
                            try {
                                inputStream = view.getContext().getAssets().open(url.substring(1));
                                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                                bankCardLruCache.put(url,bitmap);
                                view.setImageBitmap(bitmap);
                                return;
                            } catch (IOException e) {
//                                LogUtil.LogThrowable("Fresco",e);
                            }
                        }else{
                            view.setImageBitmap(bankCardLruCache.get(url));
                            return;
                        }
                    }
                }
                if (view.getLayoutParams().width <= 0 || view.getLayoutParams().height <= 0) {
                    return;
                }



                ImageDecodeOptions decodeOptions = ImageDecodeOptions.newBuilder()
                        .build();

                ImageRequest request = ImageRequestBuilder
                        .newBuilderWithSource(uri)
                        .setImageDecodeOptions(decodeOptions)
                        .setAutoRotateEnabled(true)
                        .setLocalThumbnailPreviewsEnabled(true)
                        .setLowestPermittedRequestLevel(ImageRequest.RequestLevel.FULL_FETCH)
                        .setProgressiveRenderingEnabled(false)
                        .build();

                if(view instanceof DraweeView){
                    Log.d("FrescoImageAdapter","load: "+url);
                    ControllerListener controllerListener = new BaseControllerListener<ImageInfo>() {
                        @Override
                        public void onFinalImageSet(
                                String id,
                                @Nullable ImageInfo imageInfo,
                                @Nullable Animatable anim) {
                            if (imageInfo == null) {
                                return;
                            }
                            QualityInfo qualityInfo = imageInfo.getQualityInfo();
                            FLog.d("Final image received! " +
                                            "Size %d x %d",
                                    "Quality level %d, good enough: %s, full quality: %s",
                                    imageInfo.getWidth(),
                                    imageInfo.getHeight(),
                                    qualityInfo.getQuality(),
                                    qualityInfo.isOfGoodEnoughQuality(),
                                    qualityInfo.isOfFullQuality());
                        }

                        @Override
                        public void onIntermediateImageSet(String id, @Nullable ImageInfo imageInfo) {
                            FLog.d("","Intermediate image received");
                        }

                        @Override
                        public void onFailure(String id, Throwable throwable) {
                            FLog.e(getClass(), throwable, "Error loading %s", id);
                        }
                    };
                    DraweeController controller = Fresco.newDraweeControllerBuilder()
                            .setAutoPlayAnimations(true)
                            .setControllerListener(controllerListener)
                            .setUri(uri)
                            .setImageRequest(request)
                            .build();
                    ((DraweeView)view).setController(controller);

                }else {
                    ImagePipeline imagePipeline = Fresco.getImagePipeline();
                    DataSource<CloseableReference<CloseableImage>>
                            dataSource = imagePipeline.fetchDecodedImage(request, new Object());
                    DataSubscriber dataSubscriber =
                            new BaseDataSubscriber<CloseableReference<CloseableImage>>() {
                                @Override
                                public void onNewResultImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {

                                    CloseableReference<CloseableImage> imageReference = dataSource.getResult();
                                    if (imageReference != null) {
                                        try {
                                            // do something with the image
                                            Preconditions.checkState(CloseableReference.isValid(imageReference));
                                            CloseableImage closeableImage = imageReference.get();
                                            if (closeableImage instanceof CloseableStaticBitmap) {
                                                CloseableStaticBitmap closeableStaticBitmap = (CloseableStaticBitmap) closeableImage;
                                                view.setImageBitmap(closeableStaticBitmap.getUnderlyingBitmap());
                                                // boolean hasResult =  null != closeableStaticBitmap.getUnderlyingBitmap();
                                            } else {
                                                throw new UnsupportedOperationException("Unrecognized image class: " + closeableImage);
                                            }
                                        } finally {
                                            imageReference.close();
                                        }
                                    }
                                }

                                @Override
                                public void onFailureImpl(DataSource dataSource) {
//                                    com.transfar.log.LogUtil.LogE("imageAdapter",dataSource.getFailureCause().getMessage());
                                }
                            };

                    dataSource.subscribe(dataSubscriber, UiThreadImmediateExecutorService.getInstance());
                }
            }
        }, 0);
    }
}
