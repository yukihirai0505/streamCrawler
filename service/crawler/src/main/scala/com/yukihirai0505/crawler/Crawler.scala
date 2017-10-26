package com.yukihirai0505.crawler

import com.typesafe.scalalogging.LazyLogging
import com.yukihirai0505.crawler.stream.CrawlerStreamService

object PostCrawler extends BaseCrawler {
  logger.info("-------------start PostCrawler")
  new CrawlerStreamService().postGraph
}

object PostTagCrawler extends BaseCrawler {
  logger.info("-------------start PostTagCrawler")
  new CrawlerStreamService().postTagGraph
}

object TagPostCrawler extends BaseCrawler {
  logger.info("-------------start TagPostCrawler")
  args.headOption match {
    case Some(tagName) =>
      new CrawlerStreamService().tagPostGraph(tagName)
    case None =>
      logger.warn("タグを引数に指定してください")
      System.exit(1)
  }
}

trait BaseCrawler extends App with LazyLogging