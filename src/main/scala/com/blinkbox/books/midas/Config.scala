package com.blinkbox.books.midas

import java.net.URL
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import com.typesafe.config.Config
import com.blinkbox.books.config.RichConfig

import scala.concurrent.duration.FiniteDuration

case class MidasConfig(url: URL, timeout: FiniteDuration)

object MidasConfig {
  def apply(config: Config): MidasConfig = MidasConfig(
    url = config.getHttpUrl("groupService.midas.api.public.internalUrl"),
    timeout = config.getDuration("groupService.midas.api.public.timeout", TimeUnit.MILLISECONDS).millis
  )
}
