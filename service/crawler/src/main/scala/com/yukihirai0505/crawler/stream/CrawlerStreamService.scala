package com.yukihirai0505.crawler.stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, MergePreferred, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, ThrottleMode}
import akka.{Done, NotUsed}
import com.yukihirai0505.crawler.model.{InstagramDto, InstagramMediaDto}
import com.yukihirai0505.crawler.service.{ElasticsearchService, InstagramService}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * Created by Yuky on 2017/10/10.
  */
class CrawlerStreamService extends InstagramService {
  val limit = 1000

  def postGraph: Future[Done] = {
    val name = "postGraph"
    implicit val system: ActorSystem = ActorSystem(name)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    def getPostFlow(source: MediaDto)(implicit ec: ExecutionContextExecutor) = {
      def execute() = {
        getPosts(source).flatMap { r =>
          ElasticsearchService.savePosts(r).flatMap(_ =>
            Future successful source.copy(dto = source.dto.copy(instagramMedia = r))
          )
        }
      }

      commonFlow(source, execute, "getPostFlow")
    }

    ElasticsearchService.searchTags.flatMap { tags =>
      val source: Source[MediaDto, NotUsed] = Source(tags.to[scala.collection.immutable.Seq])
      val sink = Sink.foreachParallel[MediaDto](3) { s =>
        logger.info(s"-------------start $name sink")
        Future successful s
      }
      val graph = RunnableGraph.fromGraph[Future[Done]](GraphDSL.create(sink) { implicit b =>
        sink =>
          import GraphDSL.Implicits._
          val throttle = b.add(Flow[MediaDto].throttle(limit, 1.hour, 0, ThrottleMode.shaping))
          val flowPosts = b.add(Flow[MediaDto].mapAsyncUnordered(3)(getPostFlow))

          source ~> throttle ~> flowPosts ~> sink
          ClosedShape
      })
      commonGraph(graph, name)
    }
  }

  def postTagGraph: Future[Done] = {
    val name = "postTagGraph"
    implicit val system: ActorSystem = ActorSystem(name)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    def getTagFlow(source: MediaDto)(implicit ec: ExecutionContextExecutor) = {
      def execute() = {
        getTag(source).flatMap {
          case Right(tag) =>
            ElasticsearchService.saveTag(tag).flatMap(_ => Future successful source)
          case Left(e) =>
            logger.warn("getTagFlow", e)
            Future successful source
        }
      }

      commonFlow(source, execute, "getTagFlow")
    }

    ElasticsearchService.searchTagsFromPosts.flatMap { tags =>
      val source: Source[MediaDto, NotUsed] = Source(tags.to[scala.collection.immutable.Seq])
      val sink = Sink.foreachParallel[MediaDto](3) { s =>
        logger.info(s"-------------start $name sink => hashtag: ${s.dto.hashTag}")
        Future successful s
      }
      val graph = RunnableGraph.fromGraph[Future[Done]](GraphDSL.create(sink) { implicit b =>
        sink =>
          import GraphDSL.Implicits._
          val throttle = b.add(Flow[MediaDto].throttle(limit, 1.hour, 0, ThrottleMode.shaping))
          val flowTags = b.add(Flow[MediaDto].mapAsyncUnordered(3)(getTagFlow))

          source ~> throttle ~> flowTags ~> sink
          ClosedShape
      })
      commonGraph(graph, name)
    }
  }

  def tagPostGraph(tagName: String): Future[Done] = {
    val name = "tagPostGraph"
    implicit val system: ActorSystem = ActorSystem(name)
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    def getPostPagingFlow(s: MediaDto)(implicit ec: ExecutionContextExecutor) = {
      def execute() = {
        getPostsPaging(s).flatMap { result =>
          ElasticsearchService.savePosts(result.dto.instagramMedia).flatMap(_ => Future successful result)
        }
      }

      commonFlow(s, execute, "getPostPagingFlow")
    }

    val source: Source[MediaDto, NotUsed] = Source.single(
      InstagramDto(
        dto = InstagramMediaDto(
          hashTag = tagName
        )
      ))
    val sink = Sink.foreachParallel[MediaDto](3) { s =>
      logger.info(s"-------------start $name sink")
      Future successful s
    }
    val graph = RunnableGraph.fromGraph[Future[Done]](GraphDSL.create(sink) { implicit b =>
      sink =>
        import GraphDSL.Implicits._
        val throttle = b.add(Flow[MediaDto].throttle(limit, 1.hour, 1, ThrottleMode.shaping))
        val flowPosts = b.add(Flow[MediaDto].mapAsyncUnordered(3)(getPostPagingFlow))
        val merge = b.add(MergePreferred[MediaDto](1))
        val bcast = b.add(Broadcast[MediaDto](2))
        val loopFilter = b.add(Flow[MediaDto].filter(x => x.dto.pageInfo.exists(_.hasNextPage)))

        source ~> merge ~> throttle ~> flowPosts ~> bcast ~> sink
        merge.preferred <~ loopFilter <~ bcast
        ClosedShape
    })
    commonGraph(graph, name)
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
