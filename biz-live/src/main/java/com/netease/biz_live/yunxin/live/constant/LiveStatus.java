/*
 * Copyright (c) 2021 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

package com.netease.biz_live.yunxin.live.constant;

/**
 * Created by luc on 2020/11/27.
 * <p>
 * 直播状态
 */
public @interface LiveStatus {
    /**
     * 未开始
     */
    int NO_START = 0;
    /**
     * 直播中
     */
    int LIVING = 1;
    /**
     * pk 直播中
     */
    int PK_LIVING = 2;
    /**
     * pk 直播结束
     */
    int PK_LIVING_FINISHED = 3;
    /**
     * 直播结束
     */
    int LIVING_FINISHED = 4;
    /**
     * 正在惩罚
     */
    int PK_PUNISHMENT = 5;
    /**
     * 多人互动连麦中
     */
    int MULTI_MIC = 6;
}
