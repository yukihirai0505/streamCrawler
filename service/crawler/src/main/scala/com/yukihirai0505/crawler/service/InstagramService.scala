package com.yukihirai0505.crawler.service

import com.typesafe.scalalogging.LazyLogging
import com.yukihirai0505.crawler.constants.Constants
import com.yukihirai0505.crawler.model.{InstagramDto, InstagramHashTagEntity, InstagramMediaDataEntity, InstagramMediaDto}
import com.yukihirai0505.crawler.utils.DateUtil
import com.yukihirai0505.iService.responses.{MediaNode, MediaQuery, PageInfo}
import com.yukihirai0505.iService.services.MediaService

import scala.concurrent.{ExecutionContextExecutor, Future}

trait InstagramService extends LazyLogging {
  val postSize = 100
  type MediaDto = InstagramDto[InstagramMediaDto]

  def getPosts(source: InstagramDto[InstagramMediaDto])(implicit ec: ExecutionContextExecutor): Future[Seq[InstagramMediaDataEntity]] = {
    def nodesToEntities(nodes: Seq[MediaNode]) = {
      Future successful nodes.map { n =>
        val caption = n.caption.getOrElse("")
        InstagramMediaDataEntity(
          mediaId = n.id,
          userId = n.owner.id,
          createdTime = DateUtil.getDateStrFromTimestamp(n.date),
          link = Constants.instagramPostUrl(n.code),
          tagName = extractHashTag(caption),
          likes = n.likes.count,
          comments = n.comments.count,
          caption = caption,
          timestamp = DateUtil.getNowStr
        )
      }
    }

    MediaService.getPosts(source.dto.hashTag).flatMap {
      case Right(tag) => nodesToEntities(tag.media.nodes ++ tag.topPosts.nodes)
      case Left(e) =>
        logger.warn("getPosts", e.getMessage)
        Future successful Seq.empty
    }
  }

  def getPostsPaging(source: MediaDto)
                    (implicit ec: ExecutionContextExecutor): Future[MediaDto] = {
    def makeSource(endCursor: String) = {
      MediaService.getPostsPaging(source.dto.hashTag, size = postSize, endCursor).flatMap {
        case Right(mediaQuery) =>
          val entities = mediaQuery.data.hashtag.edgeHashtagToMedia.edges.map { n =>
            val caption = n.node.edgeMediaToCaption.edges.headOption.map(_.node.text).getOrElse("")
            InstagramMediaDataEntity(
              mediaId = n.node.id,
              userId = n.node.owner.id,
              createdTime = DateUtil.getDateStrFromTimestamp(n.node.takenAtTimestamp),
              link = Constants.instagramPostUrl(n.node.shortcode),
              tagName = extractHashTag(caption),
              likes = n.node.edgeLikedBy.count,
              comments = n.node.edgeMediaToComment.count,
              caption = caption,
              timestamp = DateUtil.getNowStr
            )
          }
          Future successful source.copy(
            dto = source.dto.copy(
              instagramMedia = entities,
              pageInfo = Some(mediaQuery.data.hashtag.edgeHashtagToMedia.pageInfo)
            )
          )
        case Left(e) =>
          logger.warn("getPostsPaging", e)
          Future successful source
      }
    }

    source.dto.pageInfo match {
      case Some(pi) =>
        if (pi.hasNextPage) makeSource(pi.endCursor.getOrElse(""))
        else Future successful source
      case None => makeSource("")
    }
  }

  def getTag(source: MediaDto)(implicit ec: ExecutionContextExecutor): Future[Either[Throwable, InstagramHashTagEntity]] = {
    MediaService.getPosts(source.dto.hashTag, List.empty).flatMap {
      case Right(tag) =>
        Future successful Right(InstagramHashTagEntity(
          tagName = source.dto.hashTag,
          mediaCount = tag.media.count,
          timestamp = DateUtil.getNowStr
        ))
      case Left(e) =>
        if (e.getMessage.contains("is baned by instagram")) Future successful Right(InstagramHashTagEntity(
          tagName = source.dto.hashTag,
          isBan = true
        ))
        else Future successful Left(new Exception(e.getMessage))
    }
  }

  private def extractHashTag(caption: String): Seq[String] = {
    val pattern = """#[a-zA-Z0-9_\u3041-\u3094\u3099-\u309C\u30A1-\u30FA\u3400-\uD7FF\uFF10-\uFF19\uFF20-\uFF3A\uFF41-\uFF5A\uFF66-\uFF9E|\w-ー]*""".r
    pattern.findAllIn(caption).map(_.replace("#", "")).toSeq
  }
}
