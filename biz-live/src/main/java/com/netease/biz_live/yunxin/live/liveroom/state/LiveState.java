/*
 * Copyright (c) 2021 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

package com.netease.biz_live.yunxin.live.liveroom.state;


import com.netease.biz_live.yunxin.live.liveroom.impl.NERTCAnchorInteractionLiveRoomImpl;

public abstract class LiveState {

    /**
     * 直播未开始（初始状态）
     */
    public static final int STATE_LIVE_OFF = -1;

    /**
     * 单主播直播状态
     */
    public static final int STATE_LIVE_ON = 0;

    /**
     * 呼出状态
     */
    public static final int STATE_CALL_OUT = 1;

    /**
     * 收到邀请
     */
    public static final int STATE_INVITED = 2;

    /**
     * PK 中
     */
    public static final int STATE_PKING = 3;

    /**
     * 邀请已经接受，但PK未开始
     */
    public static final int STATE_ACCEPTED = 4;

    protected int status;

    protected NERTCAnchorInteractionLiveRoomImpl liveRoom;

    public LiveState(NERTCAnchorInteractionLiveRoomImpl liveRoom) {
        this.liveRoom = liveRoom;
    }

    public int getStatus(){
        return status;
    }

    public abstract void callPk();

    public abstract void invited();

    public abstract void startPk();

    public abstract void accept();

    public abstract void release();

    public abstract void offLive();
}
