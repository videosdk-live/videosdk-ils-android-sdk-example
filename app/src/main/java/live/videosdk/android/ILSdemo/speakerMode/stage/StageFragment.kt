package live.videosdk.android.ILSdemo.speakerMode.stage

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import live.videosdk.android.ILSdemo.R
import live.videosdk.android.ILSdemo.common.stream.activity.CreateOrJoinActivity
import live.videosdk.android.ILSdemo.common.stream.activity.MainActivity
import live.videosdk.android.ILSdemo.viewerMode.ViewerFragment
import live.videosdk.rtc.android.*
import live.videosdk.rtc.android.VideoView
import live.videosdk.rtc.android.lib.AppRTCAudioManager.AudioDevice
import live.videosdk.rtc.android.listeners.MeetingEventListener
import live.videosdk.rtc.android.listeners.ParticipantEventListener
import live.videosdk.rtc.android.listeners.PubSubMessageListener
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack
import java.util.*

class StageFragment() : Fragment() {
    var speakerGridLayout: GridLayout? = null

    private var micEnabled = false
    private var webcamEnabled = false
    private var speakerView: MutableMap<String, View>? = HashMap()
    private var speakerList: MutableList<Participant?>? = ArrayList()
    private var btnMic: MaterialButton? = null
    private var btnWebcam: MaterialButton? = null
    private var btnScreenshare: MaterialButton? = null
    private var btnSetting: MaterialButton? = null
    private var btnRecording: MaterialButton? = null

    private var fullScreen = false
    private var toolbar: Toolbar? = null
    private var screenShareParticipantNameSnackbar: Snackbar? = null
    private var screenshareEnabled = false
    private var shareView: VideoView? = null
    private var shareLayout: FrameLayout? = null
    private var selectedAudioDeviceName: String? = null
    private var facingMode: String? = null
    private var stageLayout: RelativeLayout? = null
    private var localScreenShareView: LinearLayout? = null
    private var tvScreenShareParticipantName: TextView? = null
    private var removeCoHostListener: PubSubMessageListener? = null
    private var btnLeave: ImageView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        if (context is Activity) {
            mActivity = context
            meeting = (mActivity as MainActivity?)!!.getStream()
            facingMode = (mActivity as MainActivity?)!!.getFacingMode()
        }
    }

    override fun onDestroy() {
        mContext = null
        mActivity = null
        for (i in 0 until speakerGridLayout!!.childCount) {
            val view = speakerGridLayout!!.getChildAt(i)
            val videoView = view.findViewById<VideoView>(R.id.speakerVideoView)
            if (videoView != null) {
                videoView.visibility = View.GONE
                videoView.releaseSurfaceViewRenderer()
            }
        }
        speakerGridLayout!!.removeAllViews()
        speakerList = null
        if (meeting != null) {
            meeting!!.pubSub.unsubscribe("removeCoHost", removeCoHostListener)
            meeting!!.localParticipant.removeAllListeners()
            val participants: Iterator<Participant> = meeting!!.participants.values.iterator()
            for (i in 0 until meeting!!.participants.size) {
                val participant = participants.next()
                participant.removeAllListeners()
            }
            meeting!!.removeAllListeners()
            meeting = null
        }
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_stage, container, false)
        speakerGridLayout = view.findViewById(R.id.speakerGridLayout)
        btnMic = view.findViewById(R.id.btnMic)
        btnWebcam = view.findViewById(R.id.btnWebcam)
        btnScreenshare = view.findViewById(R.id.btnScreenShare)
        btnRecording = view.findViewById(R.id.btnRecording)
        btnSetting = view.findViewById(R.id.btnSetting)
        btnLeave = view.findViewById(R.id.btnSpeakerLeave)
        toolbar = view.findViewById(R.id.material_toolbar)
        shareLayout = view.findViewById(R.id.shareLayout)
        shareView = view.findViewById(R.id.shareView)
        localScreenShareView = view.findViewById(R.id.localScreenShareView)
        tvScreenShareParticipantName = view.findViewById(R.id.tvScreenShareParticipantName)
        stageLayout = view.findViewById(R.id.stageLayout)
        if (meeting != null) {
            val isViewer = meeting!!.localParticipant.mode == "VIEWER"
            setupUIForParticipantMode(isViewer)
            meeting!!.addEventListener(meetingEventListener)
            speakerList!!.add(meeting!!.localParticipant)
            showInGUI(speakerList)
            if (!isViewer) {
                toggleMicIcon()
                toggleWebcamIcon()
            }
            val participantIterator: Iterator<Participant> = meeting!!.participants.values.iterator()
            for (i in 0 until meeting!!.participants.size) {
                val participant = participantIterator.next()
                if (participant.mode == "SEND_AND_RECV") {
                    showParticipants(participant)
                }
            }

            removeCoHostListener =
                PubSubMessageListener { pubSubMessage ->
                    if ((pubSubMessage.message == meeting!!.localParticipant.id)) {
                        meeting!!.changeMode("RECV_ONLY")
                        requireActivity().supportFragmentManager
                            .beginTransaction()
                            .replace(R.id.mainLayout, ViewerFragment(), "viewerFragment")
                            .commit()
                    }
                }
            meeting!!.pubSub.subscribe("removeCoHost", removeCoHostListener)
            setActionListeners()
            setAudioDeviceListeners()
        }
        view.findViewById<View>(R.id.controllers).bringToFront()
        view.findViewById<View>(R.id.material_toolbar).bringToFront()
        val onTouchListener = OnTouchListener { _, event ->
            if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
                if (fullScreen) {
                    toolbar!!.visibility = View.VISIBLE
                    for (i in 0 until toolbar!!.childCount) {
                        toolbar!!.getChildAt(i).visibility = View.VISIBLE
                    }
                    val toolbarAnimation = TranslateAnimation(
                        0F,
                        0F,
                        0F,
                        10F
                    )
                    toolbarAnimation.duration = 500
                    toolbarAnimation.fillAfter = true
                    toolbar!!.startAnimation(toolbarAnimation)
                    val controllers = view.findViewById<RelativeLayout>(R.id.controllers)
                    controllers.visibility = View.VISIBLE
                    for (i in 0 until controllers.childCount) {
                        controllers.getChildAt(i).visibility = View.VISIBLE
                    }
                    val animate = TranslateAnimation(
                        0F,
                        0F,
                        controllers.height.toFloat(),
                        0F
                    )
                    animate.duration = 300
                    animate.fillAfter = true
                    controllers.startAnimation(animate)
                    val tabLayout = mActivity!!.findViewById<TabLayout>(R.id.tabLayout)
                    tabLayout.visibility = View.VISIBLE
                    for (i in 0 until tabLayout.childCount) {
                        tabLayout.getChildAt(i).visibility = View.VISIBLE
                    }
                    val translateAnimation = TranslateAnimation(
                        0F,
                        0F,
                        tabLayout.height.toFloat(),
                        0F
                    )
                    animate.duration = 300
                    animate.fillAfter = true
                    tabLayout.startAnimation(translateAnimation)
                } else {
                    toolbar!!.visibility = View.GONE
                    for (i in 0 until toolbar!!.childCount) {
                        toolbar!!.getChildAt(i).visibility = View.GONE
                    }
                    val toolbarAnimation = TranslateAnimation(
                        0F,
                        0F,
                        0F,
                        10F
                    )
                    toolbarAnimation.duration = 500
                    toolbarAnimation.fillAfter = true
                    toolbar!!.startAnimation(toolbarAnimation)
                    val controllers = view.findViewById<RelativeLayout>(R.id.controllers)
                    controllers.visibility = View.GONE
                    for (i in 0 until controllers.childCount) {
                        controllers.getChildAt(i).visibility = View.GONE
                    }
                    val animate = TranslateAnimation(
                        0F,
                        0F,
                        0F,
                        controllers.height.toFloat()
                    )
                    animate.duration = 400
                    animate.fillAfter = true
                    controllers.startAnimation(animate)
                    val tabLayout = mActivity!!.findViewById<TabLayout>(R.id.tabLayout)
                    tabLayout.visibility = View.GONE
                    for (i in 0 until tabLayout.childCount) {
                        tabLayout.getChildAt(i).visibility = View.GONE
                    }
                    val translateAnimation = TranslateAnimation(
                        0F,
                        0F,
                        0F,
                        tabLayout.height.toFloat()
                    )
                    animate.duration = 400
                    animate.fillAfter = true
                    tabLayout.startAnimation(translateAnimation)
                }
                fullScreen = !fullScreen
            }
            true
        }
        view.findViewById<View>(R.id.speaker_linearLayout).setOnTouchListener(onTouchListener)
        view.findViewById<View>(R.id.btnStopScreenShare).setOnClickListener { v: View? ->
            if (screenshareEnabled) {
                if (meeting != null) meeting!!.disableScreenShare()
            }
        }
        return view
    }



    private fun setupUIForParticipantMode(isViewer: Boolean) {
        if (isViewer) {

            btnMic?.visibility = View.GONE
            btnWebcam?.visibility = View.GONE
            btnScreenshare?.visibility = View.GONE
            btnRecording?.visibility = View.GONE

            btnSetting?.visibility = View.GONE



            btnLeave?.apply {
                visibility = View.VISIBLE
                isEnabled = true
            }


            setViewerActionListeners()
        } else {

            btnMic?.visibility = View.VISIBLE
            btnWebcam?.visibility = View.VISIBLE
            btnScreenshare?.visibility = View.VISIBLE
            btnRecording?.visibility = View.VISIBLE

            btnSetting?.visibility = View.VISIBLE

            btnLeave?.visibility = View.VISIBLE

            setActionListeners()
        }
    }

    private fun setViewerActionListeners() {
        btnLeave?.setOnClickListener { v: View? ->
            (mActivity as MainActivity?)!!.showLeaveDialog()
        }
    }



    private val meetingEventListener: MeetingEventListener = object : MeetingEventListener() {
        override fun onMeetingLeft() {
            if (isAdded) {
                val intents = Intent(
                    mContext,
                    CreateOrJoinActivity::class.java
                )
                intents.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                startActivity(intents)
                mActivity!!.finish()
            }
        }

        override fun onParticipantJoined(participant: Participant) {
            showParticipants(participant)
        }

        override fun onParticipantLeft(participant: Participant) {
            if (speakerList!!.contains(participant)) {
                participant.unpin("SHARE_AND_CAM")
                val participants = meeting!!.participants
                val participantList: MutableList<Participant?> = ArrayList()
                participantList.add(meeting!!.localParticipant)
                if (screenshareEnabled) {
                    for (entry: Map.Entry<String?, Participant> in participants.entries) {
                        if ((entry.value.mode == "SEND_AND_RECV")) {
                            participantList.add(entry.value)
                            for (entry1: Map.Entry<String?, Stream> in entry.value.streams.entries) {
                                val stream = entry1.value
                                stream.resume()
                            }
                            if (participantList.size == 2) break
                        }
                    }
                    showInGUI(participantList)
                } else {
                    for (entry: Map.Entry<String?, Participant> in participants.entries) {
                        if ((entry.value.mode == "SEND_AND_RECV")) {
                            participantList.add(entry.value)
                            for (entry1: Map.Entry<String?, Stream> in entry.value.streams.entries) {
                                val stream = entry1.value
                                stream.resume()
                            }
                            if (participantList.size == 4) break
                        }
                    }
                    showInGUI(participantList)
                }
                speakerList = ArrayList()
                speakerList!!.add(meeting!!.localParticipant)
                for (entry: Map.Entry<String?, Participant> in participants.entries) {
                    if ((entry.value.mode == "SEND_AND_RECV")) {
                        speakerList!!.add(entry.value)
                        if (speakerList!!.size == 4) break
                    }
                }
                updateGridLayout(screenshareEnabled)
            }
        }

        override fun onParticipantModeChanged(data: JSONObject) {
            try {
                if (meeting!!.localParticipant.id != data.getString("peerId")) {
                    val participant = meeting!!.participants[data.getString("peerId")]

                    if (data.getString("mode") == "SEND_AND_RECV") {

                        participant?.let {
                            if (speakerList!!.size < 4) {
                                it.pin("SHARE_AND_CAM")
                                speakerList!!.add(it)

                                if (screenshareEnabled) {
                                    val participants: MutableList<Participant?> = ArrayList()
                                    participants.add(speakerList!![0])
                                    participants.add(speakerList!![1])
                                    showInGUI(participants)
                                    updateGridLayout(true)
                                } else {
                                    showInGUI(speakerList)
                                    updateGridLayout(false)
                                }
                            }
                        }
                    } else {

                        if (speakerList!!.contains(participant)) {
                            participant!!.unpin("SHARE_AND_CAM")

                            val participants = meeting!!.participants
                            val speakers: MutableMap<String, Participant> = HashMap()
                            for (entry: Map.Entry<String, Participant> in participants.entries) {
                                if (entry.value.mode == "SEND_AND_RECV") {
                                    speakers[entry.key] = entry.value
                                }
                            }

                            val participantList: MutableList<Participant?> = ArrayList()
                            participantList.add(meeting!!.localParticipant)

                            if (screenshareEnabled) {
                                for (entry: Map.Entry<String, Participant> in speakers.entries) {
                                    if (entry.value.mode == "SEND_AND_RECV") {
                                        participantList.add(entry.value)
                                        for (stream in entry.value.streams.values) {
                                            stream.resume()
                                        }
                                        if (participantList.size == 2) break
                                    }
                                }
                                showInGUI(participantList)
                            } else {
                                for (entry: Map.Entry<String, Participant> in speakers.entries) {
                                    if (entry.value.mode == "SEND_AND_RECV") {
                                        participantList.add(entry.value)
                                        for (stream in entry.value.streams.values) {
                                            stream.resume()
                                        }
                                        if (participantList.size == 4) break
                                    }
                                }
                                showInGUI(participantList)
                            }

                            speakerList = ArrayList()
                            speakerList!!.add(meeting!!.localParticipant)
                            for (entry: Map.Entry<String, Participant> in speakers.entries) {
                                if (entry.value.mode == "SEND_AND_RECV") {
                                    speakerList!!.add(entry.value)
                                    if (speakerList!!.size == 4) break
                                }
                            }
                            updateGridLayout(screenshareEnabled)
                        }
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }


        override fun onPresenterChanged(participantId: String?) {
            super.onPresenterChanged(participantId)
            if (!TextUtils.isEmpty(participantId)) {
                if ((meeting!!.localParticipant.id == participantId)) {
                    localScreenShareView!!.visibility = View.VISIBLE
                    localScreenShareView!!.bringToFront()
                } else {
                    updatePresenter(meeting!!.participants[participantId])
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    showScreenshareSnackbar()
                }
                toolbar!!.bringToFront()
                screenshareEnabled = true
                val participants: MutableList<Participant?> = ArrayList()
                participants.add(meeting!!.localParticipant)
                if (speakerList!!.size > 1) participants.add(speakerList!![1])
                showInGUI(participants)
                updateGridLayout(true)
                btnScreenshare!!.isEnabled = false
            } else {
                if (localScreenShareView!!.visibility == View.VISIBLE) {
                    localScreenShareView!!.visibility = View.GONE
                } else {
                    shareView!!.removeTrack()
                    shareView!!.visibility = View.GONE
                    shareLayout!!.visibility = View.GONE
                    tvScreenShareParticipantName!!.visibility = View.GONE
                }
                screenshareEnabled = false
                showInGUI(speakerList)
                updateGridLayout(false)
                btnScreenshare!!.isEnabled = true
            }
            toggleScreenShareIcon()
        }

        override fun onSpeakerChanged(participantId: String) {
            if (!isNullOrEmpty(participantId)) {
                if (speakerView != null) {
                    if (speakerView!!.containsKey(participantId)) {
                        val participantView = speakerView!![participantId]
                        val participantCard =
                            participantView!!.findViewById<CardView>(R.id.ParticipantCard)
                        participantCard.foreground = mContext!!.getDrawable(R.drawable.layout_bg)
                    } else {
                        val activeSpeaker = meeting!!.participants[participantId]
                        for (entry: Map.Entry<String?, Stream> in activeSpeaker!!.streams.entries) {
                            val stream = entry.value
                            stream.resume()
                        }
                        val participants: MutableList<Participant?> = ArrayList()
                        participants.add(meeting!!.localParticipant)
                        participants.add(activeSpeaker)
                        if (screenshareEnabled) {
                            showInGUI(participants)
                            updateGridLayout(true)
                        } else {
                            for (i in 1.rangeTo(2)) {
                                participants.add(speakerList!![i])
                            }
                            showInGUI(participants)
                            for (entry: Map.Entry<String?, Stream> in speakerList!![speakerList!!.size - 1]!!.streams.entries) {
                                val stream = entry.value
                                stream.pause()
                            }
                            speakerList = participants
                            updateGridLayout(false)
                        }
                        val participantView = speakerView!![participantId]
                        val participantCard =
                            participantView!!.findViewById<CardView>(R.id.ParticipantCard)
                        participantCard.foreground = mContext!!.getDrawable(R.drawable.layout_bg)
                    }
                }
            } else {
                for (entry: Map.Entry<String, View> in speakerView!!.entries) {
                    val participantView = entry.value
                    val participantCard =
                        participantView.findViewById<CardView>(R.id.ParticipantCard)
                    participantCard.foreground = null
                }
            }
        }
    }


    private fun showParticipants(participant: Participant) {

        if (participant.mode == "SEND_AND_RECV" && speakerList!!.size < 4) {
            participant.pin("SHARE_AND_CAM")
            speakerList!!.add(participant)

            var hasAudio = false
            var hasVideo = false

            for (stream in participant.streams.values) {
                when {
                    stream.kind.equals("audio", ignoreCase = true) -> hasAudio = true
                    stream.kind.equals("video", ignoreCase = true) -> hasVideo = true
                }
            }

            if (!hasAudio && participant.id != meeting!!.localParticipant.id) {
                participant.enableMic()
            }

            if (!hasVideo && participant.id != meeting!!.localParticipant.id) {
                participant.enableWebcam()
            }

            if (screenshareEnabled) {
                val participants: MutableList<Participant?> = ArrayList()
                for (i in 0..1) {
                    if (i < speakerList!!.size) {
                        participants.add(speakerList!![i])
                    }
                }
                showInGUI(participants)
                updateGridLayout(true)
            } else {
                showInGUI(speakerList)
                updateGridLayout(false)
            }
        } else {
            for (entry: Map.Entry<String, Stream> in participant.streams.entries) {
                val stream = entry.value
                stream.pause()
            }
        }
    }




    private fun updatePresenter(participant: Participant?) {
        if (participant == null) return

        var shareStream: Stream? = null
        for (stream: Stream in participant.streams.values) {
            if ((stream.kind == "share")) {
                shareStream = stream
                break
            }
        }
        if (shareStream == null) return
        tvScreenShareParticipantName!!.text = participant.displayName + " is presenting"
        tvScreenShareParticipantName!!.visibility = View.VISIBLE

        shareLayout!!.visibility = View.VISIBLE
        shareLayout!!.bringToFront()
        shareView!!.visibility = View.VISIBLE
        shareView!!.setZOrderMediaOverlay(true)
        shareView!!.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        val videoTrack = shareStream.track as VideoTrack
        shareView!!.addTrack(videoTrack)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            showScreenshareSnackbar()
        }
    }

    private fun showInGUI(participantList: List<Participant?>?) {
        if (participantList == null) return

        if (speakerView != null) {
            for (entry: Map.Entry<String, View> in speakerView!!.entries) {
                val key = entry.key
                val participant = meeting!!.participants[key]
                participant?.removeAllListeners()
                val participantVideoView = speakerView!![key]!!
                    .findViewById<VideoView>(R.id.speakerVideoView)
                participantVideoView.releaseSurfaceViewRenderer()
                speakerGridLayout!!.removeView(speakerView!![key])
            }
        }
        speakerView = HashMap()

        val conferenceParticipants = participantList.filter { participant ->
            participant?.mode == "SEND_AND_RECV"
        }

        for (participant in conferenceParticipants) {
            if (participant == null) continue

            val participantView = LayoutInflater.from(mActivity)
                .inflate(R.layout.item_speaker, speakerGridLayout, false)
            speakerView!![participant.id] = participantView

            val ivMicStatus = participantView.findViewById<ImageView>(R.id.ivMicStatus)
            val tvName = participantView.findViewById<TextView>(R.id.tvName)
            val txtParticipantName = participantView.findViewById<TextView>(R.id.txtParticipantName)
            val participantVideoView = participantView.findViewById<VideoView>(R.id.speakerVideoView)


            if (participant.id == meeting!!.localParticipant.id) {
                tvName.text = "You"
            } else {
                tvName.text = participant.displayName
            }
            txtParticipantName.text = participant.displayName.substring(0, 1)

            // Handle streams
            for (stream in participant.streams.values) {
                when {
                    stream.kind.equals("video", ignoreCase = true) -> {
                        participantVideoView.visibility = View.VISIBLE
                        val videoTrack = stream.track as VideoTrack
                        participantVideoView.addTrack(videoTrack)
                        if (participant.id == meeting!!.localParticipant.id) {
                            webcamEnabled = true
                            toggleWebcamIcon()
                        }
                    }
                    stream.kind.equals("audio", ignoreCase = true) -> {
                        ivMicStatus.setImageResource(R.drawable.ic_audio_on)
                        if (participant.id == meeting!!.localParticipant.id) {
                            micEnabled = true
                            toggleMicIcon()
                        }
                    }
                }
            }

            // Add stream listeners
            participant.addEventListener(object : ParticipantEventListener() {
                override fun onStreamEnabled(stream: Stream) {
                    if (participant.mode == "VIEWER") return

                    when {
                        stream.kind.equals("video", ignoreCase = true) -> {
                            participantVideoView.visibility = View.VISIBLE
                            val videoTrack = stream.track as VideoTrack
                            participantVideoView.addTrack(videoTrack)
                            if (participant.id == meeting!!.localParticipant.id) {
                                webcamEnabled = true
                                toggleWebcamIcon()
                            }
                        }
                        stream.kind.equals("audio", ignoreCase = true) -> {
                            ivMicStatus.setImageResource(R.drawable.ic_audio_on)
                            if (participant.id == meeting!!.localParticipant.id) {
                                micEnabled = true
                                toggleMicIcon()
                            }
                        }
                    }
                }

                override fun onStreamDisabled(stream: Stream) {
                    when {
                        stream.kind.equals("video", ignoreCase = true) -> {
                            val track: VideoTrack? = stream.track as VideoTrack
                            if (track != null) participantVideoView.removeTrack()
                            participantVideoView.visibility = View.GONE
                            if (participant.id == meeting!!.localParticipant.id) {
                                webcamEnabled = false
                                toggleWebcamIcon()
                            }
                        }
                        stream.kind.equals("audio", ignoreCase = true) -> {
                            ivMicStatus.setImageResource(R.drawable.ic_audio_off)
                            if (participant.id == meeting!!.localParticipant.id) {
                                micEnabled = false
                                toggleMicIcon()
                            }
                        }
                    }
                }
            })

            speakerGridLayout!!.addView(participantView)
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.P)
    fun showScreenshareSnackbar() {
        screenShareParticipantNameSnackbar = Snackbar.make(
            (stageLayout)!!, "You started presenting",
            Snackbar.LENGTH_SHORT
        )
        val snackbarTextId = com.google.android.material.R.id.snackbar_text
        val textView =
            screenShareParticipantNameSnackbar!!.view.findViewById<View>(snackbarTextId) as TextView
        textView.textSize = 15f
        textView.typeface = Typeface.create(null, 700, false)
        screenShareParticipantNameSnackbar!!.isGestureInsetBottomIgnored = true
        screenShareParticipantNameSnackbar!!.view.setOnClickListener({ view: View? -> screenShareParticipantNameSnackbar!!.dismiss() })
        screenShareParticipantNameSnackbar!!.show()
    }

    fun updateGridLayout(screenShareFlag: Boolean) {
        if (screenShareFlag) {
            var col = 0
            var row = 0
            for (i in 0 until speakerGridLayout!!.childCount) {
                val params =
                    speakerGridLayout!!.getChildAt(i).layoutParams as GridLayout.LayoutParams
                params.columnSpec = GridLayout.spec(col, 1, 1f)
                params.rowSpec = GridLayout.spec(row, 1, 1f)
                if (col + 1 == 2) {
                    col = 0
                    row++
                } else {
                    col++
                }
            }
            speakerGridLayout!!.requestLayout()
        } else {
            var col = 0
            var row = 0
            for (i in 0 until speakerGridLayout!!.childCount) {
                val params =
                    speakerGridLayout!!.getChildAt(i).layoutParams as GridLayout.LayoutParams
                params.columnSpec = GridLayout.spec(col, 1, 1f)
                params.rowSpec = GridLayout.spec(row, 1, 1f)
                if (col + 1 == normalLayoutColumnCount) {
                    col = 0
                    row++
                } else {
                    col++
                }
            }
            speakerGridLayout!!.requestLayout()
        }
    }

    private val normalLayoutRowCount: Int
        get() = Math.min(Math.max(1, speakerList!!.size), 2)
    private val normalLayoutColumnCount: Int
        get() {
            val maxColumns = 2
            val result =
                Math.max(1, (speakerList!!.size + normalLayoutRowCount - 1) / normalLayoutRowCount)
            if (result > maxColumns) {
                throw IllegalStateException(
                    result.toString() +
                            "videos not allowed."
                )
            }
            return result
        }

    private fun askPermissionForScreenShare() {
        val mediaProjectionManager = requireActivity().application.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE) return
        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(
                mContext,
                "You didn't give permission to capture the screen.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        meeting!!.enableScreenShare(data)
    }

    private fun setActionListeners() {
        btnMic!!.setOnClickListener { v: View? ->
            if (micEnabled) {
                meeting!!.muteMic()
            } else {
                val audioCustomTrack: CustomStreamTrack =
                    VideoSDK.createAudioTrack("high_quality", mContext)
                meeting!!.unmuteMic(audioCustomTrack)
            }
        }
        btnWebcam!!.setOnClickListener { v: View? ->
            if (webcamEnabled) {
                meeting!!.disableWebcam()
            } else {
                val videoCustomTrack: CustomStreamTrack = VideoSDK.createCameraVideoTrack(
                    "h720p_w960p",
                    facingMode,
                    CustomStreamTrack.VideoMode.DETAIL,
                    true,
                    mContext,null,null,
                )
                meeting!!.enableWebcam(videoCustomTrack)
            }
        }
        btnLeave!!.setOnClickListener { v: View? -> (mActivity as MainActivity?)!!.showLeaveDialog() }
        btnScreenshare!!.setOnClickListener { v: View? ->
            if (!screenshareEnabled) {
                askPermissionForScreenShare()
            } else {
                meeting!!.disableScreenShare()
            }
        }

        btnSetting!!.setOnClickListener { showSettings() }
    }

    private fun toggleMicIcon() {
        if (micEnabled) {
            btnMic!!.icon = mContext!!.resources.getDrawable(R.drawable.ic_mic_on)
            btnMic!!.setIconTintResource(R.color.white)
            btnMic!!.backgroundTintList =
                ContextCompat.getColorStateList((mContext)!!, R.color.semiTransperentColor)
        } else {
            btnMic!!.icon = mContext!!.resources.getDrawable(R.drawable.ic_mic_off)
            btnMic!!.setIconTintResource(R.color.black)
            btnMic!!.backgroundTintList =
                ContextCompat.getColorStateList((mContext)!!, R.color.white)
        }
    }

    private fun toggleWebcamIcon() {
        if (webcamEnabled) {
            btnWebcam!!.icon = mContext!!.resources.getDrawable(R.drawable.ic_video_camera)
            btnWebcam!!.setIconTintResource(R.color.white)
            btnWebcam!!.backgroundTintList =
                ContextCompat.getColorStateList((mContext)!!, R.color.semiTransperentColor)
        } else {
            btnWebcam!!.icon = mContext!!.resources.getDrawable(R.drawable.ic_video_camera_off)
            btnWebcam!!.setIconTintResource(R.color.black)
            btnWebcam!!.backgroundTintList =
                ContextCompat.getColorStateList((mContext)!!, R.color.white)
        }
    }


    private fun toggleScreenShareIcon() {
        if (!screenshareEnabled) {
            btnScreenshare!!.icon = mContext!!.resources.getDrawable(R.drawable.ic_screen_share)
            btnScreenshare!!.setIconTintResource(R.color.white)
            btnScreenshare!!.backgroundTintList = ContextCompat.getColorStateList(
                (mContext)!!, R.color.semiTransperentColor
            )
        } else {
            btnScreenshare!!.setIconTintResource(R.color.md_grey_10)
        }
    }


    private fun showSettings() {
        val bottomSheetDialog = BottomSheetDialog((mContext)!!)
        val v3 = LayoutInflater.from(mContext).inflate(R.layout.settings_layout, null)
        bottomSheetDialog.setContentView(v3)
        val close = v3.findViewById<ImageView>(R.id.ic_close)
        bottomSheetDialog.show()
        close.setOnClickListener { bottomSheetDialog.dismiss() }

        //mic settings
        val micDevice = v3.findViewById<AutoCompleteTextView>(R.id.micDevice)
        var selectedMic = selectedAudioDeviceName!!.substring(0, 1)
            .uppercase(Locale.getDefault()) + selectedAudioDeviceName!!.substring(1).lowercase(
            Locale.getDefault()
        )
        selectedMic = selectedMic.replace("_", " ")
        micDevice.setText(selectedMic)
        micDevice.setDropDownBackgroundDrawable(
            ResourcesCompat.getDrawable(
                mContext!!.resources,
                R.drawable.dropdown_style,
                null
            )
        )
        val mics = meeting!!.mics
        val audioDeviceList = ArrayList<String?>()
        // Prepare list
        var item: String
        for (i in mics.indices) {
            item = mics.toTypedArray()[i].toString()
            var mic =
                item.substring(0, 1).uppercase(Locale.getDefault()) + item.substring(1).lowercase(
                    Locale.getDefault()
                )
            mic = mic.replace("_", " ")
            audioDeviceList.add(mic)
        }
        val micArrayAdapter: ArrayAdapter<*> =
            ArrayAdapter<Any?>((mContext)!!, R.layout.custom_drop_down_item,
                audioDeviceList as List<Any?>
            )
        micDevice.setAdapter(micArrayAdapter)
        micDevice.onItemClickListener =
            OnItemClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                var audioDevice: AudioDevice? = null
                when (audioDeviceList.get(i)) {
                    "Bluetooth" -> audioDevice = AudioDevice.BLUETOOTH
                    "Wired headset" -> audioDevice = AudioDevice.WIRED_HEADSET
                    "Speaker phone" -> audioDevice = AudioDevice.SPEAKER_PHONE
                    "Earpiece" -> audioDevice = AudioDevice.EARPIECE
                }
                meeting!!.changeMic(
                    audioDevice,
                    VideoSDK.createAudioTrack("high_quality", mContext)
                )
            }


        // video setting
        val facingModeTextView = v3.findViewById<AutoCompleteTextView>(R.id.facingMode)
        facingModeTextView.setText(
            facingMode!!.substring(0, 1).uppercase(Locale.getDefault()) + facingMode!!.substring(1)
                .lowercase(
                    Locale.getDefault()
                )
        )
        facingModeTextView.setDropDownBackgroundDrawable(
            ResourcesCompat.getDrawable(
                mContext!!.resources,
                R.drawable.dropdown_style,
                null
            )
        )
        val facingModes = mContext!!.resources.getStringArray(R.array.facingModes)
        val modeArrayAdapter: ArrayAdapter<*> =
            ArrayAdapter<Any?>((mContext)!!, R.layout.custom_drop_down_item, facingModes)
        facingModeTextView.setAdapter(modeArrayAdapter)
        facingModeTextView.onItemClickListener =
            OnItemClickListener { adapterView: AdapterView<*>?, view: View?, i: Int, l: Long ->
                if (i == 0) {
                    if ((facingMode == "back")) {
                        meeting!!.changeWebcam()
                        facingMode = facingModes.get(i)!!.lowercase(Locale.getDefault())
                    }
                }
                if (i == 1) {
                    if ((facingMode == "front")) {
                        meeting!!.changeWebcam()
                        facingMode = facingModes.get(i)!!.lowercase(Locale.getDefault())
                    }
                }
            }
    }

    private fun setAudioDeviceListeners() {
        meeting!!.setAudioDeviceChangeListener { selectedAudioDevice: AudioDevice, availableAudioDevices: Set<AudioDevice?>? ->
            selectedAudioDeviceName = selectedAudioDevice.toString()
        }
    }


    companion object {
        private var meeting: Meeting? = null
        private val CAPTURE_PERMISSION_REQUEST_CODE = 1
        private var mActivity: Activity? = null
        private var mContext: Context? = null
        fun isNullOrEmpty(str: String?): Boolean {
            return ("null" == str) || ("" == str) || (null == str)
        }
    }
}