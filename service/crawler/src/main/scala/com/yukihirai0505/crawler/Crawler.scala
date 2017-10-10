package com.yukihirai0505.crawler

import com.typesafe.scalalogging.LazyLogging
import com.yukihirai0505.crawler.stream.CrawlerStreamService

object Crawler extends App with LazyLogging {
  logger.info("-------------start Crawler")
  new CrawlerStreamService().postGraph
}
