/*
 * Copyright (c) 2021 NetEase, Inc.  All rights reserved.
 * Use of this source code is governed by a MIT license that can be found in the LICENSE file.
 */

package com.netease.biz_live.yunxin.live.chatroom.control;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.netease.biz_live.yunxin.live.chatroom.ChatRoomMsgCreator;
import com.netease.biz_live.yunxin.live.chatroom.custom.AnchorCoinChangedAttachment;
import com.netease.biz_live.yunxin.live.chatroom.custom.LiveAttachParser;
import com.netease.biz_live.yunxin.live.chatroom.custom.PKStatusAttachment;
import com.netease.biz_live.yunxin.live.chatroom.custom.PunishmentStatusAttachment;
import com.netease.biz_live.yunxin.live.chatroom.custom.TextWithRoleAttachment;
import com.netease.biz_live.yunxin.live.chatroom.model.AudienceInfo;
import com.netease.biz_live.yunxin.live.chatroom.model.LiveChatRoomInfo;
import com.netease.biz_live.yunxin.live.chatroom.model.RewardGiftInfo;
import com.netease.biz_live.yunxin.live.chatroom.model.RoomMsg;
import com.netease.biz_live.yunxin.live.gift.GiftCache;
import com.netease.biz_live.yunxin.live.gift.GiftInfo;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.chatroom.ChatRoomMessageBuilder;
import com.netease.nimlib.sdk.chatroom.ChatRoomService;
import com.netease.nimlib.sdk.chatroom.ChatRoomServiceObserver;
import com.netease.nimlib.sdk.chatroom.constant.MemberQueryType;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomInfo;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomKickOutEvent;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomNotificationAttachment;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomData;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomResultData;
import com.netease.nimlib.sdk.msg.MsgService;
import com.netease.nimlib.sdk.msg.attachment.MsgAttachment;
import com.netease.nimlib.sdk.msg.constant.SessionTypeEnum;
import com.netease.yunxin.kit.alog.ALog;
import com.netease.yunxin.nertc.demo.user.UserCenterService;
import com.netease.yunxin.nertc.demo.user.UserModel;
import com.netease.yunxin.nertc.module.base.ModuleServiceMgr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Created by luc on 2020/R11/18.
 * <p>
 * ?????????????????????
 */
final class ChatRoomControl {
    /**
     * ??????????????????
     */
    private static final String EXTENSION_KEY_ACCOUNT_ID = "accountId";
    /**
     * ????????????????????????????????????
     */
    private static final int JOIN_ROOM_RETRY_COUNT = 1;

    /**
     * ???????????? 3s
     */
    private static final long HANDLER_DELAY_TIME = 3000;

    /**
     * handler ?????????????????????????????????
     */
    private static final int MSG_TYPE_ANCHOR_LEAVE = 1;

    private static final Handler DELAY_HANDLER = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what != MSG_TYPE_ANCHOR_LEAVE) {
                return;
            }
            ChatRoomControl.getInstance().isAnchorOnline = false;
            ChatRoomControl.getInstance().chatRoomNotify.onAnchorLeave();
        }
    };

    /**
     * ??????????????????IM SDK???
     */
    private final ChatRoomService chatRoomService = NIMClient.getService(ChatRoomService.class);
    /**
     * ????????????
     */
    private final UserCenterService userCenterService = ModuleServiceMgr.getInstance().getService(UserCenterService.class);
    /**
     * ?????????????????????????????????
     */
    private final List<ChatRoomNotify> notifyList = new ArrayList<>(4);

    /**
     * ????????????????????????????????????
     */
    private final ChatRoomNotify chatRoomNotify = new ChatRoomNotify() {

        @Override
        public void onJoinRoom(boolean success, int code) {
            notifyAllRegisteredInfo(notify -> notify.onJoinRoom(success, code));
        }

        @Override
        public void onRoomDestroyed(LiveChatRoomInfo roomInfo) {
            notifyAllRegisteredInfo(notify -> notify.onRoomDestroyed(roomInfo));
        }

        @Override
        public void onAnchorLeave() {
            notifyAllRegisteredInfo(ChatRoomNotify::onAnchorLeave);
        }

        @Override
        public void onKickedOut() {
            notifyAllRegisteredInfo(ChatRoomNotify::onKickedOut);
        }

        @Override
        public void onMsgArrived(RoomMsg msg) {
            notifyAllRegisteredInfo(notify -> notify.onMsgArrived(msg));
        }

        @Override
        public void onGiftArrived(RewardGiftInfo giftInfo) {
            notifyAllRegisteredInfo(notify -> notify.onGiftArrived(giftInfo));
        }

        @Override
        public void onUserCountChanged(int count) {
            notifyAllRegisteredInfo(notify -> notify.onUserCountChanged(count));
        }

        @Override
        public void onAnchorCoinChanged(AnchorCoinChangedAttachment attachment) {
            notifyAllRegisteredInfo(notify -> notify.onAnchorCoinChanged(attachment));
        }

        @Override
        public void onPkStatusChanged(PKStatusAttachment pkStatus) {
            notifyAllRegisteredInfo(notify -> notify.onPkStatusChanged(pkStatus));
        }

        @Override
        public void onPunishmentStatusChanged(PunishmentStatusAttachment punishmentStatus) {
            notifyAllRegisteredInfo(notify -> notify.onPunishmentStatusChanged(punishmentStatus));
        }
    };


    /**
     * ??????????????????????????????IM SDK???
     */
    private final Observer<List<ChatRoomMessage>> chatRoomMsgObserver = new Observer<List<ChatRoomMessage>>() {
        @Override
        public void onEvent(List<ChatRoomMessage> chatRoomMessages) {
            if (chatRoomMessages == null || chatRoomMessages.isEmpty()) {
                return;
            }
            if (roomInfo == null) {
                return;
            }

            final String roomId = roomInfo.roomId;

            for (ChatRoomMessage message : chatRoomMessages) {
                // ????????????????????????????????????
                if ((message.getSessionType() != SessionTypeEnum.ChatRoom) ||
                        !roomId.equals(message.getSessionId())) {
                    continue;
                }
                // ??????????????????????????????????????????/??????????????????
                MsgAttachment attachment = message.getAttachment();
                if (attachment instanceof ChatRoomNotificationAttachment) {
                    onNotification((ChatRoomNotificationAttachment) attachment);
                    continue;
                }
                // pk????????????
                if (attachment instanceof PKStatusAttachment) {
                    onPkState((PKStatusAttachment) attachment);
                    continue;
                }
                // ??????????????????
                if (attachment instanceof PunishmentStatusAttachment) {
                    onPunishmentState((PunishmentStatusAttachment) attachment);
                    continue;
                }
                // ??????????????????????????????
                if (attachment instanceof AnchorCoinChangedAttachment) {
                    onAnchorCoinChanged((AnchorCoinChangedAttachment) attachment);
                    continue;
                }
                // ???????????????????????????
                if (attachment instanceof TextWithRoleAttachment) {
                    onTextMsg(message.getChatRoomMessageExtension().getSenderNick(), (TextWithRoleAttachment) attachment);
                    continue;
                }
            }
        }
    };

    /**
     * ?????????????????????
     */
    private final Observer<ChatRoomKickOutEvent> chatRoomKickOutEventObserver = new Observer<ChatRoomKickOutEvent>() {
        @Override
        public void onEvent(ChatRoomKickOutEvent chatRoomKickOutEvent) {
            if (chatRoomKickOutEvent == null) {
                return;
            }
            if (roomInfo == null) {
                return;
            }
            if (!roomInfo.roomId.equals(chatRoomKickOutEvent.getRoomId())) {
                return;
            }

            if (chatRoomKickOutEvent.getReason().getValue() == MemberKickedReasonType.KICK_OUT_BY_CONFLICT_LOGIN) {
                chatRoomNotify.onKickedOut();
            } else {
                chatRoomNotify.onAnchorLeave();
            }
        }
    };

    /**
     * ?????????????????????
     */
    private LiveChatRoomInfo roomInfo;

    /**
     * ?????? im ??????id
     */
    private String anchorImAccId;

    /**
     * ??????????????????
     */
    private boolean isAnchorOnline = false;

    /**
     * ??????????????????release
     */
    private boolean isReleased = true;


    //------------------- ??????
    private ChatRoomControl() {
    }

    private static final class Holder {
        private static final ChatRoomControl INSTANCE = new ChatRoomControl();
    }

    static ChatRoomControl getInstance() {
        return Holder.INSTANCE;
    }
    //--------------------/ ??????


    /**
     * ???????????????
     *
     * @param roomInfo ?????????????????????
     */
    public void joinRoom(LiveChatRoomInfo roomInfo) {
        ALog.e("====>", "joinRoom");
        DELAY_HANDLER.removeMessages(MSG_TYPE_ANCHOR_LEAVE);
        // ??????????????????????????????
        if (this.roomInfo != null) {
            chatRoomService.exitChatRoom(this.roomInfo.roomId);
        }
        resetAllStates();
        this.roomInfo = roomInfo;
        // ????????????????????????????????????
        NIMClient.getService(MsgService.class).registerCustomAttachmentParser(new LiveAttachParser());
        // ???????????????????????????
        listen(true);

        // ???????????????????????????????????????????????????????????????????????????
        UserModel user = getCurrentUser();
        EnterChatRoomData chatRoomData = new EnterChatRoomData(roomInfo.roomId);
        chatRoomData.setNick(user.getNickname());
        chatRoomData.setAvatar(user.avatar);

        Map<String, Object> extension = new HashMap<>();
        extension.put(EXTENSION_KEY_ACCOUNT_ID, user.accountId);
        chatRoomData.setExtension(extension);
        // ?????????????????????????????????????????????????????? JOIN_ROOM_RETRY_COUNT
        //noinspection unchecked
        chatRoomService.enterChatRoomEx(chatRoomData, JOIN_ROOM_RETRY_COUNT)
                .setCallback(new RequestCallback<EnterChatRoomResultData>() {
                    @Override
                    public void onSuccess(EnterChatRoomResultData param) {
                        if (isReleased) {
                            return;
                        }
                        chatRoomNotify.onJoinRoom(true, 0);
                        queryRoomInfoAndNotify();
                    }

                    @Override
                    public void onFailed(int code) {
                        chatRoomNotify.onJoinRoom(false, code);
                    }

                    @Override
                    public void onException(Throwable exception) {
                        chatRoomNotify.onJoinRoom(false, -1);
                    }
                });
        isReleased = false;
    }

    /**
     * ????????????????????????
     */
    private void queryRoomInfoAndNotify() {
        chatRoomService.fetchRoomInfo(roomInfo.roomId).setCallback(new RequestCallback<ChatRoomInfo>() {
            @Override
            public void onSuccess(ChatRoomInfo param) {
                if (isReleased) {
                    return;
                }
                if (roomInfo == null || param == null) {
                    return;
                }
                // ???????????? ?????? imAccId
                anchorImAccId = param.getCreator();
                roomInfo.setOnlineUserCount(param.getOnlineUserCount());
                // ????????????????????????
                queryAnchorOnlineStatusAndNotify(anchorImAccId);
            }

            @Override
            public void onFailed(int code) {
            }

            @Override
            public void onException(Throwable exception) {
            }
        });
    }

    /**
     * ????????????????????????
     *
     * @param anchorImAccId ???????????? im id
     */
    private void queryAnchorOnlineStatusAndNotify(String anchorImAccId) {
        if (isAnchor(String.valueOf(getCurrentUser().imAccid))) {
            isAnchorOnline = true;
        }
        chatRoomService.fetchRoomMembersByIds(roomInfo.roomId, Collections.singletonList(anchorImAccId))
                .setCallback(new RequestCallback<List<ChatRoomMember>>() {
                    @Override
                    public void onSuccess(List<ChatRoomMember> param) {
                        if (isReleased) {
                            return;
                        }
                        // ?????????????????????????????????
                        isAnchorOnline = !param.isEmpty() && param.get(0).isOnline();
                        if (!isAnchorOnline) {
                            chatRoomNotify.onAnchorLeave();
                        }
                        notifyUserCountChanged();
                    }

                    @Override
                    public void onFailed(int code) {
                        isAnchorOnline = false;
                        chatRoomNotify.onAnchorLeave();
                    }

                    @Override
                    public void onException(Throwable exception) {
                        isAnchorOnline = false;
                        chatRoomNotify.onAnchorLeave();
                    }
                });
    }

    /**
     * ???????????????
     */
    public void leaveRoom() {
        ALog.e("====>", "leaveRoom");
        isReleased = true;
        if (roomInfo != null) {
            chatRoomService.exitChatRoom(roomInfo.roomId);
        }
        resetAllStates();
    }

    /**
     * ??????????????????
     *
     * @param isAnchor ???????????????
     * @param msg      ??????
     */
    public void sendTextMsg(boolean isAnchor, String msg) {
        TextWithRoleAttachment attachment = new TextWithRoleAttachment(isAnchor, msg);
        sendCustomMsg(attachment);
    }

    /**
     * ?????????????????????
     *
     * @param attachment ?????????????????????
     */
    public void sendCustomMsg(MsgAttachment attachment) {
        if (roomInfo == null) {
            return;
        }
        chatRoomService.sendMessage(
                ChatRoomMessageBuilder.createChatRoomCustomMessage(roomInfo.roomId, attachment), false);

        // pk????????????
        if (attachment instanceof PKStatusAttachment) {
            onPkState((PKStatusAttachment) attachment);
            return;
        }
        // ??????????????????
        if (attachment instanceof PunishmentStatusAttachment) {
            onPunishmentState((PunishmentStatusAttachment) attachment);
            return;
        }
        // ??????????????????????????????
        if (attachment instanceof AnchorCoinChangedAttachment) {
            onAnchorCoinChanged((AnchorCoinChangedAttachment) attachment);
            return;
        }
        // ???????????????????????????
        if (attachment instanceof TextWithRoleAttachment) {
            onTextMsg(getCurrentUser().nickname, (TextWithRoleAttachment) attachment);
        }
    }

    /**
     * ???????????????????????????
     *
     * @param notify   ?????????????????????
     * @param register true ?????????false ?????????
     */
    public void registerNotify(ChatRoomNotify notify, boolean register) {
        if (register && !notifyList.contains(notify)) {
            notifyList.add(notify);
        } else if (!register) {
            notifyList.remove(notify);
        }
    }

    /**
     * ???????????????????????????
     *
     * @param size            ????????????
     * @param requestCallback ????????????
     */
    public void queryRoomTempMembers(int size, RequestCallback<List<AudienceInfo>> requestCallback) {
        Objects.requireNonNull(requestCallback);

        chatRoomService.fetchRoomMembers(roomInfo.roomId, MemberQueryType.GUEST, 0, size)
                .setCallback(new RequestCallback<List<ChatRoomMember>>() {
                    @Override
                    public void onSuccess(List<ChatRoomMember> param) {
                        if (isReleased) {
                            return;
                        }
                        if (param == null || param.isEmpty()) {
                            requestCallback.onSuccess(Collections.emptyList());
                            return;
                        }
                        List<AudienceInfo> result = new ArrayList<>(param.size());
                        convertToAudienceInfo(param, new RoomMemberToAudience() {
                            @Override
                            public void onItemConverted(AudienceInfo audienceInfo) {
                                result.add(audienceInfo);
                            }

                            @Override
                            public void onFinished() {
                                requestCallback.onSuccess(result);
                            }
                        });
                    }

                    @Override
                    public void onFailed(int code) {
                        requestCallback.onFailed(code);
                    }

                    @Override
                    public void onException(Throwable exception) {
                        requestCallback.onException(exception);
                    }
                });
    }

    /**
     * ??????/????????? ????????????IM SDK???
     *
     * @param register true ?????????false ?????????
     */
    private void listen(boolean register) {
        NIMClient.getService(ChatRoomServiceObserver.class).observeReceiveMessage(chatRoomMsgObserver, register);
        NIMClient.getService(ChatRoomServiceObserver.class).observeKickOutEvent(chatRoomKickOutEventObserver, register);
    }

    /**
     * ??????????????????????????????
     */
    private UserModel getCurrentUser() {
        return userCenterService.getCurrentUser();
    }

    /**
     * ????????????????????????
     */
    private void resetAllStates() {
        this.roomInfo = null;
        this.notifyList.clear();
        this.isAnchorOnline = false;
        this.anchorImAccId = null;
        listen(false);
        DELAY_HANDLER.removeMessages(MSG_TYPE_ANCHOR_LEAVE);
    }

    /**
     * ?????????????????? notification ????????????
     */
    private void onNotification(ChatRoomNotificationAttachment notification) {
        ALog.e("======>", "notification is " + notification + ", type is " + notification.getType());
        switch (notification.getType()) {
            // ?????????????????????
            case ChatRoomMemberIn: {
                notifyUserIO(true, notification.getTargetNicks(), notification.getTargets());
                break;
            }
            //  ?????????????????????
            case ChatRoomMemberExit: {
                notifyUserIO(false, notification.getTargetNicks(), notification.getTargets());
                break;
            }
            // ???????????????
            case ChatRoomClose: {
                chatRoomNotify.onRoomDestroyed(roomInfo);
                break;
            }
            default: {
            }
        }
    }

    /**
     * ?????????????????????????????????
     *
     * @param enter        true ?????????false ??????
     * @param nicknameList ????????????????????????
     */
    private void notifyUserIO(boolean enter, List<String> nicknameList, List<String> imAccIdList) {

        if (nicknameList == null
                || nicknameList.isEmpty()
                || imAccIdList == null
                || imAccIdList.isEmpty()
                || nicknameList.size() != imAccIdList.size()) {
            return;
        }

        for (int i = 0; i < imAccIdList.size(); i++) {
            String imAccId = imAccIdList.get(i);
            String nickname = nicknameList.get(i);
            if (enter) {
                if (isNotUserSelf(imAccId)) {
                    roomInfo.increaseCount();
                }
                if (isAnchor(imAccId)) {
                    DELAY_HANDLER.removeMessages(MSG_TYPE_ANCHOR_LEAVE);
                } else {
                    chatRoomNotify.onMsgArrived(new RoomMsg(RoomMsg.MsgType.USER_IN, ChatRoomMsgCreator.createRoomEnter(nickname)));
                }
            } else {
                if (isNotUserSelf(imAccId)) {
                    roomInfo.decreaseCount();
                }
                if (isAnchor(imAccId)) {
                    DELAY_HANDLER.sendEmptyMessageDelayed(MSG_TYPE_ANCHOR_LEAVE, HANDLER_DELAY_TIME);
                } else {
                    chatRoomNotify.onMsgArrived(new RoomMsg(RoomMsg.MsgType.USER_OUT, ChatRoomMsgCreator.createRoomExit(nickname)));
                }
            }
            notifyUserCountChanged();
        }
    }

    private void notifyUserCountChanged() {
        // ????????????
        int extraCount = isAnchorOnline ? 1 : 0;
        int userCount = roomInfo.getOnlineUserCount() - extraCount;
        chatRoomNotify.onUserCountChanged(userCount);
    }

    /**
     * ??????????????????????????????
     */
    private void onTextMsg(String nickname, TextWithRoleAttachment attachment) {
        String content = attachment.msg;
        boolean isAnchor = attachment.isAnchor;
        chatRoomNotify.onMsgArrived(new RoomMsg(RoomMsg.MsgType.TEXT, ChatRoomMsgCreator.createText(isAnchor, nickname, content)));
    }

    /**
     * ??????????????? PK ????????????
     */
    private void onPkState(PKStatusAttachment attachment) {
        chatRoomNotify.onPkStatusChanged(attachment);
    }

    /**
     * ??????????????? ?????? ????????????
     */
    private void onPunishmentState(PunishmentStatusAttachment attachment) {
        chatRoomNotify.onPunishmentStatusChanged(attachment);
    }

    /**
     * ???????????????????????????
     */
    private void onGiftReward(int giftId, String nickname) {
        GiftInfo info = GiftCache.getGift(giftId);
        if (info == null) {
            return;
        }
        chatRoomNotify.onMsgArrived(new RoomMsg(RoomMsg.MsgType.GIFT, ChatRoomMsgCreator.createGiftReward(nickname, 1, info.staticIconResId)));
        chatRoomNotify.onGiftArrived(new RewardGiftInfo(roomInfo.roomId, getCurrentUser().accountId, nickname, roomInfo.creator, giftId));
    }

    /**
     * ????????????????????????????????????
     */
    private void onAnchorCoinChanged(AnchorCoinChangedAttachment attachment) {
        chatRoomNotify.onAnchorCoinChanged(attachment);
        if (roomInfo == null || roomInfo.fromUserAvRoomId == null) {
            return;
        }

        if (roomInfo.fromUserAvRoomId.equals(attachment.toAnchorId)) {
            onGiftReward((int) attachment.giftId, attachment.nickname);
        }
    }

    /**
     * ??????????????????????????????
     */
    private void notifyAllRegisteredInfo(NotifyHelper helper) {
        for (ChatRoomNotify notify : notifyList) {
            helper.onNotifyAction(notify);
        }
    }

    /**
     * ????????????????????????????????????
     *
     * @param members ?????????????????????
     * @param convert ??????????????????
     */
    private void convertToAudienceInfo(List<ChatRoomMember> members, RoomMemberToAudience convert) {
        for (ChatRoomMember member : members) {
            Map<String, Object> extension = member.getExtension();
            if (extension == null) {
                extension = new HashMap<>(0);
            }
            String accountId = String.valueOf(extension.get(EXTENSION_KEY_ACCOUNT_ID));
            AudienceInfo info = new AudienceInfo(accountId, Long.parseLong(member.getAccount()), member.getNick(), member.getAvatar());
            convert.onItemConverted(info);
        }
        convert.onFinished();
    }

    /**
     * ???????????????????????????
     */
    private boolean isAnchor(String imAccId) {
        return imAccId != null && imAccId.equals(anchorImAccId);
    }

    /**
     * ????????????????????????????????????
     *
     * @param imAccId ??????im ????????????id
     */
    private boolean isNotUserSelf(String imAccId) {
        return !String.valueOf(getCurrentUser().imAccid).equals(imAccId);
    }

    /**
     * ??????????????????
     */
    private interface NotifyHelper {
        void onNotifyAction(ChatRoomNotify notify);
    }

    /**
     * ????????????????????????????????????
     */
    private interface RoomMemberToAudience {
        /**
         * ??????????????????
         *
         * @param audienceInfo ????????????
         */
        void onItemConverted(AudienceInfo audienceInfo);

        /**
         * ????????????
         */
        void onFinished();
    }
}
