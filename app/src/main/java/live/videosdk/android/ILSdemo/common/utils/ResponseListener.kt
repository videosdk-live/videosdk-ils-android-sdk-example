package live.videosdk.android.ILSdemo.common.utils

interface ResponseListener<T> {
    fun onResponse(response: T)
}