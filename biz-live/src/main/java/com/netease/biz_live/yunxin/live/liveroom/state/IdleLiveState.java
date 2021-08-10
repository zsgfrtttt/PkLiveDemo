/*
 * Copyright (c) 2021 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

package com.netease.biz_live.yunxin.live.liveroom.state;

import com.netease.biz_live.yunxin.live.liveroom.impl.NERTCAnchorInteractionLiveRoomImpl;

public class IdleLiveState extends LiveState {

    public IdleLiveState(NERTCAnchorInteractionLiveRoomImpl liveRoom) {
        super(liveRoom);
        status = STATE_LIVE_ON;
    }

    @Override
    public void callPk() {
        this.liveRoom.setState(liveRoom.getCallOutState());
    }

    @Override
    public void invited() {
        this.liveRoom.setState(liveRoom.getInvitedState());
    }

    @Override
    public void startPk() {

    }

    @Override
    public void accept() {

    }

    @Override
    public void release() {

    }

    @Override
    public void offLive() {
        liveRoom.setState(liveRoom.getOffState());
    }
}
