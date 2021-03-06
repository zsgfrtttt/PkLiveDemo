/*
 * Copyright (c) 2021 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

package com.netease.yunxin.nertc.demo.user.business;

import android.text.TextUtils;

import com.google.gson.reflect.TypeToken;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.auth.AuthService;
import com.netease.nimlib.sdk.auth.LoginInfo;
import com.netease.yunxin.android.lib.network.common.NetworkClient;
import com.blankj.utilcode.util.ToastUtils;
import com.netease.yunxin.nertc.demo.user.UserCenterServiceNotify;
import com.netease.yunxin.nertc.demo.user.UserModel;
import com.netease.yunxin.nertc.demo.user.network.UserServerImpl;
import com.netease.yunxin.nertc.demo.utils.FileCache;
import com.netease.yunxin.nertc.module.base.sdk.NESdkBase;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by luc on 2020/11/8.
 */

public final class UserBizControl {
    private static final String USER_CACHE_NAME = "user-cache";
    private static final List<UserCenterServiceNotify> observerCache = new ArrayList<>();
    private static final UserCenterServiceNotify USER_STATE_OBSERVER = new UserCenterServiceNotify() {
        @Override
        public void onUserLogin(boolean success, int code) {
            notifyAllRegisteredInfo(notify -> notify.onUserLogin(success, code));
        }

        @Override
        public void onUserLogout(boolean success, int code) {
            notifyAllRegisteredInfo(notify -> notify.onUserLogout(success, code));
        }

        @Override
        public void onError(Throwable exception) {
            notifyAllRegisteredInfo(notify -> notify.onError(exception));
        }

        @Override
        public void onUserInfoUpdate(UserModel model) {
            notifyAllRegisteredInfo(notify -> notify.onUserInfoUpdate(model));
        }
    };

    /**
     * ????????????
     */
    private static UserModel currentUser = null;

    /**
     * ??????/???????????????????????????
     *
     * @param notify   ????????????
     * @param register true ?????????false ?????????
     */
    public static void registerUserStatus(UserCenterServiceNotify notify, boolean register) {
        if (register) {
            observerCache.add(notify);
        } else {
            observerCache.remove(notify);
        }
    }

    /**
     * ????????????????????????????????????????????????
     */
    public static Single<Boolean> tryLogin() {
        // ?????????????????????????????????????????????????????? false????????????????????????????????????
        UserModel userModel =
                FileCache.getCacheValue(NESdkBase.getInstance().getContext(), USER_CACHE_NAME,
                        new TypeToken<UserModel>() {
                        });
        // ??????????????????????????????
        if (userModel == null || TextUtils.isEmpty(userModel.imToken) || userModel.imAccid == 0) {
            return Single.just(false);
        }
        // ?????? im ????????????
        LoginInfo loginInfo = new LoginInfo(String.valueOf(userModel.imAccid), userModel.imToken);
        return loginIM(loginInfo).doOnSuccess(aBoolean -> {
            currentUser = userModel;
            NetworkClient.getInstance().configAccessToken(userModel.accessToken);
        }).retry(1);
    }

    /**
     * ????????????
     *
     * @param phoneNumber ?????????
     * @param verifyCode  ?????????
     */
    public static Single<Boolean> login(String phoneNumber, String verifyCode) {
        return UserServerImpl.loginWithVerifyCode(phoneNumber, verifyCode)
                .doOnSuccess(model -> currentUser = model)
                .map(userModel -> new LoginInfo(String.valueOf(userModel.imAccid), userModel.imToken))
                // ???????????????
                .doOnSuccess(loginInfo -> {
                    FileCache.cacheValue(NESdkBase.getInstance().getContext(), USER_CACHE_NAME, currentUser,
                            new TypeToken<UserModel>() {
                            });
                    NetworkClient.getInstance().configAccessToken(currentUser.accessToken);
                })
                .flatMap((Function<LoginInfo, SingleSource<Boolean>>) UserBizControl::loginIM)
                .doOnSuccess(aBoolean -> {
                    // ??????im ???????????????????????????????????????????????????
                    if (!aBoolean) {
                        currentUser = null;
                    }
                });
    }

    /**
     * ?????? IM ??????
     */
    @SuppressWarnings("all")
    private static Single<Boolean> loginIM(LoginInfo loginInfo) {
        PublishSubject<Boolean> subject = PublishSubject.create();
        NIMClient.getService(AuthService.class)
                .login(loginInfo)
                .setCallback(new RequestCallback<LoginInfo>() {

                    @Override
                    public void onSuccess(LoginInfo param) {
                        subject.onNext(true);
                        subject.onComplete();
                    }

                    @Override
                    public void onFailed(int code) {
                        FileCache.removeCache(NESdkBase.getInstance().getContext(), USER_CACHE_NAME);
                        currentUser = null;
                        ToastUtils.showShort("??????IM ?????????????????? " + code);
                        subject.onNext(false);
                        subject.onComplete();
                    }

                    @Override
                    public void onException(Throwable exception) {
                        subject.onError(exception);
                    }
                });
        return subject.serialize()
                .singleOrError()
                .doOnSuccess(aBoolean -> USER_STATE_OBSERVER.onUserLogin(aBoolean, 0))
                .doOnError(USER_STATE_OBSERVER::onError);
    }

    /**
     * ????????????
     */
    public static Single<Boolean> logout() {
        return Single.create((SingleOnSubscribe<Boolean>) emitter -> {
            try {
                boolean result = FileCache.removeCache(NESdkBase.getInstance().getContext(), USER_CACHE_NAME);
                currentUser = null;
                emitter.onSuccess(result);
            } catch (Exception e) {
                emitter.onError(e);
            }
        }).doOnSuccess(aBoolean -> {
            NIMClient.getService(AuthService.class).logout();
            USER_STATE_OBSERVER.onUserLogout(aBoolean, 0);
        }).doOnError(USER_STATE_OBSERVER::onError);
    }

    /**
     * ??????????????????
     *
     * @param model ????????????
     */
    public static Single<UserModel> updateUserInfo(UserModel model) {
        if (model == null) {
            return Single.error(new Throwable("UserModel is null"));
        }

        return UserServerImpl.updateNickname(model.nickname)
                .doOnSuccess(userModel -> {
                    UserModel backup = userModel.backup();
                    currentUser = backup;
                    FileCache.cacheValue(NESdkBase.getInstance().getContext(), USER_CACHE_NAME, backup,
                            new TypeToken<UserModel>() {
                            });
                    USER_STATE_OBSERVER.onUserInfoUpdate(backup);
                })
                .doOnError(USER_STATE_OBSERVER::onError);
    }

    public static UserModel getUserInfo() {
        return currentUser != null ? currentUser.backup() : null;
    }

    /**
     * ???????????????????????????
     */
    private static void notifyAllRegisteredInfo(NotifyHelper helper) {
        for (UserCenterServiceNotify notify : observerCache) {
            helper.onNotifyAction(notify);
        }
    }

    /**
     * ??????????????????
     */
    private interface NotifyHelper {
        void onNotifyAction(UserCenterServiceNotify notify);
    }
}
