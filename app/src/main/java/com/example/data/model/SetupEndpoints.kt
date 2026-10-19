package com.example.data.model

import com.example.data.api.SetupDto
import java.net.Inet6Address
import java.net.InetAddress

/** Accept only literal IPv6 addresses supplied for this profile; never resolve a hostname. */
fun setupIpv6Addresses(setup: SetupDto?): List<String> = setup?.ipv6.orEmpty()
  .map(String::trim)
  .filter { value ->
    value.contains(':') && value.matches(Regex("[0-9a-fA-F:]+")) &&
      runCatching { InetAddress.getByName(value) is Inet6Address }.getOrDefault(false)
  }.distinct()
