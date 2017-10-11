package com.yukihirai0505.crawler.service

import com.typesafe.scalalogging.LazyLogging
import com.yukihirai0505.crawler.constants.Constants
import com.yukihirai0505.crawler.model.{InstagramDto, InstagramHashTagEntity, InstagramMediaDataEntity, InstagramMediaDto}
import com.yukihirai0505.crawler.utils.DateUtil
import com.yukihirai0505.iService.services.MediaService

import scala.concurrent.{ExecutionContextExecutor, Future}

trait InstagramService extends LazyLogging {
  def getPosts(source: InstagramDto[InstagramMediaDto])(implicit ec: ExecutionContextExecutor): Future[Seq[InstagramMediaDataEntity]] = {
    def extractHashTag(caption: String): Seq[String] = {
      val pattern = """#[a-zA-Z0-9_\u3041-\u3094\u3099-\u309C\u30A1-\u30FA\u3400-\uD7FF\uFF10-\uFF19\uFF20-\uFF3A\uFF41-\uFF5A\uFF66-\uFF9E|\w-ー]*""".r
      pattern.findAllIn(caption).map(_.replace("#", "")).toSeq
    }

    MediaService.getPosts(source.dto.hashTag, List.empty).flatMap { tag =>
      val nodes = tag.media.nodes.map { n =>
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
      if (tag.media.pageInfo.hasNextPage) {
        MediaService.getPostsPaging(source.dto.hashTag, tag.media.pageInfo.endCursor).flatMap {
          case Right(posts) => Future successful posts.data.hashtag.edgeHashtagToMedia.edges.map { n =>
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
          } ++ nodes
          case Left(e) =>
            logger.warn("getPosts", e)
            Future successful nodes
        }
      } else Future successful nodes
    }
  }

  def getTag(source: InstagramDto[InstagramMediaDto])(implicit ec: ExecutionContextExecutor): Future[InstagramHashTagEntity] = {
    // TODO: Update iService library and if the tag is baned, isBan equal true
    MediaService.getPosts(source.dto.hashTag, List.empty).flatMap { tag =>
      Future successful InstagramHashTagEntity(
        tagName = source.dto.hashTag,
        mediaCount = tag.media.count,
        timestamp = DateUtil.getNowStr
      )
    }
  }

}
