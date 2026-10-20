package com.example.data.api

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Bounds security JSON before Moshi materializes unknown nested values. */
internal class SecurityResponseSizeInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val response = chain.proceed(request)
    val pathSegments = request.url.pathSegments
    if (
      request.method != "GET" ||
      pathSegments.size != 3 ||
      pathSegments[0] != "profiles" ||
      pathSegments[1].isBlank() ||
      pathSegments[2] != "security"
    ) return response

    val body = response.body ?: return response
    if (body.contentLength() > MAX_SECURITY_RESPONSE_BYTES) {
      response.close()
      throw IOException("NextDNS security response exceeded the size limit")
    }

    val source = body.source()
    if (source.request(MAX_SECURITY_RESPONSE_BYTES + 1) && source.buffer.size > MAX_SECURITY_RESPONSE_BYTES) {
      response.close()
      throw IOException("NextDNS security response exceeded the size limit")
    }
    return response
  }

  private companion object {
    const val MAX_SECURITY_RESPONSE_BYTES = 512L * 1024L
  }
}
