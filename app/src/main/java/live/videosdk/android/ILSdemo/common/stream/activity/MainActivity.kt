package live.videosdk.android.ILSdemo.common.stream.activity

import live.videosdk.android.ILSdemo.common.utils.sdkScope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch



import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nabinbhandari.android.permissions.PermissionHandler
import com.nabinbhandari.android.permissions.Permissions
import live.videosdk.android.ILSdemo.R
import live.videosdk.android.ILSdemo.speakerMode.manageTabs.SpeakerFragment
import live.videosdk.android.ILSdemo.viewerMode.ViewerFragment
import live.videosdk.rtc.android.CustomStreamTrack
import live.videosdk.rtc.android.Room
import live.videosdk.rtc.android.VideoSDK
import live.videosdk.rtc.android.listeners.RoomEventListener

class MainActivity : AppCompatActivity() {

    private var meeting: Room? = null
    private var facingMode: String? = "front"
    
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val mode = intent.getStringExtra("mode")
        val token = intent.getStringExtra("token")
        val micEnabled = intent.getBooleanExtra("micEnabled", true)
        val webcamEnabled = intent.getBooleanExtra("webcamEnabled", true)
        val streamId = intent.getStringExtra("streamId")
        var localParticipantName = intent.getStringExtra("participantName")
        if (localParticipantName == null) {
            localParticipantName = "John Doe"
        }

        meeting = VideoSDK.createRoom(
            streamId!!, token!!, localParticipantName,
            micEnabled, webcamEnabled,
            mode = if (mode == "VIEWER" || mode == "RECV_ONLY") live.videosdk.rtc.android.lib.Mode.RECV_ONLY else live.videosdk.rtc.android.lib.Mode.SEND_AND_RECV
        )
        meeting!!.addEventListener(object : RoomEventListener() {
            override fun onError(error: live.videosdk.rtc.android.lib.VideoSDKError) {
                android.widget.Toast.makeText(this@MainActivity, "${error.code} ${error.message}", android.widget.Toast.LENGTH_LONG).show()
            }

            override fun onRoomJoined(switchRoomId: String?) {
                if (meeting != null) {
                    if (mode == "SEND_AND_RECV") {
                        sdkScope.launch { meeting!!.localParticipant.pin(live.videosdk.rtc.android.PinType.SHARE_AND_CAM) }
                        supportFragmentManager
                            .beginTransaction()
                            .replace(R.id.mainLayout, SpeakerFragment(), "MainFragment")
                            .commit()
                        findViewById<View>(R.id.progress_layout).visibility = View.GONE
                    } else if (mode == "RECV_ONLY") {
                        supportFragmentManager
                            .beginTransaction()
                            .replace(R.id.mainLayout, ViewerFragment(), "viewerFragment")
                            .commit()
                        findViewById<View>(R.id.progress_layout).visibility = View.GONE
                    }
                }
            }
        })
        checkPermissions()
    }


    private fun checkPermissions() {
        val permissionList: MutableList<String> = ArrayList()
        permissionList.add(Manifest.permission.INTERNET)
        permissionList.add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        permissionList.add(Manifest.permission.RECORD_AUDIO)
        permissionList.add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissionList.add(
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val rationale = "Please provide permissions"
        val options =
            Permissions.Options().setRationaleDialogTitle("Info").setSettingsDialogTitle("Warning")
        Permissions.check(
            this,
            permissionList.toTypedArray(),
            rationale,
            options,
            permissionHandler
        )
    }

    private val permissionHandler: PermissionHandler = object : PermissionHandler() {
        override fun onGranted() {
            if (meeting != null) sdkScope.launch { meeting!!.join() }
        }
    }

    fun getStream(): Room? {
        return meeting
    }

    fun getFacingMode(): String? {
        return facingMode
    }

    override fun onDestroy() {
        if (meeting != null) {

            meeting = null
        }
        super.onDestroy()
    }

    fun showLeaveDialog() {
        val alertDialog =
            MaterialAlertDialogBuilder(this@MainActivity, R.style.AlertDialogCustom).create()
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_style, null)
        alertDialog.setView(dialogView)
        val message = dialogView.findViewById<TextView>(R.id.message)
        message.text = "Are you sure you want to leave?"
        val positiveButton = dialogView.findViewById<Button>(R.id.positiveBtn)
        positiveButton.text = "Yes"
        positiveButton.setOnClickListener {
            sdkScope.launch { meeting!!.leave() }
            alertDialog.dismiss()
            val intents = Intent(this@MainActivity, CreateOrJoinActivity::class.java)
            intents.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            startActivity(intents)
            finish()
        }
        val negativeButton = dialogView.findViewById<Button>(R.id.negativeBtn)
        negativeButton.text = "No"
        negativeButton.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
    }

    override fun onBackPressed() {
        showLeaveDialog()
    }
}
