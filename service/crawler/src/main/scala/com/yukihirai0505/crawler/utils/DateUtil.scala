package com.yukihirai0505.crawler.utils

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

/**
  * author Yuki Hirai on 2017/09/13.
  */
object DateUtil {
  val fmt = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss+09:00")

  def getNowStr: String = {
    toStrFormat(new DateTime())
  }

  def getDateStrFromTimestamp(timestamp: Long) = {
    toStrFormat(new DateTime(timestamp * 1000L))
  }

  def toStrFormat(date: DateTime): String = {
    fmt.print(date)
  }
}
