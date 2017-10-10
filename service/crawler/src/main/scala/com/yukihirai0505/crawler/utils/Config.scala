package com.yukihirai0505.crawler.utils

import com.typesafe.config.ConfigFactory

/**
  * author Yuki Hirai on 2017/08/24.
  */
trait Config {
  lazy val config = ConfigFactory.load()
  lazy val elasticSearchConfig = config.getConfig("elasticsearch")
}
