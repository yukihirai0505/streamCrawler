package com.yukihirai0505.crawler.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, ThrottleMode}
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.LazyLogging
import com.yukihirai0505.crawler.constants.Constants
import com.yukihirai0505.crawler.model.{InstagramDto, InstagramMediaDataEntity, InstagramMediaDto}
import com.yukihirai0505.crawler.service.ElasticsearchService
import com.yukihirai0505.crawler.utils.DateUtil
import com.yukihirai0505.iService.services.MediaService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Created by Yuky on 2017/10/10.
  */
class CrawlerStreamService extends LazyLogging {
  def postGraph = {
    val name = "postGraph"
    implicit val system: ActorSystem = ActorSystem(name)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    type Dto = InstagramDto[InstagramMediaDto]

    def getPostFlow(s: Dto)(implicit ec: ExecutionContextExecutor) = {
      def extractHashTag(caption: String): Seq[String] = {
        val pattern = """#[a-zA-Z0-9_\u3041-\u3094\u3099-\u309C\u30A1-\u30FA\u3400-\uD7FF\uFF10-\uFF19\uFF20-\uFF3A\uFF41-\uFF5A\uFF66-\uFF9E|\w-ー]*""".r
        pattern.findAllIn(caption).toSeq
      }

      def execute() = {
        MediaService.getPosts(s.dto.hashTag, List.empty).flatMap { tag =>
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
            MediaService.getPostsPaging(s.dto.hashTag, tag.media.pageInfo.endCursor).flatMap {
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
              case Left(e) => Future successful nodes
            }
          } else Future successful nodes
        }.flatMap { r =>
          ElasticsearchService.savePosts(r).flatMap(_ =>
            Future successful s.copy(dto = s.dto.copy(instagramMedia = r))
          )
        }
      }

      commonFlow(s, execute, "getPostFlow")
    }

    ElasticsearchService.searchTags.flatMap { tags =>
      val source: Source[Dto, NotUsed] = Source(tags.to[scala.collection.immutable.Seq])
      val sink = Sink.foreachParallel[Dto](3) { s =>
        logger.info(s"-------------start $name sink")
        Future successful s
      }
      val graph = RunnableGraph.fromGraph[Future[Done]](GraphDSL.create(sink) { implicit b =>
        sink =>
          import GraphDSL.Implicits._
          val limit = 1000
          val throttle = b.add(Flow[Dto].throttle(limit, 1.hour, 0, ThrottleMode.shaping))
          val flowPosts = b.add(Flow[Dto].mapAsyncUnordered(3)(getPostFlow))

          source ~> throttle ~> flowPosts ~> sink
          ClosedShape
      })
      commonGraph(graph, name)
    }
  }

  private def commonFlow[T](s: InstagramDto[T], execute: () => Future[InstagramDto[T]], name: String)
                           (implicit ec: ExecutionContextExecutor): Future[InstagramDto[T]] = {
    val methodName = s"CrawlerStreamService.$name"
    logger.debug(s"-------------start $methodName s=$s")
    try {
      execute().recover {
        case e: Exception =>
          logger.warn(s"-------------failure $methodName", e)
          s.copy(exception = Some(e))
      }
    } catch {
      case e: Exception =>
        logger.warn(s"-------------failure $methodName", e)
        Future successful s.copy(exception = Some(e))
    }
  }

  private def commonGraph(graph: RunnableGraph[Future[Done]], name: String)
                         (implicit system: ActorSystem, ec: ExecutionContextExecutor, materializer: ActorMaterializer): Future[Done] = {
    graph.run().andThen {
      case _ =>
        logger.info(s"-------------finish $name")
        system.terminate()
        System.exit(0)
    }.recover {
      case e =>
        logger.info(s"-------------failure $name", e)
        system.terminate()
        System.exit(1)
        Done
    }
  }
}
