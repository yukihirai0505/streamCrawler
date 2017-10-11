package com.yukihirai0505.crawler

import com.typesafe.scalalogging.LazyLogging
import com.yukihirai0505.crawler.stream.CrawlerStreamService

object PostCrawler extends App with LazyLogging {
  logger.info("-------------start PostCrawler")
  new CrawlerStreamService().postGraph
}

object TagCrawler extends App with LazyLogging {
  logger.info("-------------start TagCrawler")
  new CrawlerStreamService().tagGraph
}

