/*
 * Copyright (c) 2021 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

package com.netease.yunxin.nertc.demo.feedback.network;

import com.netease.yunxin.android.lib.network.common.BaseResponse;
import com.netease.yunxin.android.lib.network.common.NetworkClient;
import com.netease.yunxin.nertc.demo.basic.BuildConfig;
import com.netease.yunxin.nertc.demo.user.UserModel;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by luc on 2020/11/16.
 */
public class FeedbackServiceImpl {

    /**
     * 用户中心意见反馈
     *
     * @param model       用户信息
     * @param demoName    demo 名称
     * @param content     反馈内容
     * @param contentType 反馈类型数组
     */
    public static Single<Boolean> demoSuggest(UserModel model, String demoName, String content, int... contentType) {
        FeedbackServiceApi api = NetworkClient.getInstance().getService(FeedbackServiceApi.class);
        Map<String, Object> map = new HashMap<>();
        map.put("tel", model.mobile);
        map.put("uid", model.accountId);
        map.put("contact", model.mobile);
        map.put("content_type", contentType);
        map.put("feedback_source", demoName);
        map.put("content", content);
        map.put("type", 1);
        map.put("appkey", BuildConfig.APP_KEY);
        return api.demoSuggest(map).map(BaseResponse::isSuccessful)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}
