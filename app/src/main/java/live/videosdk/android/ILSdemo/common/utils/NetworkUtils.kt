package live.videosdk.android.ILSdemo.common.utils

import android.content.Context
import android.net.ConnectivityManager
import android.widget.Toast
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import live.videosdk.android.ILSdemo.BuildConfig
import org.json.JSONException
import org.json.JSONObject

class NetworkUtils(private var context: Context) {
    private val AUTH_TOKEN: String = BuildConfig.AUTH_TOKEN
    private val AUTH_URL: String = BuildConfig.AUTH_URL

    init {
        this.context = context
    }

    val isNetworkAvailable: Boolean
        get() {
            val manager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = manager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }

    fun getToken(responseListener: ResponseListener<String?>) {
        if (!isNullOrEmpty(AUTH_TOKEN) && !isNullOrEmpty(AUTH_URL)) {
            Toast.makeText(
                context,
                "Please Provide only one - either auth_token or auth_url",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!isNullOrEmpty(AUTH_TOKEN)) {
            responseListener.onResponse(AUTH_TOKEN)
            return
        }
        if (!isNullOrEmpty(AUTH_URL)) {
            AndroidNetworking.get("$AUTH_URL/get-token")
                .build()
                .getAsJSONObject(object : JSONObjectRequestListener {
                    override fun onResponse(response: JSONObject) {
                        try {
                            val token = response.getString("token")
                            responseListener.onResponse(token)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onError(anError: ANError) {
                        anError.printStackTrace()
                        Toast.makeText(
                            context,
                            anError.errorDetail, Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            return
        }
        Toast.makeText(
            context,
            "Please Provide auth_token or auth_url", Toast.LENGTH_SHORT
        ).show()
    }

    fun createStream(token: String?, meetingEventListener: ResponseListener<String?>) {
        AndroidNetworking.post("https://api.videosdk.live/v2/rooms")
            .addHeaders("Authorization", token)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject) {
                    try {
                        val streamId = response.getString("roomId")
                        meetingEventListener.onResponse(streamId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onError(anError: ANError) {
                    anError.printStackTrace()
                    Toast.makeText(
                        context, anError.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    fun joinStream(
        token: String?,
        roomId: String,
        meetingEventListener: ResponseListener<String?>
    ) {
        AndroidNetworking.post("https://api.videosdk.live/v1/meetings/$roomId")
            .addHeaders("Authorization", token)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener {
                override fun onResponse(response: JSONObject) {
                    meetingEventListener.onResponse(roomId)
                }
                override fun onError(anError: ANError) {
                    anError.printStackTrace()
                    Toast.makeText(
                        context, anError.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    companion object {
        fun isNullOrEmpty(str: String?): Boolean {
            return "null" == str || "" == str || null == str
        }
    }
}
