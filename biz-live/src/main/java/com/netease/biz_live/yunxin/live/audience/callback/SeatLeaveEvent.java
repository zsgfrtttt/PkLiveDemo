/*
 * Copyright (c) 2021 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

package com.netease.biz_live.yunxin.live.audience.callback;

import com.netease.biz_live.yunxin.live.model.SeatMemberInfo;

public class SeatLeaveEvent {
    public int index;
    public SeatMemberInfo member;
    /**
     * 0: 正常，1: 被踢，2: 断线 {@link com.netease.biz_live.yunxin.live.constant.SeatLeftReason}
     */
    public int reason;

    public SeatLeaveEvent(int index, SeatMemberInfo member, int reason) {
        this.index = index;
        this.member = member;
        this.reason = reason;
    }
}
