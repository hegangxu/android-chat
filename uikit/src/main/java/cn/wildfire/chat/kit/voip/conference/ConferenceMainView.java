/*
 * Copyright (c) 2022 WildFireChat. All rights reserved.
 */

package cn.wildfire.chat.kit.voip.conference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import org.webrtc.RendererCommon;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.wildfire.chat.kit.R;
import cn.wildfire.chat.kit.R2;
import cn.wildfire.chat.kit.voip.VoipBaseActivity;
import cn.wildfire.chat.kit.voip.VoipCallItem;
import cn.wildfirechat.avenginekit.AVEngineKit;
import cn.wildfirechat.avenginekit.PeerConnectionClient;
import cn.wildfirechat.model.UserInfo;
import cn.wildfirechat.remote.ChatManager;

class ConferenceMainView extends RelativeLayout {
    @BindView(R2.id.rootView)
    RelativeLayout rootLinearLayout;

    @BindView(R2.id.topBarView)
    LinearLayout topBarView;

    @BindView(R2.id.bottomPanel)
    FrameLayout bottomPanel;

    @BindView(R2.id.durationTextView)
    TextView durationTextView;

    @BindView(R2.id.manageParticipantTextView)
    TextView manageParticipantTextView;

    @BindView(R2.id.previewContainerFrameLayout)
    FrameLayout previewContainerFrameLayout;
    @BindView(R2.id.focusContainerFrameLayout)
    FrameLayout focusContainerFrameLayout;

    @BindView(R2.id.muteImageView)
    ImageView muteImageView;
    @BindView(R2.id.videoImageView)
    ImageView videoImageView;
    @BindView(R2.id.shareScreenImageView)
    ImageView shareScreenImageView;

    private AVEngineKit.CallSession callSession;
    private AVEngineKit.ParticipantProfile myProfile;
    private AVEngineKit.ParticipantProfile focusProfile;

    public ConferenceMainView(Context context) {
        super(context);
        initView(context, null);
    }

    public ConferenceMainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public ConferenceMainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context, attrs);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ConferenceMainView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        View view = inflate(context, R.layout.av_conference_main, this);
        ButterKnife.bind(this, view);
    }

    public void setup(AVEngineKit.CallSession session, AVEngineKit.ParticipantProfile myProfile, AVEngineKit.ParticipantProfile focusProfile) {
        this.callSession = session;
        this.myProfile = myProfile;
        this.focusProfile = focusProfile;
        setupConferenceMainView();
    }

    public void setFocusProfile(AVEngineKit.ParticipantProfile focusProfile) {
        if (this.focusProfile == null) {
            // myProfile 和 focusProfile 换位置
        }
        this.focusProfile = focusProfile;
        setupConferenceMainView();
    }

    public void updateParticipantVolume(String userId, int volume) {

    }

    public void onDestroyView() {
        // TODO
        // do nothing
    }

    private UserInfo me;

    // TODO 移除，并将VoipBaseActivity.focusVideoUserId 修改为static
    private String focusVideoUserId;


    private final RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_BALANCED;
    public static final String TAG = "ConferenceVideoFragment";


    private AVEngineKit getEngineKit() {
        return AVEngineKit.Instance();
    }

    private void init() {
        me = ChatManager.Instance().getUserInfo(ChatManager.Instance().getUserId(), false);
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session == null || session.getState() == AVEngineKit.CallState.Idle) {
            ((Activity) getContext()).finish();
            return;
        }

        setupConferenceMainView();

        if (session.getState() == AVEngineKit.CallState.Outgoing) {
            session.startPreview();
        }

        handler.post(updateCallDurationRunnable);
        updateParticipantStatus(session);

        updateControlStatus();

        manageParticipantTextView.setText("管理(" + (session.getParticipantProfiles().size() + 1) + ")");
        rootLinearLayout.setOnClickListener(clickListener);
        startHideBarTimer();
    }

    private void updateControlStatus() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session == null || session.getState() == AVEngineKit.CallState.Idle) {
            return;
        }

        if (session.isAudience()) {
            muteImageView.setSelected(true);
            videoImageView.setSelected(true);
            shareScreenImageView.setSelected(true);
        } else {
            muteImageView.setSelected(session.isAudioMuted());
            videoImageView.setSelected(session.videoMuted);
            shareScreenImageView.setSelected(session.isScreenSharing());
        }
    }

    private void setupConferenceMainView() {

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int size = Math.min(dm.widthPixels, dm.heightPixels);

        previewContainerFrameLayout.removeAllViews();

        focusVideoUserId = myProfile.getUserId();

        List<AVEngineKit.ParticipantProfile> mainProfiles = new ArrayList<>();
        mainProfiles.add(myProfile);
        if (focusProfile != null && !focusProfile.getUserId().equals(myProfile.getUserId())) {
            mainProfiles.add(focusProfile);
            focusVideoUserId = focusProfile.getUserId();
        }

        for (AVEngineKit.ParticipantProfile profile : mainProfiles) {
            ConferenceParticipantItemView conferenceItem = new ConferenceParticipantItemView(getContext());
            conferenceItem.setOnClickListener(clickListener);
            conferenceItem.setup(this.callSession, profile);

            if (focusProfile != null) {
                if (profile.getUserId().equals(ChatManager.Instance().getUserId())) {
                    previewContainerFrameLayout.removeAllViews();
                    conferenceItem.setLayoutParams(new ViewGroup.LayoutParams(size / 3, size / 3));
                    previewContainerFrameLayout.addView(conferenceItem);
                } else {
                    focusContainerFrameLayout.removeAllViews();
                    conferenceItem.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    focusContainerFrameLayout.addView(conferenceItem);
                    if (!profile.isVideoMuted()) {
                        this.callSession.setParticipantVideoType(focusProfile.getUserId(), focusProfile.isScreenSharing(), AVEngineKit.VideoType.VIDEO_TYPE_BIG_STREAM);
                    }
                }
            } else {
                previewContainerFrameLayout.removeAllViews();

                focusContainerFrameLayout.removeAllViews();
                conferenceItem.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                focusContainerFrameLayout.addView(conferenceItem);
            }

        }
    }

    private void updateParticipantStatus(AVEngineKit.CallSession session) {
        String meUid = ChatManager.Instance().getUserId();
        VoipCallItem item = rootLinearLayout.findViewWithTag(meUid);
        if (item != null) {
            if (session.videoMuted) {
                item.getStatusTextView().setVisibility(View.VISIBLE);
                item.getStatusTextView().setText("关闭摄像头");
            }
        }

        for (String userId : session.getParticipantIds()) {
            item = rootLinearLayout.findViewWithTag(userId);
            if (item == null) {
                continue;
            }
            PeerConnectionClient client = session.getClient(userId);
            if (client.state == AVEngineKit.CallState.Connected) {
                item.getStatusTextView().setVisibility(View.GONE);
            } else if (client.videoMuted) {
                item.getStatusTextView().setText("关闭摄像头");
                item.getStatusTextView().setVisibility(View.VISIBLE);
            }
        }
    }


    @OnClick(R2.id.minimizeImageView)
    void minimize() {
//        ((ConferenceActivity) getActivity()).showFloatingView(focusVideoUserId);
        // VoipBaseActivity#onStop会处理，这儿仅仅finish
        ((Activity) getContext()).finish();
    }

    @OnClick(R2.id.manageParticipantView)
    void addParticipant() {
        ((ConferenceActivity) getContext()).showParticipantList();
    }

    @OnClick(R2.id.muteView)
    void mute() {
        AVEngineKit.CallSession session = AVEngineKit.Instance().getCurrentSession();
        if (session != null && session.getState() == AVEngineKit.CallState.Connected) {
            boolean toMute = !session.isAudioMuted();
            muteImageView.setSelected(toMute);
            session.muteAudio(toMute);
            startHideBarTimer();
        }
    }

    @OnClick(R2.id.switchCameraImageView)
    void switchCamera() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null && session.getState() == AVEngineKit.CallState.Connected) {
            session.switchCamera();
            startHideBarTimer();
        }
    }

    @OnClick(R2.id.videoView)
    void video() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null && session.getState() == AVEngineKit.CallState.Connected) {
            boolean toMute = !session.videoMuted;
            videoImageView.setSelected(toMute);
            session.muteVideo(toMute);
            startHideBarTimer();
        }
    }

    @OnClick(R2.id.hangupView)
    void hangup() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null) {
            if (ChatManager.Instance().getUserId().equals(session.getHost())) {
                new AlertDialog.Builder(getContext())
                    .setMessage("请选择是否结束会议")
                    .setIcon(R.mipmap.ic_launcher)
                    .setNeutralButton("退出会议", (dialogInterface, i) -> {
                        if (session.getState() != AVEngineKit.CallState.Idle)
                            session.leaveConference(false);
                    })
                    .setPositiveButton("结束会议", (dialogInterface, i) -> {
                        if (session.getState() != AVEngineKit.CallState.Idle)
                            session.leaveConference(true);
                    })
                    .create()
                    .show();
            } else {
                session.leaveConference(false);
            }
        }
    }

    @OnClick(R2.id.shareScreenView)
    void shareScreen() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session != null) {
            if (session.isAudience()) {
                return;
            }

//            shareScreenImageView.setSelected(!session.isScreenSharing());
            if (!session.isScreenSharing()) {
                ((VoipBaseActivity) getContext()).startScreenShare();
            } else {
                ((VoipBaseActivity) getContext()).stopScreenShare();
            }
        }
    }

    private final View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String userId = (String) v.getTag();
            if (userId != null && !userId.equals(focusVideoUserId)) {
//                VoipCallItem clickedConferenceItem = (VoipCallItem) v;
//                int clickedIndex = previewVideoContainerFrameLayout.indexOfChild(v);
//                previewVideoContainerFrameLayout.removeView(clickedConferenceItem);
//                previewVideoContainerFrameLayout.endViewTransition(clickedConferenceItem);
//
//                clickedConferenceItem.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//                if (focusConferenceItem != null) {
//                    focusVideoContainerFrameLayout.removeView(focusConferenceItem);
//                    focusVideoContainerFrameLayout.endViewTransition(focusConferenceItem);
//                    DisplayMetrics dm = getResources().getDisplayMetrics();
//                    int size = Math.min(dm.widthPixels, dm.heightPixels);
//                    previewVideoContainerFrameLayout.addView(focusConferenceItem, clickedIndex, new FrameLayout.LayoutParams(size / 3, size / 3));
//                }
//                focusVideoContainerFrameLayout.addView(clickedConferenceItem, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//                focusConferenceItem = clickedConferenceItem;
//                focusVideoUserId = userId;
//                Log.e(TAG, "focusVideoUserId " + userId + " " + ChatManager.Instance().getUserId());
//                ((VoipBaseActivity) getActivity()).setFocusVideoUserId(focusVideoUserId);

                if (bottomPanel.getVisibility() == View.GONE) {
                    bottomPanel.setVisibility(View.VISIBLE);
                    topBarView.setVisibility(View.VISIBLE);
                    startHideBarTimer();
                }
            } else {
                if (bottomPanel.getVisibility() == View.GONE) {
                    bottomPanel.setVisibility(View.VISIBLE);
                    topBarView.setVisibility(View.VISIBLE);
                    startHideBarTimer();
                } else {
                    bottomPanel.setVisibility(View.GONE);
                    topBarView.setVisibility(View.GONE);
                }
            }
        }
    };

    private void startHideBarTimer() {
        if (bottomPanel.getVisibility() == View.GONE) {
            return;
        }
        handler.removeCallbacks(hideBarCallback);
        handler.postDelayed(hideBarCallback, 3000);
    }

    private final Runnable hideBarCallback = new Runnable() {
        @Override
        public void run() {
            AVEngineKit.CallSession session = AVEngineKit.Instance().getCurrentSession();
            if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
                bottomPanel.setVisibility(View.GONE);
                topBarView.setVisibility(View.GONE);
            }
        }
    };

    private final Handler handler = ChatManager.Instance().getMainHandler();

    private final Runnable updateCallDurationRunnable = new Runnable() {
        @Override
        public void run() {
            AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
            if (session != null && session.getState() == AVEngineKit.CallState.Connected) {
                String text;
                if (session.getConnectedTime() == 0) {
                    text = "会议连接中";
                } else {
                    long s = System.currentTimeMillis() - session.getConnectedTime();
                    s = s / 1000;
                    if (s > 3600) {
                        text = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
                    } else {
                        text = String.format("%02d:%02d", s / 60, (s % 60));
                    }
                }
                durationTextView.setText(text);
            }
            handler.postDelayed(updateCallDurationRunnable, 1000);
        }
    };


//    @Override
//    public void onStop() {
//        super.onStop();
//        handler.removeCallbacks(hideBarCallback);
//        handler.removeCallbacks(updateCallDurationRunnable);
//    }

//    private AVEngineKit.ParticipantProfile findFocusProfile(AVEngineKit.CallSession session) {
//        List<AVEngineKit.ParticipantProfile> profiles = session.getParticipantProfiles();
//
//        AVEngineKit.ParticipantProfile focusProfile = null;
//        for (AVEngineKit.ParticipantProfile profile : profiles) {
//            if (!profile.isAudience()) {
//                if (profile.isScreenSharing()) {
//                    focusProfile = profile;
//                    break;
//                } else if (!profile.isVideoMuted() && (focusProfile == null || focusProfile.isVideoMuted())) {
//                    focusProfile = profile;
//                } else if (!profile.isAudioMuted() && focusProfile == null) {
//                    focusProfile = profile;
//                }
//            }
//        }
//        if (focusProfile == null) {
//            focusProfile = session.getMyProfile();
//        }
//        return focusProfile;
//    }
}
