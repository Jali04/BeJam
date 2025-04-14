package com.example.bejam.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.bejam.AuthCallbackActivity
import fi.iki.elonen.NanoHTTPD

class LocalHttpServer(private val context: Context) : NanoHTTPD(8888) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val parameters = session.parameters

        Log.d("LOCAL_SERVER", "Request on $uri")

        if (uri == "/callback" && parameters["code"] != null) {
            val code = parameters["code"]?.firstOrNull()

            Log.d("LOCAL_SERVER", "Code received: $code")

            // Weiterleiten in die App (z. B. zur AuthCallbackActivity)
            val intent = Intent(context, AuthCallbackActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("code", code)
            }
            context.startActivity(intent)

            return newFixedLengthResponse(
                Response.Status.OK,
                "text/html",
                "<html><body><h2>Login erfolgreich! Du kannst zurück zur App gehen.</h2></body></html>"
            )
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
    }
}
