package live.videosdk.android.ILSdemo.viewerMode

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import live.videosdk.android.ILSdemo.R
import live.videosdk.android.ILSdemo.common.stream.activity.CreateOrJoinActivity
import live.videosdk.android.ILSdemo.common.stream.activity.MainActivity
import live.videosdk.android.ILSdemo.speakerMode.manageTabs.SpeakerFragment
import live.videosdk.rtc.android.*
import live.videosdk.rtc.android.VideoView
import live.videosdk.rtc.android.lib.PubSubMessage
import live.videosdk.rtc.android.listeners.MeetingEventListener
import live.videosdk.rtc.android.listeners.ParticipantEventListener
import live.videosdk.rtc.android.listeners.PubSubMessageListener
import live.videosdk.rtc.android.model.PubSubPublishOptions
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack
import java.util.*

class ViewerFragment : Fragment() {
    private var speakerGridLayout: GridLayout? = null
    private var speakerView: MutableMap<String, View> = HashMap()
    private var speakerList: MutableList<Participant?> = ArrayList()
    private var btnLeave: ImageView? = null
    private var stream: Meeting? = null
    private var mActivity: Activity? = null
    private var mContext: Context? = null
    private var waitingLayout: LinearLayout? = null
    private var coHostListener: PubSubMessageListener? = null

    private var shareLayout: FrameLayout? = null
    private var shareView: VideoView? = null
    private var tvScreenShareParticipantName: TextView? = null
    private var screenshareEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        if (context is Activity) {
            mActivity = context
            stream = (mActivity as MainActivity?)!!.getStream()
        }
    }

    override fun onDestroy() {
        clearAllViews()
        shareView?.apply {
            removeTrack()
            releaseSurfaceViewRenderer()
        }
        stream?.apply {
            localParticipant.removeAllListeners()
            pubSub.unsubscribe("coHost", coHostListener)
            participants.values.forEach { participant ->
                participant.removeAllListeners()
            }
            removeAllListeners()
            stream = null
        }
        mContext = null
        mActivity = null
        super.onDestroy()
    }

    private fun clearAllViews() {

        speakerView.values.forEach { view ->
            view.findViewById<VideoView>(R.id.speakerVideoView)?.apply {
                visibility = View.GONE
                releaseSurfaceViewRenderer()
            }
        }
        speakerGridLayout?.removeAllViews()
        speakerView.clear()
        speakerList.clear()
        waitingLayout?.visibility = View.VISIBLE
        speakerGridLayout?.visibility = View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_viewer, container, false)

        speakerGridLayout = view.findViewById(R.id.ViewerGridLayout)
        btnLeave = view.findViewById(R.id.btnViewerLeave)
        waitingLayout = view.findViewById(R.id.waiting_layout)

        shareLayout = view.findViewById(R.id.shareLayout)
        shareView = view.findViewById(R.id.shareView)
        tvScreenShareParticipantName = view.findViewById(R.id.tvScreenShareParticipantName)

        stream?.apply {
            addEventListener(meetingEventListener)
            speakerList.clear()
            speakerView.clear()

            participants.values
                .filter { it.mode == "SEND_AND_RECV" }
                .forEach { participant ->
                    showParticipants(participant)
                }

            val sendRecvParticipants = participants.values
                .filter { it.mode == "SEND_AND_RECV" }

            if (sendRecvParticipants.isEmpty()) {
                waitingLayout?.visibility = View.VISIBLE
                speakerGridLayout?.visibility = View.GONE
            } else {
                waitingLayout?.visibility = View.GONE
                speakerGridLayout?.visibility = View.VISIBLE
                sendRecvParticipants.forEach { participant ->
                    showParticipants(participant)
                }
            }

            updateGridWithParticipants()

            btnLeave?.setOnClickListener {
                (mActivity as MainActivity?)!!.showLeaveDialog()
            }
        }

        if (stream != null) {

            coHostListener = PubSubMessageListener { pubSubMessage: PubSubMessage ->
                if ((pubSubMessage.message == stream!!.localParticipant.id)) {
                    showCoHostRequestDialog(pubSubMessage.senderName)
                }
            }
            stream!!.pubSub.subscribe("coHost", coHostListener)
        }
        return view
    }

    private val meetingEventListener = object : MeetingEventListener() {
        override fun onMeetingLeft() {
            if (isAdded) {
                val intent = Intent(mContext, CreateOrJoinActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                mActivity!!.finish()
            }
        }

        override fun onParticipantJoined(participant: Participant) {
            if (participant.mode == "SEND_AND_RECV") {

                if (speakerList.isEmpty()) {
                    waitingLayout?.visibility = View.GONE
                    speakerGridLayout?.visibility = View.VISIBLE
                }
                showParticipants(participant)
                updateGridWithParticipants()
            }
        }

        override fun onParticipantLeft(participant: Participant) {
            if (speakerList.contains(participant)) {
                speakerList.remove(participant)
                speakerView.remove(participant.id)?.let { view ->
                    view.findViewById<VideoView>(R.id.speakerVideoView)?.releaseSurfaceViewRenderer()
                }
                if (speakerList.isEmpty()) {
                    waitingLayout?.visibility = View.VISIBLE
                    speakerGridLayout?.visibility = View.GONE
                }
                updateGridWithParticipants()
            }
        }

        override fun onPresenterChanged(participantId: String?) {
            super.onPresenterChanged(participantId)

            if (!participantId.isNullOrEmpty()) {
                val presenter = stream?.participants?.get(participantId)
                if (presenter?.mode == "SEND_AND_RECV") {
                    updatePresenter(presenter)
                }
                screenshareEnabled = true
                updateGridWithParticipants()
            } else {
                // Remove screen share
                shareView?.apply {
                    removeTrack()
                    visibility = View.GONE
                }
                shareLayout?.visibility = View.GONE
                tvScreenShareParticipantName?.visibility = View.GONE
                screenshareEnabled = false
                updateGridWithParticipants()
            }
        }
    }



    private fun updatePresenter(participant: Participant?) {
        if (participant == null) return

        // Find share stream in participant
        val shareStream = participant.streams.values.find { stream ->
            stream.kind == "share"
        } ?: return

        // Update UI for screen share
        tvScreenShareParticipantName?.apply {
            text = "${participant.displayName} is presenting"
            visibility = View.VISIBLE
        }

        // Display share video
        shareLayout?.apply {
            visibility = View.VISIBLE
            bringToFront()
        }

        shareView?.apply {
            visibility = View.VISIBLE
            setZOrderMediaOverlay(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)

            // Add the video track
            (shareStream.track as? VideoTrack)?.let { videoTrack ->
                addTrack(videoTrack)
            }
        }
    }

    private fun showParticipants(participant: Participant) {
        if (participant.mode == "SEND_AND_RECV" && speakerList.size < 4 && !speakerList.contains(participant)) {
            speakerList.add(participant)
        }
    }

    private fun updateGridWithParticipants() {
        speakerGridLayout?.removeAllViews()

        speakerList.filterNotNull()
            .filter { it.mode == "SEND_AND_RECV" }
            .forEach { participant ->
                // Check if we need to create a new view or reuse existing one
                val participantView = if (speakerView.containsKey(participant.id)) {
                    // Get the existing view
                    val existingView = speakerView[participant.id]
                    // Remove it from any potential parent first
                    (existingView?.parent as? ViewGroup)?.removeView(existingView)
                    existingView
                } else {
                    // Create new view if none exists
                    val newView = createParticipantView(participant)
                    speakerView[participant.id] = newView
                    newView
                }

                // Add the view to the grid
                participantView?.let { view ->
                    // Ensure the view doesn't have a parent before adding
                    (view.parent as? ViewGroup)?.removeView(view)
                    speakerGridLayout?.addView(view)
                }
            }

        updateGridLayout()
    }

    private fun createParticipantView(participant: Participant): View {
        val participantView = LayoutInflater.from(mActivity)
            .inflate(R.layout.item_speaker, speakerGridLayout, false)

        val tvName = participantView.findViewById<TextView>(R.id.tvName)
        val txtParticipantName = participantView.findViewById<TextView>(R.id.txtParticipantName)
        val participantVideoView = participantView.findViewById<VideoView>(R.id.speakerVideoView)
        val ivMicStatus = participantView.findViewById<ImageView>(R.id.ivMicStatus)

        tvName.text = participant.displayName
        txtParticipantName.text = participant.displayName.firstOrNull()?.toString() ?: ""

        participant.streams.values.forEach { stream ->
            when {
                stream.kind.equals("video", ignoreCase = true) -> {
                    participantVideoView.visibility = View.VISIBLE
                    (stream.track as? VideoTrack)?.let { videoTrack ->
                        participantVideoView.addTrack(videoTrack)
                    }
                }
                stream.kind.equals("audio", ignoreCase = true) -> {
                    ivMicStatus.setImageResource(R.drawable.ic_audio_on)
                }
            }
        }

        participant.addEventListener(object : ParticipantEventListener() {
            override fun onStreamEnabled(stream: Stream) {
                when {
                    stream.kind.equals("video", ignoreCase = true) -> {
                        participantVideoView.visibility = View.VISIBLE
                        (stream.track as? VideoTrack)?.let { videoTrack ->
                            participantVideoView.addTrack(videoTrack)
                        }
                    }
                    stream.kind.equals("audio", ignoreCase = true) -> {
                        ivMicStatus.setImageResource(R.drawable.ic_audio_on)
                    }
                }
            }

            override fun onStreamDisabled(stream: Stream) {
                when {
                    stream.kind.equals("video", ignoreCase = true) -> {
                        (stream.track as? VideoTrack)?.let { _ ->
                            participantVideoView.removeTrack()
                        }
                        participantVideoView.visibility = View.GONE
                    }
                    stream.kind.equals("audio", ignoreCase = true) -> {
                        ivMicStatus.setImageResource(R.drawable.ic_audio_off)
                    }
                }
            }
        })

        return participantView
    }
    private fun updateGridLayout() {
        var col = 0
        var row = 0
        val childCount = speakerGridLayout?.childCount ?: 0

        if (screenshareEnabled) {
            // When screen sharing is active, arrange participants in a vertical layout
            for (i in 0 until childCount) {
                val params = speakerGridLayout!!.getChildAt(i).layoutParams as GridLayout.LayoutParams
                params.columnSpec = GridLayout.spec(0, 1, 1f)
                params.rowSpec = GridLayout.spec(i, 1, 1f)
            }
        } else {
            // Normal grid layout (2x2)
            if (childCount == 2) {
                for (i in 0 until childCount) {
                    val params = speakerGridLayout!!.getChildAt(i).layoutParams as GridLayout.LayoutParams
                    params.columnSpec = GridLayout.spec(0, 1, 1f)
                    params.rowSpec = GridLayout.spec(i, 1, 1f)
                }
            } else {
                for (i in 0 until childCount) {
                    val params = speakerGridLayout!!.getChildAt(i).layoutParams as GridLayout.LayoutParams
                    params.columnSpec = GridLayout.spec(col, 1, 1f)
                    params.rowSpec = GridLayout.spec(row, 1, 1f)

                    if (col + 1 == 2) {
                        col = 0
                        row++
                    } else {
                        col++
                    }
                }
            }
        }

        speakerGridLayout?.requestLayout()
    }

    private fun showCoHostRequestDialog(name: String) {
        val alertDialog =
            MaterialAlertDialogBuilder((mContext)!!, R.style.AlertDialogCustom).create()
        alertDialog.setCancelable(false)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.cohost_request_dialog, null)
        alertDialog.setView(dialogView)
        val message = dialogView.findViewById<TextView>(R.id.txtMessage1)
        val message2 = dialogView.findViewById<TextView>(R.id.txtMessage2)
        message.text = "$name has requested you to"
        message2.text = "join as speaker"
        val acceptBtn = dialogView.findViewById<Button>(R.id.acceptBtn)
        acceptBtn.setOnClickListener {
            stream!!.changeMode("SEND_AND_RECV")
            alertDialog.dismiss()
            val pubSubPublishOptions: PubSubPublishOptions = PubSubPublishOptions()
            pubSubPublishOptions.isPersist = false
            stream!!.pubSub.publish("coHostRequestAnswer", "accept", pubSubPublishOptions)
            requireActivity().supportFragmentManager
                .beginTransaction()
                .replace(R.id.mainLayout, SpeakerFragment(), "MainFragment")
                .commit()
        }
        val declineBtn = dialogView.findViewById<Button>(R.id.declineBtn)
        declineBtn.setOnClickListener {
            alertDialog.dismiss()
            val pubSubPublishOptions: PubSubPublishOptions = PubSubPublishOptions()
            pubSubPublishOptions.isPersist = false
            stream!!.pubSub.publish("coHostRequestAnswer", "decline", pubSubPublishOptions)
        }
        alertDialog.show()
    }


    companion object {
        fun newInstance(): ViewerFragment {
            return ViewerFragment()
        }
    }
}